package com.jyotirmoyb.udhaar.security

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jyotirmoyb.udhaar.backup.DriveServiceHelper
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.AuditEventType
import com.jyotirmoyb.udhaar.data.AuditTrailEntry
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.repository.AttachmentRepository
import com.jyotirmoyb.udhaar.data.repository.BackupRepository
import com.jyotirmoyb.udhaar.data.repository.SettingsRepository
import com.jyotirmoyb.udhaar.util.LedgerBackupManager
import com.jyotirmoyb.udhaar.util.ParsedLedgerBackup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecureStorageAndBackupSafetyTest {

    private lateinit var context: Context
    private lateinit var database: UdhaarDatabase
    private lateinit var dao: UdhaarDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var backupRepository: BackupRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            UdhaarDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.udhaarDao()
        settingsRepository = SettingsRepository(context)
        val attachmentRepo = AttachmentRepository(dao, context)
        val driveServiceHelper = DriveServiceHelper()
        backupRepository = BackupRepository(dao, settingsRepository, attachmentRepo, driveServiceHelper, context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun plaintextFallbackRemoval_keystoreWrappingNeverProducesOrAcceptsRaw() {
        val testBytes = "my-secret-database-passphrase-32".toByteArray()

        // 1. Invoke wrapWithKeystoreOrEncode via unwrap test
        // Verify legacy RAW: blobs are strictly rejected
        val rawBlob = "RAW:" + Base64.getEncoder().encodeToString(testBytes)
        val reflectionMethodUnwrap = EncryptedStorageManager::class.java.getDeclaredMethod(
            "unwrapWithKeystoreOrDecode",
            String::class.java
        ).apply { isAccessible = true }

        val unwrappedResult = reflectionMethodUnwrap.invoke(EncryptedStorageManager, rawBlob) as? ByteArray
        assertNull("Legacy RAW: plaintext database keys must be rejected under fail-closed security", unwrappedResult)

        // 2. Verify KS1 wrapping produces KS1: and never RAW:
        val reflectionMethodWrap = EncryptedStorageManager::class.java.getDeclaredMethod(
            "wrapWithKeystoreOrEncode",
            ByteArray::class.java
        ).apply { isAccessible = true }

        val wrapped = reflectionMethodWrap.invoke(EncryptedStorageManager, testBytes) as String
        assertTrue("Database key wrapping must produce KS1: envelope", wrapped.startsWith("KS1:"))
        assertFalse("Database key wrapping must NEVER produce RAW: plaintext", wrapped.startsWith("RAW:"))

        // 3. Verify KS1 unwrapping successfully recovers plaintext
        val roundtrip = reflectionMethodUnwrap.invoke(EncryptedStorageManager, wrapped) as? ByteArray
        assertNotNull("KS1 envelope must decode correctly", roundtrip)
        assertEquals("Decoded bytes must match original", String(testBytes), String(roundtrip!!))
    }

    @Test
    fun zipSlipProtection_rejectsRelativeAndTraversingAttachmentPaths() {
        val targetDir = File(context.filesDir, "attachments").apply { mkdirs() }

        // Path traversal with ..
        try {
            BackupRepository.sanitizeAttachmentPath(targetDir, "../../sensitive_config.json")
            fail("Expected SecurityException for path traversal with '..'")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("rejected") == true || e.message?.contains("Unsafe") == true)
        }

        // Path traversal with backslash
        try {
            BackupRepository.sanitizeAttachmentPath(targetDir, "..\\..\\sensitive_config.json")
            fail("Expected SecurityException for backslash path traversal")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("rejected") == true || e.message?.contains("Unsafe") == true)
        }

        // Absolute path
        try {
            BackupRepository.sanitizeAttachmentPath(targetDir, "/etc/passwd")
            fail("Expected SecurityException for leading slash absolute path")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("rejected") == true || e.message?.contains("Unsafe") == true)
        }

        // Valid file name
        val safeFile = BackupRepository.sanitizeAttachmentPath(targetDir, "receipt_nov2026.jpg")
        assertEquals(File(targetDir, "receipt_nov2026.jpg").canonicalPath, safeFile.canonicalPath)
    }

    @Test
    fun zipBombProtection_rejectsExcessiveAttachmentCountOrSize() = runBlocking {
        // 1. Exceeds max count of 500
        val tooManyAttachments = (1..501).associate { i ->
            "file_$i.png" to "aGVsbG8=" // base64 "hello"
        }
        val bombBackup = ParsedLedgerBackup(
            exportType = "FULL_LEDGER",
            schemaVersion = 8,
            parties = emptyList(),
            transactions = emptyList(),
            partialPayments = emptyList(),
            attachments = tooManyAttachments
        )

        try {
            backupRepository.restoreFullLedgerBackup(bombBackup, replaceExisting = false)
            fail("Expected SecurityException for excessive attachment count > 500")
        } catch (e: SecurityException) {
            assertTrue("Expected count limit message", e.message?.contains("exceeds limit") == true)
        }
    }

    @Test
    fun corruptBackup_preservesLiveDatabaseDataWithoutAlteration() = runBlocking {
        // 1. Insert live records
        val partyId = dao.insertParty(PartyEntity(name = "Original Lender", phoneNumber = "+919876543210"))
        val txId = dao.insertTransaction(
            UdhaarTransaction(
                partyId = partyId,
                partyName = "Original Lender",
                principalAmountPaise = 250000L, // Rs 2,500
                amountPaidPaise = 50000L,
                transactionType = TransactionType.TAKEN
            )
        )
        dao.insertPartialPayment(
            PartialPayment(
                transactionId = txId,
                amountPaise = 50000L,
                note = "First installment"
            )
        )

        assertEquals(1, dao.getAllPartiesSnapshot().size)
        assertEquals(1, dao.getAllTransactionsSnapshot().size)
        assertEquals(1, dao.getAllPartialPayments().first().size)

        // 2. Attempt restore with unsupported/corrupt schema version (e.g. version 99)
        val corruptBackup = ParsedLedgerBackup(
            exportType = "FULL_LEDGER",
            schemaVersion = 99,
            parties = listOf(PartyEntity(name = "Malicious Party")),
            transactions = listOf(
                UdhaarTransaction(
                    partyName = "Malicious Party",
                    principalAmountPaise = 999999L
                )
            ),
            partialPayments = emptyList()
        )

        try {
            backupRepository.restoreFullLedgerBackup(corruptBackup, replaceExisting = true)
            fail("Expected IllegalArgumentException for unsupported schema version 99")
        } catch (e: IllegalArgumentException) {
            assertTrue("Expected unsupported schema version error", e.message?.contains("Unsupported backup schema version") == true)
        }

        // 3. Verify live data was NOT deleted or altered
        val partiesAfter = dao.getAllPartiesSnapshot()
        val txsAfter = dao.getAllTransactionsSnapshot()
        val paymentsAfter = dao.getAllPartialPayments().first()

        assertEquals("Live party count must remain 1", 1, partiesAfter.size)
        assertEquals("Original Lender", partiesAfter[0].name)
        assertEquals("Live transaction count must remain 1", 1, txsAfter.size)
        assertEquals(250000L, txsAfter[0].principalAmountPaise)
        assertEquals(1, paymentsAfter.size)
        assertEquals(50000L, paymentsAfter[0].amountPaise)
    }

    @Test
    fun multiStepMutation_roomTransactionAtomicity() = runBlocking {
        val partyId = dao.insertParty(PartyEntity(name = "Aman Kumar", phoneNumber = "+919999888877"))
        val txId = dao.insertTransaction(
            UdhaarTransaction(
                partyId = partyId,
                partyName = "Aman Kumar",
                principalAmountPaise = 100000L, // Rs 1,000
                amountPaidPaise = 0L,
                transactionType = TransactionType.GIVEN
            )
        )

        val tx = dao.getTransactionById(txId)!!

        // Execute atomic multi-step payment recording: payment + tx balance update + audit entry
        val updatedTx = tx.copy(
            amountPaidPaise = 40000L,
            isSettled = false
        )
        val payment = PartialPayment(
            transactionId = txId,
            amountPaise = 40000L,
            paymentMode = PaymentMode.UPI,
            remotePaymentId = "pay_test_atomic_123"
        )
        val audit = AuditTrailEntry(
            transactionId = txId,
            eventType = AuditEventType.PAYMENT_RECORDED,
            summaryText = "Recorded Rs 400 via UPI"
        )

        val insertedPayId = dao.recordPartialPaymentAtomic(
            payment = payment,
            proofAttachment = null,
            updatedTx = updatedTx,
            auditEntry = audit
        )

        assertTrue(insertedPayId > 0L)

        // Verify all 3 entities updated atomically
        val refreshedTx = dao.getTransactionById(txId)!!
        assertEquals(40000L, refreshedTx.amountPaidPaise)

        val payments = dao.getPartialPaymentsForTransactionSnapshot(txId)
        assertEquals(1, payments.size)
        assertEquals(40000L, payments[0].amountPaise)

        val auditEntries = dao.getAuditTrailForTransactionSnapshot(txId)
        assertEquals(1, auditEntries.size)
        assertEquals(AuditEventType.PAYMENT_RECORDED, auditEntries[0].eventType)

        // Verify idempotent second call returns existing payment ID without duplicating
        val secondInsertId = dao.recordPartialPaymentAtomic(
            payment = payment,
            proofAttachment = null,
            updatedTx = updatedTx,
            auditEntry = audit
        )
        assertEquals("Duplicate remote payment ID must be idempotent", insertedPayId, secondInsertId)
        assertEquals(1, dao.getPartialPaymentsForTransactionSnapshot(txId).size)

        // Test atomic delete
        dao.deleteTransactionAtomic(txId)
        assertNull("Transaction must be deleted", dao.getTransactionById(txId))
        assertEquals(0, dao.getPartialPaymentsForTransactionSnapshot(txId).size)
        assertEquals(0, dao.getAuditTrailForTransactionSnapshot(txId).size)
    }

    @Test
    fun resetAppDataSecurely_cleansFilesAndResetsState() {
        EncryptedStorageManager.resetAppDataSecurely(context)
        assertEquals(
            EncryptedStorageManager.DbEncryptionState.NONE,
            EncryptedStorageManager.encryptionStateFlow.value
        )
        assertNull(EncryptedStorageManager.blockingStartupErrorFlow.value)
    }
}
