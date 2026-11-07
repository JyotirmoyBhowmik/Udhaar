# Udhaar Platform v2 — Legal Compliance & Privacy Framework (DPDP & Nepal)

**Document Version**: 2.0.0-LEGAL  
**Applicable Statutes**:
- **India**: Digital Personal Data Protection Act, 2023 (DPDP Act) & IT Act, 2000.
- **Nepal**: Privacy Act, 2075 (2018) & Individual Privacy Regulation, 2077 (2020).

> [!IMPORTANT]
> **Legal Disclaimer**: This document represents technical and architectural compliance implementations prepared by engineering. All items marked under **Section 7: Points Flagged for External Legal Counsel Review** must be reviewed and signed off by qualified legal counsel in India and Nepal prior to General Availability (GA).

---

## 1. Notice, Consent & Age Verification Architecture

### 1.1 Itemised & Multilingual Notice (DPDP Section 5)
Before collecting any personal data during the sign-up flow (`POST /api/v1/auth/signup/start`), the user is presented with a clear, plain-language notice in their selected language (English, Hindi, Bengali, Assamese, or Nepali):
- Specific categories of personal data collected (phone number, email address, ledger counterparty names, transaction amounts).
- Explicit processing purposes.
- The right to withdraw consent and the mechanism to lodge a grievance.

### 1.2 Granular Consent Choices (DPDP Section 6)
Consent is unbundled and not conditional on granting unrelated permissions:
1. **Core Ledger Processing (Mandatory)**: Necessary to create accounts, store encrypted records, and synchronize between devices.
2. **WhatsApp Transaction & Reminder Notices (Optional)**: Permitting automated polite reminders and counterparty updates via Meta WhatsApp Cloud API.
3. **Product Telemetry & Diagnostic RUM (Optional)**: Ingestion of anonymized performance metrics (Core Web Vitals). Default is opt-in, toggleable anytime in Settings.

### 1.3 Verifiable Age Confirmation (DPDP Section 9)
Users must explicitly check an affirmative declaration: *"I confirm I am 18 years of age or older and legally competent to enter into agreements."* No processing of children's data or behavioral tracking is conducted.

---

## 2. Data Principal Rights & Self-Service Portal (DPDP Section 11–14)

| Statutory Right | Technical Implementation | Endpoint / UI Path | SLA |
| :--- | :--- | :--- | :--- |
| **Right to Access & Summary** | Download complete ledger history, counterparties, payments, and audit events | `GET /api/v1/me/export` (JSON / CSV) | Instant self-service |
| **Right to Correction** | Update display name, preferred language, UPI ID, or replace email/phone | Settings > Profile & Contact Information | Instant |
| **Right to Erasure / Deletion** | Instant cryptographic shredding of user DEK; 30-day cooling period before purge | `DELETE /api/v1/me` | Instant DEK shred; 30-day purge |
| **Right of Grievance Redressal** | Direct submission of privacy inquiries and statutory dispute escalations | Email to Grievance Officer | Acknowledged $< 48\text{h}$; Resolved $< 30\text{d}$ |

---

## 3. Cryptographic Shredding & Account Erasure Workflow

When a user initiates account deletion (`DELETE /api/v1/me`):
1. **Instant Crypto-Shredding (T + 0 seconds)**:
   - The user's unique Data Encryption Key (DEK) stored in `users/{uid}/cryptoKey` is wiped and destroyed using `shredUserDek()`.
   - All encrypted attributes (`partyName`, `notes`, `phone`, `email`, `upiId`) become mathematically unrecoverable instantaneously.
2. **Counterparty Ledger Integrity**:
   - In shared counterparties' ledgers, the deleted party appears masked as *"Deleted User"* to maintain historical ledger balance continuity without leaking personal data.
3. **Cooling Period & Final Purge (T + 30 days)**:
   - Account enters a 30-day statutory cooling period.
   - At day 30, a scheduled Cloud Function permanently purges blind index records (`phoneIndex/`, `emailIndex/`) and tombstone documents.

---

## 4. Statutory Data Retention Schedule

| Data Category | Purpose | Retention Period | Deletion Mechanism |
| :--- | :--- | :--- | :--- |
| **Account Credentials & Sessions** | Authentication & Security | Active account lifetime | Purged on session revocation or deletion |
| **OTP Challenges (`otpChallenges/`)** | Verification codes | 15 minutes TTL | Automated Firestore TTL policy |
| **Ledger Records & Events** | Personal accounting | User lifetime + 30 days | Cryptographic shredding upon deletion |
| **Payment Proof Attachments** | Payment verification receipts | 90 days from upload | Automated Cloud Storage lifecycle policy |
| **Immutable Merkle Audit Logs** | Fraud prevention & non-repudiation | 365 days rolling | Rolling retention window |

---

## 5. Authorized Third-Party Data Processors (DPDP Section 8)

| Processor Name | Role & Processing Activity | Data Shared | Processing Location | Data Protection Safeguards |
| :--- | :--- | :--- | :--- | :--- |
| **Google Cloud Platform / Firebase** | Cloud Hosting, Firestore, KMS, Storage | Encrypted ciphertexts, blind hashes | `asia-south1` (Mumbai, India) | ISO 27001, SOC 2, HIPAA, DPA signed |
| **Meta Platforms, Inc.** | WhatsApp Cloud API for OTP and notifications | Destination phone number, OTP code | India / Global CDN | Business Associate Terms & WhatsApp DPA |
| **Amazon Web Services (SES)** | Transactional email dispatches | Destination email, verification codes | India / Global | AWS Customer Agreement & GDPR/DPDP DPA |

---

## 6. Grievance Redressal Mechanism & Officer Details

In compliance with DPDP Act Section 13 and Nepal Privacy Act provisions:

- **Data Protection Officer / Grievance Officer**: Jyotirmoy Bhowmik
- **Designated Email**: `grievance@jyotirmoyb.com`
- **Address**: Udhaar Compliance Office, Salt Lake Sector V, Kolkata, West Bengal, India - 700091
- **Statutory Resolution SLA**: Acknowledgment within 48 hours; full resolution and report within 30 calendar days.

---

## 7. Points Flagged for External Legal Counsel Review

Prior to general commercial availability, retained counsel must review and advise on:

1. **Dual-Jurisdiction Scope (India & Nepal)**:
   - Review whether cross-border transfer requirements under Section 16 of India's DPDP Act impact Nepalese citizens accessing services hosted in `asia-south1` (Mumbai).
   - Confirm compliance with Section 13 of Nepal's Privacy Act 2075 regarding public register disclosures.
2. **Unilateral vs. Bilateral Ledger Records**:
   - Legal classification of ledger entries: confirm that informal peer-to-peer loan records maintained by a single party do not constitute an unauthorized public credit bureau or registry under RBI NBFC/P2P guidelines.
3. **Limitation of Liability for Informal Loans**:
   - Verify that Terms of Service Section 8 clearly excludes platform liability for loan defaults, payment disputes, or debt collection disputes between private individuals.
4. **WhatsApp Authentication Message Consent**:
   - Confirm Meta's requirement for implicit vs. explicit opt-in when sending verification codes requested directly by the user on the sign-up form.
