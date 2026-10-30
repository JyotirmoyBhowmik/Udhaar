package com.jyotirmoyb.udhaar

import android.content.Context
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.LedgerBackupManager
import com.jyotirmoyb.udhaar.util.ThermalReceiptGenerator
import com.jyotirmoyb.udhaar.util.UpiQrGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string and package from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Udhaar", appName)
        assertEquals("com.jyotirmoyb.udhaar", BuildConfig.APPLICATION_ID)
        assertEquals("8.0", BuildConfig.VERSION_NAME)
        assertEquals(8, BuildConfig.VERSION_CODE)
    }

    @get:org.junit.Rule
    val composeRule = androidx.compose.ui.test.junit4.createAndroidComposeRule<MainActivity>()

    @Test
    fun `mainActivity launches and renders dashboard UI without crashing`() {
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasText("UDHAAR")).assertExists()
    }

    @Test
    fun `upi uri and qr bitmap generate offline`() {
        val uri = UpiQrGenerator.buildUpiUri(
            upiId = "merchant@okaxis",
            payeeName = "Udhaar Store",
            amountInr = 1250.50
        )
        assertTrue(uri.startsWith("upi://pay?"))
        assertTrue(uri.contains("pa=merchant%40okaxis"))
        assertTrue(uri.contains("am=1250.50"))
        assertTrue(uri.contains("cu=INR"))

        val bitmap = UpiQrGenerator.generateQrBitmap(uri, 256)
        assertNotNull(bitmap)
    }

    @Test
    fun `thermal receipt bitmap renders offline for single transaction`() {
        val tx = UdhaarTransaction(
            id = 42L,
            transactionType = TransactionType.GIVEN,
            partyName = "Vikram Traders",
            phoneNumber = "+91 9876543210",
            currency = CurrencyType.INR,
            principalAmount = 10_000.0,
            amountPaid = 2_500.0,
            paymentMode = PaymentMode.UPI
        )
        val summary = InterestCalculator.calculate(tx)
        val payments = listOf(
            PartialPayment(
                id = 1L,
                transactionId = 42L,
                amount = 2_500.0,
                paymentMode = PaymentMode.UPI,
                note = "25% Partial Installment"
            )
        )
        val bitmap = ThermalReceiptGenerator.createTransactionReceiptBitmap(
            summary = summary,
            partialPayments = payments,
            userSettings = UserSettings(upiId = "jyotirmoy@upi", payeeName = "Jyotirmoy Bhowmik")
        )
        assertNotNull(bitmap)
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
    }

    @Test
    fun `ledger backup json roundtrip preserves parties transactions and payments with paymentMode`() {
        val party = PartyEntity(
            partyId = 10L,
            name = "Ramesh Suppliers",
            phoneNumber = "+91 9876543210",
            upiId = "ramesh@okicici",
            email = "ramesh@example.com",
            tagOrNotes = "Supplier",
            preferredCurrency = CurrencyType.INR
        )
        val tx = UdhaarTransaction(
            id = 20L,
            partyId = 10L,
            transactionType = TransactionType.TAKEN,
            partyName = "Ramesh Suppliers",
            phoneNumber = "+91 9876543210",
            currency = CurrencyType.INR,
            principalAmount = 15_000.0,
            amountPaid = 5_000.0
        )
        val payment = PartialPayment(
            id = 30L,
            transactionId = 20L,
            amount = 5_000.0,
            paymentMode = PaymentMode.UPI,
            note = "First installment"
        )

        val json = LedgerBackupManager.buildBackupJson(
            parties = listOf(party),
            transactions = listOf(tx),
            partialPayments = listOf(payment),
            exportType = "FULL_LEDGER"
        )

        val parsed = LedgerBackupManager.parseBackupJson(json)
        assertEquals("FULL_LEDGER", parsed.exportType)
        assertEquals(1, parsed.parties.size)
        assertEquals("ramesh@okicici", parsed.parties.first().upiId)
        assertEquals(1, parsed.transactions.size)
        assertEquals(15_000.0, parsed.transactions.first().principalAmount, 0.01)
        assertEquals(1, parsed.partialPayments.size)
        assertEquals("First installment", parsed.partialPayments.first().note)
        assertEquals(PaymentMode.UPI, parsed.partialPayments.first().paymentMode)
    }

    @Test
    fun `pinSecurityManager stores salted PBKDF2 hash without 0000 default and enforces 5-attempt growing delay`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pinManager = com.jyotirmoyb.udhaar.security.PinSecurityManager(context)
        pinManager.clearPin()

        // No default PIN ("0000" is not pre-configured)
        org.junit.Assert.assertFalse(pinManager.hasPinConfigured())
        org.junit.Assert.assertFalse(pinManager.verifyPin("0000").isSuccess)

        // Setting a valid 4..8 digit PIN
        assertTrue(pinManager.setPin("4826"))
        assertTrue(pinManager.hasPinConfigured())

        // 4 wrong attempts do not lock out yet
        val baseTime = 1_700_000_000_000L
        for (i in 1..4) {
            val res = pinManager.verifyPin("1111", nowMillis = baseTime)
            org.junit.Assert.assertFalse(res.isSuccess)
            assertEquals(i, res.failedAttempts)
            assertEquals(0L, res.lockoutRemainingMillis)
        }

        // 5th wrong attempt triggers 30s lockout
        val fifth = pinManager.verifyPin("1111", nowMillis = baseTime)
        org.junit.Assert.assertFalse(fifth.isSuccess)
        assertEquals(5, fifth.failedAttempts)
        assertEquals(30_000L, fifth.lockoutRemainingMillis)

        // Even with the right PIN during lockout window, access is blocked
        val duringLockout = pinManager.verifyPin("4826", nowMillis = baseTime + 10_000L)
        org.junit.Assert.assertFalse(duringLockout.isSuccess)
        assertEquals(20_000L, duringLockout.lockoutRemainingMillis)

        // 6th wrong attempt after 30s triggers growing 60s lockout
        val sixth = pinManager.verifyPin("1111", nowMillis = baseTime + 31_000L)
        org.junit.Assert.assertFalse(sixth.isSuccess)
        assertEquals(6, sixth.failedAttempts)
        assertEquals(60_000L, sixth.lockoutRemainingMillis)

        // Correct PIN after 60s lockout succeeds and resets counter
        val success = pinManager.verifyPin("4826", nowMillis = baseTime + 31_000L + 61_000L)
        assertTrue(success.isSuccess)
        assertEquals(0, success.failedAttempts)
        assertEquals(0L, success.lockoutRemainingMillis)
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h640dp-normal-long-port-hdpi")
    fun `small phone 360dp - header groups Lock Settings More and filter chips scroll to Settled with count badges`() {
        composeRule.waitForIdle()

        // Top bar keeps Lock, Settings, and More visible
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("lock_app_top_button")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("open_settings_button")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("more_options_top_button")).assertExists()

        // Opening More menu reveals QR, Theme, and Share
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("more_options_top_button"))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("global_upi_qr_button")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("cycle_theme_top_button")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("share_app_top_button")).assertExists()

        // Filter chips LazyRow scrolls to Settled chip and displays count badge
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("filter_chips_lazy_row"))
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("filter_chip_settled"))
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("filter_chip_settled")).assertExists()
        composeRule.onNode(
            androidx.compose.ui.test.hasTestTag("filter_badge_settled"),
            useUnmergedTree = true
        ).assertExists()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h891dp-normal-long-port-xxhdpi")
    fun `large phone - full screen Settings has sticky Save button and 5 scrollable sections`() {
        composeRule.waitForIdle()

        composeRule.onNode(androidx.compose.ui.test.hasTestTag("open_settings_button"))
            .performClick()
        composeRule.waitForIdle()

        // Sticky top bar and sticky Save button are always present
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("close_settings_button")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("save_settings_button")).assertExists()

        // All 5 sections are reachable in the LazyColumn
        val settingsList = composeRule.onNode(androidx.compose.ui.test.hasTestTag("settings_lazy_column"))
        listOf(
            "section_profile_and_upi",
            "section_security",
            "section_currency_and_theme",
            "section_backup_and_sync",
            "section_about"
        ).forEach { sectionTag ->
            settingsList.performScrollToNode(androidx.compose.ui.test.hasTestTag(sectionTag))
            composeRule.waitForIdle()
            composeRule.onNode(androidx.compose.ui.test.hasTestTag(sectionTag)).assertExists()
        }

        // Last button inside About section is reachable
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("contact_admin_button")).assertExists()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w360dp-h640dp-normal-long-port-hdpi", fontScale = 1.3f)
    fun `font scale 1_3 on 360dp phone - modals and Settings keep last buttons reachable`() {
        composeRule.waitForIdle()

        // Open More -> Share App ModalBottomSheet and verify last button (copy_app_link_button) is reachable
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("more_options_top_button"))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("share_app_top_button"))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("copy_app_link_button"))
            .performScrollTo()
            .assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("close_share_app_modal_button"))
            .performClick()
        composeRule.waitForIdle()

        // Open Settings at 1.3x font scale and scroll to the very last section
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("open_settings_button"))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("settings_lazy_column"))
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("section_about"))
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("contact_admin_button")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("save_settings_button")).assertExists()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w640dp-h360dp-normal-long-land-hdpi")
    fun `landscape 640x360 - Settings and UPI QR ModalBottomSheet scroll to last button`() {
        composeRule.waitForIdle()

        // Open More -> UPI QR ModalBottomSheet in landscape and verify QR input + close button work
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("more_options_top_button"))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("global_upi_qr_button"))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("qr_upi_id_input")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("close_qr_modal_button"))
            .performClick()
        composeRule.waitForIdle()

        // Open Settings in landscape and scroll to About section while sticky Save button stays accessible
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("open_settings_button"))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("settings_lazy_column"))
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("section_about"))
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("contact_admin_button")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("save_settings_button")).assertExists()
    }

    @Test
    fun `phase 4 in-app communication - chat messages extension request updates due date and audit trail and moderation works`() =
        kotlinx.coroutines.test.runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = androidx.room.Room.inMemoryDatabaseBuilder(
                context,
                com.jyotirmoyb.udhaar.data.UdhaarDatabase::class.java
            ).allowMainThreadQueries().build()
            val dao = db.udhaarDao()
            val repo = com.jyotirmoyb.udhaar.data.UdhaarRepository(dao, context)

            val initialDue = 1_750_000_000_000L
            val proposedDue = initialDue + 15L * 86_400_000L
            val txId = dao.insertTransaction(
                UdhaarTransaction(
                    transactionType = TransactionType.GIVEN,
                    partyName = "Suresh Hardware",
                    phoneNumber = "+91 9876500001",
                    currency = CurrencyType.INR,
                    principalAmount = 12_000.0,
                    startDateMillis = 1_740_000_000_000L,
                    expiryDateMillis = initialDue
                )
            )
            val savedTx = dao.getTransactionById(txId)!!
            val threadKey = repo.buildTransactionThreadKey(savedTx)

            // 1. Send quick reply & payment proof in transaction thread
            repo.sendChatMessage(
                threadKey = threadKey,
                scope = com.jyotirmoyb.udhaar.data.ChatThreadScope.TRANSACTION,
                transactionId = txId,
                partyId = savedTx.partyId,
                partyName = savedTx.partyName,
                messageType = com.jyotirmoyb.udhaar.data.ChatMessageType.QUICK_REPLY,
                textContent = "I will pay on Friday"
            )
            repo.sendChatMessage(
                threadKey = threadKey,
                scope = com.jyotirmoyb.udhaar.data.ChatThreadScope.TRANSACTION,
                transactionId = txId,
                partyId = savedTx.partyId,
                partyName = savedTx.partyName,
                messageType = com.jyotirmoyb.udhaar.data.ChatMessageType.PAYMENT_PROOF,
                textContent = "Attached UPI receipt",
                proofAttachmentUri = "content://com.jyotirmoyb.udhaar.fileprovider/proof.pdf",
                proofMimeType = "application/pdf",
                proofFileName = "proof.pdf"
            )

            // 2. One-tap Request Extension -> Accept updates expiryDateMillis, contentHash, and Audit Trail
            val extMsg = repo.requestDueDateExtension(
                transaction = savedTx,
                proposedDueDateMillis = proposedDue,
                reasonText = "Need 15 more days for stock clearance"
            )
            assertEquals(com.jyotirmoyb.udhaar.data.ChatMessageType.EXTENSION_REQUEST, extMsg.messageType)
            assertEquals(com.jyotirmoyb.udhaar.data.ExtensionRequestStatus.PENDING, extMsg.extensionStatus)

            repo.respondToExtensionRequest(message = extMsg, accept = true)

            val updatedTx = dao.getTransactionById(txId)!!
            assertEquals(proposedDue, updatedTx.expiryDateMillis)
            org.junit.Assert.assertNotEquals(savedTx.contentHash, updatedTx.contentHash)

            val audits = dao.getAuditTrailForTransactionSnapshot(txId)
            assertTrue(audits.any { it.eventType == com.jyotirmoyb.udhaar.data.AuditEventType.EXTENSION_REQUESTED })
            assertTrue(audits.any { it.eventType == com.jyotirmoyb.udhaar.data.AuditEventType.EXTENSION_ACCEPTED })

            // 3. Mute, Block & Report abusive user controls on the thread
            repo.toggleThreadMute(threadKey, true)
            assertTrue(dao.getChatThreadByKey(threadKey)!!.isMuted)

            repo.reportAbusiveUserInThread(threadKey, "Abusive language in payment chat")
            val moderatedThread = dao.getChatThreadByKey(threadKey)!!
            assertTrue(moderatedThread.isReported)
            assertTrue(moderatedThread.isBlocked)
            assertEquals("Abusive language in payment chat", moderatedThread.reportReason)

            db.close()
        }

    @Test
    fun `driveBackupCrypto encrypt and decrypt roundtrip with AES-256-GCM and preview`() {
        val originalPayload = """{"appName":"Udhaar","transactionsCount":5,"secret":"vault_data_12345"}"""
        val passphrase = "MasterPassphrase@2026"
        val preview = com.jyotirmoyb.udhaar.backup.BackupPreviewSummary(
            partiesCount = 3,
            transactionsCount = 5,
            paymentsCount = 8,
            attachmentsCount = 2,
            appVersion = "7.0",
            createdAtMillis = System.currentTimeMillis()
        )

        val encryptedBytes = com.jyotirmoyb.udhaar.backup.DriveBackupCrypto.encrypt(
            plainBytes = originalPayload.toByteArray(Charsets.UTF_8),
            passphrase = passphrase,
            preview = preview
        )

        assertTrue(encryptedBytes.isNotEmpty())

        // Verify unencrypted preview peek
        val peekedPreview = com.jyotirmoyb.udhaar.backup.DriveBackupCrypto.peekPreview(encryptedBytes)
        assertNotNull(peekedPreview)
        assertEquals(3, peekedPreview?.partiesCount)
        assertEquals(5, peekedPreview?.transactionsCount)
        assertEquals(8, peekedPreview?.paymentsCount)
        assertEquals(2, peekedPreview?.attachmentsCount)

        // Decrypt with correct passphrase
        val decryptedBytes = com.jyotirmoyb.udhaar.backup.DriveBackupCrypto.decrypt(encryptedBytes, passphrase)
        val decryptedString = String(decryptedBytes, Charsets.UTF_8)
        assertEquals(originalPayload, decryptedString)
    }

    @Test(expected = com.jyotirmoyb.udhaar.backup.InvalidPassphraseException::class)
    fun `driveBackupCrypto decrypt with wrong passphrase throws InvalidPassphraseException`() {
        val originalPayload = "Confidential ledger data"
        val correctPass = "CorrectSecret123"
        val wrongPass = "IncorrectSecret999"
        val preview = com.jyotirmoyb.udhaar.backup.BackupPreviewSummary(
            partiesCount = 1,
            transactionsCount = 1,
            paymentsCount = 0,
            attachmentsCount = 0,
            appVersion = "7.0",
            createdAtMillis = System.currentTimeMillis()
        )

        val encryptedBytes = com.jyotirmoyb.udhaar.backup.DriveBackupCrypto.encrypt(
            plainBytes = originalPayload.toByteArray(Charsets.UTF_8),
            passphrase = correctPass,
            preview = preview
        )

        com.jyotirmoyb.udhaar.backup.DriveBackupCrypto.decrypt(encryptedBytes, wrongPass)
    }

    @Test
    fun `ledgerBackupManager schema version 5 supports chat, settings, and attachments with backwards compatibility`() {
        val party = PartyEntity(partyId = 1L, name = "Kishore")
        val tx = UdhaarTransaction(
            id = 100L,
            partyName = "Kishore",
            principalAmount = 25_000.0,
            currency = CurrencyType.INR
        )
        val payment = PartialPayment(
            id = 50L,
            transactionId = 100L,
            amount = 5_000.0
        )
        val thread = com.jyotirmoyb.udhaar.data.ChatThreadMetadata(
            threadKey = "tx_100",
            threadScope = com.jyotirmoyb.udhaar.data.ChatThreadScope.TRANSACTION,
            transactionId = 100L,
            partyName = "Kishore"
        )
        val chatMsg = com.jyotirmoyb.udhaar.data.ChatMessageEntity(
            id = 1L,
            threadKey = "tx_100",
            bodyText = "Will pay balance on Monday",
            isFromMe = true
        )
        val settingsMap = mapOf(
            "upiId" to "kishore@upi",
            "defaultCurrency" to "INR",
            "inrEnabled" to true
        )
        val attachments = mapOf("proof_100.png" to "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==")

        val jsonString = LedgerBackupManager.buildBackupJson(
            parties = listOf(party),
            transactions = listOf(tx),
            partialPayments = listOf(payment),
            chatThreads = listOf(thread),
            chatMessages = listOf(chatMsg),
            settings = settingsMap,
            attachments = attachments
        )

        val parsed = LedgerBackupManager.parseBackupJson(jsonString)
        assertEquals(7, parsed.schemaVersion)
        assertEquals(1, parsed.parties.size)
        assertEquals(1, parsed.transactions.size)
        assertEquals(1, parsed.partialPayments.size)
        assertEquals(1, parsed.chatThreads.size)
        assertEquals(1, parsed.chatMessages.size)
        assertEquals("Will pay balance on Monday", parsed.chatMessages[0].bodyText)
        assertEquals("kishore@upi", parsed.settings["upiId"])
        assertEquals(1, parsed.attachments.size)

        // Test backwards compatibility with legacy v1 format
        val legacyV1Json = """
            {
                "schemaVersion": 1,
                "parties": [{"id": 1, "name": "Old Friend"}],
                "transactions": [{"id": 1, "partyName": "Old Friend", "principalAmount": 1000.0, "currency": "INR", "transactionType": "GIVEN"}]
            }
        """.trimIndent()
        val parsedLegacy = LedgerBackupManager.parseBackupJson(legacyV1Json)
        assertEquals(1, parsedLegacy.schemaVersion)
        assertEquals(1, parsedLegacy.parties.size)
        assertEquals(1, parsedLegacy.transactions.size)
    }

    @Test
    fun `restoreFullLedgerBackup wipes old data on replaceExisting true and preserves on merge`() =
        kotlinx.coroutines.test.runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = androidx.room.Room.inMemoryDatabaseBuilder(context, com.jyotirmoyb.udhaar.data.UdhaarDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            val dao = db.udhaarDao()
            val repo = com.jyotirmoyb.udhaar.data.UdhaarRepository(dao, context)

            // Seed initial local data
            dao.insertParty(PartyEntity(partyId = 1L, name = "Local Party"))
            dao.insertTransaction(UdhaarTransaction(id = 1L, partyName = "Local Party", principalAmount = 500.0))

            val backupToRestore = com.jyotirmoyb.udhaar.util.ParsedLedgerBackup(
                exportType = "FULL_LEDGER",
                schemaVersion = 5,
                parties = listOf(PartyEntity(partyId = 99L, name = "Cloud Restored Party")),
                transactions = listOf(UdhaarTransaction(id = 99L, partyName = "Cloud Restored Party", principalAmount = 5000.0)),
                partialPayments = emptyList(),
                chatThreads = emptyList(),
                chatMessages = emptyList(),
                settings = mapOf("upiId" to "restored@upi"),
                attachments = emptyMap()
            )

            // Test Replace: should wipe local data
            val restoredCount = repo.restoreFullLedgerBackup(backupToRestore, replaceExisting = true)
            assertEquals(1, restoredCount)

            val partiesAfterReplace = dao.getAllPartiesSnapshot()
            assertEquals(1, partiesAfterReplace.size)
            assertEquals("Cloud Restored Party", partiesAfterReplace[0].name)

            db.close()
        }

    @Test
    fun `room migrations 1 to 7 apply cleanly without schema violation`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = context.getDatabasePath("migration_test.db")
        dbFile.delete()

        val helperFactory = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration_test.db")
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `udhaar_transactions` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `transactionType` TEXT NOT NULL,
                            `partyName` TEXT NOT NULL,
                            `phoneNumber` TEXT NOT NULL,
                            `currency` TEXT NOT NULL,
                            `principalAmount` REAL NOT NULL,
                            `amountPaid` REAL NOT NULL,
                            `paymentMode` TEXT NOT NULL,
                            `isInterestEnabled` INTEGER NOT NULL,
                            `interestType` TEXT NOT NULL,
                            `interestRate` REAL NOT NULL,
                            `interestRatePeriod` TEXT NOT NULL,
                            `compoundFrequency` TEXT NOT NULL,
                            `startDateMillis` INTEGER NOT NULL,
                            `expiryDateMillis` INTEGER,
                            `gracePeriodDays` INTEGER NOT NULL,
                            `notes` TEXT NOT NULL,
                            `isSettled` INTEGER NOT NULL,
                            `createdAtMillis` INTEGER NOT NULL,
                            `updatedAtMillis` INTEGER NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `partial_payments` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `transactionId` INTEGER NOT NULL,
                            `amount` REAL NOT NULL,
                            `paymentDateMillis` INTEGER NOT NULL,
                            `note` TEXT NOT NULL
                        )
                    """.trimIndent())
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = helperFactory.create(config)
        val v1Db = helper.writableDatabase
        v1Db.execSQL("INSERT INTO `udhaar_transactions` (`id`, `transactionType`, `partyName`, `phoneNumber`, `currency`, `principalAmount`, `amountPaid`, `paymentMode`, `isInterestEnabled`, `interestType`, `interestRate`, `interestRatePeriod`, `compoundFrequency`, `startDateMillis`, `gracePeriodDays`, `notes`, `isSettled`, `createdAtMillis`, `updatedAtMillis`) VALUES (1, 'GIVEN', 'Aarav', '9876543210', 'INR', 5000.0, 0.0, 'CASH', 0, 'SIMPLE', 0.0, 'PER_ANNUM', 'YEARLY', 1700000000, 0, '', 0, 1700000000, 1700000000)")

        // Apply migrations 1->2, 2->3, 3->4, 4->5, 5->6, 6->7
        com.jyotirmoyb.udhaar.data.UdhaarDatabase.MIGRATION_1_2.migrate(v1Db)
        com.jyotirmoyb.udhaar.data.UdhaarDatabase.MIGRATION_2_3.migrate(v1Db)
        com.jyotirmoyb.udhaar.data.UdhaarDatabase.MIGRATION_3_4.migrate(v1Db)
        com.jyotirmoyb.udhaar.data.UdhaarDatabase.MIGRATION_4_5.migrate(v1Db)
        com.jyotirmoyb.udhaar.data.UdhaarDatabase.MIGRATION_5_6.migrate(v1Db)
        com.jyotirmoyb.udhaar.data.UdhaarDatabase.MIGRATION_6_7.migrate(v1Db)

        val cursor = v1Db.query("SELECT partyId, remoteId, verificationStatus, contentHash FROM udhaar_transactions WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals("LOCAL_ONLY", cursor.getString(cursor.getColumnIndexOrThrow("verificationStatus")))
        cursor.close()

        // Check that new tables exist
        val partyCursor = v1Db.query("SELECT count(*) FROM parties")
        assertTrue(partyCursor.moveToFirst())
        partyCursor.close()

        val attachmentCursor = v1Db.query("SELECT count(*) FROM attachments")
        assertTrue(attachmentCursor.moveToFirst())
        attachmentCursor.close()

        v1Db.close()
        helper.close()
        dbFile.delete()
    }

    @Test
    fun `pdfStatementGenerator generates valid non-empty PDF document`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val party = PartyEntity(partyId = 1L, name = "Harish Traders", phoneNumber = "+91 9876543210")
        val tx = UdhaarTransaction(
            id = 1L,
            transactionType = TransactionType.GIVEN,
            partyName = "Harish Traders",
            principalAmount = 25_000.0,
            currency = CurrencyType.INR,
            startDateMillis = System.currentTimeMillis() - 86400000L * 30
        )
        val summary = InterestCalculator.calculate(tx)
        val khata = com.jyotirmoyb.udhaar.ui.PartyKhataSummary(
            party = party,
            transactions = listOf(summary)
        )
        val file = com.jyotirmoyb.udhaar.util.PdfStatementGenerator.generatePartyStatementPdf(
            context = context,
            khata = khata,
            userSettings = UserSettings(payeeName = "Shop Owner", upiId = "owner@upi")
        )
        assertNotNull(file)
        assertTrue("PDF file must exist and have content", file.exists() && file.length() > 0)
    }
}


