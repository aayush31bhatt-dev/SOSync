from __future__ import annotations

import os
import re
import sqlite3
import tempfile
from pathlib import Path

try:
    from pymongo import ASCENDING, DESCENDING, MongoClient, ReturnDocument
    from pymongo.errors import DuplicateKeyError
except Exception:  # pragma: no cover
    MongoClient = None  # type: ignore[assignment]
    ReturnDocument = None  # type: ignore[assignment]
    DuplicateKeyError = Exception  # type: ignore[assignment]
    ASCENDING = 1  # type: ignore[assignment]
    DESCENDING = -1  # type: ignore[assignment]


AUTH_DB_BACKEND = os.getenv("AUTH_DB_BACKEND", "sqlite").strip().lower()
MONGODB_URI = os.getenv("MONGODB_URI", "").strip() or os.getenv("MONGO_URI", "").strip()
MONGODB_DB_NAME = os.getenv("MONGODB_DB_NAME", "sosync").strip() or "sosync"

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
AUTH_DB_STRICT_BACKEND = _read_bool_env("AUTH_DB_STRICT_BACKEND", False)


def _resolve_sqlite_db_path() -> tuple[Path, str]:
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


SQLITE_DB_PATH, SQLITE_DB_PATH_SOURCE = _resolve_sqlite_db_path()

_MONGO_CLIENT = None
REQUESTED_DB_BACKEND = AUTH_DB_BACKEND if AUTH_DB_BACKEND else "sqlite"
DB_BACKEND_FALLBACK_REASON: str | None = None


def _probe_mongodb_client() -> tuple[bool, str | None]:
    global _MONGO_CLIENT

    if MongoClient is None:
        return False, "pymongo import failed; using SQLite fallback"
    if not MONGODB_URI:
        return False, "MONGODB_URI is not set; using SQLite fallback"

    try:
        _MONGO_CLIENT = MongoClient(
            MONGODB_URI,
            serverSelectionTimeoutMS=10000,
            connectTimeoutMS=10000,
        )
        _MONGO_CLIENT.admin.command("ping")
        return True, None
    except Exception as exc:
        _MONGO_CLIENT = None
        return False, f"MongoDB ping failed ({exc.__class__.__name__}); using SQLite fallback"


if REQUESTED_DB_BACKEND not in {"sqlite", "mongodb"}:
    DB_BACKEND = "sqlite"
    DB_BACKEND_FALLBACK_REASON = (
        f"Unsupported AUTH_DB_BACKEND='{REQUESTED_DB_BACKEND}'; using SQLite fallback"
    )
elif REQUESTED_DB_BACKEND == "mongodb" and not AUTH_DB_STRICT_BACKEND:
    can_use_mongo, fallback_reason = _probe_mongodb_client()
    if can_use_mongo:
        DB_BACKEND = "mongodb"
    else:
        DB_BACKEND = "sqlite"
        DB_BACKEND_FALLBACK_REASON = fallback_reason
else:
    DB_BACKEND = REQUESTED_DB_BACKEND

if DB_BACKEND == "mongodb":
    if MONGODB_URI:
        DB_PATH = f"{MONGODB_URI}/{MONGODB_DB_NAME}"
    else:
        DB_PATH = "mongodb://<unset>/sosync"
    DB_PATH_SOURCE = "mongodb_uri"
    DB_PATH_IS_PERSISTENT = True
else:
    DB_PATH = SQLITE_DB_PATH
    if REQUESTED_DB_BACKEND == "mongodb":
        DB_PATH_SOURCE = f"sqlite_fallback:{SQLITE_DB_PATH_SOURCE}"
    else:
        DB_PATH_SOURCE = SQLITE_DB_PATH_SOURCE
    DB_PATH_IS_PERSISTENT = _is_render_persistent_path(SQLITE_DB_PATH)


