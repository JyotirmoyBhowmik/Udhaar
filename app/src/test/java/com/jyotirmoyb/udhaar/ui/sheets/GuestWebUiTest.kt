package com.jyotirmoyb.udhaar.ui.sheets

import com.jyotirmoyb.udhaar.ui.sheets.detail.TransactionVerificationCard
import com.jyotirmoyb.udhaar.util.InterestCalculator

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.jyotirmoyb.udhaar.data.cloud.GuestWebLinkCreateResult
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.UserSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GuestWebUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun webShareSheet_rendersOptionsAndCallsGenerate() {
        val tx = UdhaarTransaction(
            id = 42L,
            partyName = "Ravi Kumar",
            principalAmount = 5000.0,
            currency = CurrencyType.INR,
            transactionType = TransactionType.GIVEN
        )

        var generatedExpiry = 0
        var generatedTrust = ""

        composeRule.setContent {
            WebShareSheet(
                transaction = tx,
                settings = UserSettings(defaultWebLinkExpiryDays = 30, defaultWebTrustLevel = "PRIVATE_LINK"),
                isGenerating = false,
                existingResult = null,
                onGenerateLink = { exp, trust, _, _, _, _, _ ->
                    generatedExpiry = exp
                    generatedTrust = trust
                },
                onRevokeLink = {},
                onDismiss = {}
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Send Web Link").assertExists()
        composeRule.onNodeWithTag("btn_generate_web_link").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(30, generatedExpiry)
        assertEquals("PRIVATE_LINK", generatedTrust)
    }

    @Test
    fun webShareSheet_whenGenerationFails_showsErrorAndRetry_hidesShareButton() {
        val tx = UdhaarTransaction(
            id = 43L,
            partyName = "Aman Sharma",
            principalAmount = 2500.0,
            currency = CurrencyType.INR,
            transactionType = TransactionType.GIVEN
        )

        var retried = false

        composeRule.setContent {
            WebShareSheet(
                transaction = tx,
                settings = UserSettings(defaultWebLinkExpiryDays = 30, defaultWebTrustLevel = "WHATSAPP_OTP"),
                isGenerating = false,
                existingResult = GuestWebLinkCreateResult(
                    linkId = "",
                    secret = "",
                    url = "",
                    whatsappOtp = "",
                    expiresAtMillis = 0L,
                    trustLevel = "LINK_ONLY",
                    success = false,
                    errorMessage = "Unable to publish guest link"
                ),
                onGenerateLink = { _, _, _, _, _, _, _ ->
                    retried = true
                },
                onRevokeLink = {},
                onDismiss = {}
            )
        }

        composeRule.waitForIdle()
        // Error banner must be displayed
        composeRule.onNodeWithTag("web_share_error_banner").assertExists()
        composeRule.onNodeWithTag("web_share_error_text").assertExists()

        // WhatsApp / Share button must NOT exist on failure
        composeRule.onNodeWithTag("btn_share_whatsapp").assertDoesNotExist()

        // Clicking retry invokes onGenerateLink
        composeRule.onNodeWithTag("btn_retry_generate_link").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue("Retry button must invoke onGenerateLink", retried)
    }

    @Test
    fun webShareSheet_whenGenerationSucceeds_showsWhatsAppButton_hidesErrorBanner() {
        val tx = UdhaarTransaction(
            id = 44L,
            partyName = "Rohit Verma",
            principalAmount = 10000.0,
            currency = CurrencyType.INR,
            transactionType = TransactionType.GIVEN
        )

        composeRule.setContent {
            WebShareSheet(
                transaction = tx,
                settings = UserSettings(defaultWebLinkExpiryDays = 30, defaultWebTrustLevel = "WHATSAPP_OTP"),
                isGenerating = false,
                existingResult = GuestWebLinkCreateResult(
                    linkId = "abc123456",
                    secret = "xyzSecret123",
                    url = "https://udhaar.jyotirmoyb.com/u/#k=xyzSecret123",
                    whatsappOtp = "",
                    expiresAtMillis = System.currentTimeMillis() + 86400000L * 30,
                    trustLevel = "LINK_ONLY",
                    success = true,
                    errorMessage = ""
                ),
                onGenerateLink = { _, _, _, _, _, _, _ -> },
                onRevokeLink = {},
                onDismiss = {}
            )
        }

        composeRule.waitForIdle()
        // WhatsApp button must be present only when success == true
        composeRule.onNodeWithTag("btn_share_whatsapp").assertExists()
        composeRule.onNodeWithTag("btn_copy_web_link").assertExists()
        composeRule.onNodeWithTag("btn_revoke_web_link").assertExists()

        // Error banner must not exist
        composeRule.onNodeWithTag("web_share_error_banner").assertDoesNotExist()
    }

    @Test
    fun webShareSheet_whenGoogleVerifiedSelected_showsEmailInputAndPassesEmail() {
        val tx = UdhaarTransaction(
            id = 45L,
            partyName = "Pooja Das",
            principalAmount = 3000.0,
            currency = CurrencyType.INR,
            transactionType = TransactionType.GIVEN
        )

        var generatedTrust = ""
        var generatedEmail = ""

        composeRule.setContent {
            WebShareSheet(
                transaction = tx,
                settings = UserSettings(defaultWebLinkExpiryDays = 30, defaultWebTrustLevel = "PRIVATE_LINK"),
                isGenerating = false,
                existingResult = null,
                onGenerateLink = { _, trust, _, _, _, _, email ->
                    generatedTrust = trust
                    generatedEmail = email
                },
                onRevokeLink = {},
                onDismiss = {}
            )
        }

        composeRule.waitForIdle()
        // Select Google Verified
        composeRule.onNodeWithTag("trust_level_whatsapp_otp").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Email field must exist
        composeRule.onNodeWithTag("input_borrower_google_email").assertExists()
        composeRule.onNodeWithTag("input_borrower_google_email").performTextInput("borrower@gmail.com")
        composeRule.waitForIdle()

        // Click generate
        composeRule.onNodeWithTag("btn_generate_web_link").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("GOOGLE_VERIFIED", generatedTrust)
        assertEquals("borrower@gmail.com", generatedEmail)
    }

    @Test
    fun updatesScreen_rendersPendingClaimAndAccepts() {
        val claimJson = JSONObject().apply {
            put("amountDouble", 2000.0)
            put("dateMillis", System.currentTimeMillis())
            put("paymentMode", "UPI")
            put("reference", "UPI-123456")
        }.toString()

        val tx = UdhaarTransaction(
            id = 101L,
            partyName = "Suresh",
            principalAmount = 5000.0,
            pendingPaymentClaim = claimJson
        )

        var confirmedTxId = 0L

        composeRule.setContent {
            UpdatesScreen(
                transactionsWithUpdates = listOf(tx),
                onConfirmPaymentClaim = { id -> confirmedTxId = id },
                onRejectPaymentClaim = { _, _ -> },
                onAcceptDateProposal = {},
                onDeclineDateProposal = { _, _ -> },
                onSuggestCounterDate = { _, _ -> },
                onOpenTransactionDetail = {},
                onDismiss = {}
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Updates & Inbox").assertExists()
        composeRule.onNodeWithTag("btn_confirm_claim_101").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(101L, confirmedTxId)
    }

    @Test
    fun updatesScreen_rendersDateProposalAndAccepts() {
        val futureDate = System.currentTimeMillis() + 86400000L * 7
        val proposalJson = JSONObject().apply {
            put("proposedDateMillis", futureDate)
            put("message", "Requesting 1 week extension")
        }.toString()

        val tx = UdhaarTransaction(
            id = 202L,
            partyName = "Anita",
            principalAmount = 8000.0,
            pendingDateProposal = proposalJson
        )

        var acceptedTxId = 0L

        composeRule.setContent {
            UpdatesScreen(
                transactionsWithUpdates = listOf(tx),
                onConfirmPaymentClaim = {},
                onRejectPaymentClaim = { _, _ -> },
                onAcceptDateProposal = { id -> acceptedTxId = id },
                onDeclineDateProposal = { _, _ -> },
                onSuggestCounterDate = { _, _ -> },
                onOpenTransactionDetail = {},
                onDismiss = {}
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("btn_accept_date_202").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(202L, acceptedTxId)
    }

    @Test
    fun transactionVerificationCard_triggersOnOpenWebShare_onSendWebLinkClick() {
        val tx = UdhaarTransaction(
            id = 88L,
            partyName = "Priya",
            principalAmount = 3000.0,
            currency = CurrencyType.INR,
            transactionType = TransactionType.GIVEN,
            phoneNumber = "+91 9876543210"
        )
        val summary = InterestCalculator.calculate(tx)
        var webShareClicked = false

        composeRule.setContent {
            TransactionVerificationCard(
                summary = summary,
                tx = tx,
                onOpenWebShare = { webShareClicked = true }
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("send_web_link_button").performClick()
        assertTrue("onOpenWebShare callback must be called when clicking Send Web Link", webShareClicked)
    }
}
