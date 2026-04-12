from __future__ import annotations

import os
import sqlite3
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_DB_PATH = PROJECT_ROOT / "backend" / "app" / "smartcommunity.db"
DB_PATH = Path(os.getenv("SMARTCOMMUNITY_DB_PATH", str(DEFAULT_DB_PATH))).expanduser()
DB_PATH.parent.mkdir(parents=True, exist_ok=True)


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
