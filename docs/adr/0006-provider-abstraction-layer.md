# ADR-0006: Provider Abstraction Layer for Communications & Push

- **Status**: Accepted
- **Date**: 2026-11-07
- **Deciders**: Principal Engineer, Backend Lead, SRE Lead

---

## 1. Context and Problem Statement

Udhaar Platform v2 interacts with multiple external communication providers:
1. **WhatsApp Delivery**: Meta WhatsApp Cloud API (Primary channel for OTP and reminder templates).
2. **SMS Delivery**: Indian SMS aggregators (e.g. Twilio, Gupshup, Exotel) requiring DLT-registered headers and PE entity IDs.
3. **Email Delivery**: Transactional email services (Amazon SES, SendGrid, Resend) for account verification and security alerts.
4. **Push Notifications**: Firebase Cloud Messaging (FCM Web & Android native push).

Hardcoding provider-specific SDKs or HTTP requests throughout business logic causes vendor lock-in, complicates automated testing in CI/CD, prevents smooth fallback during vendor outages, and hinders local emulator development.

## 2. Decision Drivers

- **Zero-Cost Local & CI Testing**: The entire platform must testable without sending real WhatsApp messages or incurring third-party SMS/email charges.
- **Failover & Redundancy**: If WhatsApp experiences an outage, the system must smoothly transition to fallback channels without downtime or code redeployment.
- **Strict Interface Boundaries**: Business logic (e.g. `otpService`, `reminders`) must interact only with abstract provider contracts.

## 3. Decision Outcome

Chosen Option: **Provider Abstraction Layer with Dynamic Configuration & Dependency Injection**.

### 1. Abstract Interfaces

#### A. `OtpChannelProvider`
```typescript
export interface OtpSendRequest {
  destination: string;      // E.164 phone number
  code: string;             // 6-digit verification code
  locale: "en" | "hi" | "bn" | "as" | "ne";
  purpose: "SIGNUP" | "SIGNIN" | "CHANGE_PHONE" | "GUEST_CONFIRM";
  expiresInMinutes: number;
}

export interface OtpSendResult {
  success: boolean;
  providerMessageId: string;
  channel: "WHATSAPP" | "SMS";
  deliveryStatus: "SENT" | "PENDING" | "FAILED";
  errorCode?: string;
}

export interface OtpChannelProvider {
  sendCode(request: OtpSendRequest): Promise<OtpSendResult>;
  verifyWebhookSignature?(headers: Record<string, string>, rawBody: string | Buffer): boolean;
  parseWebhookPayload?(body: any): { messageId: string; status: "delivered" | "read" | "failed" } | null;
}
```

#### B. `EmailProvider`
```typescript
export interface EmailSendRequest {
  toEmail: string;
  subject: string;
  htmlContent: string;
  textContent: string;
  category: "OTP_VERIFICATION" | "SECURITY_ALERT" | "DATA_EXPORT";
}

export interface EmailProvider {
  sendEmail(request: EmailSendRequest): Promise<{ success: boolean; messageId: string }>;
}
```

#### C. `PushProvider`
```typescript
export interface PushMessageRequest {
  targetUid: string;
  title: string;
  body: string;
  data: Record<string, string>;
  priority: "high" | "normal";
}

export interface PushProvider {
  sendPush(request: PushMessageRequest): Promise<{ success: boolean; messageId: string }>;
}
```

### 2. Concrete Implementations & Swappability
- **WhatsApp**:
  - `WhatsAppCloudApiProvider`: Connects to Graph API `https://graph.facebook.com/v21.0/{phone_number_id}/messages`.
  - `FakeWhatsAppProvider`: In-memory provider for local emulators and automated CI/CD tests. Returns instantaneous delivery confirmations and records sent codes in memory.
- **SMS**:
  - `IndianDltSmsProvider`: Configurable provider supporting registered DLT template IDs. Disabled by default until production credentials are provided.
  - `FakeSmsProvider`: Simulated SMS delivery for testing.
- **Email**:
  - `ResendEmailProvider` / `SesEmailProvider`: Transactional email delivery with SPF/DKIM/DMARC headers.
  - `FakeEmailProvider`: Captures sent emails for assertions in unit and integration test runners.

### 3. Factory & Configuration Selection
A centralized provider factory resolves implementations at boot based on environment variables:
```typescript
export function getOtpChannelProvider(channel: "WHATSAPP" | "SMS"): OtpChannelProvider {
  if (process.env.NODE_ENV === "test" || process.env.USE_FAKE_PROVIDERS === "true") {
    return channel === "WHATSAPP" ? fakeWhatsAppProvider : fakeSmsProvider;
  }
  return channel === "WHATSAPP" ? metaWhatsAppProvider : indianDltSmsProvider;
}
```

## 4. Consequences

### Positive
- 100% of identity, authentication, and reminder tests execute in CI without network dependencies, cost, or flaky external APIs.
- Switching from SendGrid to Resend or Amazon SES requires modifying a single provider class.
- Allows canary deployments and runtime feature flags in Firebase Remote Config to throttle or disable a provider during upstream outages.

### Negative / Tradeoffs
- Requires maintaining simulated fake provider classes alongside production SDK integrations.
