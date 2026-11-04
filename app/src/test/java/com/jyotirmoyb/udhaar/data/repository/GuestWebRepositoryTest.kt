package com.jyotirmoyb.udhaar.data.repository

import com.jyotirmoyb.udhaar.data.AuditEventType
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.GuestDateProposalData
import com.jyotirmoyb.udhaar.data.GuestPaymentClaimData
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.cloud.GuestWebCloudRepository
import com.jyotirmoyb.udhaar.data.cloud.GuestWebLinkCreateResult
import com.jyotirmoyb.udhaar.data.getParsedDateProposal
import com.jyotirmoyb.udhaar.data.getParsedPaymentClaim
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
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
class GuestWebRepositoryTest {

    private lateinit var fakeDao: FakeUdhaarDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var installmentRepository: InstallmentRepository
    private lateinit var guestWebRepository: GuestWebRepository

    @Before
    fun setup() {
        fakeDao = FakeUdhaarDao()
        settingsRepository = SettingsRepository(null, null)
        installmentRepository = InstallmentRepository(fakeDao, settingsRepository, null)
        paymentRepository = PaymentRepository(
            dao = fakeDao,
            settingsRepository = settingsRepository,
            cloudRepository = null,
            installmentRepository = installmentRepository
        )
        val dummyCloudRepo = GuestWebCloudRepository(null, null, null)
        guestWebRepository = GuestWebRepository(
            dao = fakeDao,
            settingsRepository = settingsRepository,
            paymentRepository = paymentRepository,
            installmentRepository = installmentRepository,
            guestWebCloudRepository = dummyCloudRepo,
            context = null
        )
    }

    @Test
    fun createGuestWebLink_generatesOfflineSecretAndUpdatesTx() = runTest {
        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(
                partyName = "Ravi",
                principalAmount = 5000.0,
                currency = CurrencyType.INR,
                transactionType = TransactionType.GIVEN
            )
        )

        val result = guestWebRepository.createGuestWebLink(
            transactionId = txId,
            expiryDays = 30,
            trustLevelRequired = "WHATSAPP_OTP",
            allowNote = true,
            allowAttachments = false,
            showUpiQr = true
        )

        assertTrue(result.success)
        assertTrue(result.url.contains("/u/r/"))
        assertEquals("WHATSAPP_OTP", result.trustLevel)
        assertTrue(result.whatsappOtp.isNotBlank())

        val updatedTx = fakeDao.getTransactionById(txId)!!
        assertEquals("ACTIVE", updatedTx.webLinkStatus)
        assertEquals("WHATSAPP_OTP", updatedTx.trustLevel)

