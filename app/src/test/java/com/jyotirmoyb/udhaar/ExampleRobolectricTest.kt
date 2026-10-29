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
        assertEquals("7.0", BuildConfig.VERSION_NAME)
        assertEquals(7, BuildConfig.VERSION_CODE)
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
}
