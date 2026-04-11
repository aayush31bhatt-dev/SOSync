from __future__ import annotations

import logging

from fastapi import APIRouter, Depends, Header, HTTPException, Query, status
from pydantic import BaseModel, Field

from .config import AUTH_DEBUG_RESET_LINKS, PASSWORD_RESET_EXP_MINUTES
from .db import cleanup_expired_records, get_connection
from .mailer import build_reset_link, send_password_reset_email
from .schemas import (
    AuthResponse,
    DeleteAccountRequest,
    ForgotPasswordRequest,
    LoginRequest,
    RegisterRequest,
    ResetPasswordRequest,
    UpdateProfileRequest,
    UserProfileResponse,
    normalize_email,
    normalize_username,
    sanitize_name,
    sanitize_phone,
    validate_gender,
    validate_password_strength,
)
from .security import (
    LoginAttemptLimiter,
    create_access_token,
    create_reset_token,
    decode_access_token,
    hash_password,
    hash_reset_token,
    now_epoch,
    utc_now_iso,
    verify_password,
)

router = APIRouter(tags=["auth"])
limiter = LoginAttemptLimiter()
logger = logging.getLogger(__name__)


class MessageSendRequest(BaseModel):
    recipient_username: str = Field(min_length=3, max_length=120)
    message_text: str = Field(min_length=1, max_length=2000)


class LiveLocationShareRequest(BaseModel):
    recipient_username: str = Field(min_length=3, max_length=120)
    duration_minutes: int = Field(default=40, ge=1, le=240)


def _public_user(row) -> UserProfileResponse:
    return UserProfileResponse(
        user_id=row["user_id"],
        name=row["name"],
        username=row["username"],
        email=row["email"],
        gender=row["gender"],
        phone_number=row["phone_number"],
        emergency_contact_name=row["emergency_contact_name"],
        emergency_contact_phone=row["emergency_contact_phone"],
        trusted_contacts_enabled=bool(row["trusted_contacts_enabled"]),
        created_at=row["created_at"],
        updated_at=row["updated_at"],
    )


def _extract_bearer_token(authorization: str | None) -> str:
    if not authorization:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing authorization header.")
    parts = authorization.split(" ", 1)
    if len(parts) != 2 or parts[0].lower() != "bearer" or not parts[1].strip():
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid bearer token format.")
    return parts[1].strip()


def _get_current_user(authorization: str | None = Header(default=None)):
    token = _extract_bearer_token(authorization)
    try:
        payload = decode_access_token(token)
    except Exception as exc:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid or expired token.") from exc

    user_id = int(payload.get("sub", 0))
    jti = payload.get("jti")
    exp = int(payload.get("exp", 0))

    cleanup_expired_records(now_epoch())
    with get_connection() as connection:
        revoked = connection.execute(
            "SELECT 1 FROM revoked_tokens WHERE jti = ? LIMIT 1",
            (jti,),
        ).fetchone()
        if revoked:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token has been revoked.")

        row = connection.execute(
            "SELECT * FROM users WHERE user_id = ? LIMIT 1",
            (user_id,),
        ).fetchone()

    if row is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User not found.")

    return {
        "user": row,
        "jti": jti,
        "exp": exp,
        "token": token,
    }


