# Runbook: Data Breach Response & Statutory Notification Protocol

- **ID**: RB-SEC-004
- **Severity**: P1 (CRITICAL)
- **Statutory Authority**: DPDP Act 2023 (Section 8(6)) & Nepal Privacy Act 2075 (2018).
- **Mandatory Notification Window**: Within 72 hours of confirmation to Data Protection Board of India (DPBI) and affected Data Principals.

---

## 1. Trigger & Classification

A **Personal Data Breach** is any unauthorized access, disclosure, acquisition, alteration, loss, or destruction of personal data that compromises confidentiality, integrity, or availability.

### Potential Vectors
- Unauthorized administrative credential access or privilege escalation.
- Compromise of Cloud KMS master Key Encryption Key (KEK).
- Misconfigured Cloud Storage bucket permissions on payment proof receipts.
- Intercepted API traffic exposing unmasked phone numbers, emails, or names.

---

## 2. Immediate Incident Containment (< 1 Hour)

1. **Invoke Global Emergency Kill Switch**:
   - Activate `globalWriteLock` and `exportDisabled` in `/admin/killswitches` via Admin Console or API to freeze ledger mutations and prevent data exfiltration.
2. **Revoke Active Admin & Provider Credentials**:
   - In GCP Console &rarr; IAM, immediately revoke service account tokens suspected of compromise.
   - In Meta Developer Portal & AWS Console, revoke WhatsApp Graph API tokens and SES SMTP keys.
3. **Isolate Affected Cloud Resources**:
   - Restrict Cloud Armor / WAF ingress rules.
   - If user-level data keys (DEKs) are compromised, initiate emergency **Crypto-Shredding** or re-wrap via `POST /api/v1/admin/users/:id/crypto-shred`.

---

## 3. Forensic Investigation & Assessment (< 24 Hours)

1. **Determine Blast Radius**:
   - Inspect Cloud Audit Logs (`cloudaudit.googleapis.com`) and Firestore access logs.
   - Identify distinct User IDs (`uid`), phone HMACs, and timestamps accessed by unauthorized actors.
2. **Evaluate Field Exposure**:
   - Note: Personal attributes (`partyName`, `notes`, `phone`, `email`, `upiId`) are envelope-encrypted with AES-256-GCM.
   - Determine whether encrypted ciphertexts were exfiltrated alone, or if the wrapped Data Encryption Key (DEK) was also decrypted.
   - Financial amounts and dates are stored in plain text for indexing; confirm exposure count.

---

## 4. Statutory Notification Workflow (< 72 Hours)

### Step 4.1: Report to the Data Protection Board of India (DPBI)
Submit formal notification to `breach-report@dpbi.gov.in` including:
1. Nature, extent, and timeline of the personal data breach.
2. Estimated number of affected Data Principals and categories of data.
3. Consequences and potential harm to Data Principals.
4. Immediate containment and mitigation measures implemented.
5. Name and contact details of the Grievance Officer (`grievance@jyotirmoyb.com`).

### Step 4.2: Direct Notification to Affected Data Principals
Send notification in plain language (in user's preferred language among EN, HI, BN, AS, NE) via WhatsApp, transactional email, and in-app system notification:
```text
Important Security Notice regarding your Udhaar account:
We detected unauthorized access affecting account identifiers. Your ledger financial data is protected by encryption. We have secured the platform, revoked affected credentials, and notified statutory authorities. As a safety measure, please review your active sessions in Settings > Devices and sign in again. For assistance, contact our Grievance Officer at grievance@jyotirmoyb.com.
```

---

## 5. Post-Breach Remediation & Root Cause Analysis

1. Full audit of all IAM roles, service accounts, and API permissions.
2. Key rotation: issue new master KEK in Cloud KMS and re-encrypt all user DEKs.
3. Publish final postmortem report within 14 calendar days.
