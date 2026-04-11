from __future__ import annotations

import os

JWT_SECRET = os.getenv("JWT_SECRET", "change-this-in-production")
JWT_ALGORITHM = "HS256"
JWT_EXP_MINUTES = int(os.getenv("JWT_EXP_MINUTES", "30"))
JWT_REMEMBER_ME_DAYS = int(os.getenv("JWT_REMEMBER_ME_DAYS", "30"))
PASSWORD_RESET_EXP_MINUTES = int(os.getenv("PASSWORD_RESET_EXP_MINUTES", "20"))
FRONTEND_RESET_URL = os.getenv("FRONTEND_RESET_URL", "https://your-frontend/reset-password")

SMTP_HOST = os.getenv("SMTP_HOST", "").strip()
SMTP_PORT = int(os.getenv("SMTP_PORT", "587"))
SMTP_USERNAME = os.getenv("SMTP_USERNAME", "").strip()
SMTP_PASSWORD = os.getenv("SMTP_PASSWORD", "").strip()
EMAIL_FROM = os.getenv("EMAIL_FROM", "").strip()
SMTP_USE_TLS = os.getenv("SMTP_USE_TLS", "true").strip().lower() == "true"
SMTP_TIMEOUT_SECONDS = int(os.getenv("SMTP_TIMEOUT_SECONDS", "10"))
AUTH_DEBUG_RESET_LINKS = os.getenv("AUTH_DEBUG_RESET_LINKS", "false").strip().lower() == "true"

# Simple brute-force protection settings.
MAX_LOGIN_ATTEMPTS = int(os.getenv("MAX_LOGIN_ATTEMPTS", "5"))
LOGIN_WINDOW_SECONDS = int(os.getenv("LOGIN_WINDOW_SECONDS", "300"))
LOGIN_LOCK_SECONDS = int(os.getenv("LOGIN_LOCK_SECONDS", "600"))
