package com.jyotirmoyb.udhaar.data.repository

import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.AuditEventType
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.VerificationStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PaymentRepositoryTest {

    private lateinit var fakeDao: FakeUdhaarDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var paymentRepository: PaymentRepository

    @Before
    fun setup() {
        fakeDao = FakeUdhaarDao()
        settingsRepository = SettingsRepository(null, null)
        paymentRepository = PaymentRepository(
            dao = fakeDao,
            settingsRepository = settingsRepository,
            cloudRepository = null
        )
    }

    @Test
    fun recordPartialPayment_success_updatesAmountPaidAndInsertsAudit() = runTest {
        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(
                partyName = "Vikas",
                principalAmount = 1000.0,
                amountPaid = 0.0,
                currency = CurrencyType.INR
            )
        )
        val tx = fakeDao.getTransactionById(txId)!!

        val paymentId = paymentRepository.recordPartialPayment(
            transaction = tx,
            paymentAmount = 350.0,
            paymentMode = PaymentMode.UPI,
            note = "First installment",
            totalAccruedAmount = 1000.0,
            actorName = "Self"
        )
        assertTrue("Payment ID must be positive", paymentId > 0L)

        val updatedTx = fakeDao.getTransactionById(txId)!!
        assertEquals(350.0, updatedTx.amountPaid, 0.01)
        assertFalse(updatedTx.isSettled)

        val payments = paymentRepository.getPartialPayments(txId).first()
        assertEquals(1, payments.size)
        assertEquals(350.0, payments.first().amount, 0.01)
        assertEquals(PaymentMode.UPI, payments.first().paymentMode)
        assertEquals("First installment", payments.first().note)

        val audit = fakeDao.getAuditTrailForTransactionSnapshot(txId)
        assertTrue(audit.any { it.eventType == AuditEventType.PAYMENT_RECORDED })
    }

    @Test
    fun recordPartialPayment_zeroOrNegativeAmount_rejected() = runTest {
        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(partyName = "Ravi", principalAmount = 500.0)
        )
        val tx = fakeDao.getTransactionById(txId)!!

        val resZero = paymentRepository.recordPartialPayment(
            transaction = tx,
            paymentAmount = 0.0,
            note = "Zero",
            totalAccruedAmount = 500.0
        )
        assertEquals(-1L, resZero)

        val resNeg = paymentRepository.recordPartialPayment(
            transaction = tx,
            paymentAmount = -100.0,
            note = "Negative",
            totalAccruedAmount = 500.0
        )
        assertEquals(-1L, resNeg)

        assertEquals(0, fakeDao.getAllPartialPaymentsSnapshot().size)
    }

    @Test
    fun recordPartialPayment_triggersFullSettlementWhenPaidInFull() = runTest {
        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(
                partyName = "Suresh",
                principalAmount = 2000.0,
                amountPaid = 1500.0,
                isSettled = false
            )
        )
        val tx = fakeDao.getTransactionById(txId)!!

        val paymentId = paymentRepository.recordPartialPayment(
            transaction = tx,
            paymentAmount = 500.0,
            note = "Final clearance",
            totalAccruedAmount = 2000.0
        )
        assertTrue(paymentId > 0L)

        val updatedTx = fakeDao.getTransactionById(txId)!!
        assertEquals(2000.0, updatedTx.amountPaid, 0.01)
        assertTrue("Transaction must be marked settled", updatedTx.isSettled)
        assertNotNull(updatedTx.settledAtMillis)

        val audit = fakeDao.getAuditTrailForTransactionSnapshot(txId)
        assertTrue(audit.any { it.eventType == AuditEventType.SETTLED })
    }

    @Test
    fun recordPartialPayment_withProofAttachment_savesAttachment() = runTest {
        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(partyName = "Meera", principalAmount = 4000.0)
        )
        val tx = fakeDao.getTransactionById(txId)!!

        val proof = Attachment(
            transactionId = txId,
            fileName = "receipt.jpg",
            localPath = "/data/attachments/receipt.jpg",
            sizeBytes = 12040,
            mimeType = "image/jpeg"
        )

        val paymentId = paymentRepository.recordPartialPayment(
            transaction = tx,
            paymentAmount = 1000.0,
            note = "With receipt",
            totalAccruedAmount = 4000.0,
            proofAttachment = proof
        )

        val attachments = fakeDao.getAttachmentsForTransactionSnapshot(txId)
        assertEquals(1, attachments.size)
        assertEquals(paymentId, attachments.first().paymentId)
        assertEquals("receipt.jpg", attachments.first().fileName)
    }

    @Test
    fun acknowledgePartialPayment_marksAcknowledged() = runTest {
        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(
                partyName = "Partner",
                principalAmount = 3000.0,
                verificationStatus = VerificationStatus.CONFIRMED
            )
        )
        val tx = fakeDao.getTransactionById(txId)!!

        val paymentId = fakeDao.insertPartialPayment(
            PartialPayment(
                transactionId = txId,
                amount = 1000.0,
                isAcknowledged = false,
                needsMyAcknowledgement = true
            )
        )
        val payment = fakeDao.getAllPartialPaymentsSnapshot().first { it.id == paymentId }

        paymentRepository.acknowledgePartialPayment(tx, payment, actorName = "Receiver")

        val updatedPayment = fakeDao.getAllPartialPaymentsSnapshot().first { it.id == paymentId }
        assertTrue(updatedPayment.isAcknowledged)
        assertFalse(updatedPayment.needsMyAcknowledgement)
        assertNotNull(updatedPayment.acknowledgedAtMillis)

        val audit = fakeDao.getAuditTrailForTransactionSnapshot(txId)
        assertTrue(audit.any { it.eventType == AuditEventType.PAYMENT_ACKNOWLEDGED })
    }

    @Test
    fun deletePartialPayment_revertsPaidAmountAndUnsettles() = runTest {
        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(
                partyName = "Gopal",
                principalAmount = 1000.0,
                amountPaid = 1000.0,
                isSettled = true,
                settledAtMillis = 123456789L
            )
        )
        val tx = fakeDao.getTransactionById(txId)!!

        val paymentId = fakeDao.insertPartialPayment(
            PartialPayment(transactionId = txId, amount = 400.0)
        )
        val payment = fakeDao.getAllPartialPaymentsSnapshot().first { it.id == paymentId }

        paymentRepository.deletePartialPayment(tx, payment, totalAccruedAmount = 1000.0)

        val updatedTx = fakeDao.getTransactionById(txId)!!
        assertEquals(600.0, updatedTx.amountPaid, 0.01)
        assertFalse("Should no longer be settled after payment removal", updatedTx.isSettled)

        assertTrue(fakeDao.getAllPartialPaymentsSnapshot().none { it.id == paymentId })
    }
}
