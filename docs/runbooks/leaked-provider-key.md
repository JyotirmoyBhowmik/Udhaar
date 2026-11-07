# Runbook: Leaked Provider Secret Key Emergency Rotation

- **ID**: RB-SEC-005
- **Severity**: CRITICAL (P1)
- **Target SLA**: Revocation and rotation executed within 15 minutes.
- **Trigger**: GitHub Secret Scanning / GitGuardian alert, anomalous API traffic, or accidental repository commit.

---

## 1. Immediate Containment Checklist (< 15 Minutes)

When a secret key (Meta Graph API, AWS SES, Firebase Admin, or Cloud KMS) is leaked or suspected compromised:

### 1. Meta WhatsApp Cloud API Token Compromise
1. Open [Meta for Developers Portal](https://developers.facebook.com/) &rarr; **My Apps** &rarr; Select Udhaar App.
2. Navigate to **WhatsApp > API Setup**.
3. Under **Access Tokens**, click **Generate New Permanent Token**.
4. In [GCP Secret Manager](https://console.cloud.google.com/security/secret-manager), create a new version of secret `WHATSAPP_CLOUD_API_TOKEN` with the new value.
5. In Meta Developer Console, immediately **Revoke** the compromised token.
6. Cloud Functions will pick up the new Secret Manager version within 60 seconds without redeployment.

### 2. AWS SES SMTP / IAM Credentials Compromise
1. Open [AWS IAM Management Console](https://console.aws.amazon.com/iam/).
2. Locate service user `udhaar-ses-mailer`.
3. Under **Security Credentials**, click **Create Access Key**.
4. Update GCP Secret Manager secret `EMAIL_PROVIDER_CONFIG` with the new access key and secret.
5. In AWS IAM Console, deactivate and **Delete** the old compromised access key.

### 3. Firebase Admin SDK Service Account Compromise
1. Open [GCP IAM & Admin > Service Accounts](https://console.cloud.google.com/iam-admin/serviceaccounts).
2. Locate `firebase-adminsdk-xxxxx@udhaar-prod.iam.gserviceaccount.com`.
3. Go to **Keys** tab &rarr; Click **Add Key > Create new key (JSON)**.
4. Replace secret `FIREBASE_SERVICE_ACCOUNT_PROD` in GitHub Repository Secrets and deployment pipelines.
5. Immediately click **Delete** on the old compromised key in GCP Console.

### 4. Cloud KMS Master Key (KEK) Compromise
1. If the Cloud KMS master key itself is compromised, follow [`docs/runbooks/key-rotation.md`](file:///C:/Users/TEST/antigravity/Udhaar-2026-11-03-5112b/docs/runbooks/key-rotation.md).
2. Issue a new key version:
   ```bash
   gcloud kms keys versions create --key udhaar-master-kek --keyring udhaar-keyring-asia-south1 --location asia-south1
   ```
3. Destroy the compromised key version after scheduling emergency DEK re-wrapping.

---

## 2. Post-Rotation Auditing

1. Review provider audit logs for any unauthorized API requests during the exposure window.
2. Verify delivery status of all messages dispatched during the incident.
3. If user personal data was exposed through unauthorized API queries, trigger [`docs/runbooks/data-breach.md`](file:///C:/Users/TEST/antigravity/Udhaar-2026-11-03-5112b/docs/runbooks/data-breach.md).
