from __future__ import annotations

from pydantic import BaseModel, ConfigDict, EmailStr, Field


ALLOWED_GENDERS = {"Male", "Female", "Other", "Prefer not to say"}


class RegisterRequest(BaseModel):
    name: str = Field(min_length=2, max_length=100)
    username: str = Field(min_length=3, max_length=30, pattern=r"^[a-zA-Z0-9._]+$")
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)
    gender: str
    phone_number: str = Field(min_length=7, max_length=20, pattern=r"^[0-9+\-() ]+$")
    emergency_contact_name: str = Field(min_length=2, max_length=100)
    emergency_contact_phone: str = Field(min_length=7, max_length=20, pattern=r"^[0-9+\-() ]+$")
    trusted_contacts_enabled: bool = True


class LoginRequest(BaseModel):
    identifier: str = Field(min_length=3, max_length=120)
    password: str = Field(min_length=8, max_length=128)
    remember_me: bool = False


class ForgotPasswordRequest(BaseModel):
    email: EmailStr


class ResetPasswordRequest(BaseModel):
    token: str = Field(min_length=20, max_length=512)
    new_password: str = Field(min_length=8, max_length=128)


class UpdateProfileRequest(BaseModel):
    name: str | None = Field(default=None, min_length=2, max_length=100)
    gender: str | None = None
    phone_number: str | None = Field(default=None, min_length=7, max_length=20, pattern=r"^[0-9+\-() ]+$")
    emergency_contact_name: str | None = Field(default=None, min_length=2, max_length=100)
    emergency_contact_phone: str | None = Field(default=None, min_length=7, max_length=20, pattern=r"^[0-9+\-() ]+$")
    trusted_contacts_enabled: bool | None = None


class DeleteAccountRequest(BaseModel):
    password: str = Field(min_length=8, max_length=128)


class AuthResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    access_token: str
    token_type: str = "bearer"
    expires_in: int
    user: "UserProfileResponse"


class UserProfileResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    user_id: int
    name: str
    username: str
    email: EmailStr
    gender: str
    phone_number: str
    emergency_contact_name: str
    emergency_contact_phone: str
    trusted_contacts_enabled: bool
    created_at: str
    updated_at: str


def normalize_username(username: str) -> str:
    return username.strip().lower()


def normalize_email(email: str) -> str:
    return email.strip().lower()


def sanitize_name(name: str) -> str:
    return " ".join(name.strip().split())


def sanitize_phone(phone: str) -> str:
    return "".join(ch for ch in phone.strip() if ch.isdigit() or ch in "+-() ")


def validate_gender(gender: str) -> str:
    sanitized = gender.strip()
    if sanitized not in ALLOWED_GENDERS:
        raise ValueError(f"Gender must be one of: {', '.join(sorted(ALLOWED_GENDERS))}")
    return sanitized


def validate_password_strength(password: str) -> None:
    checks = {
        "uppercase": any(ch.isupper() for ch in password),
        "lowercase": any(ch.islower() for ch in password),
        "digit": any(ch.isdigit() for ch in password),
        "symbol": any(not ch.isalnum() for ch in password),
    }
    if not all(checks.values()):
        raise ValueError(
            "Password must include uppercase, lowercase, number, and special character."
        )
