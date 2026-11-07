variable "project_id" {
  description = "The Google Cloud Platform project ID for Udhaar"
  type        = string
}

variable "region" {
  description = "The GCP region for KMS and Cloud Functions (must be asia-south1 for DPDP Act data residency)"
  type        = string
  default     = "asia-south1"
}

variable "billing_account_id" {
  description = "The GCP Billing Account ID for budget alerts"
  type        = string
  default     = ""
}

variable "monthly_budget_inr" {
  description = "Monthly budget limit in INR"
  type        = number
  default     = 5000
}
