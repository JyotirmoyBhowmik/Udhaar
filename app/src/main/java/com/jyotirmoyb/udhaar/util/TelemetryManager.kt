package com.jyotirmoyb.udhaar.util

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Interface decoupling telemetry collection from the concrete Firebase SDK,
 * enabling direct non-reflective calls in production and zero-overhead mocking in tests.
 */
interface TelemetrySink {
    fun setConsent(context: Context?, enabled: Boolean)
    fun logScreenView(context: Context?, screenName: String)
    fun recordNonFatal(throwable: Throwable, contextTag: String)
}

/**
 * Production sink executing direct calls to FirebaseCrashlytics and FirebaseAnalytics
 * without reflection.
 */
class DirectFirebaseTelemetrySink : TelemetrySink {
    override fun setConsent(context: Context?, enabled: Boolean) {
        runCatching {
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = enabled
        }
        if (context != null) {
            runCatching {
                FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
            }
        }
    }

    override fun logScreenView(context: Context?, screenName: String) {
        if (context != null) {
            runCatching {
                val bundle = Bundle().apply {
                    putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                }
                FirebaseAnalytics.getInstance(context).logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
            }
        }
    }

    override fun recordNonFatal(throwable: Throwable, contextTag: String) {
        runCatching {
            val sanitized = RuntimeException("${throwable.javaClass.simpleName}[$contextTag]: ${throwable.message}")
            FirebaseCrashlytics.getInstance().recordException(sanitized)
        }
    }
}

/**
 * Privacy-first Crashlytics & Screen Analytics manager.
 *
 * Rules:
 * 1. Strictly OFF by default (`isConsentGranted == false`) until the user explicitly opts in.
 * 2. Zero personal data (no names, phone numbers, UPI IDs, amounts, emails, or tokens).
 * 3. Every screen name or crash message is scrubbed with `SafeLogger.redact` before recording.
 */
object TelemetryManager {

    data class ScreenAnalyticsEvent(
        val screenName: String,
        val timestampMillis: Long = System.currentTimeMillis()
    )

    data class CrashReportEvent(
        val contextTag: String,
        val exceptionClass: String,
        val redactedMessage: String,
        val timestampMillis: Long = System.currentTimeMillis()
    )

    private val ALLOWED_SCREENS = setOf(
        "dashboard",
        "people_directory",
        "history",
        "add_edit_udhaar",
        "party_detail",
        "record_detail",
        "settings",
        "updates",
        "chat_thread",
        "group_split",
        "installment_plan",
        "onboarding"
    )

    private const val RECOVERY_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    private val secureRandom = SecureRandom()

    @Volatile
    var isConsentGranted: Boolean = false
        private set

    @Volatile
    var sink: TelemetrySink = DirectFirebaseTelemetrySink()

    private val recordedScreenEvents = CopyOnWriteArrayList<ScreenAnalyticsEvent>()
    private val recordedCrashEvents = CopyOnWriteArrayList<CrashReportEvent>()

    fun setConsent(context: Context?, enabled: Boolean) {
        isConsentGranted = enabled
        if (!enabled) {
            recordedScreenEvents.clear()
            recordedCrashEvents.clear()
        }
        sink.setConsent(context, enabled)
    }

    fun sanitizeScreenName(rawScreenName: String): String {
        val normalized = rawScreenName.trim().lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
        return if (normalized in ALLOWED_SCREENS) {
            normalized
        } else {
            val redacted = SafeLogger.redact(normalized)
                .replace(Regex("[^a-z0-9_]+"), "_")
                .trim('_')
                .take(32)
            redacted.ifBlank { "unknown_screen" }
        }
    }

    fun logScreenView(screenName: String, context: Context? = null) {
        if (!isConsentGranted) return
        val safeScreen = sanitizeScreenName(screenName)
        recordedScreenEvents.add(ScreenAnalyticsEvent(screenName = safeScreen))
        sink.logScreenView(context, safeScreen)
    }

    fun recordNonFatal(throwable: Throwable, contextTag: String = "app") {
        if (!isConsentGranted) return
        val safeTag = sanitizeScreenName(contextTag)
        val rawMsg = throwable.message.orEmpty()
        val safeMsg = SafeLogger.redact(rawMsg).take(160)
        recordedCrashEvents.add(
            CrashReportEvent(
                contextTag = safeTag,
                exceptionClass = throwable.javaClass.simpleName,
                redactedMessage = safeMsg
            )
        )

        val scrubbed = RuntimeException("${throwable.javaClass.simpleName}[$safeTag]: $safeMsg")
        sink.recordNonFatal(scrubbed, safeTag)
    }

    fun getRecordedScreenEventsForTesting(): List<ScreenAnalyticsEvent> =
        recordedScreenEvents.toList()

    fun getRecordedCrashEventsForTesting(): List<CrashReportEvent> =
        recordedCrashEvents.toList()

    fun resetForTesting() {
        isConsentGranted = false
        recordedScreenEvents.clear()
        recordedCrashEvents.clear()
        sink = DirectFirebaseTelemetrySink()
    }

    /**
     * Generates a 24-character high-entropy Backup Recovery Key formatted as 6 groups of 4 chars:
     * XXXX-XXXX-XXXX-XXXX-XXXX-XXXX
     */
    fun generateBackupRecoveryKey(): String {
        val groups = (0 until 6).map {
            buildString(4) {
                repeat(4) {
                    val idx = secureRandom.nextInt(RECOVERY_ALPHABET.length)
                    append(RECOVERY_ALPHABET[idx])
                }
            }
        }
        return groups.joinToString("-")
    }

    fun extractRecoveryKeyId(recoveryKey: String): String {
        val cleaned = recoveryKey.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
        if (cleaned.length < 8) return ""
        return "${cleaned.substring(0, 4)}-${cleaned.substring(4, 8)}"
    }

    fun isValidRecoveryKey(recoveryKey: String): Boolean {
        val cleaned = recoveryKey.trim().uppercase().replace("-", "").replace(" ", "")
        return cleaned.length == 24 && cleaned.all { it in RECOVERY_ALPHABET }
    }
}
