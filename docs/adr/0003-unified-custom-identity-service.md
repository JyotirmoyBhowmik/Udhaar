# ADR-0003: Unified Custom Identity & Multi-Factor Verification Service

- **Status**: Accepted
- **Date**: 2026-11-07
- **Deciders**: Principal Engineer, Security Lead, Backend Lead, Android Lead

---

## 1. Context and Problem Statement

Today, the Android application relies on an optional Google Account sign-in (via Google Credential Manager) solely for encrypted Google Drive backups, while the web interface uses independent client-side storage partitions.

To enable seamless multi-device synchronization across Android, Web, and counterparty workflows, Udhaar Platform v2 requires an identity system that:
1. Aligns with standard Indian consumer habits (mobile numbers are the universal primary identifier, while passwords are universally disliked and vulnerable to credential stuffing).
2. Works reliably without costly SMS OTP aggregators that experience high failure rates and carrier filtering in India.
3. Unifies Android mobile apps, desktop browsers, and unauthenticated guest counterparties.
4. Complies with DPDP Act 2023 privacy safeguards against phone number enumeration and user tracking.

## 2. Decision Drivers

- **Zero Passwords**: Passwords introduce phishing, reuse, and reset vulnerabilities.
- **Cost & Reliability**: WhatsApp Business Cloud API provides high delivery rates (>95% under 20s) and predictable unit costs compared to traditional Indian SMS DLT routing.
- **Privacy by Design**: User IDs must never be derived from or correlated with phone numbers. Phone numbers and emails must never be enumerable by unauthenticated attackers.
- **Multi-Platform Support**: Android receives a Firebase Custom Token (exchanged for an ID token); Web receives an `__session` HttpOnly cookie.

## 3. Decision Outcome

Chosen Option: **Custom Cloud Functions Auth Service with WhatsApp OTP, Email Verification, and WebAuthn Passkeys**.

### 1. Identity Flow Architecture

```
[User Phone (E.164)] ──> POST /api/v1/auth/signup/start
                              │
                    Validate libphonenumber (+91 / +977)
                    Generate flowId + 6-digit OTP
                    HMAC-SHA256(Code) stored in otpChallenges/
                    Send via WhatsApp Cloud API
                              │
[WhatsApp Code] ────> POST /api/v1/auth/otp/verify (Phone Verified)
                              │
[User Email] ───────> POST /api/v1/auth/signup/email
                    Send 6-digit verification code via transactional Email
                              │
[Email Code] ───────> POST /api/v1/auth/otp/verify (Email Verified)
                              │
[Consents & Name] ──> POST /api/v1/auth/signup/complete
                    Atomic Firestore Transaction:
                    1. Create users/{uid} (random UUIDv4, decoupled from phone)
                    2. Write phoneIndex/{HMAC(phone, pepper)}
                    3. Write emailIndex/{HMAC(email, pepper)}
                    4. Mint Custom Token (App) or __session Cookie (Web)
```

### 2. Preventing Account Enumeration
- `POST /api/v1/auth/signin/start` accepts a phone number.
- The server responds with identical HTTP status (`200 OK`) and uniform response payloads (`{ flowId, channel: "WHATSAPP", resendAfterSeconds: 30 }`) whether the phone number is already registered or unknown.
- Execution times are equalized using constant-time HMAC operations and synthetic jitter, preventing timing attacks that infer user registration status.

### 3. Session Lifecycles & Security Constraints
- **Web App**: `__session` cookie with flags `HttpOnly`, `Secure`, `SameSite=Strict`. Sliding 7-day expiration with a 30-day absolute ceiling. 10-minute inactivity auto-lock requiring re-authentication via passkey or WhatsApp OTP.
- **Android App**: Custom token converted to Firebase ID token and refresh token, stored in SQLCipher-backed encrypted SharedPreferences.
- **Public Terminal Mode**: Web option to store nothing in IndexedDB/localStorage; session terminates automatically on browser tab closure.
- **Step-Up Authentication**: A fresh email code or passkey assertion within 10 minutes is strictly enforced for sensitive account operations (data export, account deletion, phone/email change, session revocation).

## 4. Consequences

### Positive
- Phishing-resistant, passwordless authentication tailored for Indian mobile ecosystems.
- High delivery reliability using WhatsApp Cloud API authentication templates.
- Strict prevention of phone number harvesting and user identity enumeration.

### Negative / Tradeoffs
- Reliance on Meta WhatsApp Business Cloud API requires maintaining approved message templates and handling webhook delivery status callbacks.
- Fallback SMS provider must be implemented behind abstraction interfaces to guarantee delivery when users lack WhatsApp.
