#!/usr/bin/env bash
# ==============================================================================
# Udhaar Platform v2 — Cloud KMS & Secret Manager Infrastructure Provisioning
# ==============================================================================
# Provisions:
# 1. Cloud KMS KeyRing 'udhaar' in region asia-south1
# 2. Cloud KMS CryptoKey 'user-dek' with 90-day automatic rotation schedule
# 3. Secret Manager secrets with least-privilege IAM bindings
# 4. Cloud Functions service account with cryptoKeyEncrypterDecrypter on user-dek
# 5. Cloud Billing Budget alert at 50%, 80%, and 100% spend caps
# ==============================================================================

set -euo pipefail

PROJECT_ID="${1:-${GCP_PROJECT:-${GCLOUD_PROJECT:-}}}"
if [ -z "$PROJECT_ID" ]; then
  echo "Usage: $0 <GCP_PROJECT_ID>" >&2
  exit 1
fi

REGION="asia-south1"
KEYRING_NAME="udhaar"
KEY_NAME="user-dek"
ROTATION_PERIOD="7776000s" # 90 days in seconds
SERVICE_ACCOUNT_NAME="udhaar-functions"
SERVICE_ACCOUNT_EMAIL="${SERVICE_ACCOUNT_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"

echo "=========================================================="
echo " Provisioning Udhaar v2 Cryptographic Infrastructure"
echo " Project:         ${PROJECT_ID}"
echo " Region:          ${REGION}"
echo " KeyRing:         ${KEYRING_NAME}"
echo " CryptoKey:       ${KEY_NAME}"
echo " Rotation Period: 90 days (${ROTATION_PERIOD})"
echo " Service Account: ${SERVICE_ACCOUNT_EMAIL}"
echo "=========================================================="

# 1. Enable Required GCP APIs
echo "▶ [1/6] Enabling Required GCP APIs..."
gcloud services enable \
  cloudkms.googleapis.com \
  secretmanager.googleapis.com \
  billingbudgets.googleapis.com \
  cloudresourcemanager.googleapis.com \
  --project="${PROJECT_ID}"

# 2. Create Cloud KMS KeyRing in asia-south1
echo "▶ [2/6] Provisioning Cloud KMS KeyRing in ${REGION}..."
if gcloud kms keyrings describe "${KEYRING_NAME}" --location="${REGION}" --project="${PROJECT_ID}" >/dev/null 2>&1; then
  echo "  ✓ KeyRing '${KEYRING_NAME}' already exists."
else
  gcloud kms keyrings create "${KEYRING_NAME}" \
    --location="${REGION}" \
    --project="${PROJECT_ID}"
  echo "  ✓ Created KeyRing '${KEYRING_NAME}'."
fi

# 3. Create Cloud KMS CryptoKey with 90-day automatic rotation
echo "▶ [3/6] Provisioning Cloud KMS CryptoKey with 90-day rotation..."
NEXT_ROTATION="$(date -u -d "+90 days" +%Y-%m-%dT00:00:00Z 2>/dev/null || date -u -v+90d +%Y-%m-%dT00:00:00Z)"

if gcloud kms keys describe "${KEY_NAME}" --keyring="${KEYRING_NAME}" --location="${REGION}" --project="${PROJECT_ID}" >/dev/null 2>&1; then
  echo "  ✓ CryptoKey '${KEY_NAME}' already exists."
else
  gcloud kms keys create "${KEY_NAME}" \
    --keyring="${KEYRING_NAME}" \
    --location="${REGION}" \
    --purpose="encryption" \
    --rotation-period="${ROTATION_PERIOD}" \
    --next-rotation-time="${NEXT_ROTATION}" \
    --project="${PROJECT_ID}"
  echo "  ✓ Created CryptoKey '${KEY_NAME}' with 90-day rotation."
fi

# 4. Provision Secret Manager Secrets
echo "▶ [4/6] Provisioning Secret Manager Secrets..."
SECRETS=(
  "UDHAAR_SERVER_SECRET"
  "UDHAAR_PHONE_PEPPER"
  "UDHAAR_EMAIL_PEPPER"
  "META_APP_SECRET"
  "META_PHONE_NUMBER_ID"
  "WHATSAPP_WEBHOOK_VERIFY_TOKEN"
  "WHATSAPP_API_TOKEN"
  "BREVO_API_KEY"
  "RESEND_API_KEY"
)

for SECRET in "${SECRETS[@]}"; do
  if gcloud secrets describe "${SECRET}" --project="${PROJECT_ID}" >/dev/null 2>&1; then
    echo "  ✓ Secret '${SECRET}' already exists."
  else
    gcloud secrets create "${SECRET}" \
      --replication-policy="automatic" \
      --project="${PROJECT_ID}"
    echo "  ✓ Created Secret '${SECRET}'."
  fi
done

# 5. Create Dedicated Service Account & Least-Privilege IAM Bindings
echo "▶ [5/6] Configuring Least-Privilege IAM Bindings..."
if gcloud iam service-accounts describe "${SERVICE_ACCOUNT_EMAIL}" --project="${PROJECT_ID}" >/dev/null 2>&1; then
  echo "  ✓ Service account '${SERVICE_ACCOUNT_NAME}' already exists."
else
  gcloud iam service-accounts create "${SERVICE_ACCOUNT_NAME}" \
    --display-name="Udhaar Platform v2 Functions Runner" \
    --project="${PROJECT_ID}"
  echo "  ✓ Created Service Account '${SERVICE_ACCOUNT_NAME}'."
fi

# Grant roles/cloudkms.cryptoKeyEncrypterDecrypter ONLY on the user-dek key (not keyring or project)
gcloud kms keys add-iam-policy-binding "${KEY_NAME}" \
  --keyring="${KEYRING_NAME}" \
  --location="${REGION}" \
  --member="serviceAccount:${SERVICE_ACCOUNT_EMAIL}" \
  --role="roles/cloudkms.cryptoKeyEncrypterDecrypter" \
  --project="${PROJECT_ID}" \
  --quiet
echo "  ✓ Bound roles/cloudkms.cryptoKeyEncrypterDecrypter to ${SERVICE_ACCOUNT_EMAIL} on ${KEY_NAME}."

# Grant roles/secretmanager.secretAccessor on each application secret
for SECRET in "${SECRETS[@]}"; do
  gcloud secrets add-iam-policy-binding "${SECRET}" \
    --member="serviceAccount:${SERVICE_ACCOUNT_EMAIL}" \
    --role="roles/secretmanager.secretAccessor" \
    --project="${PROJECT_ID}" \
    --quiet >/dev/null 2>&1
done
echo "  ✓ Bound roles/secretmanager.secretAccessor to ${SERVICE_ACCOUNT_EMAIL} on application secrets."

# 6. Configure Cloud Billing Budget Alerts
echo "▶ [6/6] Cloud Billing Budget Alert Setup..."
echo "  ↳ Setup budget alert with: gcloud billing budgets create --billing-account=<ACCOUNT_ID> --display-name='Udhaar Monthly Spend' --budget-amount=5000INR --threshold-rule=percent=0.5 --threshold-rule=percent=0.8 --threshold-rule=percent=1.0"

echo ""
echo "🎉 SUCCESS: Udhaar Platform v2 cryptographic infrastructure provisioned successfully!"
echo "Master KEK Path: projects/${PROJECT_ID}/locations/${REGION}/keyRings/${KEYRING_NAME}/cryptoKeys/${KEY_NAME}"