class _Result:
    def __init__(self, rows: list[dict] | None = None):
        self._rows = rows or []

    def fetchone(self):
        if not self._rows:
            return None
        return self._rows[0]

    def fetchall(self):
        return self._rows


class _SQLiteConnection:
    def __init__(self, db_path: Path):
        self._connection = sqlite3.connect(db_path, timeout=30)
        self._connection.row_factory = sqlite3.Row
        self._connection.execute("PRAGMA foreign_keys = ON")
        self._connection.execute("PRAGMA busy_timeout = 30000")

    def execute(self, sql: str, params: tuple | list = ()):  # compatibility
        return self._connection.execute(sql, tuple(params))

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        if exc_type is None:
            self._connection.commit()
        else:
            self._connection.rollback()
        self._connection.close()


def _mongo_client():
    global _MONGO_CLIENT
    if MongoClient is None:
        raise RuntimeError("pymongo is not installed. Add pymongo to requirements.txt")
    if not MONGODB_URI:
        raise RuntimeError("MONGODB_URI is required when AUTH_DB_BACKEND=mongodb")

    if _MONGO_CLIENT is None:
        _MONGO_CLIENT = MongoClient(MONGODB_URI, serverSelectionTimeoutMS=10000)
        _MONGO_CLIENT.admin.command("ping")
    return _MONGO_CLIENT