@router.post("/register")
def register(payload: RegisterRequest):
    try:
        validate_password_strength(payload.password)
        gender = validate_gender(payload.gender)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc

    username = normalize_username(payload.username)
    email = normalize_email(payload.email)
    name = sanitize_name(payload.name)
    emergency_name = sanitize_name(payload.emergency_contact_name)
    phone = sanitize_phone(payload.phone_number)
    emergency_phone = sanitize_phone(payload.emergency_contact_phone)

    password_hash = hash_password(payload.password)
    now_iso = utc_now_iso()

    try:
        with get_connection() as connection:
            connection.execute(
                """
                INSERT INTO users (
                    name, username, email, password_hash, gender,
                    phone_number, emergency_contact_name, emergency_contact_phone,
                    trusted_contacts_enabled, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    name,
                    username,
                    email,
                    password_hash,
                    gender,
                    phone,
                    emergency_name,
                    emergency_phone,
                    1 if payload.trusted_contacts_enabled else 0,
                    now_iso,
                    now_iso,
                ),
            )

            row = connection.execute(
                "SELECT * FROM users WHERE username = ? LIMIT 1",
                (username,),
            ).fetchone()
    except Exception as exc:
        msg = str(exc).lower()
        if "users.username" in msg or "unique" in msg and "username" in msg:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Username already exists.") from exc
        if "users.email" in msg or "unique" in msg and "email" in msg:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Email already exists.") from exc
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Failed to create account.") from exc

    token, expires_in, _ = create_access_token(row["user_id"], row["username"], remember_me=False)
    return AuthResponse(access_token=token, expires_in=expires_in, user=_public_user(row))


@router.post("/login")
def login(payload: LoginRequest):
    identifier = payload.identifier.strip().lower()
    now_ts = now_epoch()

    locked, retry_after = limiter.is_locked(identifier, now_ts)
    if locked:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=f"Too many failed attempts. Try again in {retry_after} seconds.",
        )

    with get_connection() as connection:
        row = connection.execute(
            "SELECT * FROM users WHERE username = ? OR email = ? LIMIT 1",
            (identifier, identifier),
        ).fetchone()

    if row is None:
        limiter.record_failure(identifier, now_ts)
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found.")

    if not verify_password(payload.password, row["password_hash"]):
        limiter.record_failure(identifier, now_ts)
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid credentials.")

    limiter.reset(identifier)
    token, expires_in, _ = create_access_token(
        row["user_id"],
        row["username"],
        remember_me=payload.remember_me,
    )
    return AuthResponse(access_token=token, expires_in=expires_in, user=_public_user(row))


@router.post("/logout")
def logout(current=Depends(_get_current_user)):
    now_iso = utc_now_iso()
    with get_connection() as connection:
        connection.execute(
            "INSERT OR REPLACE INTO revoked_tokens(jti, expires_at, created_at) VALUES (?, ?, ?)",
            (current["jti"], current["exp"], now_iso),
        )
    return {"message": "Logged out successfully."}


@router.post("/forgot-password")
def forgot_password(payload: ForgotPasswordRequest):
    email = normalize_email(payload.email)
    now_ts = now_epoch()
    cleanup_expired_records(now_ts)

    with get_connection() as connection:
        row = connection.execute(
            "SELECT user_id, email FROM users WHERE email = ? LIMIT 1",
            (email,),
        ).fetchone()

        # Privacy-first: always return generic response regardless of email existence.
        if row is not None:
            token = create_reset_token()
            token_hash = hash_reset_token(token)
            expires_at = now_ts + (PASSWORD_RESET_EXP_MINUTES * 60)
            connection.execute(
                """
                INSERT INTO password_reset_tokens(token_hash, user_id, expires_at, used, created_at)
                VALUES (?, ?, ?, 0, ?)
                """,
                (token_hash, row["user_id"], expires_at, utc_now_iso()),
            )

            try:
                sent = send_password_reset_email(to_email=email, token=token)
                if not sent and AUTH_DEBUG_RESET_LINKS:
                    logger.info("[Password Reset][Debug] %s", build_reset_link(token))
            except Exception as exc:
                logger.warning("Password reset email delivery failed for %s: %s", email, exc)
                if AUTH_DEBUG_RESET_LINKS:
                    logger.info("[Password Reset][Debug] %s", build_reset_link(token))

    return {"message": "If the email exists, a password reset link has been sent."}


@router.post("/reset-password")
def reset_password(payload: ResetPasswordRequest):
    try:
        validate_password_strength(payload.new_password)
    except ValueError as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc

    token_hash = hash_reset_token(payload.token)
    now_ts = now_epoch()

    with get_connection() as connection:
        row = connection.execute(
            """
            SELECT token_hash, user_id, expires_at, used
            FROM password_reset_tokens
            WHERE token_hash = ?
            LIMIT 1
            """,
            (token_hash,),
        ).fetchone()

        if row is None or row["used"] == 1 or row["expires_at"] < now_ts:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Invalid or expired reset token.")

        connection.execute(
            "UPDATE users SET password_hash = ?, updated_at = ? WHERE user_id = ?",
            (hash_password(payload.new_password), utc_now_iso(), row["user_id"]),
        )
        connection.execute(
            "UPDATE password_reset_tokens SET used = 1 WHERE token_hash = ?",
            (token_hash,),
        )

    return {"message": "Password reset successful."}


@router.put("/update-profile", response_model=UserProfileResponse)
def update_profile(payload: UpdateProfileRequest, current=Depends(_get_current_user)):
    updates: dict[str, object] = {}

    if payload.name is not None:
        updates["name"] = sanitize_name(payload.name)
    if payload.gender is not None:
        try:
            updates["gender"] = validate_gender(payload.gender)
        except ValueError as exc:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(exc)) from exc
    if payload.phone_number is not None:
        updates["phone_number"] = sanitize_phone(payload.phone_number)
    if payload.emergency_contact_name is not None:
        updates["emergency_contact_name"] = sanitize_name(payload.emergency_contact_name)
    if payload.emergency_contact_phone is not None:
        updates["emergency_contact_phone"] = sanitize_phone(payload.emergency_contact_phone)
    if payload.trusted_contacts_enabled is not None:
        updates["trusted_contacts_enabled"] = 1 if payload.trusted_contacts_enabled else 0

    if not updates:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="No fields to update.")

    updates["updated_at"] = utc_now_iso()

    set_clause = ", ".join(f"{key} = ?" for key in updates.keys())
    values = list(updates.values()) + [current["user"]["user_id"]]

    with get_connection() as connection:
        connection.execute(
            f"UPDATE users SET {set_clause} WHERE user_id = ?",
            values,
        )
        row = connection.execute(
            "SELECT * FROM users WHERE user_id = ? LIMIT 1",
            (current["user"]["user_id"],),
        ).fetchone()

    return _public_user(row)


@router.delete("/delete-account")
def delete_account(payload: DeleteAccountRequest, current=Depends(_get_current_user)):
    user = current["user"]
    if not verify_password(payload.password, user["password_hash"]):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid credentials.")

    with get_connection() as connection:
        connection.execute(
            "DELETE FROM users WHERE user_id = ?",
            (user["user_id"],),
        )

    return {"message": "Account deleted successfully."}


@router.get("/me", response_model=UserProfileResponse)
def me(current=Depends(_get_current_user)):
    return _public_user(current["user"])


@router.get("/users/lookup")
def lookup_user(username: str = Query(min_length=3, max_length=120), current=Depends(_get_current_user)):
    normalized = username.strip().lower()
    with get_connection() as connection:
        row = connection.execute(
            """
            SELECT user_id, name, username, email, phone_number
            FROM users
            WHERE username = ? AND user_id != ?
            LIMIT 1
            """,
            (normalized, current["user"]["user_id"]),
        ).fetchone()

    if row is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found.")

    return {
        "user_id": row["user_id"],
        "name": row["name"],
        "username": row["username"],
        "email": row["email"],
        "phone_number": row["phone_number"],
    }


@router.get("/users/search")
def search_users(query: str = Query(min_length=1, max_length=60), current=Depends(_get_current_user)):
    normalized = query.strip().lower()
    with get_connection() as connection:
        rows = connection.execute(
            """
            SELECT user_id, name, username
            FROM users
            WHERE user_id != ? AND lower(username) LIKE ?
            ORDER BY username ASC
            LIMIT 12
            """,
            (current["user"]["user_id"], f"%{normalized}%"),
        ).fetchall()

    return {
        "results": [
            {
                "user_id": row["user_id"],
                "name": row["name"],
                "username": row["username"],
            }
            for row in rows
        ]
    }


@router.get("/users/by-id")
def get_user_by_id(user_id: int = Query(ge=1), current=Depends(_get_current_user)):
    with get_connection() as connection:
        row = connection.execute(
            """
            SELECT user_id, name, username, email, phone_number
            FROM users
            WHERE user_id = ? AND user_id != ?
            LIMIT 1
            """,
            (user_id, current["user"]["user_id"]),
        ).fetchone()

    if row is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found.")

    return {
        "user_id": row["user_id"],
        "name": row["name"],
        "username": row["username"],
        "email": row["email"],
        "phone_number": row["phone_number"],
    }


@router.post("/messages/send")
def send_message(payload: MessageSendRequest, current=Depends(_get_current_user)):
    recipient_username = payload.recipient_username.strip().lower()
    message_text = payload.message_text.strip()

    with get_connection() as connection:
        recipient = connection.execute(
            "SELECT user_id, username FROM users WHERE username = ? LIMIT 1",
            (recipient_username,),
        ).fetchone()
        if recipient is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Recipient user not found.")

        sender_user_id = current["user"]["user_id"]
        recipient_user_id = recipient["user_id"]
        if sender_user_id == recipient_user_id:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Cannot send message to yourself.")

        created_at = utc_now_iso()
        connection.execute(
            """
            INSERT INTO direct_messages(sender_user_id, recipient_user_id, message_text, created_at)
            VALUES (?, ?, ?, ?)
            """,
            (sender_user_id, recipient_user_id, message_text, created_at),
        )

    return {"status": "sent"}


@router.get("/messages/thread")
def get_message_thread(
    username: str = Query(min_length=3, max_length=120),
    limit: int = Query(default=100, ge=1, le=300),
    current=Depends(_get_current_user),
):
    other_username = username.strip().lower()
    current_user_id = current["user"]["user_id"]

    with get_connection() as connection:
        other = connection.execute(
            "SELECT user_id, username FROM users WHERE username = ? LIMIT 1",
            (other_username,),
        ).fetchone()
        if other is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found.")

        rows = connection.execute(
            """
            SELECT
                dm.message_id,
                dm.sender_user_id,
                dm.recipient_user_id,
                dm.message_text,
                dm.created_at,
                su.username AS sender_username,
                ru.username AS recipient_username
            FROM direct_messages dm
            JOIN users su ON su.user_id = dm.sender_user_id
            JOIN users ru ON ru.user_id = dm.recipient_user_id
            WHERE
                (dm.sender_user_id = ? AND dm.recipient_user_id = ?)
                OR
                (dm.sender_user_id = ? AND dm.recipient_user_id = ?)
            ORDER BY dm.message_id DESC
            LIMIT ?
            """,
            (current_user_id, other["user_id"], other["user_id"], current_user_id, limit),
        ).fetchall()

    messages = [
        {
            "message_id": row["message_id"],
            "sender_user_id": row["sender_user_id"],
            "recipient_user_id": row["recipient_user_id"],
            "sender_username": row["sender_username"],
            "recipient_username": row["recipient_username"],
            "message_text": row["message_text"],
            "created_at": row["created_at"],
        }
        for row in reversed(rows)
    ]
    return {"messages": messages}


@router.get("/messages/inbox")
def get_inbox_messages(
    after_message_id: int = Query(default=0, ge=0),
    limit: int = Query(default=50, ge=1, le=200),
    current=Depends(_get_current_user),
):
    current_user_id = current["user"]["user_id"]

    with get_connection() as connection:
        rows = connection.execute(
            """
            SELECT
                dm.message_id,
                dm.sender_user_id,
                dm.recipient_user_id,
                dm.message_text,
                dm.created_at,
                su.username AS sender_username,
                ru.username AS recipient_username
            FROM direct_messages dm
            JOIN users su ON su.user_id = dm.sender_user_id
            JOIN users ru ON ru.user_id = dm.recipient_user_id
            WHERE dm.recipient_user_id = ? AND dm.message_id > ?
            ORDER BY dm.message_id ASC
            LIMIT ?
            """,
            (current_user_id, after_message_id, limit),
        ).fetchall()

    return {
        "messages": [
            {
                "message_id": row["message_id"],
                "sender_user_id": row["sender_user_id"],
                "recipient_user_id": row["recipient_user_id"],
                "sender_username": row["sender_username"],
                "recipient_username": row["recipient_username"],
                "message_text": row["message_text"],
                "created_at": row["created_at"],
            }
            for row in rows
        ]
    }


@router.post("/location-share/start")
def start_live_location_share(payload: LiveLocationShareRequest, current=Depends(_get_current_user)):
    recipient_username = payload.recipient_username.strip().lower()
    sender_user_id = current["user"]["user_id"]
    now_ts = now_epoch()
    expires_at = now_ts + (payload.duration_minutes * 60)
    started_at = utc_now_iso()

    with get_connection() as connection:
        recipient = connection.execute(
            "SELECT user_id, username FROM users WHERE username = ? LIMIT 1",
            (recipient_username,),
        ).fetchone()
        if recipient is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Recipient user not found.")

        recipient_user_id = recipient["user_id"]
        if sender_user_id == recipient_user_id:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Cannot share with yourself.")

        connection.execute(
            """
            INSERT INTO live_location_shares(sender_user_id, recipient_user_id, started_at, expires_at, is_active)
            VALUES (?, ?, ?, ?, 1)
            ON CONFLICT(sender_user_id, recipient_user_id)
            DO UPDATE SET
                started_at = excluded.started_at,
                expires_at = excluded.expires_at,
                is_active = 1
            """,
            (sender_user_id, recipient_user_id, started_at, expires_at),
        )

    return {
        "status": "active",
        "recipient_username": recipient_username,
        "expires_at": expires_at,
    }


@router.post("/location-share/stop")
def stop_live_location_share(payload: LiveLocationShareRequest, current=Depends(_get_current_user)):
    recipient_username = payload.recipient_username.strip().lower()
    sender_user_id = current["user"]["user_id"]

    with get_connection() as connection:
        recipient = connection.execute(
            "SELECT user_id FROM users WHERE username = ? LIMIT 1",
            (recipient_username,),
        ).fetchone()
        if recipient is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Recipient user not found.")

        connection.execute(
            """
            UPDATE live_location_shares
            SET is_active = 0
            WHERE sender_user_id = ? AND recipient_user_id = ?
            """,
            (sender_user_id, recipient["user_id"]),
        )

    return {
        "status": "stopped",
        "recipient_username": recipient_username,
    }


@router.get("/location-share/incoming")
def get_incoming_live_location_shares(current=Depends(_get_current_user)):
    now_ts = now_epoch()
    recipient_user_id = current["user"]["user_id"]

    with get_connection() as connection:
        connection.execute(
            """
            UPDATE live_location_shares
            SET is_active = 0
            WHERE is_active = 1 AND expires_at <= ?
            """,
            (now_ts,),
        )

        rows = connection.execute(
            """
            SELECT
                ls.share_id,
                ls.sender_user_id,
                su.username AS sender_username,
                su.name AS sender_name,
                ls.started_at,
                ls.expires_at
            FROM live_location_shares ls
            JOIN users su ON su.user_id = ls.sender_user_id
            WHERE ls.recipient_user_id = ? AND ls.is_active = 1 AND ls.expires_at > ?
            ORDER BY ls.expires_at DESC
            """,
            (recipient_user_id, now_ts),
        ).fetchall()

    return {
        "shares": [
            {
                "share_id": row["share_id"],
                "sender_user_id": row["sender_user_id"],
                "sender_username": row["sender_username"],
                "sender_name": row["sender_name"],
                "started_at": row["started_at"],
                "expires_at": row["expires_at"],
            }
            for row in rows
        ]
    }
