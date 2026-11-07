terraform {
  required_version = ">= 1.5.0"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

# ------------------------------------------------------------------------------
# 1. Cloud KMS KeyRing & Master KEK in asia-south1
# ------------------------------------------------------------------------------

resource "google_kms_key_ring" "udhaar" {
  name     = "udhaar"
  location = var.region
}

resource "google_kms_crypto_key" "user_dek" {
  name            = "user-dek"
  key_ring        = google_kms_key_ring.udhaar.id
  purpose         = "ENCRYPT_DECRYPT"
  rotation_period = "7776000s" # 90 days automatic rotation

  version_template {
    algorithm        = "GOOGLE_SYMMETRIC_ENCRYPTION"
    protection_level = "SOFTWARE"
  }

  lifecycle {
    prevent_destroy = true
  }
}

# ------------------------------------------------------------------------------
# 2. Cloud Secret Manager Secrets
# ------------------------------------------------------------------------------

locals {
  app_secrets = [
    "UDHAAR_SERVER_SECRET",
    "UDHAAR_PHONE_PEPPER",
    "UDHAAR_EMAIL_PEPPER",
    "META_APP_SECRET",
    "META_PHONE_NUMBER_ID",
    "WHATSAPP_WEBHOOK_VERIFY_TOKEN",
    "WHATSAPP_API_TOKEN",
    "BREVO_API_KEY",
    "RESEND_API_KEY"
  ]
}

resource "google_secret_manager_secret" "secrets" {
  for_each  = toset(local.app_secrets)
  secret_id = each.key

  replication {
    auto {}
  }
}

# ------------------------------------------------------------------------------
# 3. Dedicated Service Account & Least-Privilege IAM
# ------------------------------------------------------------------------------

resource "google_service_account" "functions_sa" {
  account_id   = "udhaar-functions"
  display_name = "Udhaar Platform v2 Functions Runner"
}

# Grant cryptoKeyEncrypterDecrypter ONLY on user-dek
resource "google_kms_crypto_key_iam_member" "functions_kms" {
  crypto_key_id = google_kms_crypto_key.user_dek.id
  role          = "roles/cloudkms.cryptoKeyEncrypterDecrypter"
  member        = "serviceAccount:${google_service_account.functions_sa.email}"
}

# Grant secretAccessor on each Secret Manager secret
resource "google_secret_manager_secret_iam_member" "functions_secrets" {
  for_each  = toset(local.app_secrets)
  secret_id = google_secret_manager_secret.secrets[each.key].id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.functions_sa.email}"
}

# ------------------------------------------------------------------------------
# 4. Cloud Billing Budget Alerts (50%, 80%, 100%)
# ------------------------------------------------------------------------------

resource "google_billing_budget" "budget" {
  count           = var.billing_account_id != "" ? 1 : 0
  billing_account = var.billing_account_id
  display_name    = "Udhaar Platform Monthly Spend Alert"

  budget_filter {
    projects = ["projects/${var.project_id}"]
  }

  amount {
    specified_amount {
      currency_code = "INR"
      units         = var.monthly_budget_inr
    }
  }

  threshold_rules {
    threshold_percent = 0.5
    spend_basis       = "CURRENT_SPEND"
  }

  threshold_rules {
    threshold_percent = 0.8
    spend_basis       = "CURRENT_SPEND"
  }

  threshold_rules {
    threshold_percent = 1.0
    spend_basis       = "CURRENT_SPEND"
  }
}

output "kms_key_id" {
  description = "The fully-qualified identifier of the Master KEK"
  value       = google_kms_crypto_key.user_dek.id
}

output "service_account_email" {
  description = "The email of the Cloud Functions service account"
  value       = google_service_account.functions_sa.email
}