        val audit = fakeDao.getAuditTrailForTransactionSnapshot(txId)
        assertTrue(audit.any { it.eventType == AuditEventType.SHARED })
    }

    @Test
    fun revokeGuestWebLink_marksRevokedAndAudits() = runTest {
        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(
                partyName = "Ravi",
                principalAmount = 5000.0,
                webLinkStatus = "ACTIVE"
            )
        )

        val revoked = guestWebRepository.revokeGuestWebLink(txId)
        assertTrue(revoked)

        val updatedTx = fakeDao.getTransactionById(txId)!!
        assertEquals("REVOKED", updatedTx.webLinkStatus)

        val audit = fakeDao.getAuditTrailForTransactionSnapshot(txId)
        assertTrue(audit.any { it.eventType == AuditEventType.EDITED && it.summaryText.contains("revoked") })
    }

    @Test
    fun acceptPaymentClaim_recordsPartialPayment_andUpdatesTx() = runTest {
        val claimJson = JSONObject().apply {
            put("amount", 2000.0)
            put("amountDouble", 2000.0)
            put("dateMillis", System.currentTimeMillis())
            put("paymentMode", "UPI")
            put("reference", "UPI987654321")
            put("proofUrl", "")
            put("note", "Paid by PhonePe")
        }.toString()

        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(
                partyName = "Ravi",
                principalAmount = 5000.0,
                amountPaid = 0.0,
                pendingPaymentClaim = claimJson
            )
        )

        val accepted = guestWebRepository.acceptPaymentClaim(txId)
        assertTrue(accepted)

        val updatedTx = fakeDao.getTransactionById(txId)!!
        assertEquals("", updatedTx.pendingPaymentClaim)
        assertEquals("PAYMENT_CONFIRMED", updatedTx.lastGuestAction)
        assertEquals(2000.0, updatedTx.amountPaid, 0.01)

        val payments = paymentRepository.getPartialPayments(txId).first()
        assertEquals(1, payments.size)
        assertEquals(2000.0, payments.first().amount, 0.01)
        assertEquals(PaymentMode.UPI, payments.first().paymentMode)
        assertTrue(payments.first().note.contains("UPI987654321"))
    }

    @Test
    fun rejectPaymentClaim_clearsClaim_andMarksRejected() = runTest {
        val claimJson = JSONObject().apply {
            put("amount", 1500.0)
            put("amountDouble", 1500.0)
            put("paymentMode", "CASH")
        }.toString()

        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(
                partyName = "Suresh",
                principalAmount = 3000.0,
                pendingPaymentClaim = claimJson
            )
        )

        val rejected = guestWebRepository.rejectPaymentClaim(txId, reason = "Did not receive cash")
        assertTrue(rejected)

        val updatedTx = fakeDao.getTransactionById(txId)!!
        assertEquals("", updatedTx.pendingPaymentClaim)
        assertEquals("PAYMENT_REJECTED", updatedTx.lastGuestAction)

        val audit = fakeDao.getAuditTrailForTransactionSnapshot(txId)
        assertTrue(audit.any { it.eventType == AuditEventType.DISPUTED })
    }

    @Test
    fun acceptDateProposal_updatesDueDate_andAudits() = runTest {
        val newDate = System.currentTimeMillis() + 86400000L * 14
        val proposalJson = JSONObject().apply {
            put("proposedDateMillis", newDate)
            put("message", "Need two more weeks")
        }.toString()

        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(
                partyName = "Anita",
                principalAmount = 10000.0,
                expiryDateMillis = System.currentTimeMillis() + 86400000L,
                pendingDateProposal = proposalJson
            )
        )

        val accepted = guestWebRepository.acceptDateProposal(txId)
        assertTrue(accepted)

        val updatedTx = fakeDao.getTransactionById(txId)!!
        assertEquals(newDate, updatedTx.expiryDateMillis)
        assertEquals("", updatedTx.pendingDateProposal)
        assertEquals("DATE_ACCEPTED", updatedTx.lastGuestAction)

        val audit = fakeDao.getAuditTrailForTransactionSnapshot(txId)
        assertTrue(audit.any { it.eventType == AuditEventType.EXTENSION_ACCEPTED })
    }

    @Test
    fun declineDateProposal_clearsProposal_andAudits() = runTest {
        val proposalJson = JSONObject().apply {
            put("proposedDateMillis", System.currentTimeMillis() + 86400000L * 30)
            put("message", "Please give one month")
        }.toString()

        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(
                partyName = "Anita",
                principalAmount = 10000.0,
                pendingDateProposal = proposalJson
            )
        )

        val declined = guestWebRepository.declineDateProposal(txId)
        assertTrue(declined)

        val updatedTx = fakeDao.getTransactionById(txId)!!
        assertEquals("", updatedTx.pendingDateProposal)
        assertEquals("DATE_DECLINED", updatedTx.lastGuestAction)

        val audit = fakeDao.getAuditTrailForTransactionSnapshot(txId)
        assertTrue(audit.any { it.eventType == AuditEventType.EXTENSION_DECLINED })
    }

    @Test
    fun revokeAllGuestLinks_revokesOnlyActiveRecords() = runTest {
        val id1 = fakeDao.insertTransaction(UdhaarTransaction(partyName = "A", principalAmount = 100.0, webLinkStatus = "ACTIVE"))
        val id2 = fakeDao.insertTransaction(UdhaarTransaction(partyName = "B", principalAmount = 200.0, webLinkStatus = "ACTIVE"))
        val id3 = fakeDao.insertTransaction(UdhaarTransaction(partyName = "C", principalAmount = 300.0, webLinkStatus = "REVOKED"))

        val count = guestWebRepository.revokeAllGuestLinks()
        assertEquals(2, count)

        assertEquals("REVOKED", fakeDao.getTransactionById(id1)!!.webLinkStatus)
        assertEquals("REVOKED", fakeDao.getTransactionById(id2)!!.webLinkStatus)
        assertEquals("REVOKED", fakeDao.getTransactionById(id3)!!.webLinkStatus)
    }
}
