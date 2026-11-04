package com.jyotirmoyb.udhaar.data.repository

import com.jyotirmoyb.udhaar.backup.DriveServiceHelper
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.ChatMessageEntity
import com.jyotirmoyb.udhaar.data.ChatThreadMetadata
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.Installment
import com.jyotirmoyb.udhaar.data.InstallmentPlan
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.util.ParsedLedgerBackup
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupRepositoryTest {

    private lateinit var fakeDao: FakeUdhaarDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var attachmentRepository: AttachmentRepository
    private lateinit var backupRepository: BackupRepository

    @Before
    fun setup() {
        fakeDao = FakeUdhaarDao()
        settingsRepository = SettingsRepository(null, null)
        attachmentRepository = AttachmentRepository(fakeDao, null)
        val driveServiceHelper = DriveServiceHelper()
        backupRepository = BackupRepository(
            dao = fakeDao,
            settingsRepository = settingsRepository,
            attachmentRepository = attachmentRepository,
            driveServiceHelper = driveServiceHelper,
            context = null
        )
    }

    @Test
    fun getFullLedgerSnapshot_retrievesAllData() = runTest {
        val pId = fakeDao.insertParty(PartyEntity(name = "Kishore", phoneNumber = "9876543210"))
        val tId = fakeDao.insertTransaction(
            UdhaarTransaction(partyId = pId, partyName = "Kishore", principalAmount = 5000.0)
        )
        fakeDao.insertPartialPayment(
            PartialPayment(transactionId = tId, amount = 1000.0)
        )

        val (parties, transactions, payments) = backupRepository.getFullLedgerSnapshot()
        assertEquals(1, parties.size)
        assertEquals("Kishore", parties.first().name)
        assertEquals(1, transactions.size)
        assertEquals(5000.0, transactions.first().principalAmount, 0.01)
        assertEquals(1, payments.size)
        assertEquals(1000.0, payments.first().amount, 0.01)
    }

    @Test
    fun getFullLedgerBackupSnapshot_includesAllTables() = runTest {
        val pId = fakeDao.insertParty(PartyEntity(name = "Sunita"))
        val tId = fakeDao.insertTransaction(UdhaarTransaction(partyId = pId, partyName = "Sunita", principalAmount = 3000.0))
        val payId = fakeDao.insertPartialPayment(PartialPayment(transactionId = tId, amount = 500.0))
        fakeDao.upsertChatThreadMetadata(ChatThreadMetadata(threadKey = "thread_1", partyName = "Sunita"))
        fakeDao.insertChatMessage(ChatMessageEntity(threadKey = "thread_1", partyName = "Sunita", bodyText = "Hello"))
        fakeDao.insertAttachment(
            Attachment(
                transactionId = tId,
                paymentId = payId,
                fileName = "invoice.pdf",
                localPath = "/tmp/invoice.pdf",
                sizeBytes = 1024,
                mimeType = "application/pdf"
            )
        )

        val snapshot = backupRepository.getFullLedgerBackupSnapshot()
        assertEquals(1, snapshot.parties.size)
        assertEquals(1, snapshot.transactions.size)
        assertEquals(1, snapshot.partialPayments.size)
        assertEquals(1, snapshot.chatThreads.size)
        assertEquals(1, snapshot.chatMessages.size)
        assertEquals(1, snapshot.attachments.size)
    }

    @Test
    fun importLedgerData_relinksPartiesTransactionsAndPayments() = runTest {
        val exportParties = listOf(
            PartyEntity(partyId = 10L, name = "Rajesh", phoneNumber = "+919123456789")
        )
        val exportTransactions = listOf(
            UdhaarTransaction(
                id = 20L,
                partyId = 10L,
                partyName = "Rajesh",
                principalAmount = 12000.0,
                currency = CurrencyType.INR,
                transactionType = TransactionType.GIVEN
            )
        )
        val exportPayments = listOf(
            PartialPayment(
                id = 30L,
                transactionId = 20L,
                amount = 4000.0,
                paymentMode = PaymentMode.ONLINE
            )
        )

        val importedCount = backupRepository.importLedgerData(
            parties = exportParties,
            transactions = exportTransactions,
            partialPayments = exportPayments
        )

        assertEquals(1, importedCount)

        val importedParties = fakeDao.getAllPartiesSnapshot()
        assertEquals(1, importedParties.size)
        val newParty = importedParties.first()
        assertEquals("Rajesh", newParty.name)

        val importedTxs = fakeDao.getAllTransactionsSnapshot()
        assertEquals(1, importedTxs.size)
        val newTx = importedTxs.first()
        assertEquals(newParty.partyId, newTx.partyId)
        assertEquals(12000.0, newTx.principalAmount, 0.01)

        val importedPayments = fakeDao.getAllPartialPaymentsSnapshot()
        assertEquals(1, importedPayments.size)
        val newPayment = importedPayments.first()
        assertEquals(newTx.id, newPayment.transactionId)
        assertEquals(4000.0, newPayment.amount, 0.01)
    }

    @Test
    fun importLedgerData_mergesWithExistingPartyByName() = runTest {
        val existingPartyId = fakeDao.insertParty(
            PartyEntity(name = "Kavita Roy", phoneNumber = "9800000000", tagOrNotes = "Old note")
        )

        val exportParties = listOf(
            PartyEntity(partyId = 99L, name = "Kavita Roy", phoneNumber = "9800000000", tagOrNotes = "Updated note")
        )
        val exportTransactions = listOf(
            UdhaarTransaction(id = 88L, partyId = 99L, partyName = "Kavita Roy", principalAmount = 2500.0)
        )

        backupRepository.importLedgerData(
            parties = exportParties,
            transactions = exportTransactions,
            partialPayments = emptyList()
        )

        val allParties = fakeDao.getAllPartiesSnapshot()
        assertEquals(1, allParties.size)
        assertEquals(existingPartyId, allParties.first().partyId)
        assertEquals("Updated note", allParties.first().tagOrNotes)

        val tx = fakeDao.getAllTransactionsSnapshot().first()
        assertEquals(existingPartyId, tx.partyId)
    }

    @Test
    fun importLedgerData_relinksAttachmentsWithNewTransactionAndPaymentIds() = runTest {
        val exportParties = listOf(PartyEntity(partyId = 1L, name = "Mohan"))
        val exportTxs = listOf(UdhaarTransaction(id = 2L, partyId = 1L, partyName = "Mohan", principalAmount = 7000.0))
        val exportPayments = listOf(PartialPayment(id = 3L, transactionId = 2L, amount = 1000.0))
        val exportAttachments = listOf(
            Attachment(
                id = 4L,
                transactionId = 2L,
                paymentId = 3L,
                fileName = "proof.png",
                localPath = "/data/proof.png",
                sizeBytes = 500,
                mimeType = "image/png"
            )
        )

        backupRepository.importLedgerData(
            parties = exportParties,
            transactions = exportTxs,
            partialPayments = exportPayments,
            attachments = exportAttachments
        )

        val importedTx = fakeDao.getAllTransactionsSnapshot().first()
        val importedPayment = fakeDao.getAllPartialPaymentsSnapshot().first()
        val importedAttachment = fakeDao.getAllAttachmentsSnapshot().first()

        assertEquals(importedTx.id, importedAttachment.transactionId)
        assertEquals(importedPayment.id, importedAttachment.paymentId)
    }

    @Test
    fun autoBackupPassphrase_inMemoryFallback() {
        val testPassphrase = "MySecretBackupKey123"
        backupRepository.saveAutoBackupPassphrase(testPassphrase)
        val retrieved = backupRepository.getAutoBackupPassphrase()
        assertEquals(testPassphrase, retrieved)
    }

    @Test
    fun getFullLedgerBackupSnapshot_includesInstallmentPlansAndInstallments() = runTest {
        val tId = fakeDao.insertTransaction(UdhaarTransaction(partyName = "Geeta", principalAmount = 4000.0))
        val planId = fakeDao.insertInstallmentPlan(
            InstallmentPlan(transactionId = tId, installmentCount = 2, startDate = 1715000000000L, totalAmountDue = 400000L)
        )
        fakeDao.insertInstallments(
            listOf(
                Installment(planId = planId, sequence = 1, dueDate = 1715000000000L, amountDue = 200000L),
                Installment(planId = planId, sequence = 2, dueDate = 1717500000000L, amountDue = 200000L)
            )
        )

        val snapshot = backupRepository.getFullLedgerBackupSnapshot()
        assertEquals(1, snapshot.installmentPlans.size)
        assertEquals(2, snapshot.installments.size)
        assertEquals(400000L, snapshot.installmentPlans.first().totalAmountDue)
    }

    @Test
    fun restoreFullLedgerBackup_roundtripsInstallmentPlansAndInstallments() = runTest {
        val exportParties = listOf(PartyEntity(partyId = 1L, name = "Karan"))
        val exportTxs = listOf(UdhaarTransaction(id = 2L, partyId = 1L, partyName = "Karan", principalAmount = 5000.0))
        val exportPlans = listOf(
            InstallmentPlan(id = 10L, transactionId = 2L, installmentCount = 2, startDate = 1715000000000L, totalAmountDue = 500000L)
        )
        val exportInstallments = listOf(
            Installment(id = 101L, planId = 10L, sequence = 1, dueDate = 1715000000000L, amountDue = 250000L),
            Installment(id = 102L, planId = 10L, sequence = 2, dueDate = 1717500000000L, amountDue = 250000L)
        )

        val snapshot = ParsedLedgerBackup(
            exportType = "FULL_BACKUP",
            schemaVersion = 8,
            parties = exportParties,
            transactions = exportTxs,
            partialPayments = emptyList(),
            installmentPlans = exportPlans,
            installments = exportInstallments
        )

        val restoredCount = backupRepository.restoreFullLedgerBackup(snapshot, replaceExisting = true)
        assertEquals(1, restoredCount)

        val restoredPlans = fakeDao.getAllInstallmentPlansSnapshot()
        val restoredInstallments = fakeDao.getAllInstallmentsSnapshot()
        val restoredTxs = fakeDao.getAllTransactionsSnapshot()

        assertEquals(1, restoredPlans.size)
        assertEquals(2, restoredInstallments.size)
        assertEquals(restoredTxs.first().id, restoredPlans.first().transactionId)
        assertEquals(restoredPlans.first().id, restoredInstallments.first().planId)
    }

    @Test
    fun restoreFullLedgerBackup_backwardsCompatibleWithOlderBackupWithoutInstallments() = runTest {
        val exportParties = listOf(PartyEntity(partyId = 1L, name = "OldUser"))
        val exportTxs = listOf(UdhaarTransaction(id = 2L, partyId = 1L, partyName = "OldUser", principalAmount = 1000.0))

        val legacySnapshot = ParsedLedgerBackup(
            exportType = "FULL_BACKUP",
            schemaVersion = 7,
            parties = exportParties,
            transactions = exportTxs,
            partialPayments = emptyList(),
            installmentPlans = emptyList(),
            installments = emptyList()
        )

        val restoredCount = backupRepository.restoreFullLedgerBackup(legacySnapshot, replaceExisting = true)
        assertEquals(1, restoredCount)
        assertTrue(fakeDao.getAllInstallmentPlansSnapshot().isEmpty())
    }
}
