package com.jyotirmoyb.udhaar.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.data.ChatMessageEntity
import com.jyotirmoyb.udhaar.data.ChatMessageType
import com.jyotirmoyb.udhaar.data.ChatThreadMetadata
import com.jyotirmoyb.udhaar.data.ChatThreadScope
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.UserChatSafetyEntity
import com.jyotirmoyb.udhaar.ui.components.AddEditUdhaarScreen
import com.jyotirmoyb.udhaar.ui.components.ChatThreadBottomSheet
import com.jyotirmoyb.udhaar.ui.components.DashboardScreen
import com.jyotirmoyb.udhaar.ui.components.PeopleDirectoryContent
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode
import com.jyotirmoyb.udhaar.ui.theme.UdhaarTheme
import com.jyotirmoyb.udhaar.util.InterestCalculator
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Automated TalkBack semantics & fontScale = 2.0f accessibility UI suite
 * covering the 4 primary user journeys: Home, Record, People, and Chat.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-normal-long-port-xhdpi")
class AccessibilityTalkBackFontScaleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleParty(): PartyEntity = PartyEntity(
        partyId = 101L,
        name = "Aarav Sharma",
        phoneNumber = "+91 98765 43210",
        upiId = "aarav@upi",
        email = "aarav@example.com",
        linkedUserId = "uid_aarav_101",
        preferredCurrency = CurrencyType.INR
    )

    private fun sampleTransaction(): UdhaarTransaction = UdhaarTransaction(
        id = 201L,
        partyId = 101L,
        transactionType = TransactionType.GIVEN,
        partyName = "Aarav Sharma",
        phoneNumber = "+91 98765 43210",
        currency = CurrencyType.INR,
        principalAmountPaise = 250_000L,
        amountPaidPaise = 50_000L,
        remoteId = "remote_rec_201",
        counterpartyUserId = "uid_aarav_101"
    )

    private fun collectClickableNodes(root: SemanticsNode): List<SemanticsNode> {
        val out = mutableListOf<SemanticsNode>()
        fun walk(node: SemanticsNode) {
            if (node.config.contains(SemanticsActions.OnClick)) {
                out.add(node)
            }
            node.children.forEach { walk(it) }
        }
        walk(root)
        return out
    }

    private fun assertAllClickablesHaveTalkBackLabel(screenLabel: String) {
        val roots = composeRule.onAllNodes(isRoot(), useUnmergedTree = false).fetchSemanticsNodes()
        val clickables = roots.flatMap { collectClickableNodes(it) }
        assertTrue("$screenLabel must expose clickable semantics nodes for TalkBack", clickables.isNotEmpty())

        val unlabeled = clickables.filter { node ->
            val texts = node.config.getOrNull(SemanticsProperties.Text).orEmpty()
            val descs = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            val editable = node.config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
            val childHasLabel = node.children.any { child ->
                child.config.getOrNull(SemanticsProperties.Text).orEmpty().isNotEmpty() ||
                    child.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().isNotEmpty()
            }
            texts.isEmpty() && descs.isEmpty() && editable.isBlank() && !childHasLabel
        }
        val details = unlabeled.joinToString("; ") { node ->
            "tag=${node.config.getOrNull(SemanticsProperties.TestTag)} role=${node.config.getOrNull(SemanticsProperties.Role)}"
        }
        assertTrue(
            "$screenLabel has ${unlabeled.size} clickable nodes missing TalkBack text or contentDescription ($details)",
            unlabeled.isEmpty()
        )
    }

    private fun SemanticsNodeInteraction.assertHasAccessibleRole(expected: Role) {
        val role = fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role)
        assertTrue("Expected semantic Role.$expected but found $role", role == expected)
    }

    @Test
    fun homeScreen_talkBackSemanticsAndTouchTargets_atFontScale2x() {
        val tx = sampleTransaction()
        val summary = InterestCalculator.calculate(tx)
        val party = sampleParty()
        val uiState = UdhaarUiState(
            allCalculatedItems = listOf(summary),
            filteredItems = listOf(summary),
            allParties = listOf(party),
            isInitialLoading = false,
            requiresCloudVerificationGate = false
        )

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 2.0f)
            ) {
                UdhaarTheme(themeMode = AppThemeMode.EMERALD_FINANCE) {
                    DashboardScreen(
                        uiState = uiState,
                        onSwitchTab = {},
                        onSelectSummaryCurrency = {},
                        onToggleCurrencyEnabled = { _, _ -> },
                        onCycleTheme = {},
                        onSelectFilter = {},
                        onSearchQueryChange = {},
                        onPeopleSearchQueryChange = {},
                        onAddUdhaarClick = {},
                        onAddPartyClick = {},
                        onPartyClick = {},
                        onQuickAddLoanForParty = {},
                        onTransactionClick = {},
                        onWhatsAppClick = {},
                        onTransactionQrClick = {},
                        onGlobalQrClick = {},
                        onShareAppClick = {},
                        onLockAppClick = {},
                        onSettingsClick = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        assertAllClickablesHaveTalkBackLabel("HomeScreen@2.0x")

        composeRule.onNode(hasTestTag("open_settings_button"))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNode(hasTestTag("nav_tab_ledger"))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertHasAccessibleRole(Role.Tab)
        composeRule.onNode(hasTestTag("nav_tab_people_directory"))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertHasAccessibleRole(Role.Tab)
        composeRule.onNode(hasTestTag("nav_tab_history"))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .assertHasAccessibleRole(Role.Tab)
        composeRule.onNode(hasTestTag("privacy_hide_amounts_toggle"))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun recordScreen_talkBackSemanticsAndTouchTargets_atFontScale2x() {
        val party = sampleParty()

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 2.0f)
            ) {
                UdhaarTheme(themeMode = AppThemeMode.EMERALD_FINANCE) {
                    AddEditUdhaarScreen(
                        initialTransaction = null,
                        savedParties = listOf(party),
                        enabledCurrencies = listOf(CurrencyType.INR, CurrencyType.NPR),
                        defaultCurrency = CurrencyType.INR,
                        onSave = {},
                        onCancel = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        assertAllClickablesHaveTalkBackLabel("RecordScreen@2.0x")

        composeRule.onNode(hasTestTag("party_name_input")).assertExists()
        composeRule.onNode(hasTestTag("principal_amount_input")).assertExists()
        composeRule.onNode(hasTestTag("save_udhaar_button"))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNode(hasTestTag("cancel_udhaar_button"))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun peopleScreen_talkBackSemanticsAndTouchTargets_atFontScale2x() {
        val party = sampleParty()
        val tx = sampleTransaction()
        val summary = InterestCalculator.calculate(tx)
        val khata = PartyKhataSummary(
            party = party,
            transactions = listOf(summary),
            inrToReceivePaise = 200_000L,
            inrToPayPaise = 0L,
            nprToReceivePaise = 0L,
            nprToPayPaise = 0L
        )

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 2.0f)
            ) {
                UdhaarTheme(themeMode = AppThemeMode.EMERALD_FINANCE) {
                    PeopleDirectoryContent(
                        partyKhataList = listOf(khata),
                        enabledCurrencies = listOf(CurrencyType.INR),
                        searchQuery = "",
                        onSearchQueryChange = {},
                        onAddPartyClick = {},
                        onPartyClick = {},
                        onQuickAddLoanForParty = {},
                        unreadCountByPartyName = mapOf("Aarav Sharma" to 2)
                    )
                }
            }
        }

        composeRule.waitForIdle()
        assertAllClickablesHaveTalkBackLabel("PeopleScreen@2.0x")

        composeRule.onNode(hasTestTag("people_directory_lazy_list")).assertExists()
        composeRule.onNode(hasTestTag("party_card_${party.name}"))
            .assertIsDisplayed()
            .assertHasAccessibleRole(Role.Button)
    }

    @Test
    fun chatScreen_talkBackSemanticsSafetyControlsAndTouchTargets_atFontScale2x() {
        val tx = sampleTransaction()
        val summary = InterestCalculator.calculate(tx)
        val now = System.currentTimeMillis()
        val threadKey = "tx_${tx.id}"
        val metadata = ChatThreadMetadata(
            threadKey = threadKey,
            remoteThreadId = "tx_remote_rec_201",
            threadScope = ChatThreadScope.TRANSACTION,
            transactionId = tx.id,
            partyId = tx.partyId,
            partyName = tx.partyName,
            isMuted = false,
            isBlocked = false,
            isReported = false,
            updatedAtMillis = now
        )
        val userSafety = UserChatSafetyEntity(
            userKey = "uid:uid_aarav_101",
            counterpartyUid = "uid_aarav_101",
            partyId = tx.partyId,
            partyName = tx.partyName,
            isMuted = false,
            isBlocked = false,
            isReported = false,
            updatedAtMillis = now
        )
        val messages = listOf(
            ChatMessageEntity(
                id = 1L,
                threadKey = threadKey,
                transactionId = tx.id,
                partyId = tx.partyId,
                senderName = "Aarav Sharma",
                isFromMe = false,
                messageType = ChatMessageType.TEXT,
                bodyText = "Will send ₹2,000 by Friday evening.",
                sentAtMillis = now - 60_000L
            ),
            ChatMessageEntity(
                id = 2L,
                threadKey = threadKey,
                transactionId = tx.id,
                partyId = tx.partyId,
                senderName = "You",
                isFromMe = true,
                messageType = ChatMessageType.TEXT,
                bodyText = "Thanks, noted!",
                sentAtMillis = now - 30_000L
            )
        )

        var overflowClicked = false

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 2.0f)
            ) {
                UdhaarTheme(themeMode = AppThemeMode.EMERALD_FINANCE) {
                    ChatThreadBottomSheet(
                        threadKey = threadKey,
                        scope = ChatThreadScope.TRANSACTION,
                        partyName = tx.partyName,
                        partyId = tx.partyId,
                        partyPhone = tx.phoneNumber,
                        transactionSummary = summary,
                        threadMetadata = metadata,
                        messages = messages,
                        auditEntries = emptyList(),
                        myUpiId = "me@upi",
                        isCloudLinked = true,
                        userSafety = userSafety,
                        onDismiss = {},
                        onSendMessage = { _, _, _, _, _ -> },
                        onRequestExtension = { _, _, _ -> },
                        onRespondToExtension = { _, _ -> },
                        onToggleMute = { overflowClicked = true },
                        onToggleBlock = {},
                        onReportAbusiveUser = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        assertAllClickablesHaveTalkBackLabel("ChatScreen@2.0x")

        composeRule.onNode(hasTestTag("chat_overflow_menu_button"))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        composeRule.waitForIdle()

        // Safety menu items for both thread and user are exposed and tappable
        composeRule.onNode(hasTestTag("chat_menu_mute_thread_item")).assertExists()
        composeRule.onNode(hasTestTag("chat_menu_block_item")).assertExists()
        composeRule.onNode(hasTestTag("chat_menu_report_thread_item")).assertExists()
        composeRule.onNode(hasTestTag("chat_menu_mute_user_item")).assertExists()
        composeRule.onNode(hasTestTag("chat_menu_block_user_item")).assertExists()
        composeRule.onNode(hasTestTag("chat_menu_report_item")).assertExists()

        composeRule.onNode(hasTestTag("chat_menu_mute_thread_item")).performClick()
        composeRule.waitForIdle()
        assertTrue("Thread mute menu action should invoke callback", overflowClicked)
    }
}
