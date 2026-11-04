package com.jyotirmoyb.udhaar.data.repository

import com.jyotirmoyb.udhaar.data.AuditEventType
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.ReminderChannel
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.VerificationStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LedgerRepositoryTest {

    private lateinit var fakeDao: FakeUdhaarDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var ledgerRepository: LedgerRepository

    @Before
    fun setup() {
        fakeDao = FakeUdhaarDao()
        settingsRepository = SettingsRepository(null, null)
        ledgerRepository = LedgerRepository(
            dao = fakeDao,
            settingsRepository = settingsRepository,
            cloudRepository = null,
            context = null
        )
    }

    @Test
    fun saveTransaction_createsNewPartyAndTransactionWithAuditTrail() = runTest {
        val tx = UdhaarTransaction(
            transactionType = TransactionType.GIVEN,
            partyName = "Priya Sharma",
            phoneNumber = "9876543210",
            principalAmount = 5000.0,
            currency = CurrencyType.INR,
            paymentMode = PaymentMode.UPI
        )

        val txId = ledgerRepository.saveTransaction(tx, myProfileName = "Self")
        assertTrue("Transaction ID must be positive", txId > 0L)

        val savedTx = ledgerRepository.getTransactionById(txId)
        assertNotNull(savedTx)
        assertEquals("Priya Sharma", savedTx?.partyName)
        assertEquals(5000.0, savedTx?.principalAmount ?: 0.0, 0.01)
        assertTrue("Content hash must be generated", savedTx?.contentHash?.isNotBlank() == true)

        // Verify party auto-creation
        val party = fakeDao.getPartyByName("Priya Sharma")
        assertNotNull(party)
        assertEquals(savedTx?.partyId, party?.partyId)

        // Verify audit trail event
        val auditList = ledgerRepository.getAuditTrail(txId).first()
        assertTrue(auditList.isNotEmpty())
        assertEquals(AuditEventType.CREATED, auditList.first().eventType)
    }

    @Test
    fun saveTransaction_linksToExistingPartyWithoutDuplicates() = runTest {
        val existingPartyId = fakeDao.insertParty(
            com.jyotirmoyb.udhaar.data.PartyEntity(
                name = "Aarav Thapa",
                phoneNumber = "+9779800000000",
                preferredCurrency = CurrencyType.NPR
            )
        )

        val tx = UdhaarTransaction(
            transactionType = TransactionType.TAKEN,
            partyName = "Aarav Thapa",
            principalAmount = 15000.0,
            currency = CurrencyType.NPR,
            paymentMode = PaymentMode.CASH
        )

        val txId = ledgerRepository.saveTransaction(tx)
        val savedTx = ledgerRepository.getTransactionById(txId)

        assertEquals(existingPartyId, savedTx?.partyId)
        val allParties = fakeDao.getAllPartiesSnapshot()
        assertEquals(1, allParties.size)
    }

    @Test
    fun saveTransaction_termsChanged_updatesVerificationStatusToEdited() = runTest {
        val initialTx = UdhaarTransaction(
            transactionType = TransactionType.GIVEN,
            partyName = "Rohan",
            principalAmount = 1000.0,
            currency = CurrencyType.INR,
            paymentMode = PaymentMode.CASH
        )
        val txId = ledgerRepository.saveTransaction(initialTx)
        val saved = ledgerRepository.getTransactionById(txId)!!

        // Simulate that transaction was confirmed by counterparty
        fakeDao.updateTransaction(saved.copy(verificationStatus = VerificationStatus.CONFIRMED))

        // Edit terms (change principal amount)
        val updatedTx = saved.copy(principalAmount = 2500.0)
        ledgerRepository.saveTransaction(updatedTx)

        val modified = ledgerRepository.getTransactionById(txId)
        assertEquals(VerificationStatus.EDITED_AFTER_CONFIRM, modified?.verificationStatus)
        assertEquals(2500.0, modified?.principalAmount ?: 0.0, 0.01)

        val audit = ledgerRepository.getAuditTrail(txId).first()
        assertTrue(audit.any { it.eventType == AuditEventType.EDITED })
    }

    @Test
    fun snoozeTransaction_and_clearSnooze() = runTest {
        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(
                partyName = "Kavita",
                principalAmount = 2000.0
            )
        )

        ledgerRepository.snoozeTransaction(txId, days = 7)
        var tx = ledgerRepository.getTransactionById(txId)
        assertNotNull(tx?.snoozedUntilMillis)
        assertTrue(tx!!.snoozedUntilMillis!! > System.currentTimeMillis())

        ledgerRepository.clearTransactionSnooze(txId)
        tx = ledgerRepository.getTransactionById(txId)
        assertNull(tx?.snoozedUntilMillis)
    }

    @Test
    fun stopAndEnableTransactionReminders() = runTest {
        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(
                partyName = "Deepak",
                principalAmount = 3000.0
            )
        )

        ledgerRepository.stopTransactionReminders(txId)
        var tx = ledgerRepository.getTransactionById(txId)
        assertEquals(true, tx?.remindersDisabled)

        ledgerRepository.enableTransactionReminders(txId)
        tx = ledgerRepository.getTransactionById(txId)
        assertEquals(false, tx?.remindersDisabled)
    }

    @Test
    fun recordReminderEvent_updatesTransactionAndInsertsEvent() = runTest {
        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(
                partyName = "Sunil",
                principalAmount = 4500.0
            )
        )

        ledgerRepository.recordReminderEvent(txId, level = 2, channel = ReminderChannel.WHATSAPP)

        val events = ledgerRepository.getReminderEvents(txId).first()
        assertEquals(1, events.size)
        assertEquals(2, events.first().level)
        assertEquals(ReminderChannel.WHATSAPP, events.first().channel)

        val tx = ledgerRepository.getTransactionById(txId)
        assertEquals(2, tx?.lastReminderLevel)
        assertTrue((tx?.lastReminderSentMillis ?: 0L) > 0L)
    }

    @Test
    fun toggleFullSettlement_settlesAndReopensCorrectly() = runTest {
        val tx = UdhaarTransaction(
            partyName = "Anita",
            principalAmount = 8000.0,
            amountPaid = 0.0,
            isSettled = false
        )
        val txId = fakeDao.insertTransaction(tx)
        val loaded = ledgerRepository.getTransactionById(txId)!!

        // Toggle to settle
        ledgerRepository.toggleFullSettlement(loaded, totalAccruedAmount = 8000.0)
        val settledTx = ledgerRepository.getTransactionById(txId)!!
        assertTrue(settledTx.isSettled)
        assertNotNull(settledTx.settledAtMillis)

        // Toggle to reopen
        ledgerRepository.toggleFullSettlement(settledTx, totalAccruedAmount = 8000.0)
        val reopenedTx = ledgerRepository.getTransactionById(txId)!!
        assertFalse(reopenedTx.isSettled)
        assertNull(reopenedTx.settledAtMillis)
    }

    @Test
    fun deleteTransaction_cleansUpAssociatedData() = runTest {
        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(partyName = "Amit", principalAmount = 1000.0)
        )
        fakeDao.insertPartialPayment(
            com.jyotirmoyb.udhaar.data.PartialPayment(transactionId = txId, amount = 200.0)
        )
        fakeDao.insertAuditTrailEntry(
            com.jyotirmoyb.udhaar.data.AuditTrailEntry(
                transactionId = txId,
                eventType = AuditEventType.CREATED,
                summaryText = "Created"
            )
        )

        val tx = ledgerRepository.getTransactionById(txId)!!
        ledgerRepository.deleteTransaction(tx)

        assertNull(ledgerRepository.getTransactionById(txId))
        assertTrue(fakeDao.getPartialPaymentsForTransactionSnapshot(txId).isEmpty())
        assertTrue(fakeDao.getAuditTrailForTransactionSnapshot(txId).isEmpty())
    }
}