class _MongoConnection:
    def __init__(self, client, db_name: str):
        self._db = client[db_name]

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        return False

    def _next_seq(self, key: str) -> int:
        row = self._db["counters"].find_one_and_update(
            {"_id": key},
            {"$inc": {"value": 1}},
            upsert=True,
            return_document=ReturnDocument.AFTER,
        )
        return int(row["value"])

    @staticmethod
    def _norm(sql: str) -> str:
        return " ".join(sql.lower().split())

    def execute(self, sql: str, params: tuple | list = ()):  # noqa: C901
        q = self._norm(sql)
        p = tuple(params)

        users = self._db["users"]
        revoked = self._db["revoked_tokens"]
        resets = self._db["password_reset_tokens"]
        messages = self._db["direct_messages"]
        shares = self._db["live_location_shares"]

        if q.startswith("select 1 from revoked_tokens where jti ="):
            doc = revoked.find_one({"jti": p[0]}, {"_id": 0})
            return _Result([{"1": 1}] if doc else [])

        if q.startswith("select * from users where user_id ="):
            doc = users.find_one({"user_id": int(p[0])}, {"_id": 0})
            return _Result([doc] if doc else [])

        if q.startswith("insert into users"):
            doc = {
                "user_id": self._next_seq("users"),
                "name": p[0],
                "username": p[1],
                "email": p[2],
                "password_hash": p[3],
                "gender": p[4],
                "phone_number": p[5],
                "emergency_contact_name": p[6],
                "emergency_contact_phone": p[7],
                "trusted_contacts_enabled": int(p[8]),
                "created_at": p[9],
                "updated_at": p[10],
            }
            try:
                users.insert_one(doc)
            except DuplicateKeyError as exc:
                msg = str(exc).lower()
                if "username" in msg:
                    raise RuntimeError("UNIQUE constraint failed: users.username") from exc
                if "email" in msg:
                    raise RuntimeError("UNIQUE constraint failed: users.email") from exc
                raise
            return _Result([])

        if q.startswith("select * from users where username =") and "or email" not in q:
            doc = users.find_one({"username": str(p[0]).lower()}, {"_id": 0})
            return _Result([doc] if doc else [])

        if q.startswith("select * from users where username =") and "or email" in q:
            key = str(p[0]).lower()
            doc = users.find_one({"$or": [{"username": key}, {"email": key}]}, {"_id": 0})
            return _Result([doc] if doc else [])

        if q.startswith("insert or replace into revoked_tokens"):
            revoked.update_one(
                {"jti": p[0]},
                {"$set": {"jti": p[0], "expires_at": int(p[1]), "created_at": p[2]}},
                upsert=True,
            )
            return _Result([])

        if q.startswith("select user_id, email from users where email ="):
            doc = users.find_one({"email": str(p[0]).lower()}, {"_id": 0, "user_id": 1, "email": 1})
            return _Result([doc] if doc else [])

        if q.startswith("insert into password_reset_tokens"):
            resets.insert_one(
                {
                    "token_hash": p[0],
                    "user_id": int(p[1]),
                    "expires_at": int(p[2]),
                    "used": int(p[3]),
                    "created_at": p[4],
                }
            )
            return _Result([])

        if q.startswith("select token_hash, user_id, expires_at, used from password_reset_tokens"):
            doc = resets.find_one({"token_hash": p[0]}, {"_id": 0, "token_hash": 1, "user_id": 1, "expires_at": 1, "used": 1})
            return _Result([doc] if doc else [])

        if q.startswith("update users set password_hash ="):
            users.update_one({"user_id": int(p[2])}, {"$set": {"password_hash": p[0], "updated_at": p[1]}})
            return _Result([])

        if q.startswith("update password_reset_tokens set used = 1"):
            resets.update_one({"token_hash": p[0]}, {"$set": {"used": 1}})
            return _Result([])

        if q.startswith("update users set") and "where user_id = ?" in q:
            lower_sql = sql.lower()
            set_start = lower_sql.find("update users set") + len("update users set")
            set_end = lower_sql.rfind("where user_id = ?")
            clause = sql[set_start:set_end].strip()
            fields = [part.split("=")[0].strip() for part in clause.split(",")]
            values = p[:-1]
            update_doc = {k: v for k, v in zip(fields, values)}
            users.update_one({"user_id": int(p[-1])}, {"$set": update_doc})
            return _Result([])

        if q.startswith("delete from users where user_id ="):
            uid = int(p[0])
            users.delete_one({"user_id": uid})
            messages.delete_many({"$or": [{"sender_user_id": uid}, {"recipient_user_id": uid}]})
            shares.delete_many({"$or": [{"sender_user_id": uid}, {"recipient_user_id": uid}]})
            resets.delete_many({"user_id": uid})
            return _Result([])

        if q.startswith("select user_id, name, username, email, phone_number from users where username ="):
            doc = users.find_one(
                {"username": str(p[0]).lower(), "user_id": {"$ne": int(p[1])}},
                {"_id": 0, "user_id": 1, "name": 1, "username": 1, "email": 1, "phone_number": 1},
            )
            return _Result([doc] if doc else [])

        if q.startswith("select user_id, name, username from users where user_id !=") and "like" in q:
            term = str(p[1]).strip("%").lower()
            docs = list(
                users.find(
                    {
                        "user_id": {"$ne": int(p[0])},
                        "username": {"$regex": re.escape(term), "$options": "i"},
                    },
                    {"_id": 0, "user_id": 1, "name": 1, "username": 1},
                )
                .sort("username", ASCENDING)
                .limit(12)
            )
            return _Result(docs)

        if q.startswith("select user_id, name, username, email, phone_number from users where user_id ="):
            doc = users.find_one(
                {"$and": [{"user_id": int(p[0])}, {"user_id": {"$ne": int(p[1])}}]},
                {"_id": 0, "user_id": 1, "name": 1, "username": 1, "email": 1, "phone_number": 1},
            )
            return _Result([doc] if doc else [])

        if q.startswith("select user_id, username from users where username ="):
            doc = users.find_one({"username": str(p[0]).lower()}, {"_id": 0, "user_id": 1, "username": 1})
            return _Result([doc] if doc else [])

        if q.startswith("select user_id from users where username ="):
            doc = users.find_one({"username": str(p[0]).lower()}, {"_id": 0, "user_id": 1})
            return _Result([doc] if doc else [])

        if q.startswith("insert into direct_messages"):
            messages.insert_one(
                {
                    "message_id": self._next_seq("direct_messages"),
                    "sender_user_id": int(p[0]),
                    "recipient_user_id": int(p[1]),
                    "message_text": p[2],
                    "created_at": p[3],
                }
            )
            return _Result([])

        if q.startswith("select dm.message_id") and "order by dm.message_id desc" in q:
            u1, u2, u3, u4, limit = int(p[0]), int(p[1]), int(p[2]), int(p[3]), int(p[4])
            docs = list(
                messages.find(
                    {
                        "$or": [
                            {"sender_user_id": u1, "recipient_user_id": u2},
                            {"sender_user_id": u3, "recipient_user_id": u4},
                        ]
                    },
                    {"_id": 0},
                )
                .sort("message_id", DESCENDING)
                .limit(limit)
            )
            ids = {d["sender_user_id"] for d in docs} | {d["recipient_user_id"] for d in docs}
            user_map = {
                r["user_id"]: r["username"]
                for r in users.find({"user_id": {"$in": list(ids)}}, {"_id": 0, "user_id": 1, "username": 1})
            }
            rows = []
            for d in docs:
                rows.append(
                    {
                        "message_id": d["message_id"],
                        "sender_user_id": d["sender_user_id"],
                        "recipient_user_id": d["recipient_user_id"],
                        "message_text": d["message_text"],
                        "created_at": d["created_at"],
                        "sender_username": user_map.get(d["sender_user_id"], "unknown"),
                        "recipient_username": user_map.get(d["recipient_user_id"], "unknown"),
                    }
                )
            return _Result(rows)

        if q.startswith("select dm.message_id") and "where dm.recipient_user_id = ? and dm.message_id > ?" in q:
            recipient, after_id, limit = int(p[0]), int(p[1]), int(p[2])
            docs = list(
                messages.find(
                    {"recipient_user_id": recipient, "message_id": {"$gt": after_id}},
                    {"_id": 0},
                )
                .sort("message_id", ASCENDING)
                .limit(limit)
            )
            ids = {d["sender_user_id"] for d in docs} | {d["recipient_user_id"] for d in docs}
            user_map = {
                r["user_id"]: r["username"]
                for r in users.find({"user_id": {"$in": list(ids)}}, {"_id": 0, "user_id": 1, "username": 1})
            }
            rows = []
            for d in docs:
                rows.append(
                    {
                        "message_id": d["message_id"],
                        "sender_user_id": d["sender_user_id"],
                        "recipient_user_id": d["recipient_user_id"],
                        "message_text": d["message_text"],
                        "created_at": d["created_at"],
                        "sender_username": user_map.get(d["sender_user_id"], "unknown"),
                        "recipient_username": user_map.get(d["recipient_user_id"], "unknown"),
                    }
                )
            return _Result(rows)

        if q.startswith("insert into live_location_shares"):
            sender, recipient, started_at, expires_at = int(p[0]), int(p[1]), p[2], int(p[3])
            existing = shares.find_one({"sender_user_id": sender, "recipient_user_id": recipient}, {"_id": 0, "share_id": 1})
            if existing:
                shares.update_one(
                    {"sender_user_id": sender, "recipient_user_id": recipient},
                    {"$set": {"started_at": started_at, "expires_at": expires_at, "is_active": 1}},
                )
            else:
                shares.insert_one(
                    {
                        "share_id": self._next_seq("live_location_shares"),
                        "sender_user_id": sender,
                        "recipient_user_id": recipient,
                        "started_at": started_at,
                        "expires_at": expires_at,
                        "is_active": 1,
                    }
                )
            return _Result([])

        if q.startswith("update live_location_shares set is_active = 0 where sender_user_id ="):
            shares.update_one(
                {"sender_user_id": int(p[0]), "recipient_user_id": int(p[1])},
                {"$set": {"is_active": 0}},
            )
            return _Result([])

        if q.startswith("update live_location_shares set is_active = 0 where is_active = 1 and expires_at <="):
            shares.update_many({"is_active": 1, "expires_at": {"$lte": int(p[0])}}, {"$set": {"is_active": 0}})
            return _Result([])

        if q.startswith("select ls.share_id") and "from live_location_shares ls" in q:
            recipient_id, now_ts = int(p[0]), int(p[1])
            docs = list(
                shares.find(
                    {"recipient_user_id": recipient_id, "is_active": 1, "expires_at": {"$gt": now_ts}},
                    {"_id": 0},
                ).sort("expires_at", DESCENDING)
            )
            sender_ids = [d["sender_user_id"] for d in docs]
            sender_map = {
                r["user_id"]: r
                for r in users.find({"user_id": {"$in": sender_ids}}, {"_id": 0, "user_id": 1, "username": 1, "name": 1})
            }
            rows = []
            for d in docs:
                sender = sender_map.get(d["sender_user_id"], {"username": "unknown", "name": "Unknown"})
                rows.append(
                    {
                        "share_id": d["share_id"],
                        "sender_user_id": d["sender_user_id"],
                        "sender_username": sender.get("username", "unknown"),
                        "sender_name": sender.get("name", "Unknown"),
                        "started_at": d["started_at"],
                        "expires_at": d["expires_at"],
                    }
                )
            return _Result(rows)

        if q.startswith("delete from revoked_tokens where expires_at <"):
            revoked.delete_many({"expires_at": {"$lt": int(p[0])}})
            return _Result([])

        if q.startswith("delete from password_reset_tokens where expires_at <"):
            resets.delete_many({"$or": [{"expires_at": {"$lt": int(p[0])}}, {"used": 1}]})
            return _Result([])

        if q.startswith("select username from users"):
            docs = list(users.find({}, {"_id": 0, "username": 1}))
            return _Result(docs)

        if q.startswith("select count(*) as count from users"):
            return _Result([{"count": users.count_documents({})}])

        if q.startswith("select count(*) as count from direct_messages"):
            return _Result([{"count": messages.count_documents({})}])

        if q.startswith("select count(*) as count from live_location_shares"):
            return _Result([{"count": shares.count_documents({"is_active": 1, "expires_at": {"$gte": int(p[0])}})}])

        raise RuntimeError(f"Unsupported query for Mongo backend: {sql}")


