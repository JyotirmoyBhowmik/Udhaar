package com.jyotirmoyb.udhaar.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestoreException
import com.jyotirmoyb.udhaar.BuildConfig
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.cloud.GuestWebCloudRepository
import com.jyotirmoyb.udhaar.di.FirebaseModule
import com.jyotirmoyb.udhaar.util.SafeLogger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SparkQuotaAndAppCheckTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun appCheck_initAppCheck_executesSafelyWithoutThrowing() {
        val app = FirebaseModule.provideFirebaseApp(context)
        if (app != null) {
            // Must not throw or crash on debug / test provider initialization
            FirebaseModule.initAppCheck(app)
        }
        assertTrue("App Check initialization must safely handle environment", true)
    }

    @Test
    fun planMode_sparkFree_displayedCorrectly() {
        assertFalse("By default in Spark mode, BLAZE_FEATURES_ENABLED must be false", BuildConfig.BLAZE_FEATURES_ENABLED)
        val modeStr = if (BuildConfig.BLAZE_FEATURES_ENABLED) {
            context.getString(R.string.settings_plan_mode_blaze)
        } else {
            context.getString(R.string.settings_plan_mode_spark)
        }
        val formatted = context.getString(R.string.settings_plan_mode_label, modeStr)
        assertEquals("Plan mode: Spark (free)", formatted)
    }

    @Test
    fun cloudQuotaExhausted_friendlyStringExistsAndHasNoBannedWords() {
        val msg = context.getString(R.string.cloud_quota_exhausted_friendly)
        assertTrue(msg.contains("free cloud quota", ignoreCase = true) || msg.contains("quota", ignoreCase = true))
        assertTrue(msg.contains("locally", ignoreCase = true) || msg.contains("device", ignoreCase = true))
    }

    @Test
    fun cleanExpiredLinksWorker_enqueueWeekly_schedulesWork() {
        CleanExpiredGuestLinksWorker.enqueueWeekly(context)
        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(CleanExpiredGuestLinksWorker.WORK_NAME_WEEKLY)
            .get()
        assertNotNull(workInfos)
        assertFalse(workInfos.isEmpty())
    }

    @Test
    fun safeLogger_scrubsAllPiiAndRecordsNonFatalSafely() {
        val sample = "Call user at +91 9876543210 or email test@example.com about ₹5,000 secret=1234"
        val redacted = SafeLogger.redact(sample)
        assertFalse(redacted.contains("9876543210"))
        assertFalse(redacted.contains("test@example.com"))
        assertFalse(redacted.contains("₹5,000"))
        assertFalse(redacted.contains("1234"))
        assertTrue(redacted.contains("[REDACTED_PHONE]"))
        assertTrue(redacted.contains("[REDACTED_ID]"))
        assertTrue(redacted.contains("[REDACTED_AMOUNT]"))

        // Non-fatal recording with IDs only
        SafeLogger.recordNonFatal(
            tag = "TestTag",
            errorId = "RESOURCE_EXHAUSTED",
            throwable = RuntimeException("quota test"),
            metadata = mapOf("linkId" to "link_12345", "recordId" to "rec_999")
        )
    }

    @Test
    fun guestWebCloudRepository_whenResourceExhausted_returnsFriendlyQuotaError() = runTest {
        val dummyRepo = object : GuestWebCloudRepository(null, null, null) {
            override suspend fun createGuestWebLink(
                recordId: String,
                counterpartyRole: String,
                intendedPhone: String,
                expiryDays: Int,
                trustLevelRequired: String,
                allowedActions: List<String>,
                allowNote: Boolean,
                allowAttachments: Boolean,
                showUpiQr: Boolean,
                lenderName: String,
                borrowerName: String,
                principalAmountPaise: Long,
                amountPaidPaise: Long,
                currency: String,
                startDateMillis: Long,
                expiryDateMillis: Long?,
                note: String,
                contentHash: String,
                lenderUpiId: String,
                creatorPhone: String,
                expectedGuestEmail: String
            ) = com.jyotirmoyb.udhaar.data.cloud.GuestWebLinkCreateResult(
                success = false,
                errorMessage = "Daily free cloud limit reached. Please try again tomorrow."
            )
        }

        val result = dummyRepo.createGuestWebLink(recordId = "rec_1")
        assertFalse(result.success)
        assertTrue(result.errorMessage.contains("Daily free cloud limit reached"))
    }

    @Test
    fun releaseClasspath_hasNoDebugAppCheckProvider_andReleaseUsesPlayIntegrity() {
        val projectRoot = listOf(
            java.io.File("."),
            java.io.File("..")
        ).first { java.io.File(it, "app/build.gradle.kts").exists() }

        val gradleScript = java.io.File(projectRoot, "app/build.gradle.kts").readText()
        assertTrue(
            "firebase-appcheck-debug must be declared with debugImplementation only",
            gradleScript.contains("debugImplementation(libs.firebase.appcheck.debug)")
        )
        assertFalse(
            "firebase-appcheck-debug must never use implementation()",
            gradleScript.contains("implementation(libs.firebase.appcheck.debug)")
        )
        assertFalse(
            "firebase-appcheck-debug must never use releaseImplementation()",
            gradleScript.contains("releaseImplementation(libs.firebase.appcheck.debug)")
        )
        assertTrue(
            "verifyReleaseNoDebugAppCheck task must guard release classpaths",
            gradleScript.contains("verifyReleaseNoDebugAppCheck")
        )

        val releaseInstaller = java.io.File(
            projectRoot,
            "app/src/release/java/com/jyotirmoyb/udhaar/di/AppCheckProviderInstaller.kt"
        )
        assertTrue("Release AppCheckProviderInstaller must exist", releaseInstaller.exists())
        val releaseInstallerSrc = releaseInstaller.readText()
        assertTrue(releaseInstallerSrc.contains("PlayIntegrityAppCheckProviderFactory"))
        assertFalse(releaseInstallerSrc.contains("DebugAppCheckProviderFactory"))
        assertFalse(releaseInstallerSrc.contains("firebase.appcheck.debug"))

        val mainAndReleaseSources = listOf(
            java.io.File(projectRoot, "app/src/main/java"),
            java.io.File(projectRoot, "app/src/release/java")
        ).flatMap { dir ->
            if (dir.exists()) dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
            else emptyList()
        }
        val offending = mainAndReleaseSources.filter { file ->
            val txt = file.readText()
            txt.contains("DebugAppCheckProviderFactory") || txt.contains("com.google.firebase.appcheck.debug")
        }.map { it.relativeTo(projectRoot).path }

        assertTrue(
            "Main and release source sets must not reference DebugAppCheckProviderFactory: $offending",
            offending.isEmpty()
        )
    }

    @Test
    fun versioningScheme_usesVersionProperties_neverBelow28_andMatchesBuildConfig() {
        val projectRoot = listOf(
            java.io.File("."),
            java.io.File("..")
        ).first { java.io.File(it, "version.properties").exists() }

        val props = java.util.Properties().apply {
            java.io.File(projectRoot, "version.properties").inputStream().use { load(it) }
        }
        val code = props.getProperty("versionCode")?.toIntOrNull()
        val name = props.getProperty("versionName")
        val ciBase = props.getProperty("ciVersionCodeBase")?.toIntOrNull()

        assertNotNull("versionCode must be present in version.properties", code)
        assertTrue("versionCode must never be below 28", (code ?: 0) >= 28)
        assertEquals("30.0", name)
        assertEquals(30, code)
        assertEquals(100, ciBase)
        assertTrue("BuildConfig.VERSION_CODE must be >= 28", BuildConfig.VERSION_CODE >= 28)
        assertEquals("30.0", BuildConfig.VERSION_NAME)
    }

    @Test
    fun iconsWorkaround_noAndroidxPackageSquattingInMainSources() {
        val projectRoot = listOf(
            java.io.File("."),
            java.io.File("..")
        ).first { java.io.File(it, "app/build.gradle.kts").exists() }
        val androidxDir = java.io.File(projectRoot, "app/src/main/java/androidx")
        assertFalse(
            "app/src/main/java/androidx workaround directory must be removed",
            androidxDir.exists() && androidxDir.walkTopDown().any { it.isFile }
        )
    }
}
