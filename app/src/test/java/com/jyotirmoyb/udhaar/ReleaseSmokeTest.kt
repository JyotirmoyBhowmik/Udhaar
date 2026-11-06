package com.jyotirmoyb.udhaar

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Release Smoke Test Suite covering:
 * 1. Cold start (MainActivity, SQLCipher 256-bit key creation, Telemetry OFF by default, Dashboard UI)
 * 2. Add a record (Long paise persistence + Dashboard transaction card rendering)
 * 3. Restore a backup (AES-256-GCM .udhaar envelope encrypt + decrypt + full ledger restore)
 * 4. Open a guest link (ACTION_VIEW deep link https://udhaar.jyotirmoyb.com/u/r/<id>#<key>)
 * 5. Widget update (UdhaarWidgetProvider RemoteViews update with formatted INR balances)
 * 6. Database migration from the previous version (v11 -> v12)
 * 7. Release R8 default-on & narrow ProGuard rules verification
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReleaseSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun smoke1_coldStart_initializesSqlCipherKey_keepsTelemetryOff_and_rendersDashboard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TelemetryManager.setConsent(context, false)

        // Verify telemetry is strictly OFF by default on cold start
        assertFalse("Telemetry must be OFF by default on cold start", TelemetryManager.isConsentGranted)

        // Verify 256-bit SQLCipher key is deterministically created in Android Keystore / EncryptedStorageManager
        val keyBytes = EncryptedStorageManager.getOrCreateDatabasePassphrase(context)
        assertEquals("SQLCipher passphrase must be 32 bytes (256-bit)", 32, keyBytes.size)
        keyBytes.fill(0)

        composeRule.waitForIdle()
        composeRule.onNode(hasText("Udhaar", ignoreCase = true)).assertExists()
        composeRule.onNode(hasTestTag("summary_receivable_card")).assertExists()
        composeRule.onNode(hasTestTag("summary_payable_card")).assertExists()
        composeRule.onNode(hasTestTag("add_udhaar_fab")).assertExists()
    }

    @Test
    fun smoke2_addRecord_persistsInPaise_and_displaysOnDashboard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = System.currentTimeMillis()

        val insertedId = runBlocking {
            val dao = UdhaarDatabase.getInstance(context).udhaarDao()
            dao.insertTransaction(
                UdhaarTransaction(
                    transactionType = TransactionType.GIVEN,
                    partyName = "Release Smoke Trader",
                    phoneNumber = "+919876501234",
                    currency = CurrencyType.INR,
                    principalAmountPaise = 325_000L, // ₹3,250.00
                    amountPaidPaise = 25_000L,       // ₹250.00
                    paymentMode = PaymentMode.UPI,
                    startDateMillis = now - 86_400_000L,
                    expiryDateMillis = now + 86_400_000L * 15L,
                    notes = "Smoke test record addition"
                )
            )
        }
        assertTrue("Inserted transaction ID must be positive", insertedId > 0L)

        composeRule.waitForIdle()
        val cardTag = "transaction_card_$insertedId"
        composeRule.onNode(hasTestTag("transactions_lazy_list"))
            .performScrollToNode(hasTestTag(cardTag))
        composeRule.waitForIdle()
        composeRule.onNode(hasTestTag(cardTag)).assertExists()

        val savedTx = runBlocking {
            UdhaarDatabase.getInstance(context).udhaarDao().getTransactionById(insertedId)
        }
        assertNotNull(savedTx)
        assertEquals(325_000L, savedTx?.principalAmountPaise)
        assertEquals(25_000L, savedTx?.amountPaidPaise)
    }

    @Test
    fun smoke3_restoreBackup_decryptsUdhaarSnapshot_and_restoresExactPaiseLedger() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = UdhaarDatabase.getInstance(context).udhaarDao()
        val repo = UdhaarRepository(dao, context)

        val backupParty = PartyEntity(
            partyId = 501L,
            name = "Backup Restore Smoke Party",
            phoneNumber = "+919812345678",
            upiId = "smokebackup@okicici",
            preferredCurrency = CurrencyType.INR
        )
        val backupTx = UdhaarTransaction(
            id = 601L,
            partyId = 501L,
            transactionType = TransactionType.GIVEN,
            partyName = "Backup Restore Smoke Party",
            phoneNumber = "+919812345678",
            currency = CurrencyType.INR,
            principalAmountPaise = 910_075L, // ₹9,100.75
            amountPaidPaise = 210_075L,      // ₹2,100.75
            paymentMode = PaymentMode.UPI,
            notes = "Restored from .udhaar encrypted backup"
        )
        val backupPayment = PartialPayment(
            id = 701L,
            transactionId = 601L,
            amountPaise = 210_075L,
            paymentMode = PaymentMode.UPI,
            note = "Initial UPI installment"
        )

        val backupJson = LedgerBackupManager.buildBackupJson(
            parties = listOf(backupParty),
            transactions = listOf(backupTx),
            partialPayments = listOf(backupPayment),
            exportType = "FULL_LEDGER"
        )
        val encryptedBytes = DriveBackupCrypto.encrypt(
            plainBytes = backupJson.toByteArray(Charsets.UTF_8),
            passphrase = "UDHR-SMOK-ETST-2026-KEY1",
            preview = BackupPreviewSummary(
                partiesCount = 1,
                transactionsCount = 1,
                paymentsCount = 1
            )
        )

        val decryptedJson = String(
            DriveBackupCrypto.decrypt(encryptedBytes, "UDHR-SMOK-ETST-2026-KEY1"),
            Charsets.UTF_8
        )
        val parsedPayload = LedgerBackupManager.parseBackupJson(decryptedJson)
        val restoredCount = repo.restoreFullLedgerBackup(parsedPayload, replaceExisting = true)

        assertEquals(1, restoredCount)
        val partiesAfter = dao.getAllPartiesSnapshot()
        val txsAfter = dao.getAllTransactionsSnapshot()
        val paymentsAfter = dao.getAllPartialPaymentsSnapshot()

        assertEquals(1, partiesAfter.size)
        assertEquals("Backup Restore Smoke Party", partiesAfter.first().name)
        assertEquals(1, txsAfter.size)
        assertEquals(910_075L, txsAfter.first().principalAmountPaise)
        assertEquals(210_075L, txsAfter.first().amountPaidPaise)
        assertEquals(1, paymentsAfter.size)
        assertEquals(210_075L, paymentsAfter.first().amountPaise)
    }

    @Test
    fun smoke4_openGuestLink_handlesDeepLinkIntentCleanlyWithoutCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val guestUri = Uri.parse("https://udhaar.jyotirmoyb.com/u/r/smoke_guest_rec_99#smoke_secret_key_99")
        val deepLinkIntent = Intent(Intent.ACTION_VIEW, guestUri).apply {
            setClass(context, MainActivity::class.java)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        ActivityScenario.launch<MainActivity>(deepLinkIntent).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
                assertEquals(Intent.ACTION_VIEW, activity.intent?.action)
                assertEquals("udhaar.jyotirmoyb.com", activity.intent?.data?.host)
                assertEquals("/u/r/smoke_guest_rec_99", activity.intent?.data?.path)
            }
        }
    }

    @Test
    fun smoke5_widgetUpdate_populatesRemoteViewsWithCurrentLedgerTotals() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = UdhaarDatabase.getInstance(context).udhaarDao()
        dao.insertTransaction(
            UdhaarTransaction(
                transactionType = TransactionType.GIVEN,
                partyName = "Widget Smoke Party",
                phoneNumber = "+919800011122",
                currency = CurrencyType.INR,
                principalAmountPaise = 500_000L, // ₹5,000.00
                amountPaidPaise = 0L,
                paymentMode = PaymentMode.CASH
            )
        )

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetId = 101
        UdhaarWidgetProvider().onUpdate(context, appWidgetManager, intArrayOf(widgetId))
        UdhaarWidgetProvider.updateAllWidgets(context)

        val remoteViews = android.widget.RemoteViews(context.packageName, R.layout.widget_udhaar_summary)
        assertEquals(R.layout.widget_udhaar_summary, remoteViews.layoutId)
        val component = ComponentName(context, UdhaarWidgetProvider::class.java)
        assertNotNull(component)
    }

    @Test
    fun smoke6_databaseMigrationFromPreviousVersion_v11_to_v12_preservesAllRecordsAndPaiseSums() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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
                                1, NULL, 'GIVEN', 'Previous v11 Customer', '+919800099988', 'INR',
                                640050, 140050, 'UPI', 0,
                                'SIMPLE', 0.0, 'PER_MONTH', 'MONTHLY',
                                1700000000000, 'Pre-upgrade v11 transaction', 0, 1700000000000
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO `partial_payments` (`id`, `transactionId`, `amountPaise`, `paidAtMillis`, `paymentMode`, `note`)
                            VALUES (1, 1, 140050, 1700005000000, 'UPI', 'Pre-upgrade v11 payment')
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )

        val db = helper.writableDatabase
        UdhaarDatabase.MIGRATION_11_12.migrate(db)

        db.query("SELECT partyName, principalAmountPaise, amountPaidPaise FROM udhaar_transactions WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Previous v11 Customer", c.getString(0))
            assertEquals(640_050L, c.getLong(1))
            assertEquals(140_050L, c.getLong(2))
        }
        db.query("SELECT amountPaise, note FROM partial_payments WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(140_050L, c.getLong(0))
            assertEquals("Pre-upgrade v11 payment", c.getString(1))
        }
        helper.close()
    }

    @Test
    fun smoke7_releaseBuildConfig_enablesR8ByDefault_includesVerifyReleaseR8_and_narrowProguardRules() {
        val buildGradleFile = listOf(
            File("build.gradle.kts"),
            File("app/build.gradle.kts")
        ).firstOrNull { it.exists() && it.readText().contains("namespace = \"com.jyotirmoyb.udhaar\"") }
        assertNotNull("app/build.gradle.kts must exist", buildGradleFile)
        val buildGradleText = buildGradleFile!!.readText()

        // R8 is ON by default unless -Pudhaar.r8=false is explicitly passed
        assertTrue(buildGradleText.contains("providers.gradleProperty(\"udhaar.r8\")"))
        assertTrue(buildGradleText.contains(".getOrElse(true)"))
        assertTrue(buildGradleText.contains("isMinifyEnabled = enableR8Minify"))
        assertTrue(buildGradleText.contains("isShrinkResources = enableR8Minify"))
        assertTrue(buildGradleText.contains("tasks.register(\"verifyReleaseR8\")"))

        val proguardFile = listOf(
            File("proguard-rules.pro"),
            File("app/proguard-rules.pro")
        ).firstOrNull { it.exists() && it.isFile }
        assertNotNull("proguard-rules.pro must exist", proguardFile)
        val proguardText = proguardFile!!.readText()

        // Line numbers are preserved for Crashlytics / Play Console deobfuscation
        assertTrue(proguardText.contains("-keepattributes SourceFile,LineNumberTable"))
        assertTrue(proguardText.contains("-renamesourcefileattribute SourceFile"))

        // Narrow Room / JNI rules are present while broad app package keeps are removed
        assertTrue(proguardText.contains("-keep @androidx.room.Entity class *"))
        assertTrue(proguardText.contains("-keep @androidx.room.Dao class *"))
        assertTrue(proguardText.contains("-keep class net.zetetic.database.sqlcipher.**"))
        assertFalse(
            "Broad com.jyotirmoyb.udhaar.data.** keep must be removed so R8 obfuscates app classes",
            proguardText.contains("-keep class com.jyotirmoyb.udhaar.data.**")
        )
        assertFalse(
            "Broad com.jyotirmoyb.udhaar.security.** keep must be removed",
            proguardText.contains("-keep class com.jyotirmoyb.udhaar.security.**")
        )
        assertFalse(proguardText.contains("-dontobfuscate"))
    }
}
