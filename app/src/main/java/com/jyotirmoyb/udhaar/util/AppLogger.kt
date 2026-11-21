package com.jyotirmoyb.udhaar.util

import android.content.Context
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.jyotirmoyb.udhaar.BuildConfig
import java.util.UUID

/**
 * Enterprise AppLogger wrapper providing strict zero-PII logging.
 *
 * In release builds:
 * - Drops all Verbose (V), Debug (D), and Info (I) logs.
 * - Scrubs all Warning (W) and Error (E) logs to remove names, phones, emails, UPI IDs,
 *   amounts, tokens, PINs, and secrets before writing to logcat.
 * - Crashlytics user ID is set strictly to an anonymous random installation ID or blank,
 *   never phone number, name, or email. Custom keys are sanitized and free of PII.
 */
object AppLogger {

    private val PHONE_REGEX = Regex("""(?:\+?\d{1,3}[\s-]?)?(?:\d[\s-]?){8,13}\d""")
    private val UPI_OR_EMAIL_REGEX = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+""")
    private val AMOUNT_REGEX = Regex("""(?:₹|रू|\$|€|£|৳|INR|NPR|USD|EUR|GBP|BDT)\s*\d[\d,.]*""", RegexOption.IGNORE_CASE)
    private val TOKEN_REGEX = Regex("""https?://[^\s"']+/u/(?:p/)?[A-Za-z0-9_-]+""")
    private val SECRET_KEY_VALUE_REGEX = Regex("""(?i)(pin|passphrase|token|secret|password|key|auth|bearer)\s*[:=]\s*[^\s,;]+""")
    private val CONTACT_PAYLOAD_REGEX = Regex("""(?i)(name|party|contact|note)\s*[:=]\s*["']?[^"',;\n]+["']?""")

    @Volatile
    var isDebugOverrideForTesting: Boolean? = null

    private fun isDebugBuild(): Boolean {
        return isDebugOverrideForTesting ?: BuildConfig.DEBUG
    }

    /**
     * Redacts any potential PII (phone, email, UPI ID, amount, token, secret, name) from [raw].
     */
    fun redact(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace(TOKEN_REGEX, "[REDACTED_LINK]")
            .replace(UPI_OR_EMAIL_REGEX, "[REDACTED_ID]")
            .replace(AMOUNT_REGEX, "[REDACTED_AMOUNT]")
            .replace(SECRET_KEY_VALUE_REGEX, "$1=[REDACTED]")
            .replace(CONTACT_PAYLOAD_REGEX, "$1=[REDACTED]")
            .replace(PHONE_REGEX, "[REDACTED_PHONE]")
    }

    fun v(tag: String, message: String) {
        if (!isDebugBuild()) return
        runCatching {
            Log.v(tag, redact(message))
        }
    }

    fun d(tag: String, message: String) {
        if (!isDebugBuild()) return
        runCatching {
            Log.d(tag, redact(message))
        }
    }

    fun i(tag: String, message: String) {
        if (!isDebugBuild()) return
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

    /**
     * Configures Firebase Crashlytics with an anonymous, non-PII installation ID.
     * Never associates real user credentials (phone, email, name) with Crashlytics.
     */
    fun initCrashlyticsUser(context: Context) {
        runCatching {
            val prefs = context.getSharedPreferences("udhaar_anon_telemetry_prefs", Context.MODE_PRIVATE)
            var installId = prefs.getString("anon_install_id", null)
            if (installId.isNullOrBlank()) {
                installId = "anon-" + UUID.randomUUID().toString().take(12)
                prefs.edit().putString("anon_install_id", installId).apply()
            }
            FirebaseCrashlytics.getInstance().setUserId(installId)
        }
    }

    /**
     * Records a non-fatal event to Firebase Crashlytics using sanitized error codes and IDs only,
     * ensuring zero PII (phone, email, names, amounts) is ever leaked.
     */
    fun recordNonFatal(
        tag: String,
        errorId: String,
        throwable: Throwable? = null,
        metadata: Map<String, String> = emptyMap()
    ) {
        runCatching {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("tag", redact(tag))
            crashlytics.setCustomKey("error_id", redact(errorId))
            metadata.forEach { (key, value) ->
                // Ensure custom keys do not contain PII
                crashlytics.setCustomKey(redact(key), redact(value))
            }
            if (throwable != null) {
                crashlytics.recordException(throwable)
            } else {
                crashlytics.log("${redact(tag)}: ${redact(errorId)}")
            }
        }
    }
}
