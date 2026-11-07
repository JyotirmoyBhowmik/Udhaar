# Udhaar Platform v2: Privacy Law Compliance & DPDP Framework

- **Jurisdictions**: India (Digital Personal Data Protection Act, 2023), Nepal (Privacy Act, 2075 / 2018)
- **Document Version**: 2.0.0
- **Notice**: *The engineering specifications herein implement technical privacy safeguards. Items requiring formal legal certification by qualified counsel are explicitly flagged with `[LEGAL REVIEW REQUIRED]`.*

---

## 1. Data Fiduciary & Contact Details

* **Data Fiduciary**: Udhaar Financial Technologies / Jyotirmoy Bhowmik
* **Grievance Redressal Officer**:
  - Name: Grievance Officer, Udhaar
  - Email: `grievance@jyotirmoyb.com`
  - Response SLA: Within 48 hours acknowledgement; full resolution within 30 days under DPDP Rule requirements.

---

## 2. Consent Architecture & Plain-Language Notices

Under Section 6 of the DPDP Act 2023, consent must be **free, specific, informed, unconditional, and unambiguous with a clear affirmative action**.

### 2.1 Pre-Registration Notice (Available in 5 Languages)
Prior to collecting the user's mobile number, the client renders an itemized consent sheet in the user's selected language (English, हिन्दी, বাংলা, অসমীয়া, नेपाली):

1. **Account Management & Sync (Essential)**: Processing mobile number, email, and device IDs to authenticate and synchronize debts and credits across your devices.
2. **WhatsApp Transaction Notifications (Operational)**: Sending 6-digit verification codes and payment reminders on WhatsApp.
3. **Age Confirmation (Statutory Declaration)**: `[ ] I confirm I am 18 years of age or older`. *(Under DPDP Section 9, processing personal data of children requires verifiable parental consent; Udhaar Platform v2 is strictly restricted to adults aged 18+).* `[LEGAL REVIEW REQUIRED: Verifiable age assurance mechanism]`

### 2.2 Withdrawal of Consent & Ease of Revocation
- Users can revoke consent for WhatsApp communication at any time in **Settings &rarr; Privacy &rarr; Communication Preferences**, or by replying `STOP` to any WhatsApp message.
- Revoking consent stops non-essential automated reminders immediately without disabling basic ledger access.

---

## 3. Data Subject Rights & Fulfillment Pipeline

| Right | API Endpoint | Enforcement Mechanism | SLA |
| :--- | :--- | :--- | :--- |
| **Right to Access & Summary** | `GET /api/v1/privacy/export` | Generates a signed, password-protected JSON & PDF archive of all records, people, and audit history. | Instant download |
| **Right to Correction** | `PUT /api/v1/me`, `PUT /records/{id}` | Users edit profile, notes, and counterparties directly. | Real-time |
| **Right to Erasure (Deletion)** | `POST /api/v1/privacy/delete` | 30-day cooling-off grace period, followed by complete cryptographic erasure of DEK and purging of all records. | 30 days |
| **Right to Grievance Redressal** | Form on `/privacy` & `grievance@jyotirmoyb.com` | Automated ticketing with correlation ID sent to designated Grievance Officer. | 48h ack, 30d resolution |

### 3.1 Handling Shared Two-Party Records upon Deletion
- In personal credit/debt relationships, both counterparties hold a legitimate interest in the proof of transaction.
- When User A deletes their account, User A's identifying PII (phone, email, real name) is replaced with an immutable placeholder: `"[Deleted User]"`.
- The financial record itself remains visible to User B until settled or deleted by User B.
- `[LEGAL REVIEW REQUIRED: Balancing Right to Erasure against counterparty's evidentiary defense rights under the Indian Evidence Act]`.

---

## 4. Third-Party Data Processors (Sub-processors)

| Sub-Processor | Entity & Location | Processing Purpose | Safeguards |
| :--- | :--- | :--- | :--- |
| **Google Cloud Platform / Firebase** | Google LLC, Data Center: Mumbai (`asia-south1`) | Hosting, Database, Cloud KMS, Storage | ISO 27001, SOC 2/3, Data Processing Addendum (DPA) |
| **Meta Platforms Ireland / WhatsApp** | Meta Platforms Inc. | WhatsApp Cloud API for OTP and reminder delivery | Standard Contractual Clauses (SCCs), Business Terms |
| **Transactional Email Provider** | Resend / Amazon Web Services (SES) | Delivery of email verification codes and security notices | TLS enforced, SPF/DKIM signed, DPA |

---

## 5. Data Retention Schedule

| Data Category | Collection | Active Retention | Post-Termination Retention | Disposal Method |
| :--- | :--- | :--- | :--- | :--- |
| **User Profile & PII** | `users/{uid}` | Duration of active account | 30 days grace | Cryptographic DEK deletion + hard purge |
| **Ledger Records** | `users/{uid}/records` | Duration of active account | 30 days grace | Hard purge |
| **OTP Challenges** | `otpChallenges/` | 10 minutes | 24 hours | Automatic Firestore TTL policy |
| **Rate Limit Counters** | `rateLimits/` | 1 hour to 24 hours | 24 hours | Automatic Firestore TTL policy |
| **Immutable Audit Logs** | `auditLogs/` | 400 days | 400 days | Automated Coldline lifecycle purge |

---

## 6. Security Incident & Breach Notification Runbook

In the event of a verified unauthorized access or compromise of personal data:
1. **Immediate Containment ($T < 1\text{ hr}$)**: Rotate Cloud KMS KEK and revoke active session cookies globally via `checkRevoked` filter.
2. **Assessment & Forensics ($T < 6\text{ hrs}$)**: Determine affected UIDs using `auditLogs/` correlation IDs.
3. **Statutory Reporting ($T < 72\text{ hrs}$)**: Report the breach to the **Data Protection Board of India (DPBI)** and CERT-In in the mandated format.
4. **Data Principal Notification ($T < 72\text{ hrs}$)**: Notify affected users via registered WhatsApp and email detailing the nature of the breach, measures taken, and recommended safety actions.
