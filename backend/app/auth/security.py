from __future__ import annotations

import hashlib
import secrets
import time
from datetime import UTC, datetime, timedelta

import bcrypt
import jwt

from .config import (
    JWT_ALGORITHM,
    JWT_EXP_MINUTES,
    JWT_REMEMBER_ME_DAYS,
    JWT_SECRET,
    LOGIN_LOCK_SECONDS,
    LOGIN_WINDOW_SECONDS,
    MAX_LOGIN_ATTEMPTS,
    PASSWORD_RESET_EXP_MINUTES,
)


def utc_now_iso() -> str:
    return datetime.now(UTC).isoformat()


def now_epoch() -> int:
    return int(time.time())


def hash_password(password: str) -> str:
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def verify_password(password: str, password_hash: str) -> bool:
    return bcrypt.checkpw(password.encode("utf-8"), password_hash.encode("utf-8"))


def create_access_token(user_id: int, username: str, remember_me: bool = False) -> tuple[str, int, str]:
    now = datetime.now(UTC)
    expiry_delta = timedelta(days=JWT_REMEMBER_ME_DAYS) if remember_me else timedelta(minutes=JWT_EXP_MINUTES)
    exp = now + expiry_delta
    jti = secrets.token_urlsafe(24)

    payload = {
        "sub": str(user_id),
        "username": username,
        "jti": jti,
        "iat": int(now.timestamp()),
        "exp": int(exp.timestamp()),
        "typ": "access",
    }
    token = jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)
    return token, int(expiry_delta.total_seconds()), jti


def decode_access_token(token: str) -> dict:
    return jwt.decode(token, JWT_SECRET, algorithms=[JWT_ALGORITHM])


def create_reset_token() -> str:
    return secrets.token_urlsafe(48)


def hash_reset_token(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


class LoginAttemptLimiter:
    def __init__(self) -> None:
        self._attempts: dict[str, list[int]] = {}
        self._locked_until: dict[str, int] = {}

    def _cleanup(self, key: str, now_ts: int) -> None:
        window_start = now_ts - LOGIN_WINDOW_SECONDS
        existing = self._attempts.get(key, [])
        filtered = [ts for ts in existing if ts >= window_start]
        if filtered:
            self._attempts[key] = filtered
        elif key in self._attempts:
            self._attempts.pop(key, None)

        locked_until = self._locked_until.get(key)
        if locked_until is not None and locked_until <= now_ts:
            self._locked_until.pop(key, None)

    def is_locked(self, key: str, now_ts: int) -> tuple[bool, int]:
        self._cleanup(key, now_ts)
        locked_until = self._locked_until.get(key)
        if locked_until is None:
            return False, 0
        return True, max(0, locked_until - now_ts)

    def record_failure(self, key: str, now_ts: int) -> None:
        self._cleanup(key, now_ts)
        self._attempts.setdefault(key, []).append(now_ts)
        if len(self._attempts[key]) >= MAX_LOGIN_ATTEMPTS:
            self._locked_until[key] = now_ts + LOGIN_LOCK_SECONDS
            self._attempts.pop(key, None)

    def reset(self, key: str) -> None:
        self._attempts.pop(key, None)
        self._locked_until.pop(key, None)
