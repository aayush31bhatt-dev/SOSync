# Authentication API (FastAPI + SQLite + JWT)

This backend provides secure authentication and profile management APIs for mobile apps.

## Security Highlights
- Password hashing with `bcrypt`
- JWT-based auth (`Bearer` token)
- SQL schema with UNIQUE constraints on username and email
- Input validation using Pydantic (`EmailStr`, regex, length limits)
- Brute-force mitigation with in-memory login rate limiter
- Token revocation on logout
- Privacy-first forgot password response (no email enumeration)
- Parameterized SQL queries to reduce injection risk

## Database Schema
Table: `users`
- `user_id` (INTEGER PRIMARY KEY AUTOINCREMENT)
- `name`
- `username` (UNIQUE)
- `email` (UNIQUE)
- `password_hash`
- `gender`
- `phone_number`
- `emergency_contact_name`
- `emergency_contact_phone`
- `trusted_contacts_enabled` (0/1)
- `created_at`
- `updated_at`

Table: `revoked_tokens`
- `jti` (PRIMARY KEY)
- `expires_at`
- `created_at`

Table: `password_reset_tokens`
- `token_hash` (PRIMARY KEY)
- `user_id` (FK users)
- `expires_at`
- `used`
- `created_at`

## Endpoints
### POST /register
Create a user account.

Request:
```json
{
  "name": "Priya Singh",
  "username": "priya.singh",
  "email": "priya@example.com",
  "password": "Strong@1234",
  "gender": "Female",
  "phone_number": "+91 9876543210",
  "emergency_contact_name": "Aman Singh",
  "emergency_contact_phone": "+91 9123456780",
  "trusted_contacts_enabled": true
}
```

Success (200):
```json
{
  "access_token": "<jwt>",
  "token_type": "bearer",
  "expires_in": 1800,
  "user": {
    "user_id": 1,
    "name": "Priya Singh",
    "username": "priya.singh",
    "email": "priya@example.com",
    "gender": "Female",
    "phone_number": "+91 9876543210",
    "emergency_contact_name": "Aman Singh",
    "emergency_contact_phone": "+91 9123456780",
    "trusted_contacts_enabled": true,
    "created_at": "...",
    "updated_at": "..."
  }
}
```

### POST /login
Login with username OR email.

Request:
```json
{
  "identifier": "priya.singh",
  "password": "Strong@1234",
  "remember_me": true
}
```

Possible errors:
- `404`: User not found
- `401`: Invalid credentials
- `429`: Too many failed attempts

### POST /logout
Revoke current JWT.

Headers:
- `Authorization: Bearer <jwt>`

### POST /forgot-password
Triggers reset link process.

Request:
```json
{ "email": "priya@example.com" }
```

Response is always generic for privacy:
```json
{ "message": "If the email exists, a password reset link has been sent." }
```

### POST /reset-password
Reset password using reset token.

Request:
```json
{
  "token": "<reset-token>",
  "new_password": "N3wStrong@123"
}
```

### PUT /update-profile
Update name, gender, phone and emergency contact fields.

Headers:
- `Authorization: Bearer <jwt>`

Request:
```json
{
  "name": "Priya S.",
  "gender": "Female",
  "phone_number": "+91 9876500000",
  "emergency_contact_name": "Aman",
  "emergency_contact_phone": "+91 9000011111",
  "trusted_contacts_enabled": true
}
```

### DELETE /delete-account
Delete authenticated user account.

Headers:
- `Authorization: Bearer <jwt>`

Request:
```json
{ "password": "Strong@1234" }
```

### GET /me
Return current user profile.

Headers:
- `Authorization: Bearer <jwt>`

## Integration Notes
- For production, set environment variable `JWT_SECRET` to a long random secret.
- Configure SMTP env vars (`SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `EMAIL_FROM`) to send real reset emails.
- Set `FRONTEND_RESET_URL` so users land on your app's reset-password page.
- For local testing only, set `AUTH_DEBUG_RESET_LINKS=true` to log reset links when SMTP is unavailable.
- Consider Redis-backed distributed rate limiting for multi-instance deployment.
