package com.jyotirmoyb.udhaar

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.security.EncryptedStorageManager
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
    }

    @Test
    fun realSqlCipher_plainDatabaseWithSampleDataMigratesCorrectly() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        EncryptedStorageManager.TestHooks.reset()
        val dbName = "instrumented_plain_to_cipher.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        dbFile.delete()
        File("${dbFile.absolutePath}-wal").delete()
        File("${dbFile.absolutePath}-shm").delete()

        val plainDb = Room.databaseBuilder(context, UdhaarDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        val plainDao = plainDb.udhaarDao()
        val partyId = plainDao.insertParty(
            PartyEntity(
                name = "Assam Tea Traders",
                phoneNumber = "+919864012345",
                upiId = "assamtea@sbi",
                preferredCurrency = CurrencyType.INR
            )
        )
        val txId = plainDao.insertTransaction(
            UdhaarTransaction(
                partyId = partyId,
                transactionType = TransactionType.GIVEN,
                partyName = "Assam Tea Traders",
                phoneNumber = "+919864012345",
                currency = CurrencyType.INR,
                principalAmount = 14_000.0,
                amountPaid = 4_000.0,
                paymentMode = PaymentMode.UPI
            )
        )
        plainDao.insertPartialPayment(
            PartialPayment(
                transactionId = txId,
                amount = 4_000.0,
                paymentMode = PaymentMode.UPI,
                note = "First payment"
            )
        )
        plainDb.close()

        assertTrue(EncryptedStorageManager.isPlaintextSqliteHeader(dbFile))
        EncryptedStorageManager.setDatabaseEncryptionState(
            context,
            EncryptedStorageManager.DbEncryptionState.NONE
        )

        val encryptedDb = EncryptedStorageManager.configureSqlCipherForRoom(
            context = context,
            builder = Room.databaseBuilder(context, UdhaarDatabase::class.java, dbName)
                .allowMainThreadQueries(),
            dbName = dbName
        ).build()

        // Verify file on disk is no longer plaintext SQLite format 3
        assertFalse(EncryptedStorageManager.isPlaintextSqliteHeader(dbFile))
        assertEquals(
            EncryptedStorageManager.DbEncryptionState.ENCRYPTED,
            EncryptedStorageManager.getDatabaseEncryptionState(context)
        )

        val dao = encryptedDb.udhaarDao()
        assertEquals(1, dao.getAllPartiesSnapshot().size)
        assertEquals("Assam Tea Traders", dao.getAllPartiesSnapshot().first().name)
        assertEquals(1, dao.getAllTransactionsSnapshot().size)
        assertEquals(14_000.0, dao.getAllTransactionsSnapshot().first().principalAmount, 0.01)
        assertEquals(1, dao.getAllPartialPaymentsSnapshot().size)

        encryptedDb.close()
        dbFile.delete()
    }

    @Test
    fun realSqlCipher_interruptedMigrationResumesAndCorruptedMigrationKeepsOriginal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        EncryptedStorageManager.TestHooks.reset()
        val dbName = "instrumented_corrupt_and_resume.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        val origDb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        origDb.execSQL("CREATE TABLE IF NOT EXISTS items (id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
        origDb.execSQL("INSERT INTO items (id, name) VALUES (1, 'SafeRecord')")
        origDb.version = 7
        origDb.close()
        val originalBytes = dbFile.readBytes()

        // 1. Simulate corruption during verification -> original plain file must remain untouched
        EncryptedStorageManager.TestHooks.simulateIntegrityCheckFailure = true
        assertFalse(EncryptedStorageManager.migratePlainDatabaseToEncrypted(context, dbFile))
        assertEquals(
            EncryptedStorageManager.DbEncryptionState.FAILED,
            EncryptedStorageManager.getDatabaseEncryptionState(context)
        )
        assertTrue(EncryptedStorageManager.isPlaintextSqliteHeader(dbFile))
        assertArrayEquals(originalBytes, dbFile.readBytes())

        // 2. Simulate interrupted migration (state = MIGRATING) -> resumes and encrypts cleanly
        EncryptedStorageManager.TestHooks.reset()
        EncryptedStorageManager.setDatabaseEncryptionState(
            context,
            EncryptedStorageManager.DbEncryptionState.MIGRATING
        )
        assertTrue(EncryptedStorageManager.migratePlainDatabaseToEncrypted(context, dbFile))
        assertEquals(
            EncryptedStorageManager.DbEncryptionState.ENCRYPTED,
            EncryptedStorageManager.getDatabaseEncryptionState(context)
        )
        assertFalse(EncryptedStorageManager.isPlaintextSqliteHeader(dbFile))
        dbFile.delete()
    }

    @Test
    fun realSqlCipher_freshInstallCreatesEncryptedDatabase() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        EncryptedStorageManager.TestHooks.reset()
        val dbName = "instrumented_fresh_install.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        val db = EncryptedStorageManager.configureSqlCipherForRoom(
            context = context,
            builder = Room.databaseBuilder(context, UdhaarDatabase::class.java, dbName)
                .allowMainThreadQueries(),
            dbName = dbName
        ).build()

        db.udhaarDao().insertParty(PartyEntity(name = "Fresh Install Party"))
        db.close()

        assertTrue(dbFile.exists() && dbFile.length() > 0L)
        assertFalse(
            "Fresh install database must be encrypted and not start with SQLite format 3",
            EncryptedStorageManager.isPlaintextSqliteHeader(dbFile)
        )
        assertEquals(
            EncryptedStorageManager.DbEncryptionState.ENCRYPTED,
            EncryptedStorageManager.getDatabaseEncryptionState(context)
        )
        dbFile.delete()
    }
}
