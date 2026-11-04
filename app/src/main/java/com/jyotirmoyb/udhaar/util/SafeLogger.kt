package com.jyotirmoyb.udhaar.util

import android.util.Log

/**
 * Zero-PII logger that automatically scrubs phone numbers, UPI IDs, currency amounts,
 * email addresses, verification tokens, and PINs before writing to Android Logcat.
 */
object SafeLogger {

    private val PHONE_REGEX = Regex("""(?:\+?\d{1,3}[\s-]?)?(?:\d[\s-]?){8,13}\d""")
    private val UPI_OR_EMAIL_REGEX = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+""")
    private val AMOUNT_REGEX = Regex("""(?:₹|रू|\$|€|£|৳|INR|NPR|USD|EUR|GBP|BDT)\s*\d[\d,.]*""", RegexOption.IGNORE_CASE)
    private val TOKEN_REGEX = Regex("""https?://[^\s"']+/u/(?:p/)?[A-Za-z0-9_-]+""")
    private val PIN_KEY_VALUE_REGEX = Regex("""(?i)(pin|passphrase|token|secret)\s*[:=]\s*[^\s,;]+""")

    /**
     * Redacts any potential PII (phones, UPI IDs, emails, amounts, tokens, PINs) from [raw].
     */
    fun redact(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace(TOKEN_REGEX, "[REDACTED_LINK]")
            .replace(UPI_OR_EMAIL_REGEX, "[REDACTED_ID]")
            .replace(AMOUNT_REGEX, "[REDACTED_AMOUNT]")
            .replace(PIN_KEY_VALUE_REGEX, "$1=[REDACTED]")
            .replace(PHONE_REGEX, "[REDACTED_PHONE]")
    }

    fun d(tag: String, message: String) {
        runCatching {
            Log.d(tag, redact(message))
        }
    }

    fun i(tag: String, message: String) {
        runCatching {
            Log.i(tag, redact(message))
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        runCatching {
            val safeMsg = redact(message)
            val safeErr = throwable?.let { "${it::class.simpleName}: ${redact(it.message)}" }
            if (safeErr != null) {
                Log.w(tag, "$safeMsg ($safeErr)")
            } else {
                Log.w(tag, safeMsg)
            }
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        runCatching {
            val safeMsg = redact(message)
            val safeErr = throwable?.let { "${it::class.simpleName}: ${redact(it.message)}" }
            if (safeErr != null) {
                Log.e(tag, "$safeMsg ($safeErr)")
            } else {
                Log.e(tag, safeMsg)
            }
        }
    }
}
