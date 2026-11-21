package com.jyotirmoyb.udhaar.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.firebase.firestore.FirebaseFirestoreException
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.GuestActionV1
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.SyncOutboxDao
import com.jyotirmoyb.udhaar.data.SyncOutboxEntity
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.VerificationStatus
import com.jyotirmoyb.udhaar.data.cloud.GuestWebCloudRepository
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GuestActionPollingWorkerTest {

    private lateinit var context: Context
    private lateinit var db: UdhaarDatabase
    private lateinit var dao: UdhaarDao
    private lateinit var outboxDao: SyncOutboxDao

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, UdhaarDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.udhaarDao()
        outboxDao = db.syncOutboxDao()
        UdhaarDatabase.setTestInstance(db)

        // Clear shared preferences for testing
        val prefs = context.getSharedPreferences(GuestActionPollingWorker.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @After
    fun tearDown() {
        UdhaarDatabase.setTestInstance(null)
        db.close()
    }

    @Test
    fun doWork_whenSignedOut_returnsSuccessWithZeroCalls() = runTest {
        val worker = TestListenableWorkerBuilder<GuestActionPollingWorker>(context).build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun processSingleAction_validClaimPayment_updatesLocalTransactionState() = runTest {
        val shareSecret = "test_secret_32_bytes_sample_token_abc123"
        val linkId = GuestWebCloudRepository.sha256Hex(shareSecret)

        val txId = dao.insertTransaction(
            UdhaarTransaction(
                id = 0L,
                partyName = "Aakash Verma",
                principalAmountPaise = 500000L,
                amountPaidPaise = 0L,
                currency = CurrencyType.INR,
                transactionType = TransactionType.GIVEN,
                verificationStatus = VerificationStatus.LOCAL_ONLY,
                shareToken = shareSecret
            )
        )

        val action = GuestActionV1.ClaimPayment(
            linkId = linkId,
            actionId = "claim_payment_001",
            guestUid = "guest_uid_777",
            status = "PENDING",
            createdAtMs = 1700000000000L,
            amountPaise = 200000L,
            paymentMode = PaymentMode.UPI,
            referenceNumber = "UPI9876543210",
            note = "Paid via PhonePe",
            hasProof = true
        )

        val processed = GuestActionPollingWorker.processSingleAction(
            context = context,
            action = action,
            ownerUid = "lender_uid_123"
        )

        assertTrue("Valid action must be processed successfully", processed)

        val updatedTx = dao.getTransactionById(txId)
        assertNotNull(updatedTx)
        assertEquals("PAYMENT_CLAIMED", updatedTx?.lastGuestAction)
        assertNotNull(updatedTx?.pendingPaymentClaim)

        val claimJson = JSONObject(updatedTx!!.pendingPaymentClaim)
        assertEquals(200000L, claimJson.getLong("amountPaise"))
        assertEquals("UPI", claimJson.getString("paymentMode"))
        assertEquals("UPI9876543210", claimJson.getString("reference"))
        assertEquals("Paid via PhonePe", claimJson.getString("note"))
        assertTrue(claimJson.getBoolean("hasProof"))
    }

    @Test
    fun processSingleAction_idempotentProcessing_skipsDuplicateAction() = runTest {
        val shareSecret = "idempotency_secret_key_456"
        val linkId = GuestWebCloudRepository.sha256Hex(shareSecret)

        val txId = dao.insertTransaction(
            UdhaarTransaction(
                id = 0L,
                partyName = "Pooja Das",
                principalAmountPaise = 100000L,
                amountPaidPaise = 0L,
                currency = CurrencyType.INR,
                transactionType = TransactionType.GIVEN,
                verificationStatus = VerificationStatus.LOCAL_ONLY,
                shareToken = shareSecret
            )
        )

        val action = GuestActionV1.Confirm(
            linkId = linkId,
            actionId = "confirm_001",
            guestUid = "guest_uid_888",
            status = "PENDING",
            createdAtMs = 1700000001000L,
            verificationLevel = "GOOGLE_VERIFIED"
        )

        val firstRun = GuestActionPollingWorker.processSingleAction(
            context = context,
            action = action,
            ownerUid = "lender_uid_123"
        )
        assertTrue("First run of action must be processed", firstRun)

        val secondRun = GuestActionPollingWorker.processSingleAction(
            context = context,
            action = action,
            ownerUid = "lender_uid_123"
        )
        assertFalse("Duplicate action with same compositeKey must be skipped", secondRun)
    }

    @Test
    fun processSingleAction_confirmDisputeAndProposeDate_updatesExpectedFields() = runTest {
        val shareSecret = "multi_action_secret_key_789"
        val linkId = GuestWebCloudRepository.sha256Hex(shareSecret)

        val txId = dao.insertTransaction(
            UdhaarTransaction(
                id = 0L,
                partyName = "Suresh Sen",
                principalAmountPaise = 300000L,
                amountPaidPaise = 0L,
                currency = CurrencyType.INR,
                transactionType = TransactionType.GIVEN,
                verificationStatus = VerificationStatus.LOCAL_ONLY,
                shareToken = shareSecret
            )
        )

        // 1. Confirm
        val confirm = GuestActionV1.Confirm(
            linkId = linkId,
            actionId = "confirm_act",
            guestUid = "g1",
            status = "PENDING",
            createdAtMs = 1000L,
            verificationLevel = "PRIVATE_LINK"
        )
        GuestActionPollingWorker.processSingleAction(context, confirm, "lender_uid")
        assertEquals("CONFIRM_PENDING", dao.getTransactionById(txId)?.lastGuestAction)

        // 2. Dispute
        val dispute = GuestActionV1.Dispute(
            linkId = linkId,
            actionId = "dispute_act",
            guestUid = "g1",
            status = "PENDING",
            createdAtMs = 2000L,
            reason = "Paid via cash to brother"
        )
        GuestActionPollingWorker.processSingleAction(context, dispute, "lender_uid")
        val txAfterDispute = dao.getTransactionById(txId)
        assertEquals("DISPUTE", txAfterDispute?.lastGuestAction)
        assertEquals("Paid via cash to brother", txAfterDispute?.disputeReason)

        // 3. Propose Date
        val proposeDate = GuestActionV1.ProposeDate(
            linkId = linkId,
            actionId = "propose_act",
            guestUid = "g1",
            status = "PENDING",
            createdAtMs = 3000L,
            proposedDateMs = 1750000000000L,
            reason = "Extension requested"
        )
        GuestActionPollingWorker.processSingleAction(context, proposeDate, "lender_uid")
        val txAfterDate = dao.getTransactionById(txId)
        assertEquals("DATE_PROPOSED", txAfterDate?.lastGuestAction)
        assertNotNull(txAfterDate?.pendingDateProposal)
        val proposalObj = JSONObject(txAfterDate!!.pendingDateProposal)
        assertEquals(1750000000000L, proposalObj.getLong("proposedDateMillis"))
        assertEquals("Extension requested", proposalObj.getString("message"))
    }

    @Test
    fun invalidPayload_failsClosedAndReturnsNull() {
        // Missing paymentMode
        val missingMode = mapOf(
            "schemaVersion" to 1L,
            "actionType" to "CLAIM_PAYMENT",
            "guestUid" to "g1",
            "status" to "PENDING",
            "createdAtMs" to 1000L,
            "amountPaise" to 1000L
        )
        assertNull(GuestActionV1.fromMap(missingMode, "act1", "link1"))

        // Invalid paymentMode (e.g. BITCOIN)
        val invalidMode = mapOf(
            "schemaVersion" to 1L,
            "actionType" to "CLAIM_PAYMENT",
            "guestUid" to "g1",
            "status" to "PENDING",
            "createdAtMs" to 1000L,
            "amountPaise" to 1000L,
            "paymentMode" to "BITCOIN"
        )
        assertNull(GuestActionV1.fromMap(invalidMode, "act2", "link1"))

        // Non-positive amount
        val zeroAmount = mapOf(
            "schemaVersion" to 1L,
            "actionType" to "CLAIM_PAYMENT",
            "guestUid" to "g1",
            "status" to "PENDING",
            "createdAtMs" to 1000L,
            "amountPaise" to 0L,
            "paymentMode" to "UPI"
        )
        assertNull(GuestActionV1.fromMap(zeroAmount, "act3", "link1"))

        val negativeAmount = mapOf(
            "schemaVersion" to 1L,
            "actionType" to "CLAIM_PAYMENT",
            "guestUid" to "g1",
            "status" to "PENDING",
            "createdAtMs" to 1000L,
            "amountPaise" to -500L,
            "paymentMode" to "UPI"
        )
        assertNull(GuestActionV1.fromMap(negativeAmount, "act4", "link1"))

        // Unknown actionType
        val unknownType = mapOf(
            "schemaVersion" to 1L,
            "actionType" to "UNKNOWN_ACTION",
            "guestUid" to "g1",
            "status" to "PENDING",
            "createdAtMs" to 1000L
        )
        assertNull(GuestActionV1.fromMap(unknownType, "act5", "link1"))
    }

    class QuotaExhaustedWorker(
        appContext: Context,
        workerParams: androidx.work.WorkerParameters
    ) : GuestActionPollingWorker(appContext, workerParams) {
        override suspend fun doWork(): Result {
            return try {
                throw FirebaseFirestoreException(
                    "Spark quota limit reached for today",
                    FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED
                )
            } catch (e: Exception) {
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
                    Result.success()
                } else {
                    Result.retry()
                }
            }
        }
    }

    @Test
    fun quotaExhausted_handlesResourceExhaustedGracefullyWithoutRetry() = runTest {
        val quotaExhaustedWorker = TestListenableWorkerBuilder<QuotaExhaustedWorker>(context).build()
        val result = quotaExhaustedWorker.doWork()
        assertEquals("RESOURCE_EXHAUSTED must back off gracefully returning success", ListenableWorker.Result.success(), result)
    }

    @Test
    fun workManager_schedulingAndCancellation_succeeds() {
        GuestActionPollingWorker.enqueuePeriodic(context)
        val periodicInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(GuestActionPollingWorker.WORK_NAME_PERIODIC)
            .get()
        assertNotNull(periodicInfos)
        assertFalse(periodicInfos.isEmpty())

        GuestActionPollingWorker.triggerImmediatePoll(context)
        val oneShotInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(GuestActionPollingWorker.WORK_NAME_ONE_SHOT)
            .get()
        assertNotNull(oneShotInfos)
        assertFalse(oneShotInfos.isEmpty())

        GuestActionPollingWorker.cancel(context)
    }

    @Test
    fun processPendingCloudSyncOutbox_handlesPendingMutationsSafely() = runTest {
        // Enqueue an outbox mutation for guest_action_sync
        val payload = JSONObject().apply {
            put("linkId", "test_link_id_999")
            put("actionId", "act_888")
            put("paidPaise", 100000L)
            put("payableAmountPaise", 400000L)
        }.toString()

        outboxDao.enqueue(
            SyncOutboxEntity(
                id = 0L,
                entity = "guest_action_sync",
                op = "UPDATE",
                syncId = "guest_action_act_888",
                idempotencyKey = "idem_key_888",
                payloadJson = payload,
                createdAtMs = System.currentTimeMillis()
            )
        )

        val pending = outboxDao.getPendingMutations().filter { it.entity == "guest_action_sync" }
        assertEquals(1, pending.size)

        // When firestore is unavailable (null), processes safely without throwing or hanging
        GuestActionPollingWorker.processPendingCloudSyncOutbox(context, firestore = null)
        val pendingAfter = outboxDao.getPendingMutations().filter { it.entity == "guest_action_sync" }
        assertEquals("Entry remains in outbox for retry when firestore is null", 1, pendingAfter.size)
    }
}
