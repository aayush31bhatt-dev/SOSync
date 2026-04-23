from __future__ import annotations

import os
import sqlite3
import tempfile
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_DB_PATH = PROJECT_ROOT / "backend" / "app" / "smartcommunity.db"
RENDER_PERSISTENT_DB_PATH = Path("/var/data/smartcommunity.db")
TEMP_DB_PATH = Path(tempfile.gettempdir()) / "smartcommunity.db"


def _read_bool_env(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


def _is_render_runtime() -> bool:
    return (
        os.getenv("RENDER", "").strip().lower() == "true"
        or bool(os.getenv("RENDER_SERVICE_ID", "").strip())
    )


def _is_render_persistent_path(path: Path) -> bool:
    normalized = str(path).replace("\\", "/")
    return normalized.startswith("/var/data/")


def _path_writable(path: Path) -> bool:
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        probe = path.parent / ".smartcommunity-db-probe"
        probe.write_text("ok", encoding="utf-8")
        probe.unlink(missing_ok=True)
        return True
    except Exception:
        return False


RENDER_RUNTIME = _is_render_runtime()
STRICT_DB_PATH = _read_bool_env("SMARTCOMMUNITY_STRICT_DB_PATH", False)


def _resolve_db_path() -> tuple[Path, str]:
    configured = os.getenv("SMARTCOMMUNITY_DB_PATH", "").strip()
    configured_path = Path(configured).expanduser() if configured else None

    if configured_path is not None:
        if _path_writable(configured_path):
            if RENDER_RUNTIME and STRICT_DB_PATH and not _is_render_persistent_path(configured_path):
                raise RuntimeError(
                    "SMARTCOMMUNITY_DB_PATH must point under /var/data on Render to avoid data loss. "
                    f"Current resolved path: {configured_path}"
                )
            return configured_path, "configured"

        if STRICT_DB_PATH:
            raise RuntimeError(
                "Configured SMARTCOMMUNITY_DB_PATH is not writable. "
                f"Current value: {configured_path}"
            )

    if RENDER_RUNTIME:
        if _path_writable(RENDER_PERSISTENT_DB_PATH):
            return RENDER_PERSISTENT_DB_PATH, "render_default"

        if STRICT_DB_PATH:
            raise RuntimeError(
                "Render persistent DB path is not writable. Ensure persistent disk is mounted at /var/data."
            )

    fallback_candidates: list[tuple[str, Path]] = [
        ("repo_default", DEFAULT_DB_PATH),
        ("temp_fallback", TEMP_DB_PATH),
    ]

    for source, candidate in fallback_candidates:
        if _path_writable(candidate):
            return candidate, source

    raise RuntimeError("No writable path found for SQLite database.")


DB_PATH, DB_PATH_SOURCE = _resolve_db_path()
DB_PATH_IS_PERSISTENT = _is_render_persistent_path(DB_PATH)


def get_connection() -> sqlite3.Connection:
    connection = sqlite3.connect(DB_PATH, timeout=30)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys = ON")
    connection.execute("PRAGMA busy_timeout = 30000")
    return connection


def init_auth_db() -> None:
    with get_connection() as connection:
        connection.execute("PRAGMA journal_mode = WAL")
        connection.execute("PRAGMA synchronous = NORMAL")

        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS users (
                user_id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                username TEXT NOT NULL UNIQUE,
                email TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                gender TEXT NOT NULL,
                phone_number TEXT NOT NULL,
                emergency_contact_name TEXT NOT NULL,
                emergency_contact_phone TEXT NOT NULL,
                trusted_contacts_enabled INTEGER NOT NULL DEFAULT 1,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """
        )

        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS revoked_tokens (
                jti TEXT PRIMARY KEY,
                expires_at INTEGER NOT NULL,
                created_at TEXT NOT NULL
            )
            """
        )

        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS password_reset_tokens (
                token_hash TEXT PRIMARY KEY,
                user_id INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                used INTEGER NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(user_id) ON DELETE CASCADE
            )
            """
        )

        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS direct_messages (
                message_id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender_user_id INTEGER NOT NULL,
                recipient_user_id INTEGER NOT NULL,
                message_text TEXT NOT NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY(sender_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
                FOREIGN KEY(recipient_user_id) REFERENCES users(user_id) ON DELETE CASCADE
            )
            """
        )

        connection.execute(
            """
            CREATE TABLE IF NOT EXISTS live_location_shares (
                share_id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender_user_id INTEGER NOT NULL,
                recipient_user_id INTEGER NOT NULL,
                started_at TEXT NOT NULL,
                expires_at INTEGER NOT NULL,
                is_active INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY(sender_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
                FOREIGN KEY(recipient_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
                UNIQUE(sender_user_id, recipient_user_id)
            )
            """
        )

        connection.execute(
            "CREATE INDEX IF NOT EXISTS idx_users_username ON users(username)"
        )
        connection.execute(
            "CREATE INDEX IF NOT EXISTS idx_users_email ON users(email)"
        )
        connection.execute(
            "CREATE INDEX IF NOT EXISTS idx_reset_user_id ON password_reset_tokens(user_id)"
        )
        connection.execute(
            "CREATE INDEX IF NOT EXISTS idx_revoked_expires ON revoked_tokens(expires_at)"
        )
        connection.execute(
            "CREATE INDEX IF NOT EXISTS idx_messages_sender_created ON direct_messages(sender_user_id, created_at)"
        )
        connection.execute(
            "CREATE INDEX IF NOT EXISTS idx_messages_recipient_created ON direct_messages(recipient_user_id, created_at)"
        )
        connection.execute(
            "CREATE INDEX IF NOT EXISTS idx_live_share_recipient_active ON live_location_shares(recipient_user_id, is_active, expires_at)"
        )
        connection.execute(
            "CREATE INDEX IF NOT EXISTS idx_live_share_sender_active ON live_location_shares(sender_user_id, is_active, expires_at)"
        )


def cleanup_expired_records(now_epoch: int) -> None:
    with get_connection() as connection:
        connection.execute("DELETE FROM revoked_tokens WHERE expires_at < ?", (now_epoch,))
        connection.execute(
            "DELETE FROM password_reset_tokens WHERE expires_at < ? OR used = 1",
            (now_epoch,),
        )
