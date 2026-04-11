from __future__ import annotations

import smtplib
from email.message import EmailMessage

from .config import (
    EMAIL_FROM,
    FRONTEND_RESET_URL,
    SMTP_HOST,
    SMTP_PASSWORD,
    SMTP_PORT,
    SMTP_TIMEOUT_SECONDS,
    SMTP_USE_TLS,
    SMTP_USERNAME,
)


def build_reset_link(token: str) -> str:
    base = FRONTEND_RESET_URL.rstrip("/")
    return f"{base}?token={token}"


def send_password_reset_email(to_email: str, token: str) -> bool:
    if not SMTP_HOST or not SMTP_USERNAME or not SMTP_PASSWORD or not EMAIL_FROM:
        return False

    reset_link = build_reset_link(token)

    msg = EmailMessage()
    msg["Subject"] = "Smart Community SOS Password Reset"
    msg["From"] = EMAIL_FROM
    msg["To"] = to_email
    msg.set_content(
        """
You requested a password reset for your Smart Community SOS account.

Use the link below to reset your password:
{reset_link}

If you did not request this, you can ignore this email.
""".strip().format(reset_link=reset_link)
    )

    with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=SMTP_TIMEOUT_SECONDS) as smtp:
        if SMTP_USE_TLS:
            smtp.starttls()
        smtp.login(SMTP_USERNAME, SMTP_PASSWORD)
        smtp.send_message(msg)

    return True
