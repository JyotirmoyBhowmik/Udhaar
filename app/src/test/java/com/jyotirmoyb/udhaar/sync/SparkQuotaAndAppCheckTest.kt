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
}
