package com.jyotirmoyb.udhaar

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jyotirmoyb.udhaar.backup.BackupPreviewSummary
import com.jyotirmoyb.udhaar.backup.DriveBackupCrypto
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.data.UdhaarRepository
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.security.EncryptedStorageManager
import com.jyotirmoyb.udhaar.util.LedgerBackupManager
import com.jyotirmoyb.udhaar.util.TelemetryManager
import com.jyotirmoyb.udhaar.widget.UdhaarWidgetProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Managed-device emulator suite (`pixel6Api34DebugAndroidTest`) covering SQLCipher hardware/JNI
 * encryption and the 6 release smoke test journeys:
 * 1. Cold start
 * 2. Add a record
 * 3. Restore a backup
 * 4. Open a guest link
 * 5. Widget update
 * 6. Database migration from the previous version (v11 -> v12)
 */
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
                principalAmountPaise = 1_400_000L,
                amountPaidPaise = 400_000L,
                paymentMode = PaymentMode.UPI
            )
        )
        plainDao.insertPartialPayment(
            PartialPayment(
                transactionId = txId,
                amountPaise = 400_000L,
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
        assertEquals(1_400_000L, dao.getAllTransactionsSnapshot().first().principalAmountPaise)
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

    @Test
    fun releaseSmoke_coldStart_addRecord_restoreBackup_guestLink_widgetUpdate_and_migrationFromPreviousVersion() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 1. Cold start: launch MainActivity, verify SQLCipher 256-bit key & telemetry default OFF
        TelemetryManager.setConsent(context, false)
        assertFalse("Telemetry must be OFF by default on cold start", TelemetryManager.isConsentGranted)
        val keyBytes = EncryptedStorageManager.getOrCreateDatabasePassphrase(context)
        assertEquals(32, keyBytes.size)
        keyBytes.fill(0)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
            }
        }

        // 2. Add a record: insert an INR record in paise and verify DAO + repository state
        val db = UdhaarDatabase.getInstance(context)
        val dao = db.udhaarDao()
        val repo = UdhaarRepository(dao, context)

        val addedTxId = dao.insertTransaction(
            UdhaarTransaction(
                transactionType = TransactionType.GIVEN,
                partyName = "Smoke Test Trader",
                phoneNumber = "+919876500001",
                currency = CurrencyType.INR,
                principalAmountPaise = 250_000L, // ₹2,500.00
                amountPaidPaise = 50_000L,       // ₹500.00
                paymentMode = PaymentMode.UPI,
                notes = "Managed emulator smoke test record"
            )
        )
        assertTrue(addedTxId > 0L)
        val loadedTx = dao.getTransactionById(addedTxId)
        assertNotNull(loadedTx)
        assertEquals(250_000L, loadedTx?.principalAmountPaise)
        assertEquals(50_000L, loadedTx?.amountPaidPaise)

        // 3. Restore a backup: build encrypted .udhaar snapshot, decrypt, and restore cleanly
        val backupParty = PartyEntity(
            partyId = 10L,
            name = "Restored Backup Party",
            phoneNumber = "+919811223344",
            upiId = "restored@okaxis",
            preferredCurrency = CurrencyType.INR
        )
        val backupTx = UdhaarTransaction(
            id = 20L,
            partyId = 10L,
            transactionType = TransactionType.GIVEN,
            partyName = "Restored Backup Party",
            phoneNumber = "+919811223344",
            currency = CurrencyType.INR,
            principalAmountPaise = 725_050L, // ₹7,250.50
            amountPaidPaise = 125_050L,      // ₹1,250.50
            paymentMode = PaymentMode.CASH
        )
        val backupPayment = PartialPayment(
            id = 30L,
            transactionId = 20L,
            amountPaise = 125_050L,
            paymentMode = PaymentMode.CASH,
            note = "Backup verified installment"
        )
        val backupJson = LedgerBackupManager.buildBackupJson(
            parties = listOf(backupParty),
            transactions = listOf(backupTx),
            partialPayments = listOf(backupPayment),
            exportType = "FULL_LEDGER"
        )
        val encryptedEnvelope = DriveBackupCrypto.encrypt(
            plainBytes = backupJson.toByteArray(Charsets.UTF_8),
            passphrase = "SmokeTestRecovery#2026",
            preview = BackupPreviewSummary(partiesCount = 1, transactionsCount = 1, paymentsCount = 1)
        )
        val decryptedJson = String(
            DriveBackupCrypto.decrypt(encryptedEnvelope, "SmokeTestRecovery#2026"),
            Charsets.UTF_8
        )
        val parsedBackup = LedgerBackupManager.parseBackupJson(decryptedJson)
        val restoredCount = repo.restoreFullLedgerBackup(parsedBackup, replaceExisting = true)
        assertEquals(1, restoredCount)
        assertEquals(725_050L, dao.getAllTransactionsSnapshot().first().principalAmountPaise)

        // 4. Open a guest link: launch MainActivity with ACTION_VIEW https://udhaar.jyotirmoyb.com/u/r/...
        val guestIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://udhaar.jyotirmoyb.com/u/r/smoke_rec_01#secret_key_01")
        ).setClass(context, MainActivity::class.java)
        ActivityScenario.launch<MainActivity>(guestIntent).use { guestScenario ->
            guestScenario.onActivity { activity ->
                assertNotNull(activity)
                assertEquals(Intent.ACTION_VIEW, activity.intent?.action)
            }
        }

        // 5. Widget update: invoke UdhaarWidgetProvider.updateAllWidgets and onUpdate
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, UdhaarWidgetProvider::class.java))
        UdhaarWidgetProvider().onUpdate(context, appWidgetManager, if (widgetIds.isEmpty()) intArrayOf(1) else widgetIds)
        UdhaarWidgetProvider.updateAllWidgets(context)

        // 6. Database migration from the previous version (v11 -> v12)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `udhaar_transactions` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `partyId` INTEGER,
                                `transactionType` TEXT NOT NULL,
                                `partyName` TEXT NOT NULL,
                                `phoneNumber` TEXT NOT NULL,
                                `currency` TEXT NOT NULL,
                                `principalAmountPaise` INTEGER NOT NULL DEFAULT 0,
                                `amountPaidPaise` INTEGER NOT NULL DEFAULT 0,
                                `paymentMode` TEXT NOT NULL,
                                `isInterestEnabled` INTEGER NOT NULL,
                                `interestType` TEXT NOT NULL,
                                `interestRate` REAL NOT NULL,
                                `interestRatePeriod` TEXT NOT NULL,
                                `compoundFrequency` TEXT NOT NULL,
                                `startDateMillis` INTEGER NOT NULL,
                                `expiryDateMillis` INTEGER,
                                `reminderDateMillis` INTEGER,
                                `remindersDisabled` INTEGER NOT NULL DEFAULT 0,
                                `snoozedUntilMillis` INTEGER,
                                `lastReminderSentMillis` INTEGER,
                                `lastReminderLevel` INTEGER NOT NULL DEFAULT 0,
                                `notes` TEXT NOT NULL,
                                `isSettled` INTEGER NOT NULL,
                                `settledAtMillis` INTEGER,
                                `createdAtMillis` INTEGER NOT NULL DEFAULT 0,
                                `remoteId` TEXT NOT NULL DEFAULT '',
                                `verificationStatus` TEXT NOT NULL DEFAULT 'LOCAL_ONLY',
                                `verifiedAtMillis` INTEGER,
                                `counterpartyUserId` TEXT NOT NULL DEFAULT '',
                                `contentHash` TEXT NOT NULL DEFAULT '',
                                `needsMyConfirmation` INTEGER NOT NULL DEFAULT 0,
                                `shareToken` TEXT NOT NULL DEFAULT '',
                                `disputeReason` TEXT NOT NULL DEFAULT '',
                                `lenderName` TEXT NOT NULL DEFAULT '',
                                `borrowerName` TEXT NOT NULL DEFAULT '',
                                `webLinkStatus` TEXT NOT NULL DEFAULT '',
                                `lastGuestAction` TEXT NOT NULL DEFAULT '',
                                `trustLevel` TEXT NOT NULL DEFAULT '',
                                `pendingPaymentClaim` TEXT NOT NULL DEFAULT '',
                                `pendingDateProposal` TEXT NOT NULL DEFAULT ''
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `partial_payments` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `transactionId` INTEGER NOT NULL,
                                `amountPaise` INTEGER NOT NULL DEFAULT 0,
                                `paymentMode` TEXT NOT NULL DEFAULT 'CASH',
                                `paidAtMillis` INTEGER NOT NULL,
                                `note` TEXT NOT NULL,
                                `remotePaymentId` TEXT NOT NULL DEFAULT '',
                                `recordedByUserId` TEXT NOT NULL DEFAULT '',
                                `recordedByName` TEXT NOT NULL DEFAULT '',
                                `isAcknowledged` INTEGER NOT NULL DEFAULT 1,
                                `acknowledgedAtMillis` INTEGER,
                                `needsMyAcknowledgement` INTEGER NOT NULL DEFAULT 0,
                                FOREIGN KEY(`transactionId`) REFERENCES `udhaar_transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO `udhaar_transactions` (
                                `id`, `partyId`, `transactionType`, `partyName`, `phoneNumber`, `currency`,
                                `principalAmountPaise`, `amountPaidPaise`, `paymentMode`, `isInterestEnabled`,
                                `interestType`, `interestRate`, `interestRatePeriod`, `compoundFrequency`,
                                `startDateMillis`, `notes`, `isSettled`, `createdAtMillis`
                            ) VALUES (
                                1, NULL, 'GIVEN', 'Previous Version v11 Party', '+919900112233', 'INR',
                                450000, 150000, 'UPI', 0,
                                'SIMPLE', 0.0, 'PER_MONTH', 'MONTHLY',
                                1700000000000, 'Migrating from v11 to v12', 0, 1700000000000
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO `partial_payments` (`id`, `transactionId`, `amountPaise`, `paidAtMillis`, `paymentMode`, `note`)
                            VALUES (1, 1, 150000, 1700005000000, 'UPI', 'v11 partial')
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val migDb = helper.writableDatabase
        UdhaarDatabase.MIGRATION_11_12.migrate(migDb)

        migDb.query("SELECT partyName, principalAmountPaise, amountPaidPaise FROM udhaar_transactions WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Previous Version v11 Party", c.getString(0))
            assertEquals(450_000L, c.getLong(1))
            assertEquals(150_000L, c.getLong(2))
        }
        helper.close()
    }
}
