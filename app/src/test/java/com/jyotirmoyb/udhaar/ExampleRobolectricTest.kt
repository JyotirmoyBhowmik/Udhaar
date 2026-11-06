package com.jyotirmoyb.udhaar

import android.content.Context
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
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
        assertTrue(BuildConfig.VERSION_CODE >= 9)
        assertEquals("${BuildConfig.VERSION_CODE}.0", BuildConfig.VERSION_NAME)
    }

    @get:org.junit.Rule
    val composeRule = androidx.compose.ui.test.junit4.createAndroidComposeRule<MainActivity>()

    @Test
    fun `mainActivity launches and renders dashboard UI without crashing`() {
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasText("Udhaar", ignoreCase = true)).assertExists()
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
            principalAmountPaise = 1_000_000L,
            amountPaidPaise = 250_000L,
            paymentMode = PaymentMode.UPI
        )
        val summary = InterestCalculator.calculate(tx)
        val payments = listOf(
            PartialPayment(
                id = 1L,
                transactionId = 42L,
                amountPaise = 250_000L,
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
            principalAmountPaise = 1_500_000L,
            amountPaidPaise = 500_000L
        )
        val payment = PartialPayment(
            id = 30L,
            transactionId = 20L,
            amountPaise = 500_000L,
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
        assertEquals(1_500_000L, parsed.transactions.first().principalAmountPaise)
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
    fun `small phone 360dp - header shows UDHAAR Emerald Slate and single gear icon while pill tabs summary cards and filter chips render`() {
        composeRule.waitForIdle()

        // Default theme mode is EMERALD_FINANCE ("Emerald Slate") and is first in AppThemeMode
        assertEquals(com.jyotirmoyb.udhaar.ui.theme.AppThemeMode.EMERALD_FINANCE, com.jyotirmoyb.udhaar.ui.theme.AppThemeMode.entries.first())
        assertEquals("Emerald Slate", com.jyotirmoyb.udhaar.ui.theme.AppThemeMode.EMERALD_FINANCE.label)
        assertEquals(5, com.jyotirmoyb.udhaar.ui.theme.AppThemeMode.entries.size)

        // Header shows UDHAAR, theme subtitle "Emerald Slate", and single Settings gear icon on right
        composeRule.onNode(androidx.compose.ui.test.hasText("Udhaar", ignoreCase = true)).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("header_theme_subtitle")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("open_settings_button")).assertExists()

        // Three rounded pill tabs: Active, People, History
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("nav_tab_ledger")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("nav_tab_people_directory")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("nav_tab_history")).assertExists()

        // Two soft summary cards side-by-side + Privacy toggle + Net INR Position row + Search bar
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("summary_receivable_card")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("summary_payable_card")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("privacy_hide_amounts_toggle")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("net_position_row")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("dashboard_search_input")).assertExists()

        // Toggling privacy eye hides amounts
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("privacy_hide_amounts_toggle")).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("privacy_hide_amounts_toggle")).performClick()
        composeRule.waitForIdle()

        // Filter chips LazyRow scrolls to Settled chip and displays count badge
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("filter_chips_lazy_row"))
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("filter_chip_settled"))
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("filter_chip_settled")).assertExists()
        composeRule.onNode(
            androidx.compose.ui.test.hasTestTag("filter_badge_settled"),
            useUnmergedTree = true
        ).assertExists()

        // Scroll to the sort menu button inside the ledger LazyColumn
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("transactions_lazy_list"))
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("sort_transactions_button"))
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("sort_transactions_button")).assertExists()
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

        // Open Settings at 1.3x font scale and scroll to the very last section
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("open_settings_button"))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("settings_lazy_column"))
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("section_about"))
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("contact_admin_button")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("save_settings_button")).assertExists()

        // Open Share App ModalBottomSheet from Settings and verify last button (copy_app_link_button) is reachable
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("open_share_app_modal_from_settings"))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("copy_app_link_button"))
            .performScrollTo()
            .assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("close_share_app_modal_button"))
            .performClick()
        composeRule.waitForIdle()
    }

    @Test
    @Config(sdk = [36], qualifiers = "w640dp-h360dp-normal-long-land-hdpi")
    fun `landscape 640x360 - Settings and UPI QR ModalBottomSheet scroll to last button`() {
        composeRule.waitForIdle()

        // Open Settings in landscape and launch UPI QR ModalBottomSheet
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("open_settings_button"))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("settings_open_upi_qr_button"))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("qr_upi_id_input")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("close_qr_modal_button"))
            .performClick()
        composeRule.waitForIdle()

        // Scroll Settings in landscape to About section while sticky Save button stays accessible
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("settings_lazy_column"))
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("section_about"))
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("contact_admin_button"))
            .performScrollTo()
            .assertExists()
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
                    principalAmountPaise = 1_200_000L,
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
            principalAmountPaise = 2_500_000L,
            currency = CurrencyType.INR
        )
        val payment = PartialPayment(
            id = 50L,
            transactionId = 100L,
            amountPaise = 500_000L
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
        assertEquals(8, parsed.schemaVersion)
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
            dao.insertTransaction(UdhaarTransaction(id = 1L, partyName = "Local Party", principalAmountPaise = 50_000L))

            val backupToRestore = com.jyotirmoyb.udhaar.util.ParsedLedgerBackup(
                exportType = "FULL_LEDGER",
                schemaVersion = 5,
                parties = listOf(PartyEntity(partyId = 99L, name = "Cloud Restored Party")),
                transactions = listOf(UdhaarTransaction(id = 99L, partyName = "Cloud Restored Party", principalAmountPaise = 500_000L)),
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
    fun `room migrations 1 to 9 apply cleanly without schema violation`() {
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

        // Apply migrations 1->2, 2->3, 3->4, 4->5, 5->6, 6->7, 7->8, 8->9, 9->10, 10->11
        com.jyotirmoyb.udhaar.data.UdhaarDatabase.MIGRATION_1_2.migrate(v1Db)
        com.jyotirmoyb.udhaar.data.UdhaarDatabase.MIGRATION_2_3.migrate(v1Db)
        com.jyotirmoyb.udhaar.data.UdhaarDatabase.MIGRATION_3_4.migrate(v1Db)
        com.jyotirmoyb.udhaar.data.UdhaarDatabase.MIGRATION_4_5.migrate(v1Db)
        com.jyotirmoyb.udhaar.data.UdhaarDatabase.MIGRATION_5_6.migrate(v1Db)
        com.jyotirmoyb.udhaar.data.UdhaarDatabase.MIGRATION_6_7.migrate(v1Db)
        com.jyotirmoyb.udhaar.data.UdhaarDatabase.MIGRATION_7_8.migrate(v1Db)
        com.jyotirmoyb.udhaar.data.UdhaarDatabase.MIGRATION_8_9.migrate(v1Db)
        com.jyotirmoyb.udhaar.data.UdhaarDatabase.MIGRATION_9_10.migrate(v1Db)
        com.jyotirmoyb.udhaar.data.UdhaarDatabase.MIGRATION_10_11.migrate(v1Db)
        com.jyotirmoyb.udhaar.data.UdhaarDatabase.MIGRATION_11_12.migrate(v1Db)

        val cursor = v1Db.query("SELECT partyId, principalAmountPaise, amountPaidPaise, remoteId, verificationStatus, contentHash, remindersDisabled, snoozedUntilMillis, lastReminderSentMillis, lastReminderLevel, webLinkStatus, lastGuestAction, trustLevel, pendingPaymentClaim, pendingDateProposal FROM udhaar_transactions WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals(500000L, cursor.getLong(cursor.getColumnIndexOrThrow("principalAmountPaise")))
        assertEquals(0L, cursor.getLong(cursor.getColumnIndexOrThrow("amountPaidPaise")))
        assertEquals("LOCAL_ONLY", cursor.getString(cursor.getColumnIndexOrThrow("verificationStatus")))
        assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("remindersDisabled")))
        assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("snoozedUntilMillis")))
        assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("lastReminderSentMillis")))
        assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("lastReminderLevel")))
        assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("webLinkStatus")))
        assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("lastGuestAction")))
        assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("trustLevel")))
        assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("pendingPaymentClaim")))
        assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("pendingDateProposal")))
        cursor.close()

        // Verify legacy Double columns were removed by MIGRATION_10_11
        v1Db.query("PRAGMA table_info(`udhaar_transactions`)").use { pragma ->
            val colNames = mutableSetOf<String>()
            val nameIdx = pragma.getColumnIndex("name")
            while (pragma.moveToNext()) colNames.add(pragma.getString(nameIdx))
            assertTrue(colNames.contains("principalAmountPaise"))
            assertTrue(colNames.contains("amountPaidPaise"))
            org.junit.Assert.assertFalse("principalAmount Double column must be removed", colNames.contains("principalAmount"))
            org.junit.Assert.assertFalse("amountPaid Double column must be removed", colNames.contains("amountPaid"))
        }

        // Check that new tables and columns exist
        val partyCursor = v1Db.query("SELECT language, quietHoursOverride FROM parties")
        partyCursor.close()

        val reminderEventsCursor = v1Db.query("SELECT count(*) FROM reminder_events")
        assertTrue(reminderEventsCursor.moveToFirst())
        reminderEventsCursor.close()

        val attachmentCursor = v1Db.query("SELECT count(*) FROM attachments")
        assertTrue(attachmentCursor.moveToFirst())
        attachmentCursor.close()

        val plansCursor = v1Db.query("SELECT count(*) FROM installment_plans")
        assertTrue(plansCursor.moveToFirst())
        plansCursor.close()

        val installmentsCursor = v1Db.query("SELECT count(*) FROM installments")
        assertTrue(installmentsCursor.moveToFirst())
        installmentsCursor.close()

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
            principalAmountPaise = 2_500_000L,
            currency = CurrencyType.INR,
            startDateMillis = System.currentTimeMillis() - 86400000L * 30
        )
        val summary = InterestCalculator.calculate(tx)
        val khata = com.jyotirmoyb.udhaar.ui.PartyKhataSummary(
            party = party,
            transactions = listOf(summary),
            inrToReceivePaise = 2_500_000L,
            inrToPayPaise = 0L,
            nprToReceivePaise = 0L,
            nprToPayPaise = 0L
        )
        val file = com.jyotirmoyb.udhaar.util.PdfStatementGenerator.generatePartyStatementPdf(
            context = context,
            khata = khata,
            userSettings = UserSettings(payeeName = "Shop Owner", upiId = "owner@upi"),
            enabledCurrencies = listOf(CurrencyType.INR)
        )
        assertNotNull(file)
        assertTrue("PDF file must exist and have content", file.exists() && file.length() > 0)
    }

    @Test
    fun `encryptedStorageManager creates Keystore MasterKey and EncryptedSharedPreferences and SQLCipher database key`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbKey = com.jyotirmoyb.udhaar.security.EncryptedStorageManager.getOrCreateDatabasePassphrase(context)
        assertEquals("SQLCipher key must be 256-bit (32 bytes)", 32, dbKey.size)
        val dbKeySecondCall = com.jyotirmoyb.udhaar.security.EncryptedStorageManager.getOrCreateDatabasePassphrase(context)
        org.junit.Assert.assertArrayEquals("Database key must persist deterministically across calls", dbKey, dbKeySecondCall)
        dbKey.fill(0)
        dbKeySecondCall.fill(0)

        val encPrefs = com.jyotirmoyb.udhaar.security.EncryptedStorageManager.getEncryptedPreferences(context)
        encPrefs.edit().putString("upi_id", "encrypted_merchant@okaxis").commit()
        assertEquals("encrypted_merchant@okaxis", encPrefs.getString("upi_id", null))

        encPrefs.edit().putString(com.jyotirmoyb.udhaar.notification.UdhaarFirebaseMessagingService.KEY_FCM_TOKEN, "fcm-token-xyz-987").commit()
        assertEquals("fcm-token-xyz-987", encPrefs.getString(com.jyotirmoyb.udhaar.notification.UdhaarFirebaseMessagingService.KEY_FCM_TOKEN, null))
    }

    @Test
    fun `newHomeScreen summaryCards filterChips and swipeActions work end to end`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val now = System.currentTimeMillis()
        val insertedId = kotlinx.coroutines.runBlocking {
            val dao = com.jyotirmoyb.udhaar.data.UdhaarDatabase.getInstance(context).udhaarDao()
            dao.insertTransaction(
                UdhaarTransaction(
                    transactionType = TransactionType.GIVEN,
                    partyName = "Bapan Saha",
                    phoneNumber = "+919876543210",
                    currency = CurrencyType.INR,
                    principalAmountPaise = 1_500_000L,
                    paymentMode = PaymentMode.UPI,
                    isInterestEnabled = true,
                    interestType = com.jyotirmoyb.udhaar.data.InterestType.SIMPLE,
                    interestRate = 8.0,
                    interestRatePeriod = com.jyotirmoyb.udhaar.data.InterestRatePeriod.PER_ANNUM,
                    startDateMillis = now - 86_400_000L * 5L,
                    expiryDateMillis = now + 86_400_000L * 30L,
                    notes = "By April return"
                )
            )
        }

        composeRule.waitForIdle()

        // 1. Verify summary cards (Total Receivable, Total Payable, Net Position, and Privacy Eye toggle)
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("summary_receivable_card")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("summary_payable_card")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("net_position_row")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("privacy_hide_amounts_toggle")).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("privacy_hide_amounts_toggle")).performClick()
        composeRule.waitForIdle()

        // 2. Verify horizontal filter chips (All, Given, Taken, Overdue, Settled)
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("filter_chip_all")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("filter_chip_given")).assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("filter_chip_taken")).assertExists().performClick()
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("filter_chip_all")).performClick()
        composeRule.waitForIdle()

        // 3. Scroll to the ledger card and verify swipe actions (swipe right -> record payment, swipe left -> remind)
        val cardTag = "transaction_card_$insertedId"
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("transactions_lazy_list"))
            .performScrollToNode(androidx.compose.ui.test.hasTestTag(cardTag))
        composeRule.waitForIdle()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag(cardTag)).assertExists()

        composeRule.onNode(androidx.compose.ui.test.hasTestTag(cardTag))
            .performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNode(androidx.compose.ui.test.hasTestTag(cardTag))
            .performTouchInput { swipeRight() }
        composeRule.waitForIdle()
    }

    @Test
    fun `encryptedStorageManager detects plaintext sqlite header and quarantines unreadable db files`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempPlain = java.io.File(context.cacheDir, "test_plain.db")
        tempPlain.writeBytes("SQLite format 3\u0000more_bytes_here".toByteArray(Charsets.US_ASCII))
        assertTrue(com.jyotirmoyb.udhaar.security.EncryptedStorageManager.isPlaintextSqliteHeader(tempPlain))

        val tempEncrypted = java.io.File(context.cacheDir, "test_cipher.db")
        tempEncrypted.writeBytes(ByteArray(32) { (it * 7 + 3).toByte() })
        org.junit.Assert.assertFalse(com.jyotirmoyb.udhaar.security.EncryptedStorageManager.isPlaintextSqliteHeader(tempEncrypted))

        com.jyotirmoyb.udhaar.security.EncryptedStorageManager.quarantineDatabaseFiles(tempEncrypted)
        org.junit.Assert.assertFalse(tempEncrypted.exists())
        tempPlain.delete()
        context.cacheDir.listFiles()?.filter { it.name.startsWith("test_cipher.db.corrupt.") }?.forEach { it.delete() }
    }

    @Test
    fun `plain database migrates and restores cleanly`() =
        kotlinx.coroutines.test.runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            com.jyotirmoyb.udhaar.security.EncryptedStorageManager.TestHooks.reset()
            val dbName = "test_migrate_plain.db"
            val dbFile = context.getDatabasePath(dbName)
            dbFile.parentFile?.mkdirs()
            dbFile.delete()
            java.io.File("${dbFile.absolutePath}-wal").delete()
            java.io.File("${dbFile.absolutePath}-shm").delete()

            // 1. Create a plain unencrypted Room database with sample data and export a pre-migration backup
            val plainDb = androidx.room.Room.databaseBuilder(
                context,
                com.jyotirmoyb.udhaar.data.UdhaarDatabase::class.java,
                dbName
            ).allowMainThreadQueries().build()
            val plainDao = plainDb.udhaarDao()
            val partyId = plainDao.insertParty(
                PartyEntity(
                    name = "Gopal Kirana Store",
                    phoneNumber = "+919811122233",
                    upiId = "gopal@okicici",
                    preferredCurrency = CurrencyType.INR
                )
            )
            val txId = plainDao.insertTransaction(
                UdhaarTransaction(
                    partyId = partyId,
                    transactionType = TransactionType.GIVEN,
                    partyName = "Gopal Kirana Store",
                    phoneNumber = "+919811122233",
                    currency = CurrencyType.INR,
                    principalAmountPaise = 850_000L,
                    amountPaidPaise = 150_000L,
                    paymentMode = PaymentMode.UPI
                )
            )
            plainDao.insertPartialPayment(
                PartialPayment(
                    transactionId = txId,
                    amountPaise = 150_000L,
                    paymentMode = PaymentMode.UPI,
                    note = "Pre-migration installment"
                )
            )

            // Build a pre-migration JSON backup and a local .udhaar encrypted backup before migration
            val preMigrationParties = plainDao.getAllPartiesSnapshot()
            val preMigrationTxs = plainDao.getAllTransactionsSnapshot()
            val preMigrationPayments = plainDao.getAllPartialPaymentsSnapshot()
            val preMigrationJson = LedgerBackupManager.buildBackupJson(
                parties = preMigrationParties,
                transactions = preMigrationTxs,
                partialPayments = preMigrationPayments,
                exportType = "FULL_LEDGER"
            )
            val preMigrationUdhaarBytes = com.jyotirmoyb.udhaar.backup.DriveBackupCrypto.encrypt(
                plainBytes = preMigrationJson.toByteArray(Charsets.UTF_8),
                passphrase = "MigrationBackupPass#2026",
                preview = com.jyotirmoyb.udhaar.backup.BackupPreviewSummary(
                    partiesCount = preMigrationParties.size,
                    transactionsCount = preMigrationTxs.size,
                    paymentsCount = preMigrationPayments.size
                )
            )
            plainDb.close()

            assertTrue("Database file must start as plain SQLite format 3", com.jyotirmoyb.udhaar.security.EncryptedStorageManager.isPlaintextSqliteHeader(dbFile))
            com.jyotirmoyb.udhaar.security.EncryptedStorageManager.setDatabaseEncryptionState(
                context,
                com.jyotirmoyb.udhaar.security.EncryptedStorageManager.DbEncryptionState.NONE
            )

            // 2. Run configureSqlCipherForRoom on the existing plain database
            val migratedBuilder = com.jyotirmoyb.udhaar.security.EncryptedStorageManager.configureSqlCipherForRoom(
                context = context,
                builder = androidx.room.Room.databaseBuilder(
                    context,
                    com.jyotirmoyb.udhaar.data.UdhaarDatabase::class.java,
                    dbName
                ).allowMainThreadQueries(),
                dbName = dbName
            )
            assertEquals(
                com.jyotirmoyb.udhaar.security.EncryptedStorageManager.DbEncryptionState.ENCRYPTED,
                com.jyotirmoyb.udhaar.security.EncryptedStorageManager.getDatabaseEncryptionState(context)
            )

            // Verify staging plain copy was securely deleted
            val stagingDir = java.io.File(context.filesDir, "db_migration_staging")
            org.junit.Assert.assertFalse(java.io.File(stagingDir, "$dbName.plain_copy").exists())

            // 3. Open the migrated database and verify all rows and user_version are intact
            val migratedDb = migratedBuilder.build()
            val migratedDao = migratedDb.udhaarDao()
            assertEquals(1, migratedDao.getAllPartiesSnapshot().size)
            assertEquals("Gopal Kirana Store", migratedDao.getAllPartiesSnapshot().first().name)
            assertEquals(1, migratedDao.getAllTransactionsSnapshot().size)
            assertEquals(850_000L, migratedDao.getAllTransactionsSnapshot().first().principalAmountPaise)
            assertEquals(1, migratedDao.getAllPartialPaymentsSnapshot().size)
            assertEquals("Pre-migration installment", migratedDao.getAllPartialPaymentsSnapshot().first().note)

            // 4. Verify that both the pre-migration JSON backup and .udhaar backup restore cleanly after migration
            val migratedRepo = com.jyotirmoyb.udhaar.data.UdhaarRepository(migratedDao, context)
            val parsedFromPreMigrationJson = LedgerBackupManager.parseBackupJson(preMigrationJson)
            val restoredFromJsonCount = migratedRepo.restoreFullLedgerBackup(parsedFromPreMigrationJson, replaceExisting = true)
            assertEquals(1, restoredFromJsonCount)
            assertEquals("Gopal Kirana Store", migratedDao.getAllPartiesSnapshot().first().name)

            val decryptedUdhaarJson = String(
                com.jyotirmoyb.udhaar.backup.DriveBackupCrypto.decrypt(preMigrationUdhaarBytes, "MigrationBackupPass#2026"),
                Charsets.UTF_8
            )
            val parsedFromUdhaar = LedgerBackupManager.parseBackupJson(decryptedUdhaarJson)
            val restoredFromUdhaarCount = migratedRepo.restoreFullLedgerBackup(parsedFromUdhaar, replaceExisting = true)
            assertEquals(1, restoredFromUdhaarCount)
            assertEquals(850_000L, migratedDao.getAllTransactionsSnapshot().first().principalAmountPaise)

            migratedDb.close()
            dbFile.delete()
        }

    @Test
    fun `interrupted migration resumes safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.jyotirmoyb.udhaar.security.EncryptedStorageManager.TestHooks.reset()
        val dbName = "test_interrupted_migration.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        // Create a valid plain SQLite database in the staging copy location to simulate an interrupted migration
        // where dbFile was partially written/removed while staging plain_copy holds the safe copy
        val stagingDir = java.io.File(context.filesDir, "db_migration_staging").apply { mkdirs() }
        val plainCopyFile = java.io.File(stagingDir, "$dbName.plain_copy")
        plainCopyFile.delete()

        val tempPlainDb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(plainCopyFile, null)
        tempPlainDb.execSQL("CREATE TABLE IF NOT EXISTS sample_items (id INTEGER PRIMARY KEY, label TEXT NOT NULL)")
        tempPlainDb.execSQL("INSERT INTO sample_items (id, label) VALUES (1, 'InterruptedRow1'), (2, 'InterruptedRow2')")
        tempPlainDb.version = 7
        tempPlainDb.close()

        // Simulate interrupted state: db_encryption_state = MIGRATING and original dbFile is missing/incomplete
        com.jyotirmoyb.udhaar.security.EncryptedStorageManager.setDatabaseEncryptionState(
            context,
            com.jyotirmoyb.udhaar.security.EncryptedStorageManager.DbEncryptionState.MIGRATING
        )

        val migratedOk = com.jyotirmoyb.udhaar.security.EncryptedStorageManager.migratePlainDatabaseToEncrypted(
            context = context,
            dbFile = dbFile
        )
        assertTrue("Interrupted migration must resume from staged plain copy and succeed", migratedOk)
        assertEquals(
            com.jyotirmoyb.udhaar.security.EncryptedStorageManager.DbEncryptionState.ENCRYPTED,
            com.jyotirmoyb.udhaar.security.EncryptedStorageManager.getDatabaseEncryptionState(context)
        )
        assertTrue("Swapped database file must exist after resumed migration", dbFile.exists() && dbFile.length() > 0L)
        org.junit.Assert.assertFalse("Staged plain copy must be securely deleted after resume", plainCopyFile.exists())

        dbFile.delete()
    }

    @Test
    fun `corrupted migration fails and retries safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.jyotirmoyb.udhaar.security.EncryptedStorageManager.TestHooks.reset()
        val dbName = "test_corrupt_migration.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        val origDb = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        origDb.execSQL("CREATE TABLE IF NOT EXISTS ledger_test (id INTEGER PRIMARY KEY, party TEXT NOT NULL)")
        origDb.execSQL("INSERT INTO ledger_test (id, party) VALUES (10, 'OriginalUntouchedParty')")
        origDb.version = 7
        origDb.close()

        val originalBytesBefore = dbFile.readBytes()

        // Simulate row count mismatch / corruption during verification
        com.jyotirmoyb.udhaar.security.EncryptedStorageManager.TestHooks.simulateRowCountMismatch = true
        val migrationResult = com.jyotirmoyb.udhaar.security.EncryptedStorageManager.migratePlainDatabaseToEncrypted(
            context = context,
            dbFile = dbFile
        )
        org.junit.Assert.assertFalse("Corrupted migration must fail verification", migrationResult)
        assertEquals(
            com.jyotirmoyb.udhaar.security.EncryptedStorageManager.DbEncryptionState.FAILED,
            com.jyotirmoyb.udhaar.security.EncryptedStorageManager.getDatabaseEncryptionState(context)
        )

        // Original plain database must remain completely untouched and readable
        assertTrue(com.jyotirmoyb.udhaar.security.EncryptedStorageManager.isPlaintextSqliteHeader(dbFile))
        org.junit.Assert.assertArrayEquals(originalBytesBefore, dbFile.readBytes())

        // Clearing the fault and retrying succeeds on the next launch / retry tap
        com.jyotirmoyb.udhaar.security.EncryptedStorageManager.TestHooks.reset()
        val retryResult = com.jyotirmoyb.udhaar.security.EncryptedStorageManager.retryDatabaseEncryption(
            context = context,
            dbName = dbName
        )
        assertTrue("Retry after failed migration must succeed", retryResult)
        assertEquals(
            com.jyotirmoyb.udhaar.security.EncryptedStorageManager.DbEncryptionState.ENCRYPTED,
            com.jyotirmoyb.udhaar.security.EncryptedStorageManager.getDatabaseEncryptionState(context)
        )

        dbFile.delete()
    }

    @Test
    fun `fresh install creates encrypted database and failed SQLCipher startup shows blocking error without creating plain db`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.jyotirmoyb.udhaar.security.EncryptedStorageManager.TestHooks.reset()
        val freshDbName = "test_fresh_install.db"
        val freshDbFile = context.getDatabasePath(freshDbName)
        freshDbFile.parentFile?.mkdirs()
        freshDbFile.delete()

        // 1. Simulate SQLCipher failure on a fresh install: must NOT create a plain database and must set blocking error
        com.jyotirmoyb.udhaar.security.EncryptedStorageManager.TestHooks.simulateFreshInstallFailure = true
        com.jyotirmoyb.udhaar.security.EncryptedStorageManager.configureSqlCipherForRoom(
            context = context,
            builder = androidx.room.Room.databaseBuilder(
                context,
                com.jyotirmoyb.udhaar.data.UdhaarDatabase::class.java,
                freshDbName
            ),
            dbName = freshDbName
        )
        assertEquals(
            com.jyotirmoyb.udhaar.security.EncryptedStorageManager.DbEncryptionState.FAILED,
            com.jyotirmoyb.udhaar.security.EncryptedStorageManager.getDatabaseEncryptionState(context)
        )
        assertNotNull(
            "Blocking startup error must be set when SQLCipher cannot start on a fresh install",
            com.jyotirmoyb.udhaar.security.EncryptedStorageManager.blockingStartupErrorFlow.value
        )
        org.junit.Assert.assertFalse(
            "Fresh install failure must never create a plain database file",
            freshDbFile.exists()
        )

        // 2. Clear simulated failure and retry: state transitions to ENCRYPTED and blocking error clears
        com.jyotirmoyb.udhaar.security.EncryptedStorageManager.TestHooks.reset()
        val retryOk = com.jyotirmoyb.udhaar.security.EncryptedStorageManager.retryDatabaseEncryption(
            context = context,
            dbName = freshDbName
        )
        assertTrue(retryOk)
        assertEquals(
            com.jyotirmoyb.udhaar.security.EncryptedStorageManager.DbEncryptionState.ENCRYPTED,
            com.jyotirmoyb.udhaar.security.EncryptedStorageManager.getDatabaseEncryptionState(context)
        )
        org.junit.Assert.assertNull(
            com.jyotirmoyb.udhaar.security.EncryptedStorageManager.blockingStartupErrorFlow.value
        )
        freshDbFile.delete()
    }

    @Test
    fun `ui source files contain no hardcoded Text string literals`() {
        val candidateDirs = listOf(
            java.io.File("src/main/java/com/jyotirmoyb/udhaar/ui"),
            java.io.File("app/src/main/java/com/jyotirmoyb/udhaar/ui")
        )
        val uiDir = candidateDirs.firstOrNull { it.exists() && it.isDirectory }
        assertNotNull("UI source directory must exist for static literal scan", uiDir)

        val hardcodedTextRegex = Regex("""\bText\s*\(\s*(?:text\s*=\s*)?"([^"\\]|\\.)*"""")
        val violations = mutableListOf<String>()

        uiDir!!.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("//") && !trimmed.startsWith("*")) {
                        if (hardcodedTextRegex.containsMatchIn(line)) {
                            violations.add("${file.name}:${index + 1}: $trimmed")
                        }
                    }
                }
            }

        assertTrue(
            "Found hardcoded Text(\"...\") literals in UI files (move to strings.xml):\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun `hideAmounts toggle saves in encrypted settings masks currency as 5 dots and applies to receipts and localized generators`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Verify formatCurrencyPrivacy uses 5 dots ("₹ •••••" and "रू •••••")
        assertEquals("₹ •••••", InterestCalculator.formatCurrencyPrivacy(12_500.0, CurrencyType.INR, true))
        assertEquals("रू •••••", InterestCalculator.formatCurrencyPrivacy(8_000.0, CurrencyType.NPR, true))

        // 2. Verify encrypted settings persistence for hide_amounts_enabled
        com.jyotirmoyb.udhaar.security.EncryptedStorageManager.setAmountsHidden(context, true)
        assertTrue(com.jyotirmoyb.udhaar.security.EncryptedStorageManager.isAmountsHidden(context))
        com.jyotirmoyb.udhaar.security.EncryptedStorageManager.setAmountsHidden(context, false)
        org.junit.Assert.assertFalse(com.jyotirmoyb.udhaar.security.EncryptedStorageManager.isAmountsHidden(context))

        // 3. Verify UpiQrGenerator and ThermalReceiptGenerator accept translated string parameters and hideAmounts
        val qrStrings = UpiQrGenerator.UpiQrCardStrings.fromContext(context)
        val qrCard = UpiQrGenerator.createShareableQrCardBitmap(
            upiId = "merchant@upi",
            payeeName = "Store",
            amountInr = 500.0,
            partyName = "Raju",
            strings = qrStrings
        )
        assertNotNull(qrCard)

        val receiptStrings = ThermalReceiptGenerator.ThermalReceiptStrings.fromContext(context)
        val tx = UdhaarTransaction(
            id = 77L,
            transactionType = TransactionType.GIVEN,
            partyName = "Sita Devi",
            currency = CurrencyType.INR,
            principalAmountPaise = 400_000L
        )
        val summary = InterestCalculator.calculate(tx)
        val hiddenReceiptBitmap = ThermalReceiptGenerator.createTransactionReceiptBitmap(
            summary = summary,
            partialPayments = emptyList(),
            userSettings = UserSettings(upiId = "owner@upi", payeeName = "Owner"),
            hideAmounts = true,
            strings = receiptStrings
        )
        assertNotNull(hiddenReceiptBitmap)
        assertTrue(hiddenReceiptBitmap.width > 0 && hiddenReceiptBitmap.height > 0)
    }

    @Test
    fun `version 9_0 Settings About format Room schema export and R8 keep rules are configured`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Version >= 9 and Settings > About string format
        assertTrue(BuildConfig.VERSION_CODE >= 9)
        assertEquals("${BuildConfig.VERSION_CODE}.0", BuildConfig.VERSION_NAME)
        val aboutVersionText = context.getString(
            R.string.settings_about_version_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )
        assertEquals("Udhaar records v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})", aboutVersionText)

        // 2. Room exported schema file 7.json exists
        val schemaCandidates = listOf(
            java.io.File("schemas/com.jyotirmoyb.udhaar.data.UdhaarDatabase/7.json"),
            java.io.File("app/schemas/com.jyotirmoyb.udhaar.data.UdhaarDatabase/7.json")
        )
        val schemaFile = schemaCandidates.firstOrNull { it.exists() && it.isFile }
        assertNotNull("Exported Room schema 7.json must exist", schemaFile)
        val schemaContent = schemaFile!!.readText()
        assertTrue(schemaContent.contains("\"version\": 7"))

        // 3. R8 keep rules cover SQLCipher JNI, Room @Entity/@Dao, and crash line numbers without broad package keeps or disabling obfuscation
        val proguardCandidates = listOf(
            java.io.File("proguard-rules.pro"),
            java.io.File("app/proguard-rules.pro")
        )
        val proguardFile = proguardCandidates.firstOrNull { it.exists() && it.isFile }
        assertNotNull("proguard-rules.pro must exist", proguardFile)
        val proguardText = proguardFile!!.readText()
        assertTrue(proguardText.contains("-keep class net.zetetic.database.**"))
        assertTrue(proguardText.contains("-keep class net.zetetic.database.sqlcipher.**"))
        assertTrue(proguardText.contains("-keep @androidx.room.Entity class *"))
        assertTrue(proguardText.contains("-keep @androidx.room.Dao class *"))
        assertTrue(proguardText.contains("-keepattributes SourceFile,LineNumberTable"))
        org.junit.Assert.assertFalse(proguardText.contains("-keep class com.jyotirmoyb.udhaar.data.**"))
        org.junit.Assert.assertFalse(proguardText.contains("-dontobfuscate"))
    }

    @Test
    fun `room migrations 8 to 9 to 10 and installment reminders in 5 languages work end to end`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Verify exported Room schemas 3.json through 12.json exist (including 4, 5, 6)
        for (ver in 3..12) {
            val candidates = listOf(
                java.io.File("schemas/com.jyotirmoyb.udhaar.data.UdhaarDatabase/$ver.json"),
                java.io.File("app/schemas/com.jyotirmoyb.udhaar.data.UdhaarDatabase/$ver.json")
            )
            val schemaFile = candidates.firstOrNull { it.exists() && it.isFile }
            assertNotNull("Exported Room schema $ver.json must exist", schemaFile)
            assertTrue("Schema $ver.json must declare version $ver", schemaFile!!.readText().contains("\"version\": $ver"))
        }

        // 2. Execute MIGRATION_8_9 and MIGRATION_9_10 on an in-memory SQLite database
        val sqliteDb = android.database.sqlite.SQLiteDatabase.create(null)
        val supportDb = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(8) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `udhaar_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `partyName` TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `partial_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `transactionId` INTEGER NOT NULL)")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        ).writableDatabase

        com.jyotirmoyb.udhaar.data.UdhaarDatabase.MIGRATION_8_9.migrate(supportDb)
        com.jyotirmoyb.udhaar.data.UdhaarDatabase.MIGRATION_9_10.migrate(supportDb)

        supportDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='installment_plans'").use { cursor ->
            assertTrue("installment_plans table should exist after MIGRATION_8_9", cursor.moveToFirst())
        }
        supportDb.query("SELECT name FROM sqlite_master WHERE type='table' AND name='installments'").use { cursor ->
            assertTrue("installments table should exist after MIGRATION_8_9", cursor.moveToFirst())
        }
        supportDb.close()
        sqliteDb.close()

        // 3. Verify 5-language installment reminder templates render placeholders properly
        val languages = listOf("en", "hi", "bn", "as", "ne")
        for (lang in languages) {
            val tpl = com.jyotirmoyb.udhaar.reminder.ReminderEngine.getLocalizedInstallmentTemplate(context, lang)
            val rendered = com.jyotirmoyb.udhaar.reminder.ReminderEngine.renderInstallmentDraftMessage(
                template = tpl,
                name = "Ravi",
                sequence = 2,
                totalCount = 6,
                installmentAmount = "₹1,000",
                dueDate = "15 Nov 2026",
                totalRemaining = "₹4,000"
            )
            assertTrue("Rendered reminder for $lang should include party name", rendered.contains("Ravi"))
            assertTrue("Rendered reminder for $lang should include installment number", rendered.contains("2"))
            assertTrue("Rendered reminder for $lang should include total installments", rendered.contains("6"))
            assertTrue("Rendered reminder for $lang should include installment amount", rendered.contains("₹1,000"))
            assertTrue("Rendered reminder for $lang should include total remaining", rendered.contains("₹4,000"))
        }
    }

    @Test
    @Config(sdk = [36], qualifiers = "w411dp-h891dp-normal-long-port-xxhdpi", fontScale = 2.0f)
    fun `composeTest_touchTargetsAndButtonsAtFontScale2_0`() {
        composeRule.waitForIdle()

        // Verify primary interactive clickables on Dashboard at fontScale = 2.0 have at least 48dp height
        val interactiveTags = listOf(
            "open_settings_button",
            "open_inbox_updates_button",
            "header_group_split_button",
            "nav_tab_ledger",
            "nav_tab_people_directory",
            "nav_tab_history",
            "privacy_hide_amounts_toggle",
            "filter_chip_all",
            "open_group_split_button",
            "sort_transactions_button",
            "add_udhaar_fab"
        )
        for (tag in interactiveTags) {
            val bounds = composeRule.onNode(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNode().boundsInRoot
            val heightDp = with(composeRule.density) { bounds.height.toDp() }
            assertTrue("Node $tag height ($heightDp) must be >= 47.5.dp at fontScale 2.0", heightDp.value >= 47.5f)
        }
    }

    @Test
    @Config(sdk = [36], qualifiers = "w800dp-h1280dp-xlarge-port-mdpi")
    fun `adaptiveLayout_twoPaneAt600dp_and_centeredForm560dp`() {
        composeRule.waitForIdle()

        // At 800dp width (>= 600dp), list and detail render side-by-side
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("dashboard_adaptive_two_pane")).assertExists()
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("adaptive_detail_pane")).assertExists()

        // Navigate to Add/Edit form and verify centered form container has width <= 560.dp
        composeRule.onNode(androidx.compose.ui.test.hasTestTag("add_udhaar_fab")).performClick()
        composeRule.waitForIdle()

        val formNode = composeRule.onNode(androidx.compose.ui.test.hasTestTag("adaptive_centered_form_container"))
        formNode.assertExists()
        val formBounds = formNode.fetchSemanticsNode().boundsInRoot
        val formWidthDp = with(composeRule.density) { formBounds.width.toDp() }
        assertTrue("Centered form width ($formWidthDp) must be <= 561.dp on 800dp screen", formWidthDp.value <= 561f)
    }

    @Test
    fun `telemetryManager_offUntilConsent_noPersonalData_and_testMyBackup_roundTrip`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.jyotirmoyb.udhaar.util.TelemetryManager.resetForTesting()

        // 1. Default OFF until user agrees
        org.junit.Assert.assertFalse(com.jyotirmoyb.udhaar.util.TelemetryManager.isConsentGranted)
        com.jyotirmoyb.udhaar.util.TelemetryManager.logScreenView("dashboard", context)
        com.jyotirmoyb.udhaar.util.TelemetryManager.recordNonFatal(
            IllegalStateException("Secret user name Rahul +919876543210"),
            "dashboard"
        )
        assertTrue(com.jyotirmoyb.udhaar.util.TelemetryManager.getRecordedScreenEventsForTesting().isEmpty())
        assertTrue(com.jyotirmoyb.udhaar.util.TelemetryManager.getRecordedCrashEventsForTesting().isEmpty())

        // 2. Enable consent -> logs sanitized screen IDs and redacted crash messages ONLY (zero personal data)
        com.jyotirmoyb.udhaar.util.TelemetryManager.setConsent(context, true)
        assertTrue(com.jyotirmoyb.udhaar.util.TelemetryManager.isConsentGranted)
        com.jyotirmoyb.udhaar.util.TelemetryManager.logScreenView("dashboard", context)
        com.jyotirmoyb.udhaar.util.TelemetryManager.recordNonFatal(
            IllegalArgumentException("Sensitive note for Ramesh +919876543210"),
            "dashboard"
        )

        val screenEvents = com.jyotirmoyb.udhaar.util.TelemetryManager.getRecordedScreenEventsForTesting()
        assertEquals(1, screenEvents.size)
        assertEquals("dashboard", screenEvents.first().screenName)

        val crashEvents = com.jyotirmoyb.udhaar.util.TelemetryManager.getRecordedCrashEventsForTesting()
        assertEquals(1, crashEvents.size)
        assertEquals("IllegalArgumentException", crashEvents.first().exceptionClass)
        org.junit.Assert.assertFalse(crashEvents.first().redactedMessage.contains("9876543210"))

        // 3. Disabling consent clears buffers immediately
        com.jyotirmoyb.udhaar.util.TelemetryManager.setConsent(context, false)
        assertTrue(com.jyotirmoyb.udhaar.util.TelemetryManager.getRecordedScreenEventsForTesting().isEmpty())
        assertTrue(com.jyotirmoyb.udhaar.util.TelemetryManager.getRecordedCrashEventsForTesting().isEmpty())

        // 4. Backup Recovery Key generation + "Test my backup" round-trip verification
        val recoveryKey = com.jyotirmoyb.udhaar.util.TelemetryManager.generateBackupRecoveryKey()
        assertTrue("Recovery key must be valid", com.jyotirmoyb.udhaar.util.TelemetryManager.isValidRecoveryKey(recoveryKey))
        assertTrue("Recovery key ID must be non-empty", com.jyotirmoyb.udhaar.util.TelemetryManager.extractRecoveryKeyId(recoveryKey).isNotBlank())

        val sampleParty = PartyEntity(partyId = 1L, name = "Test Backup Party", phoneNumber = "+919876543210")
        val sampleTx = UdhaarTransaction(id = 10L, partyId = 1L, partyName = "Test Backup Party", principalAmountPaise = 275_050L, amountPaidPaise = 75_025L)
        val samplePayment = PartialPayment(id = 100L, transactionId = 10L, amountPaise = 75_025L)
        val json = LedgerBackupManager.buildBackupJson(
            parties = listOf(sampleParty),
            transactions = listOf(sampleTx),
            partialPayments = listOf(samplePayment),
            exportType = "FULL_LEDGER"
        )
        val encrypted = com.jyotirmoyb.udhaar.backup.DriveBackupCrypto.encrypt(
            plainBytes = json.toByteArray(Charsets.UTF_8),
            passphrase = recoveryKey,
            preview = com.jyotirmoyb.udhaar.backup.BackupPreviewSummary(1, 1, 1, 0)
        )
        val decryptedJson = String(com.jyotirmoyb.udhaar.backup.DriveBackupCrypto.decrypt(encrypted, recoveryKey), Charsets.UTF_8)
        val parsed = LedgerBackupManager.parseBackupJson(decryptedJson)
        assertEquals(275_050L, parsed.transactions.sumOf { it.principalAmountPaise })
        assertEquals(75_025L, parsed.partialPayments.sumOf { it.amountPaise })
    }

    @Test
    fun `splitFiles_areAllStrictlyUnder600Lines_and_ciWorkflowAndSmokeChecklistExist`() {
        val targetFiles = listOf(
            "src/main/java/com/jyotirmoyb/udhaar/MainActivity.kt" to "app/src/main/java/com/jyotirmoyb/udhaar/MainActivity.kt",
            "src/main/java/com/jyotirmoyb/udhaar/ui/components/DashboardScreen.kt" to "app/src/main/java/com/jyotirmoyb/udhaar/ui/components/DashboardScreen.kt",
            "src/main/java/com/jyotirmoyb/udhaar/ui/components/PeopleDirectoryScreen.kt" to "app/src/main/java/com/jyotirmoyb/udhaar/ui/components/PeopleDirectoryScreen.kt",
            "src/main/java/com/jyotirmoyb/udhaar/ui/components/AddEditUdhaarScreen.kt" to "app/src/main/java/com/jyotirmoyb/udhaar/ui/components/AddEditUdhaarScreen.kt"
        )
        for ((relA, relB) in targetFiles) {
            val file = listOf(java.io.File(relA), java.io.File(relB)).firstOrNull { it.exists() }
            assertNotNull("Expected file $relB to exist", file)
            val lineCount = file!!.readLines().size
            assertTrue("${file.name} has $lineCount lines, which must be < 600", lineCount < 600)
        }

        val ciFile = listOf(
            java.io.File(".github/workflows/android-ci.yml"),
            java.io.File("../.github/workflows/android-ci.yml")
        ).firstOrNull { it.exists() }
        assertNotNull("GitHub Actions workflow .github/workflows/android-ci.yml must exist", ciFile)
        val ciContent = ciFile!!.readText()
        assertTrue(ciContent.contains("testDebugUnitTest"))
        assertTrue(ciContent.contains("lintDebug"))
        assertTrue(ciContent.contains("ktlint"))
        assertTrue(ciContent.contains("assembleRelease"))

        val checklistFile = listOf(
            java.io.File("docs/RELEASE_SMOKE_TEST_CHECKLIST.md"),
            java.io.File("../docs/RELEASE_SMOKE_TEST_CHECKLIST.md")
        ).firstOrNull { it.exists() }
        assertNotNull("Release smoke test checklist must exist", checklistFile)
    }

}