def get_connection():
    if DB_BACKEND == "mongodb":
        return _MongoConnection(_mongo_client(), MONGODB_DB_NAME)
    return _SQLiteConnection(SQLITE_DB_PATH)


def init_auth_db() -> None:
    if DB_BACKEND == "mongodb":
        db = _mongo_client()[MONGODB_DB_NAME]
        db["users"].create_index("user_id", unique=True)
        db["users"].create_index("username", unique=True)
        db["users"].create_index("email", unique=True)

        db["revoked_tokens"].create_index("jti", unique=True)
        db["revoked_tokens"].create_index("expires_at")

        db["password_reset_tokens"].create_index("token_hash", unique=True)
        db["password_reset_tokens"].create_index("user_id")
        db["password_reset_tokens"].create_index("expires_at")

        db["direct_messages"].create_index("message_id", unique=True)
        db["direct_messages"].create_index([("sender_user_id", ASCENDING), ("created_at", ASCENDING)])
        db["direct_messages"].create_index([("recipient_user_id", ASCENDING), ("created_at", ASCENDING)])

        db["live_location_shares"].create_index("share_id", unique=True)
        db["live_location_shares"].create_index([("sender_user_id", ASCENDING), ("recipient_user_id", ASCENDING)], unique=True)
        db["live_location_shares"].create_index([("recipient_user_id", ASCENDING), ("is_active", ASCENDING), ("expires_at", ASCENDING)])
        db["live_location_shares"].create_index([("sender_user_id", ASCENDING), ("is_active", ASCENDING), ("expires_at", ASCENDING)])
        return

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
