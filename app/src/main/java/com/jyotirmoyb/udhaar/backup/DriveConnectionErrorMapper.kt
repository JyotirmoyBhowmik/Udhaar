package com.jyotirmoyb.udhaar.backup

data class DriveErrorDiagnosis(
    val plainFix: String,
    val technicalDetails: String
) {
    val plainLanguageFix: String get() = plainFix
}

object DriveConnectionErrorMapper {

    /**
     * Maps an authorization or Drive API error to a plain-language fix with the exact values to check.
     *
     * @param statusCode GMS ApiException status code if applicable (e.g., 10 for DEVELOPER_ERROR, 12500 for SIGN_IN_FAILED)
     * @param rawMessage Raw exception or API error message
     * @param httpCode HTTP response status code if an HTTP request failed
     * @param apiReason Google API error reason (e.g., "accessNotConfigured", "SERVICE_DISABLED", "access_denied")
     * @param packageName The application ID / package name (defaults to com.jyotirmoyb.udhaar)
     */
    fun diagnose(
        statusCode: Int? = null,
        rawMessage: String? = null,
        httpCode: Int? = null,
        apiReason: String? = null,
        packageName: String = "com.jyotirmoyb.udhaar"
    ): DriveErrorDiagnosis {
        val msg = rawMessage.orEmpty()
        val reason = apiReason.orEmpty()
        val combined = "$msg $reason".lowercase()

        val plainFix = when {
            // 1. ApiException 10 (DEVELOPER_ERROR) or 12500 (SIGN_IN_FAILED)
            statusCode == 10 || statusCode == 12500 ||
                combined.contains("developer_error") ||
                combined.contains("code 10") ||
                combined.contains("12500") -> {
                "Package name or SHA-1 not registered. Add the SHA-1 shown above to an Android OAuth client for $packageName."
            }

            // 2. access_denied or "access blocked" (unapproved test user / unpublished OAuth consent)
            combined.contains("access_denied") ||
                combined.contains("access blocked") ||
                combined.contains("access_blocked") ||
                combined.contains("unapproved_app") ||
                combined.contains("org_internal") -> {
                "Add your Gmail as a test user, or publish the consent screen."
            }

            // 3. HTTP 403 with accessNotConfigured or SERVICE_DISABLED (Drive API disabled)
            httpCode == 403 && (
                combined.contains("accessnotconfigured") ||
                    combined.contains("service_disabled") ||
                    combined.contains("has not been used in project") ||
                    combined.contains("drive api has not been used") ||
                    combined.contains("drive.googleapis.com")
            ) -> {
                "Enable the Google Drive API in the Google Cloud project."
            }

            // 4. HTTP 401 (token expired / unauthorized after refresh)
            httpCode == 401 || combined.contains("http_401") || combined.contains("authexpired") -> {
                "Reconnect Google Drive."
            }

            // 5. Network errors
            combined.contains("unknownhostexception") ||
                combined.contains("sockettimeoutexception") ||
                combined.contains("connectexception") ||
                combined.contains("no_network") ||
                combined.contains("network error") ||
                combined.contains("timeout") ||
                statusCode == 7 /* NETWORK_ERROR */ ||
                statusCode == 15 /* TIMEOUT */ -> {
                "Unable to connect to Google servers. Please check your internet connection and try again."
            }

            // Fallback for other errors
            else -> {
                "Google Drive connection check failed. Please verify your Google Cloud and OAuth settings."
            }
        }

        val technicalDetails = buildString {
            if (statusCode != null) {
                append("GMS Status Code: ").append(statusCode).append("\n")
            }
            if (httpCode != null) {
                append("HTTP Status: ").append(httpCode).append("\n")
            }
            if (reason.isNotBlank()) {
                append("API Reason: ").append(reason).append("\n")
            }
            if (msg.isNotBlank()) {
                append("Raw Message: ").append(msg)
            }
        }.trim()

        return DriveErrorDiagnosis(plainFix = plainFix, technicalDetails = technicalDetails)
    }
}
