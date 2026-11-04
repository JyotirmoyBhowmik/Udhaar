package com.jyotirmoyb.udhaar.ui.sheets

import com.jyotirmoyb.udhaar.ui.sheets.detail.TransactionVerificationCard
import com.jyotirmoyb.udhaar.util.InterestCalculator

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.UserSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
                settings = UserSettings(defaultWebLinkExpiryDays = 30, defaultWebTrustLevel = "WHATSAPP_OTP"),
                isGenerating = false,
                existingResult = null,
                onGenerateLink = { exp, trust, _, _, _, _ ->
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
        assertEquals("WHATSAPP_OTP", generatedTrust)
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
