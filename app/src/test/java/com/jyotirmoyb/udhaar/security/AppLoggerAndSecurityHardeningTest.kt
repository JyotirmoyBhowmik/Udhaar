package com.jyotirmoyb.udhaar.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.util.AppLogger
import com.jyotirmoyb.udhaar.util.InputValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AppLoggerAndSecurityHardeningTest {

    @Test
    fun appLogger_redactsAllPiiAndSecretsFromLogMessages() {
        val rawMessage = "Sync failed for user John Doe, phone +91 9876543210, email test@udhaar.com, upi payee@okaxis, amount ₹12,500.50, token https://udhaar.jyotirmoyb.com/u/secretToken123, pin=4829, secret: mySuperSecretKey"
        val scrubbed = AppLogger.redact(rawMessage)

        assertFalse("Phone number must be scrubbed: $scrubbed", scrubbed.contains("9876543210"))
        assertFalse("Email must be scrubbed: $scrubbed", scrubbed.contains("test@udhaar.com"))
        assertFalse("UPI ID must be scrubbed: $scrubbed", scrubbed.contains("payee@okaxis"))
        assertFalse("Amount must be scrubbed: $scrubbed", scrubbed.contains("12,500.50"))
        assertFalse("Token link must be scrubbed: $scrubbed", scrubbed.contains("secretToken123"))
        assertFalse("PIN must be scrubbed: $scrubbed", scrubbed.contains("4829"))
        assertFalse("Secret must be scrubbed: $scrubbed", scrubbed.contains("mySuperSecretKey"))

        assertTrue("Should contain redacted placeholders: $scrubbed", scrubbed.contains("[REDACTED_PHONE]"))
        assertTrue("Should contain redacted placeholders: $scrubbed", scrubbed.contains("[REDACTED_ID]"))
        assertTrue("Should contain redacted placeholders: $scrubbed", scrubbed.contains("[REDACTED_AMOUNT]"))
        assertTrue("Should contain redacted placeholders: $scrubbed", scrubbed.contains("[REDACTED_LINK]"))
    }

    @Test
    fun appLogger_inReleaseMode_suppressesVerboseDebugAndInfo() {
        try {
            AppLogger.isDebugOverrideForTesting = false

            // Should not throw and should safely drop logs
            AppLogger.v("TestTag", "Verbose message")
            AppLogger.d("TestTag", "Debug message")
            AppLogger.i("TestTag", "Info message")
        } finally {
            AppLogger.isDebugOverrideForTesting = null
        }
    }

    @Test
    fun appLogger_initCrashlyticsUser_usesAnonymousInstallationIdWithoutPii() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppLogger.initCrashlyticsUser(context)

        val prefs = context.getSharedPreferences("udhaar_anon_telemetry_prefs", Context.MODE_PRIVATE)
        val installId = prefs.getString("anon_install_id", null)

        assertNotNull("Anonymous installation ID must be created", installId)
        assertTrue("Installation ID must be anonymous prefix", installId!!.startsWith("anon-"))
        assertFalse("Must never contain phone number", installId.contains("98"))
        assertFalse("Must never contain email", installId.contains("@"))
    }

    @Test
    fun noDirectAndroidUtilLogOrPrintlnUsedOutsideAppLogger() {
        val candidateDirs = listOf(
            File("app/src/main/java"),
            File("src/main/java"),
            File("../app/src/main/java")
        )
        val srcDir = candidateDirs.firstOrNull { it.exists() && it.isDirectory }
        assertNotNull("Source directory must exist for static code scan", srcDir)

        val logViolations = mutableListOf<String>()
        val printViolations = mutableListOf<String>()

        srcDir!!.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("//") && !trimmed.startsWith("*") && !trimmed.startsWith("/*")) {
                        // Check for direct Log usage outside AppLogger.kt
                        if (file.name != "AppLogger.kt") {
                            if (trimmed.contains("android.util.Log") || Regex("""\bLog\.[vdiew]\s*\(""").containsMatchIn(trimmed)) {
                                logViolations.add("${file.name}:${index + 1}: $trimmed")
                            }
                        }
                        // Check for println and printStackTrace
                        if (trimmed.contains("System.out.println") || trimmed.contains("System.err.println") ||
                            trimmed.contains(".printStackTrace()") || (trimmed.startsWith("println(") && file.name != "AppLogger.kt")
                        ) {
                            printViolations.add("${file.name}:${index + 1}: $trimmed")
                        }
                    }
                }
            }

        assertTrue(
            "Found direct android.util.Log calls outside AppLogger.kt:\n${logViolations.joinToString("\n")}",
            logViolations.isEmpty()
        )
        assertTrue(
            "Found println or printStackTrace calls in production code:\n${printViolations.joinToString("\n")}",
            printViolations.isEmpty()
        )
    }

    @Test
    fun inputValidator_validatesNamesPhoneAmountsDatesAndNotes() {
        // 1. Name validation
        assertTrue(InputValidator.validatePartyName("Raju Sharma").isValid)
        assertFalse(InputValidator.validatePartyName("").isValid)
        assertFalse(InputValidator.validatePartyName("   ").isValid)
        assertFalse(InputValidator.validatePartyName("a".repeat(101)).isValid)

        // 2. Phone validation (E.164 normalization)
        assertTrue(InputValidator.validatePhoneNumber("9876543210", CurrencyType.INR).isValid)
        assertTrue(InputValidator.validatePhoneNumber("+91 98765 43210", CurrencyType.INR).isValid)
        assertTrue(InputValidator.validatePhoneNumber("9841234567", CurrencyType.NPR).isValid)
        assertTrue(InputValidator.validatePhoneNumber("", CurrencyType.INR, isRequired = false).isValid)
        assertFalse(InputValidator.validatePhoneNumber("", CurrencyType.INR, isRequired = true).isValid)
        assertFalse(InputValidator.validatePhoneNumber("123", CurrencyType.INR).isValid) // Too short

        // 3. Amount validation (Strict Long paise)
        assertTrue(InputValidator.validateAmountPaise(50000L).isValid) // ₹500
        assertFalse(InputValidator.validateAmountPaise(0L).isValid)
        assertFalse(InputValidator.validateAmountPaise(-100L).isValid)
        assertFalse(InputValidator.validateAmountPaise(null).isValid)
        assertFalse(InputValidator.validateAmountPaise(InputValidator.MAX_TRANSACTION_PAISE + 1L).isValid)

        // 4. Date validation
        val now = System.currentTimeMillis()
        assertTrue(InputValidator.validateDate(now).isValid)
        assertFalse(InputValidator.validateDate(-5L).isValid)
        assertFalse(InputValidator.validateDate(InputValidator.MAX_EPOCH_MILLIS + 1000L).isValid)

        // Date range
        assertTrue(InputValidator.validateDateRange(now, now + 86400000L).isValid)
        assertFalse(InputValidator.validateDateRange(now + 86400000L, now).isValid)

        // 5. Notes validation
        assertTrue(InputValidator.validateNotes("Payment for supplies").isValid)
        assertFalse(InputValidator.validateNotes("x".repeat(1001)).isValid)

        // 6. Complete Transaction validation
        val validTx = UdhaarTransaction(
            partyName = "Sita Devi",
            phoneNumber = "+919876543210",
            principalAmountPaise = 250000L,
            startDateMillis = now,
            expiryDateMillis = now + 86400000L,
            notes = "Store credit"
        )
        assertTrue(InputValidator.validateTransaction(validTx).isValid)

        val invalidTx = validTx.copy(partyName = "   ")
        assertFalse(InputValidator.validateTransaction(invalidTx).isValid)
    }

    @Test
    fun androidManifest_securityHardeningAttributesConfigured() {
        val candidateFiles = listOf(
            File("app/src/main/AndroidManifest.xml"),
            File("src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml")
        )
        val manifestFile = candidateFiles.firstOrNull { it.exists() }
        assertNotNull("AndroidManifest.xml must exist", manifestFile)
        val content = manifestFile!!.readText()

        // 1. allowBackup must be false
        assertTrue("android:allowBackup=\"false\" must be set", content.contains("android:allowBackup=\"false\""))

        // 2. Network security config cleartext disabled
        assertTrue("networkSecurityConfig must be referenced", content.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))

        // 3. VIBRATE permission disabled
        assertTrue("VIBRATE permission must be commented out with [LEGACY-DISABLED]", content.contains("[LEGACY-DISABLED: Permission Policy]"))
        val uncommented = content.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
        assertFalse(
            "VIBRATE permission must not be active",
            Regex("""<uses-permission\s+android:name="android.permission.VIBRATE"\s*/>""").containsMatchIn(uncommented)
        )
    }

    @Test
    fun networkSecurityConfig_disablesCleartextTraffic() {
        val candidateFiles = listOf(
            File("app/src/main/res/xml/network_security_config.xml"),
            File("src/main/res/xml/network_security_config.xml"),
            File("../app/src/main/res/xml/network_security_config.xml")
        )
        val configFile = candidateFiles.firstOrNull { it.exists() }
        assertNotNull("network_security_config.xml must exist", configFile)
        val content = configFile!!.readText()

        assertTrue("cleartextTrafficPermitted=\"false\" must be enforced", content.contains("cleartextTrafficPermitted=\"false\""))
    }
}
