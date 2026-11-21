package com.jyotirmoyb.udhaar.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jyotirmoyb.udhaar.data.cloud.GuestWebCloudRepository
import com.jyotirmoyb.udhaar.data.repository.GuestWebRepository
import com.jyotirmoyb.udhaar.data.repository.InstallmentRepository
import com.jyotirmoyb.udhaar.data.repository.PaymentRepository
import com.jyotirmoyb.udhaar.data.repository.SettingsRepository
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GuestActionAcceptTest {

    private lateinit var context: Context
    private lateinit var db: UdhaarDatabase
    private lateinit var dao: UdhaarDao
    private lateinit var outboxDao: SyncOutboxDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var installmentRepository: InstallmentRepository
    private lateinit var guestWebRepository: GuestWebRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, UdhaarDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.udhaarDao()
        outboxDao = db.syncOutboxDao()

        settingsRepository = SettingsRepository(null, null)
        installmentRepository = InstallmentRepository(dao, settingsRepository, null)
        paymentRepository = PaymentRepository(
            dao = dao,
            settingsRepository = settingsRepository,
            cloudRepository = null,
            installmentRepository = installmentRepository
        )

        val dummyCloudRepo = object : GuestWebCloudRepository(null, null, null) {
            override suspend fun revokeGuestWebLink(recordIdOrSecretOrLinkId: String): Boolean = true
        }

        guestWebRepository = GuestWebRepository(
            dao = dao,
            settingsRepository = settingsRepository,
            paymentRepository = paymentRepository,
            installmentRepository = installmentRepository,
            guestWebCloudRepository = dummyCloudRepo,
            context = context,
            syncOutboxDao = outboxDao
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun idempotentAccept_neverDoubleApplies() = runTest {
        // 1. Create a transaction for ₹100.00 (10,000 paise)
        val txId = dao.insertTransaction(
            UdhaarTransaction(
                id = 0L,
                partyName = "Ravi Sharma",
                principalAmountPaise = 10000L,
                amountPaidPaise = 0L,
                currency = CurrencyType.INR,
                transactionType = TransactionType.GIVEN,
                verificationStatus = VerificationStatus.LOCAL_ONLY
            )
        )

        val payment = PartialPayment(
            id = 0L,
            transactionId = txId,
            amountPaise = 5000L,
            paymentMode = PaymentMode.UPI,
            paidAtMillis = 1700000000000L,
            note = "First installment",
            remotePaymentId = "claim_1_1700000000000",
            syncId = "guest_action_claim_1_1700000000000"
        )
        val audit = AuditTrailEntry(
            id = 0L,
            transactionId = txId,
            remoteEventId = "claim_1_1700000000000",
            eventType = AuditEventType.PAYMENT_RECORDED,
            summaryText = "Payment claim accepted"
        )

        // First application
        val firstId = dao.applyGuestPaymentClaimAtomic(
            transactionId = txId,
            payment = payment,
            proofAttachmentId = null,
            auditEntry = audit
        )
        assertTrue("First payment ID should be positive", firstId > 0L)

        val txAfterFirst = dao.getTransactionById(txId)
        assertNotNull(txAfterFirst)
        assertEquals(5000L, txAfterFirst!!.amountPaidPaise)
        assertFalse(txAfterFirst.isSettled)

        val paymentsAfterFirst = dao.getPartialPaymentsForTransactionSnapshot(txId)
        assertEquals(1, paymentsAfterFirst.size)

        // Second application with the EXACT same actionId+createdAtMs (idempotent duplicate)
        val secondId = dao.applyGuestPaymentClaimAtomic(
            transactionId = txId,
            payment = payment,
            proofAttachmentId = null,
            auditEntry = audit
        )

        assertEquals("Second application must return identical payment ID", firstId, secondId)

        val txAfterSecond = dao.getTransactionById(txId)
        assertEquals("Amount paid must NOT double on duplicate apply", 5000L, txAfterSecond!!.amountPaidPaise)

        val paymentsAfterSecond = dao.getPartialPaymentsForTransactionSnapshot(txId)
        assertEquals("Must still have only 1 partial payment", 1, paymentsAfterSecond.size)
    }

    @Test
    fun transactionRollbackOnFailure() = runTest {
        // Attempting to apply payment to a non-existent transaction must throw an IllegalStateException
        // and roll back the entire transaction without leaving orphan payment rows in SQLite.
        val nonExistentTxId = 999999L
        val payment = PartialPayment(
            id = 0L,
            transactionId = nonExistentTxId,
            amountPaise = 2000L,
            paymentMode = PaymentMode.CASH,
            remotePaymentId = "claim_fail_1",
            syncId = "guest_action_claim_fail_1"
        )
        val audit = AuditTrailEntry(
            id = 0L,
            transactionId = nonExistentTxId,
            remoteEventId = "claim_fail_1",
            summaryText = "Should fail and rollback"
        )

        try {
            dao.applyGuestPaymentClaimAtomic(
                transactionId = nonExistentTxId,
                payment = payment,
                proofAttachmentId = null,
                auditEntry = audit
            )
            fail("Should have thrown IllegalStateException for non-existent transaction")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Transaction not found") == true)
        }

        // Verify rollback: partial_payments and audit_trail_entries must have 0 rows
        val allPayments = dao.getAllPartialPaymentsSnapshot()
        assertTrue("No payment should remain after transaction rollback", allPayments.isEmpty())

        val allAudits = dao.getAllAuditTrailEntriesSnapshot()
        assertTrue("No audit trail should remain after transaction rollback", allAudits.isEmpty())
    }

    @Test
    fun acceptPaymentClaim_recomputesBalanceAndClearsPending() = runTest {
        val claimJson = JSONObject().apply {
            put("amountPaise", 10000L) // Full payment
            put("dateMillis", 1700000005000L)
            put("paymentMode", "UPI")
            put("reference", "UTR-12345")
            put("note", "Paid in full")
            put("hasProof", false)
            put("linkId", "link_123")
            put("actionId", "claim_full")
        }.toString()

        val txId = dao.insertTransaction(
            UdhaarTransaction(
                id = 0L,
                partyName = "Pooja Verma",
                principalAmountPaise = 10000L,
                amountPaidPaise = 0L,
                currency = CurrencyType.INR,
                pendingPaymentClaim = claimJson
            )
        )

        val ok = guestWebRepository.acceptPaymentClaim(txId)
        assertTrue("acceptPaymentClaim should succeed", ok)

        val updated = dao.getTransactionById(txId)
        assertNotNull(updated)
        assertEquals(10000L, updated!!.amountPaidPaise)
        assertTrue("Full payment must mark settled", updated.isSettled)
        assertEquals("", updated.pendingPaymentClaim)
        assertEquals("PAYMENT_CONFIRMED", updated.lastGuestAction)

        val auditEntries = dao.getAuditTrailForTransactionSnapshot(txId)
        assertTrue(auditEntries.any { it.eventType == AuditEventType.PAYMENT_RECORDED })
    }

    @Test
    fun acceptConfirmation_marksBorrowerAcknowledged() = runTest {
        val txId = dao.insertTransaction(
            UdhaarTransaction(
                id = 0L,
                partyName = "Amit Das",
                principalAmountPaise = 5000L,
                trustLevel = "GOOGLE_VERIFIED",
                verificationStatus = VerificationStatus.LOCAL_ONLY,
                lastGuestAction = "CONFIRM_PENDING"
            )
        )

        val ok = guestWebRepository.acceptConfirmation(txId)
        assertTrue("acceptConfirmation should succeed", ok)

        val updated = dao.getTransactionById(txId)
        assertNotNull(updated)
        assertEquals(VerificationStatus.CONFIRMED, updated!!.verificationStatus)
        assertEquals("CONFIRMED", updated.lastGuestAction)

        val auditEntries = dao.getAuditTrailForTransactionSnapshot(txId)
        val confAudit = auditEntries.firstOrNull { it.eventType == AuditEventType.CONFIRMED }
        assertNotNull(confAudit)
        assertEquals("Acknowledged via Google", confAudit!!.summaryText)
    }

    @Test
    fun acceptDateProposal_updatesDueDate() = runTest {
        val newDue = 1750000000000L
        val proposalJson = JSONObject().apply {
            put("proposedDateMillis", newDue)
            put("message", "Can pay next week")
        }.toString()

        val txId = dao.insertTransaction(
            UdhaarTransaction(
                id = 0L,
                partyName = "Sunil Roy",
                principalAmountPaise = 8000L,
                expiryDateMillis = 1700000000000L,
                pendingDateProposal = proposalJson
            )
        )

        val ok = guestWebRepository.acceptDateProposal(txId)
        assertTrue(ok)

        val updated = dao.getTransactionById(txId)
        assertNotNull(updated)
        assertEquals(newDue, updated!!.expiryDateMillis)
        assertEquals("", updated.pendingDateProposal)
        assertEquals("DATE_ACCEPTED", updated.lastGuestAction)

        val audits = dao.getAuditTrailForTransactionSnapshot(txId)
        assertTrue(audits.any { it.eventType == AuditEventType.EXTENSION_ACCEPTED })
    }

    @Test
    fun rejectDispute_restoresLocalStatus() = runTest {
        val txId = dao.insertTransaction(
            UdhaarTransaction(
                id = 0L,
                partyName = "Manoj Sen",
                principalAmountPaise = 6000L,
                verificationStatus = VerificationStatus.DISPUTED,
                disputeReason = "Wrong principal amount",
                lastGuestAction = "DISPUTE"
            )
        )

        val ok = guestWebRepository.rejectDispute(txId, "Terms were agreed previously")
        assertTrue(ok)

        val updated = dao.getTransactionById(txId)
        assertNotNull(updated)
        assertEquals(VerificationStatus.LOCAL_ONLY, updated!!.verificationStatus)
        assertEquals("", updated.disputeReason)
        assertEquals("DISPUTE_REJECTED", updated.lastGuestAction)
    }
}
