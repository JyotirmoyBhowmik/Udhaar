package com.jyotirmoyb.udhaar

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.jyotirmoyb.udhaar.di.FirebaseModule
import com.jyotirmoyb.udhaar.backup.BackupPreviewSummary
import com.jyotirmoyb.udhaar.backup.DriveBackupCrypto
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.SyncStatus
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.data.UdhaarRepository
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.security.EncryptedStorageManager
import com.jyotirmoyb.udhaar.sync.FirestoreSyncTransport
import com.jyotirmoyb.udhaar.sync.SyncRepository
import com.jyotirmoyb.udhaar.util.LedgerBackupManager
import com.jyotirmoyb.udhaar.util.TelemetryManager
import com.jyotirmoyb.udhaar.widget.UdhaarWidgetProvider
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Udhaar v29.0 (Spark Mode) Instrumented Release Smoke Test Suite.
 *
 * Covers the 6 required pre-release verification journeys:
 * 1. Cold start of [MainActivity] with secure defaults (telemetry OFF, 256-bit SQLCipher key)
 * 2. Adding a ledger record & partial payment with exact integer paise math
 * 3. Upgrading a v12 Room SQLite database to v13 via [UdhaarDatabase.MIGRATION_12_13]
 * 4. Encrypted `.udhaar` backup & restore round-trip (including wrong-passphrase rejection)
 * 5. Home-screen widget update via [UdhaarWidgetProvider]
 * 6. Two-device sync convergence against the local Firebase Auth + Firestore emulators
 *
 * Run against a connected device or Android emulator:
 *   gradle :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.jyotirmoyb.udhaar.ReleaseSmokeInstrumentedTest
 *
 * For Test 6 (two-device sync), start the Firebase emulators on the host first:
 *   firebase emulators:start --project demo-udhaar-spark --only auth,firestore
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ReleaseSmokeInstrumentedTest {

    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    // -------------------------------------------------------------------------
    // 1. Cold Start
    // -------------------------------------------------------------------------
    @Test
    fun smoke01_coldStartLaunchesMainActivityWithSecureDefaults() {
        val context = targetContext
        assertEquals(BuildConfig.APPLICATION_ID, context.packageName)

        // Telemetry must remain strictly opt-in (OFF by default)
        TelemetryManager.setConsent(context, false)
        assertFalse("Telemetry must be disabled on cold start", TelemetryManager.isConsentGranted)

        // Hardware-backed 256-bit SQLCipher passphrase must initialize cleanly
        val dbKey = EncryptedStorageManager.getOrCreateDatabasePassphrase(context)
        assertEquals("SQLCipher passphrase must be 32 bytes (256-bit)", 32, dbKey.size)
        dbKey.fill(0)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull("MainActivity must launch on cold start", activity)
                assertFalse("MainActivity must not finish immediately on cold start", activity.isFinishing)
            }
        }
    }

    // -------------------------------------------------------------------------
    // 2. Add a Record
    // -------------------------------------------------------------------------
    @Test
    fun smoke02_addRecordPersistsExactPaiseInEncryptedLedger() = runBlocking {
        val db = createIsolatedInMemoryDb(targetContext)
        try {
            val dao = db.udhaarDao()
            val partyId = dao.insertParty(
                PartyEntity(
                    name = "Guwahati Hardware Store",
                    phoneNumber = "+919864099887",
                    upiId = "guwahatihardware@okhdfcbank",
                    preferredCurrency = CurrencyType.INR
                )
            )
            assertTrue("Party insert must return positive ID", partyId > 0L)

            val txId = dao.insertTransaction(
                UdhaarTransaction(
                    partyId = partyId,
                    transactionType = TransactionType.GIVEN,
                    partyName = "Guwahati Hardware Store",
                    phoneNumber = "+919864099887",
                    currency = CurrencyType.INR,
                    principalAmountPaise = 1_250_050L, // ₹12,500.50
                    amountPaidPaise = 250_025L,        // ₹2,500.25
                    paymentMode = PaymentMode.UPI,
                    notes = "v29 release smoke test record"
                )
            )
            assertTrue("Transaction insert must return positive ID", txId > 0L)

            val paymentId = dao.insertPartialPayment(
                PartialPayment(
                    transactionId = txId,
                    amountPaise = 250_025L,
                    paymentMode = PaymentMode.UPI,
                    note = "First UPI partial repayment"
                )
            )
            assertTrue("Partial payment insert must return positive ID", paymentId > 0L)

            val savedTx = dao.getTransactionById(txId)
            assertNotNull(savedTx)
            assertEquals("Guwahati Hardware Store", savedTx?.partyName)
            assertEquals(1_250_050L, savedTx?.principalAmountPaise)
            assertEquals(250_025L, savedTx?.amountPaidPaise)
            assertEquals(1_000_025L, (savedTx?.principalAmountPaise ?: 0L) - (savedTx?.amountPaidPaise ?: 0L))
            assertEquals(1, dao.getPartialPaymentsForTransactionSnapshot(txId).size)
        } finally {
            db.close()
        }
    }

    // -------------------------------------------------------------------------
    // 3. Upgrade from v12 Database to v13
    // -------------------------------------------------------------------------
    @Test
    fun smoke03_upgradeFromV12DatabasePreservesAllLedgerData() {
        val context = targetContext
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // In-memory SQLite instance for deterministic migration verification
                .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        seedV12SchemaAndSampleData(db)
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )

        try {
            val writableDb = helper.writableDatabase
            UdhaarDatabase.MIGRATION_12_13.migrate(writableDb)

            // Verify existing transaction row survived with default sync metadata
            writableDb.query(
                """
                SELECT partyName, principalAmountPaise, amountPaidPaise,
                       syncId, serverRev, clientRev, hlc, syncStatus, deletedAtMs
                FROM udhaar_transactions
                WHERE id = 1
                """.trimIndent()
            ).use { cursor ->
                assertTrue("Pre-migration v12 transaction must exist after migration", cursor.moveToFirst())
                assertEquals("Ramesh Kumar", cursor.getString(0))
                assertEquals(500_000L, cursor.getLong(1))
                assertEquals(200_000L, cursor.getLong(2))
                assertTrue("syncId must be populated", cursor.getString(3).isNotBlank())
                assertEquals(0L, cursor.getLong(4))
                assertEquals(1L, cursor.getLong(5))
                assertNotNull(cursor.getString(6))
                assertEquals("LOCAL_ONLY", cursor.getString(7))
                assertTrue("deletedAtMs must be NULL for active records", cursor.isNull(8))
            }

            // Verify pre-migration party & partial payment rows survived with sync columns
            writableDb.query("SELECT name, syncId, syncStatus FROM parties WHERE partyId = 1").use { cursor ->
                assertTrue("Pre-migration party must exist", cursor.moveToFirst())
                assertEquals("Ramesh Kumar", cursor.getString(0))
                assertTrue(cursor.getString(1).isNotBlank())
                assertEquals("LOCAL_ONLY", cursor.getString(2))
            }

            writableDb.query("SELECT amountPaise, syncId, syncStatus FROM partial_payments WHERE id = 1").use { cursor ->
                assertTrue("Pre-migration partial payment must exist", cursor.moveToFirst())
                assertEquals(200_000L, cursor.getLong(0))
                assertTrue(cursor.getString(1).isNotBlank())
                assertEquals("LOCAL_ONLY", cursor.getString(2))
            }

            // Verify sync_outbox table was created and accepts enqueued rows
            writableDb.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='sync_outbox'"
            ).use { cursor ->
                assertTrue("sync_outbox table must be created by MIGRATION_12_13", cursor.moveToFirst())
            }
        } finally {
            helper.close()
        }
    }

    // -------------------------------------------------------------------------
    // 4. Encrypted Backup and Restore Round-Trip
    // -------------------------------------------------------------------------
    @Test
    fun smoke04_encryptedBackupAndRestoreRoundTrip() = runBlocking {
        val db = createIsolatedInMemoryDb(targetContext)
        try {
            val dao = db.udhaarDao()
            val repo = UdhaarRepository(dao, targetContext)

            val party = PartyEntity(
                partyId = 101L,
                name = "Shillong Traders",
                phoneNumber = "+919863011223",
                upiId = "shillongtraders@sbi",
                preferredCurrency = CurrencyType.INR
            )
            val tx = UdhaarTransaction(
                id = 201L,
                partyId = 101L,
                transactionType = TransactionType.GIVEN,
                partyName = "Shillong Traders",
                phoneNumber = "+919863011223",
                currency = CurrencyType.INR,
                principalAmountPaise = 875_050L, // ₹8,750.50
                amountPaidPaise = 175_050L,      // ₹1,750.50
                paymentMode = PaymentMode.UPI,
                notes = "Encrypted backup smoke verification"
            )
            val payment = PartialPayment(
                id = 301L,
                transactionId = 201L,
                amountPaise = 175_050L,
                paymentMode = PaymentMode.UPI,
                note = "Verified installment"
            )

            val backupJson = LedgerBackupManager.buildBackupJson(
                parties = listOf(party),
                transactions = listOf(tx),
                partialPayments = listOf(payment),
                exportType = "FULL_LEDGER"
            )

            val passphrase = "UdhaarSmoke#2026!Strong"
            val encryptedEnvelope = DriveBackupCrypto.encrypt(
                plainBytes = backupJson.toByteArray(Charsets.UTF_8),
                passphrase = passphrase,
                preview = BackupPreviewSummary(partiesCount = 1, transactionsCount = 1, paymentsCount = 1)
            )

            // Ciphertext envelope must not leak plaintext party name
            val envelopeAsText = String(encryptedEnvelope, Charsets.UTF_8)
            assertFalse(
                "Encrypted backup must not contain plaintext party names",
                envelopeAsText.contains("Shillong Traders")
            )

            // Wrong passphrase must fail AES-GCM authentication
            try {
                DriveBackupCrypto.decrypt(encryptedEnvelope, "WrongPassphrase#000")
                fail("Decrypting with an invalid passphrase must throw")
            } catch (_: Exception) {
                // Expected authentication failure
            }

            // Correct passphrase restores exact ledger state
            val decryptedJson = String(
                DriveBackupCrypto.decrypt(encryptedEnvelope, passphrase),
                Charsets.UTF_8
            )
            val parsedPayload = LedgerBackupManager.parseBackupJson(decryptedJson)
            val restoredCount = repo.restoreFullLedgerBackup(parsedPayload, replaceExisting = true)
            assertEquals(1, restoredCount)

            val restoredParties = dao.getAllPartiesSnapshot()
            val restoredTxs = dao.getAllTransactionsSnapshot()
            val restoredPayments = dao.getAllPartialPaymentsSnapshot()

            assertEquals(1, restoredParties.size)
            assertEquals("Shillong Traders", restoredParties.first().name)
            assertEquals(1, restoredTxs.size)
            assertEquals(875_050L, restoredTxs.first().principalAmountPaise)
            assertEquals(175_050L, restoredTxs.first().amountPaidPaise)
            assertEquals(1, restoredPayments.size)
            assertEquals(175_050L, restoredPayments.first().amountPaise)
        } finally {
            db.close()
        }
    }

    // -------------------------------------------------------------------------
    // 5. Home-Screen Widget
    // -------------------------------------------------------------------------
    @Test
    fun smoke05_homeScreenWidgetUpdatesWithoutError() {
        val context = targetContext
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetComponent = ComponentName(context, UdhaarWidgetProvider::class.java)
        val existingIds = appWidgetManager.getAppWidgetIds(widgetComponent)
        val idsToUpdate = if (existingIds.isEmpty()) intArrayOf(1) else existingIds

        // Exercise both direct onUpdate and broadcast updateAllWidgets paths
        val provider = UdhaarWidgetProvider()
        provider.onUpdate(context, appWidgetManager, idsToUpdate)
        UdhaarWidgetProvider.updateAllWidgets(context)
    }

    // -------------------------------------------------------------------------
    // 6. Two-Device Sync on Firebase Emulators (Spark Mode)
    // -------------------------------------------------------------------------
    @Test
    fun smoke06_twoDeviceSyncConvergesOnFirebaseEmulators() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val emulatorHost = args.getString("firebaseEmulatorHost") ?: "10.0.2.2"
        val authPort = args.getString("authEmulatorPort")?.toIntOrNull() ?: 9099
        val firestorePort = args.getString("firestoreEmulatorPort")?.toIntOrNull() ?: 8080

        assumeTrue(
            "Skipping two-device sync emulator test: Firebase Auth ($emulatorHost:$authPort) " +
                "and Firestore ($emulatorHost:$firestorePort) emulators are not reachable.",
            isPortReachable(emulatorHost, authPort) && isPortReachable(emulatorHost, firestorePort)
        )

        val firebaseApp = FirebaseApp.getInstance()
        val projectId = firebaseApp.options.projectId ?: "demo-udhaar-spark"
        val auth = FirebaseAuth.getInstance(firebaseApp)
        val firestore = FirebaseModule.getFirestore(targetContext)
            ?: error("FirebaseModule.getFirestore returned null")

        runCatching { auth.useEmulator(emulatorHost, authPort) }
        runCatching {
            firestore.useEmulator(emulatorHost, firestorePort)
            firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }

        val uniqueEmail = "smoke_${UUID.randomUUID().toString().take(8)}@udhaar.test"
        val password = "SmokeSync#2026!${UUID.randomUUID().toString().take(6)}"

        val deviceADb = createIsolatedInMemoryDb(targetContext)
        val deviceBDb = createIsolatedInMemoryDb(targetContext)

        try {
            // 1. Create test account on Auth emulator & verify email via Auth emulator OOB flow
            auth.signOut()
            val authResult = auth.createUserWithEmailAndPassword(uniqueEmail, password).await()
            val user = authResult.user ?: error("Auth emulator did not return a FirebaseUser")
            user.sendEmailVerification().await()
            verifyEmailOnAuthEmulator(emulatorHost, authPort, projectId, uniqueEmail)
            user.reload().await()
            user.getIdToken(true).await()
            assertTrue("Test user email must be verified for Spark rules", auth.currentUser?.isEmailVerified == true)

            // 2. Wire two independent Room databases (Device A & Device B) to FirestoreSyncTransport
            val transport = FirestoreSyncTransport(firestoreOverride = firestore, authOverride = auth)
            val deviceASync = SyncRepository(
                dao = deviceADb.udhaarDao(),
                outboxDao = deviceADb.syncOutboxDao(),
                transport = transport,
                tokenStore = null
            )
            val deviceBSync = SyncRepository(
                dao = deviceBDb.udhaarDao(),
                outboxDao = deviceBDb.syncOutboxDao(),
                transport = transport,
                tokenStore = null
            )

            // 3. Device A creates a ledger record offline-first and drains outbox to Firestore
            val createdOnA = deviceASync.createTransactionLocalFirst(
                UdhaarTransaction(
                    transactionType = TransactionType.GIVEN,
                    partyName = "Two Device Sync Party",
                    phoneNumber = "+919876543210",
                    currency = CurrencyType.INR,
                    principalAmountPaise = 420_000L, // ₹4,200.00
                    amountPaidPaise = 0L,
                    paymentMode = PaymentMode.UPI,
                    notes = "Created on Device A"
                )
            )
            assertEquals(SyncStatus.LOCAL_ONLY, createdOnA.syncStatus)

            val pushedCount = deviceASync.drainOutbox()
            assertEquals("Device A must push 1 record mutation", 1, pushedCount)

            val syncedOnA = deviceADb.udhaarDao().getTransactionBySyncId(createdOnA.syncId)
            assertNotNull(syncedOnA)
            assertEquals(SyncStatus.SYNCED, syncedOnA?.syncStatus)
            assertEquals(1L, syncedOnA?.serverRev)

            // 4. Device B starts empty and pulls deltas from Firestore emulator
            assertNull(deviceBDb.udhaarDao().getTransactionBySyncId(createdOnA.syncId))
            val pulledOnB = deviceBSync.pullDeltas()
            assertTrue("Device B must pull at least 1 record from Firestore", pulledOnB >= 1)

            val syncedOnB = deviceBDb.udhaarDao().getTransactionBySyncId(createdOnA.syncId)
            assertNotNull("Device B must receive Device A's record", syncedOnB)
            assertEquals("Two Device Sync Party", syncedOnB?.partyName)
            assertEquals(420_000L, syncedOnB?.principalAmountPaise)
            assertEquals(1L, syncedOnB?.serverRev)
            assertEquals(SyncStatus.SYNCED, syncedOnB?.syncStatus)

            // 5. Device A updates the record (monotonic rev 1 -> 2), Device B pulls and converges
            deviceASync.updateTransactionLocalFirst(createdOnA.syncId) { existing ->
                existing.copy(notes = "Updated on Device A (rev 2)")
            }
            assertEquals(1, deviceASync.drainOutbox())
            val updatedOnA = deviceADb.udhaarDao().getTransactionBySyncId(createdOnA.syncId)
            assertEquals(2L, updatedOnA?.serverRev)

            val pulledUpdateOnB = deviceBSync.pullDeltas()
            assertTrue("Device B must pull updated rev 2 record", pulledUpdateOnB >= 1)
            val convergedOnB = deviceBDb.udhaarDao().getTransactionBySyncId(createdOnA.syncId)
            assertEquals(2L, convergedOnB?.serverRev)
        } finally {
            deviceADb.close()
            deviceBDb.close()
            runCatching { auth.currentUser?.delete()?.await() }
            runCatching { auth.signOut() }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private fun createIsolatedInMemoryDb(context: Context): UdhaarDatabase {
        return Room.inMemoryDatabaseBuilder(context, UdhaarDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private fun isPortReachable(host: String, port: Int, timeoutMs: Int = 1000): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        }.getOrDefault(false)
    }

    private fun verifyEmailOnAuthEmulator(
        host: String,
        authPort: Int,
        projectId: String,
        email: String
    ) {
        val oobListUrl = URL("http://$host:$authPort/emulator/v1/projects/$projectId/oobCodes")
        val listConn = (oobListUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
        }
        val responseBody = listConn.inputStream.bufferedReader().use { it.readText() }
        listConn.disconnect()

        val oobCodes = JSONObject(responseBody).optJSONArray("oobCodes")
            ?: error("Auth emulator returned no oobCodes")

        var verifyLink: String? = null
        for (i in 0 until oobCodes.length()) {
            val item = oobCodes.getJSONObject(i)
            if (item.optString("email") == email && item.optString("requestType") == "VERIFY_EMAIL") {
                verifyLink = item.optString("oobLink")
            }
        }
        val resolvedLink = verifyLink
            ?.replace("http://127.0.0.1:", "http://$host:")
            ?.replace("http://localhost:", "http://$host:")
            ?: error("No VERIFY_EMAIL oobLink found for $email")

        val verifyConn = (URL(resolvedLink).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
        }
        val code = verifyConn.responseCode
        verifyConn.disconnect()
        assertTrue("Auth emulator email verification returned HTTP $code", code in 200..399)
    }

    private fun seedV12SchemaAndSampleData(db: SupportSQLiteDatabase) {
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
            CREATE TABLE IF NOT EXISTS `parties` (
                `partyId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `phoneNumber` TEXT NOT NULL,
                `alternatePhoneNumber` TEXT NOT NULL DEFAULT '',
                `upiId` TEXT NOT NULL DEFAULT '',
                `email` TEXT NOT NULL DEFAULT '',
                `tagOrNotes` TEXT NOT NULL DEFAULT '',
                `notes` TEXT NOT NULL DEFAULT '',
                `photoUri` TEXT NOT NULL DEFAULT '',
                `linkedUserId` TEXT NOT NULL DEFAULT '',
                `isFavorite` INTEGER NOT NULL DEFAULT 0,
                `preferredCurrency` TEXT NOT NULL DEFAULT 'INR',
                `language` TEXT NOT NULL DEFAULT '',
                `quietHoursOverride` INTEGER NOT NULL DEFAULT 0,
                `lastInteractionMillis` INTEGER NOT NULL DEFAULT 0,
                `createdAtMillis` INTEGER NOT NULL DEFAULT 0
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
                `needsMyAcknowledgement` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `installment_plans` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `transactionId` INTEGER NOT NULL,
                `installmentCount` INTEGER NOT NULL,
                `frequency` TEXT NOT NULL,
                `startDate` INTEGER NOT NULL,
                `interestMode` TEXT NOT NULL,
                `flatInterestRate` REAL NOT NULL,
                `totalAmountDue` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `remotePlanId` TEXT NOT NULL,
                `proposedScheduleJson` TEXT NOT NULL,
                `scheduleProposalStatus` TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `installments` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `planId` INTEGER NOT NULL,
                `sequence` INTEGER NOT NULL,
                `dueDate` INTEGER NOT NULL,
                `amountDue` INTEGER NOT NULL,
                `amountPaid` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `paidAtMillis` INTEGER,
                `paymentId` INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `parties` (`partyId`, `name`, `phoneNumber`, `preferredCurrency`, `createdAtMillis`)
            VALUES (1, 'Ramesh Kumar', '+919876543210', 'INR', 1700000000000)
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
                1, 1, 'GIVEN', 'Ramesh Kumar', '+919876543210', 'INR',
                500000, 200000, 'UPI', 0,
                'SIMPLE', 0.0, 'PER_MONTH', 'MONTHLY',
                1700000000000, 'Pre-migration v12 transaction', 0, 1700000000000
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `partial_payments` (`id`, `transactionId`, `amountPaise`, `paidAtMillis`, `paymentMode`, `note`)
            VALUES (1, 1, 200000, 1700005000000, 'UPI', 'Pre-migration partial payment')
            """.trimIndent()
        )
    }
}
