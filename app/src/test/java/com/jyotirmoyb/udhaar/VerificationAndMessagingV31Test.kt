package com.jyotirmoyb.udhaar

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.jyotirmoyb.udhaar.data.ChatMessageEntity
import com.jyotirmoyb.udhaar.data.ChatMessageType
import com.jyotirmoyb.udhaar.data.ChatThreadScope
import com.jyotirmoyb.udhaar.data.CompoundFrequency
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.InterestRatePeriod
import com.jyotirmoyb.udhaar.data.InterestType
import com.jyotirmoyb.udhaar.data.LedgerFilter
import com.jyotirmoyb.udhaar.data.MessageDeliveryStatus
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.VerificationStatus
import com.jyotirmoyb.udhaar.data.cloud.CloudChatMessage
import com.jyotirmoyb.udhaar.data.cloud.CloudChatThread
import com.jyotirmoyb.udhaar.data.cloud.CloudSharedRecord
import com.jyotirmoyb.udhaar.data.cloud.GuestLinkFetchResult
import com.jyotirmoyb.udhaar.data.cloud.GuestWebCloudRepository
import com.jyotirmoyb.udhaar.data.cloud.VerificationCloudRepository
import com.jyotirmoyb.udhaar.data.repository.ChatRepository
import com.jyotirmoyb.udhaar.data.repository.FakeUdhaarDao
import com.jyotirmoyb.udhaar.data.repository.GuestWebRepository
import com.jyotirmoyb.udhaar.data.repository.InstallmentRepository
import com.jyotirmoyb.udhaar.data.repository.LedgerRepository
import com.jyotirmoyb.udhaar.data.repository.PaymentRepository
import com.jyotirmoyb.udhaar.data.repository.SettingsRepository
import com.jyotirmoyb.udhaar.data.repository.VerificationRepository
import com.jyotirmoyb.udhaar.ui.CurrencyLedgerTotals
import com.jyotirmoyb.udhaar.ui.components.ChatThreadBottomSheet
import com.jyotirmoyb.udhaar.ui.components.CloudVerificationGateScreen
import com.jyotirmoyb.udhaar.ui.components.IncomingVerificationReviewSheet
import com.jyotirmoyb.udhaar.ui.components.TopLedgerSummaryCard
import com.jyotirmoyb.udhaar.ui.sheets.RecordDetailSheet
import com.jyotirmoyb.udhaar.ui.sheets.settings.SparkAuthController
import com.jyotirmoyb.udhaar.ui.sheets.settings.SparkAuthSession
import com.jyotirmoyb.udhaar.ui.state.AppStateHolder
import com.jyotirmoyb.udhaar.ui.theme.UdhaarTheme
import com.jyotirmoyb.udhaar.ui.viewmodel.VerificationViewModel
import com.jyotirmoyb.udhaar.util.ChatSyncHelper
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.VerificationCryptoHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VerificationAndMessagingV31Test {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun parseGuestWebLink_supportsHashKVariants_andStripsWhatsAppPunctuationPreservingBase64Url() {
        val secretWithUrlSafeChars = "AbCdEf_123-456_XyZ"

        val slashHash = VerificationCryptoHelper.parseGuestWebLink(
            "https://udhaar.jyotirmoyb.com/u/#k=$secretWithUrlSafeChars."
        )
        assertNotNull(slashHash)
        assertEquals(secretWithUrlSafeChars, slashHash!!.secret)
        assertEquals(VerificationCryptoHelper.sha256Hex(secretWithUrlSafeChars), slashHash.linkId)

        val noSlashHash = VerificationCryptoHelper.parseGuestWebLink(
            "https://udhaar.jyotirmoyb.com/u#k=$secretWithUrlSafeChars&utm=wa),"
        )
        assertNotNull(noSlashHash)
        assertEquals(secretWithUrlSafeChars, noSlashHash!!.secret)

        // parseVerificationLink must NOT hijack /u/#k= links
        assertNull(
            VerificationCryptoHelper.parseVerificationLink(
                "https://udhaar.jyotirmoyb.com/u/#k=$secretWithUrlSafeChars"
            )
        )
        assertNull(
            VerificationCryptoHelper.parseVerificationLink(
                "https://udhaar.jyotirmoyb.com/u#k=$secretWithUrlSafeChars"
            )
        )
    }

    @Test
    fun fetchGuestLinkBySecret_signedOutOrBlankFallsClosedOrBrowser() = runTest {
        val signedOutRepo = GuestWebCloudRepository(
            authOverride = null,
            firestoreOverride = null
        )
        val signedOutResult = signedOutRepo.fetchGuestLinkBySecret("valid_secret_token_123")
        assertTrue(signedOutResult is GuestLinkFetchResult.FallbackToBrowser)

        val blankResult = signedOutRepo.fetchGuestLinkBySecret("   ")
        assertTrue(blankResult is GuestLinkFetchResult.InvalidOrExpired)
    }

    @Test
    fun verificationViewModel_handlesHashKDeepLink_browserFallback_expiredError_andSubmitConfirm() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val dao = FakeUdhaarDao()
            val settingsRepo = SettingsRepository(context = null, cloudRepository = null)
            settingsRepo.setCloudAccountState(true, "uid_receiver", "receiver@example.com", "Receiver Priya")
            val appState = AppStateHolder()
            val paymentRepo = PaymentRepository(dao, settingsRepo, null, InstallmentRepository(dao, settingsRepo, null))
            val verificationRepo = VerificationRepository(dao, settingsRepo, null, null)

            var fetchMode = "SUCCESS"
            var submittedAction = ""
            var submittedLinkId = ""

            val samplePayload = VerificationCryptoHelper.SharedVerificationPayload(
                remoteId = "rec_guest_99",
                shareToken = "secret_tok_99",
                creatorUserId = "uid_sender",
                creatorRole = TransactionType.GIVEN,
                lenderName = "Sender Rahul",
                borrowerName = "Receiver Priya",
                creatorPhone = "+919876543210",
                counterpartyPhone = "",
                principalAmount = 1500.0,
                currencyCode = "INR",
                startDateMillis = 1_700_000_000_000L,
                expiryDateMillis = null,
                isInterestEnabled = false,
                interestType = InterestType.SIMPLE,
                interestRate = 0.0,
                interestRatePeriod = InterestRatePeriod.PER_MONTH,
                compoundFrequency = CompoundFrequency.MONTHLY,
                contentHash = "hash99",
                guestLinkId = VerificationCryptoHelper.sha256Hex("secret_tok_99"),
                guestLinkSecret = "secret_tok_99",
                guestOwnerUid = "uid_sender"
            )

            val fakeCloud = object : GuestWebCloudRepository(null, null, null) {
                override suspend fun fetchGuestLinkBySecret(secret: String, fallbackRecordId: String): GuestLinkFetchResult {
                    return when (fetchMode) {
                        "SUCCESS" -> GuestLinkFetchResult.Success(samplePayload)
                        "FALLBACK" -> GuestLinkFetchResult.FallbackToBrowser("Cloud offline")
                        else -> GuestLinkFetchResult.InvalidOrExpired("This verification link has expired.")
                    }
                }

                override suspend fun submitGuestActionFromApp(linkId: String, actionType: String, reason: String): Result<Unit> {
                    submittedLinkId = linkId
                    submittedAction = actionType
                    return Result.success(Unit)
                }
            }

            val guestWebRepo = GuestWebRepository(
                dao = dao,
                settingsRepository = settingsRepo,
                paymentRepository = paymentRepo,
                installmentRepository = InstallmentRepository(dao, settingsRepo, null),
                guestWebCloudRepository = fakeCloud,
                context = null
            )

            val vm = VerificationViewModel(
                verificationRepository = verificationRepo,
                guestWebRepository = guestWebRepo,
                paymentRepository = paymentRepo,
                settingsRepository = settingsRepo,
                appStateHolder = appState
            )

            // 1. Success opens IncomingVerificationReviewSheet & Confirm saves mirrored Khata + submits CONFIRM to Firestore
            assertTrue(vm.handleIncomingDeepLinkOrToken("https://udhaar.jyotirmoyb.com/u#k=secret_tok_99!"))
            advanceUntilIdle()
            assertNotNull(vm.incomingVerificationPayload.value)

            vm.confirmOrDisputeIncomingVerification(confirm = true)
            advanceUntilIdle()
            assertNull(vm.incomingVerificationPayload.value)
            assertEquals("CONFIRM", submittedAction)
            assertEquals(VerificationCryptoHelper.sha256Hex("secret_tok_99"), submittedLinkId)
            val savedTx = dao.getTransactionByRemoteId("rec_guest_99")
            assertNotNull(savedTx)
            assertEquals(VerificationStatus.CONFIRMED, savedTx!!.verificationStatus)
            assertEquals("uid_sender", savedTx.counterpartyUserId)

            // 2. Fallback opens external browser instead of Dashboard silently
            fetchMode = "FALLBACK"
            assertTrue(vm.handleIncomingDeepLinkOrToken("https://udhaar.jyotirmoyb.com/u/#k=secret_tok_99"))
            advanceUntilIdle()
            assertEquals("https://udhaar.jyotirmoyb.com/u/#k=secret_tok_99", appState.externalBrowserFallbackUrl.value)

            // 3. Invalid/Expired surfaces clear status error
            fetchMode = "EXPIRED"
            assertTrue(vm.handleIncomingDeepLinkOrToken("https://udhaar.jyotirmoyb.com/u/#k=expired_tok"))
            advanceUntilIdle()
            assertEquals("This verification link has expired.", appState.deepLinkStatusMessage.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun youPartyNameBug_replacedWithRealProfileNameInRecordsAndShareLinks() = runTest {
        val dao = FakeUdhaarDao()
        val settingsRepo = SettingsRepository(context = null, cloudRepository = null)
        settingsRepo.setCloudAccountState(
            consentGranted = true,
            userId = "uid_alice",
            email = "alice.sharma@example.com",
            displayName = "Alice Sharma"
        )
        val ledgerRepo = LedgerRepository(dao, settingsRepo, cloudRepository = null, context = null)
        val verificationRepo = VerificationRepository(dao, settingsRepo, cloudRepository = null, context = null)

        val savedId = ledgerRepo.saveTransaction(
            UdhaarTransaction(
                transactionType = TransactionType.GIVEN,
                partyName = "Rohan",
                principalAmountPaise = 50_000L,
                lenderName = "You",
                borrowerName = "Rohan"
            )
        )
        val savedTx = dao.getTransactionById(savedId)!!
        assertEquals("Alice Sharma", savedTx.lenderName)
        assertEquals("Rohan", savedTx.borrowerName)
        val audits = dao.getAuditTrailForTransactionSnapshot(savedId)
        assertFalse(audits.first().actorName.equals("You", ignoreCase = true))
        assertEquals("Alice Sharma", audits.first().actorName)

        val legacyWithYou = savedTx.copy(lenderName = "You")
        val (sharedTx, link) = verificationRepo.prepareVerificationShare(legacyWithYou)
        assertEquals("Alice Sharma", sharedTx.lenderName)
        val parsed = VerificationCryptoHelper.parseVerificationLink(link)!!
        assertEquals("Alice Sharma", parsed.lenderName)
    }

    @Test
    fun incomingVerificationReviewSheet_allowsEditingWhenSenderIsYou_andSavesConfirmedName() {
        val payloadWithYou = VerificationCryptoHelper.SharedVerificationPayload(
            remoteId = "rec_you_test",
            shareToken = "sec_123",
            creatorUserId = "creator_uid_9",
            creatorRole = TransactionType.GIVEN,
            lenderName = "You",
            borrowerName = "Me",
            creatorPhone = "+919876543210",
            counterpartyPhone = "",
            principalAmount = 1200.0,
            currencyCode = "INR",
            startDateMillis = System.currentTimeMillis(),
            expiryDateMillis = null,
            isInterestEnabled = false,
            interestType = InterestType.SIMPLE,
            interestRate = 0.0,
            interestRatePeriod = InterestRatePeriod.PER_MONTH,
            compoundFrequency = CompoundFrequency.MONTHLY,
            contentHash = "hash123"
        )
        var confirmedOverride = ""

        composeRule.setContent {
            UdhaarTheme {
                IncomingVerificationReviewSheet(
                    payload = payloadWithYou,
                    onDismiss = {},
                    onConfirmRecord = { overrideName -> confirmedOverride = overrideName },
                    onDisputeRecord = { _, _ -> }
                )
            }
        }

        // Rejects empty/placeholder name first
        composeRule.waitForIdle()
        composeRule.onNode(hasTestTag("confirm_incoming_verification_btn"))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertEquals("", confirmedOverride)

        composeRule.onNode(hasTestTag("incoming_counterparty_name_input"))
            .performScrollTo()
            .performTextInput("Vikram Patel")
        composeRule.waitForIdle()
        composeRule.onNode(hasTestTag("confirm_incoming_verification_btn"))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertEquals("Vikram Patel", confirmedOverride)
    }

    @Test
    fun realTimeChat_deterministicThreadIds_syncsIncomingMessages_andShowsHonestLocalStatus() = runTest {
        assertEquals(
            "tx_rec_42",
            ChatSyncHelper.cloudThreadIdFor(ChatThreadScope.TRANSACTION, "rec_42", "Rohan", "tx_1")
        )
        val dmAB = ChatSyncHelper.cloudThreadIdFor(
            ChatThreadScope.PARTY_GENERAL, "", "Rohan", "party_rohan", myUid = "uid_z", counterpartyUid = "uid_a"
        )
        val dmBA = ChatSyncHelper.cloudThreadIdFor(
            ChatThreadScope.PARTY_GENERAL, "", "Rohan", "party_rohan", myUid = "uid_a", counterpartyUid = "uid_z"
        )
        assertEquals("dm_uid_a_uid_z", dmAB)
        assertEquals(dmAB, dmBA)

        val dao = FakeUdhaarDao()
        val settingsRepo = SettingsRepository(context = null, cloudRepository = null)
        settingsRepo.setCloudAccountState(true, "uid_alice", "alice@example.com", "Alice")
        val fakeCloud = FakeRealtimeChatCloudRepository(
            incomingThread = CloudChatThread(
                threadId = "tx_rec_local",
                scope = "TRANSACTION",
                remoteId = "rec_local",
                creatorUid = "uid_alice",
                counterpartyUid = "uid_bob",
                participantUids = listOf("uid_alice", "uid_bob"),
                typingUserId = "uid_bob",
                typingUpdatedAtMillis = System.currentTimeMillis()
            ),
            incomingMessages = listOf(
                CloudChatMessage(
                    messageId = "msg_remote_bob_1",
                    threadId = "tx_rec_local",
                    remoteId = "rec_local",
                    senderUid = "uid_bob",
                    senderName = "Bob",
                    participantUids = listOf("uid_alice", "uid_bob"),
                    messageType = "TEXT",
                    textContent = "Paid via UPI today",
                    deliveryStatus = "SENT",
                    sentAtMillis = System.currentTimeMillis()
                )
            )
        )
        val chatRepo = ChatRepository(dao, settingsRepo, cloudRepository = fakeCloud, context = null)

        val localTxId = dao.insertTransaction(
            UdhaarTransaction(
                transactionType = TransactionType.GIVEN,
                partyName = "Unlinked Friend",
                principalAmountPaise = 20_000L,
                remoteId = "",
                counterpartyUserId = ""
            )
        )
        val unlinkedMsg = chatRepo.sendChatMessage(
            threadKey = "tx_$localTxId",
            scope = ChatThreadScope.TRANSACTION,
            transactionId = localTxId,
            partyName = "Unlinked Friend",
            textContent = "Hi there"
        )!!
        assertEquals(MessageDeliveryStatus.SENT, unlinkedMsg.deliveryStatus)

        val cloudTxId = dao.insertTransaction(
            UdhaarTransaction(
                transactionType = TransactionType.GIVEN,
                partyName = "Bob",
                principalAmountPaise = 40_000L,
                remoteId = "rec_local",
                counterpartyUserId = ""
            )
        )
        chatRepo.observeAndSyncCloudThread(
            threadKey = "tx_$cloudTxId",
            scope = ChatThreadScope.TRANSACTION,
            transactionId = cloudTxId,
            remoteId = "rec_local",
            partyId = null,
            partyName = "Bob"
        ).collect { }

        val syncedMsg = dao.getChatMessageByRemoteId("msg_remote_bob_1")
        assertNotNull(syncedMsg)
        assertEquals("Paid via UPI today", syncedMsg!!.bodyText)
        assertFalse(syncedMsg.isFromMe)
        val updatedTx = dao.getTransactionById(cloudTxId)!!
        assertEquals("uid_bob", updatedTx.counterpartyUserId)
    }

    @Test
    fun chatThreadBottomSheet_whenUnlinked_showsNoticeAndSavedLocallyInsteadOfDelivered() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val outgoingLocal = ChatMessageEntity(
            id = 1L,
            remoteMessageId = "m1",
            threadKey = "tx_1",
            threadScope = ChatThreadScope.TRANSACTION,
            transactionId = 1L,
            partyName = "Rohan",
            senderUserId = "local_me",
            senderName = "Alice",
            isFromMe = true,
            messageType = ChatMessageType.TEXT,
            bodyText = "Testing local status",
            deliveryStatus = MessageDeliveryStatus.SENT
        )

        composeRule.setContent {
            UdhaarTheme {
                ChatThreadBottomSheet(
                    threadKey = "tx_1",
                    scope = ChatThreadScope.TRANSACTION,
                    partyName = "Rohan",
                    partyId = null,
                    partyPhone = "",
                    transactionSummary = null,
                    threadMetadata = null,
                    messages = listOf(outgoingLocal),
                    auditEntries = emptyList(),
                    myUpiId = "",
                    isCloudLinked = false,
                    onDismiss = {},
                    onSendMessage = { _, _, _, _, _ -> },
                    onRequestExtension = { _, _, _ -> },
                    onRespondToExtension = { _, _ -> },
                    onToggleMute = {},
                    onToggleBlock = {},
                    onReportAbusiveUser = {}
                )
            }
        }

        composeRule.onNode(hasTestTag("chat_local_only_notice_banner")).assertIsDisplayed()
        composeRule.onNode(hasText(context.getString(R.string.chat_status_local_only))).assertExists()
        composeRule.onNode(hasText("Delivered", substring = true)).assertDoesNotExist()
    }

    @Test
    fun cloudVerificationGateScreen_showsCloudVerificationFirst_allowsSkipForLocalOnly_andHides100UserCap() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dummyAuth = object : SparkAuthController {
            override fun currentSession(): SparkAuthSession? = null
            override suspend fun signInWithGoogle(context: Context) = throw UnsupportedOperationException()
            override suspend fun signUpWithEmailPassword(email: String, password: String, contactPhone: String) =
                throw UnsupportedOperationException()
            override suspend fun signInWithEmailPassword(email: String, password: String) =
                throw UnsupportedOperationException()
            override suspend fun sendEmailVerification() {}
            override suspend fun reloadSession(): SparkAuthSession? = null
            override suspend fun sendPasswordResetEmail(email: String) {}
            override suspend fun reauthenticateWithPassword(email: String, password: String) {}
            override suspend fun reauthenticateWithGoogle(context: Context) {}
            override suspend fun deleteCloudData(uid: String, knownLinkIds: List<String>) {}
            override suspend fun signOut() {}
        }

        var skipped = false
        composeRule.setContent {
            UdhaarTheme {
                CloudVerificationGateScreen(
                    isFirebaseConfigured = true,
                    isSignedInUnverified = false,
                    pendingEmail = "",
                    onVerifiedSession = { _, _, _ -> },
                    onSignedOut = {},
                    onSkipLocalOnly = { skipped = true },
                    authControllerOverride = dummyAuth
                )
            }
        }

        composeRule.onNode(hasTestTag("cloud_verification_gate_screen")).assertExists()
        assertFalse(
            "Default BuildConfig.REQUIRE_CLOUD_VERIFICATION must be false",
            BuildConfig.REQUIRE_CLOUD_VERIFICATION
        )
        composeRule.onNode(hasText(context.getString(R.string.cloud_gate_optional_title))).assertExists()
        composeRule.onNode(hasText("100", substring = true)).assertDoesNotExist()
        composeRule.onNode(hasTestTag("cloud_gate_skip_local_btn")).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue("Skip button should invoke onSkipLocalOnly", skipped)
    }

    @Test
    fun cloudVerificationGateScreen_whenCloudVerificationRequired_hidesSkipAndShowsRequiredTitle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dummyAuth = object : SparkAuthController {
            override fun currentSession(): SparkAuthSession? = null
            override suspend fun signInWithGoogle(context: Context): SparkAuthSession =
                throw UnsupportedOperationException()
            override suspend fun signInWithEmailPassword(email: String, password: String): SparkAuthSession =
                throw UnsupportedOperationException()
            override suspend fun signUpWithEmailPassword(email: String, password: String, contactPhone: String): SparkAuthSession =
                throw UnsupportedOperationException()
            override suspend fun sendEmailVerification() {}
            override suspend fun reloadSession(): SparkAuthSession? = null
            override suspend fun sendPasswordResetEmail(email: String) {}
            override suspend fun reauthenticateWithPassword(email: String, password: String) {}
            override suspend fun reauthenticateWithGoogle(context: Context) {}
            override suspend fun deleteCloudData(uid: String, knownLinkIds: List<String>) {}
            override suspend fun signOut() {}
        }

        composeRule.setContent {
            UdhaarTheme {
                CloudVerificationGateScreen(
                    isFirebaseConfigured = true,
                    isSignedInUnverified = false,
                    pendingEmail = "",
                    onVerifiedSession = { _, _, _ -> },
                    onSignedOut = {},
                    onSkipLocalOnly = {},
                    allowLocalSkip = false,
                    authControllerOverride = dummyAuth
                )
            }
        }

        composeRule.onNode(hasTestTag("cloud_verification_gate_screen")).assertExists()
        composeRule.onNode(hasText(context.getString(R.string.cloud_gate_title))).assertExists()
        composeRule.onNode(hasTestTag("cloud_gate_skip_local_btn")).assertDoesNotExist()
    }

    @Test
    @org.robolectric.annotation.Config(sdk = [36], qualifiers = "w360dp-h640dp-normal-long-port-hdpi")
    fun summaryCards_singleTapTogglesFilter_doubleTapAddsCategory_andAmountsFit() {
        var doubleTappedType by mutableStateOf<TransactionType?>(null)
        composeRule.setContent {
            var selectedFilter by remember { mutableStateOf(LedgerFilter.ALL) }
            UdhaarTheme {
                TopLedgerSummaryCard(
                    selectedCurrency = CurrencyType.INR,
                    enabledCurrencies = listOf(CurrencyType.INR),
                    totals = CurrencyLedgerTotals(
                        currency = CurrencyType.INR,
                        totalToReceive = 12_500_000.0,
                        totalToPay = 8_900_000.0,
                        activeGivenCount = 4,
                        activeTakenCount = 2
                    ),
                    inrCount = 6,
                    nprCount = 0,
                    selectedFilter = selectedFilter,
                    onSelectCurrency = {},
                    onSelectReceivableFilter = {
                        selectedFilter = if (selectedFilter == LedgerFilter.GIVEN) LedgerFilter.ALL else LedgerFilter.GIVEN
                    },
                    onSelectPayableFilter = {
                        selectedFilter = if (selectedFilter == LedgerFilter.TAKEN) LedgerFilter.ALL else LedgerFilter.TAKEN
                    },
                    onDoubleTapReceivable = { doubleTappedType = TransactionType.GIVEN },
                    onDoubleTapPayable = { doubleTappedType = TransactionType.TAKEN }
                )
            }
        }

        composeRule.onNode(hasTestTag("summary_total_to_receive"), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNode(hasTestTag("summary_total_to_pay"), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNode(hasTestTag("summary_receivable_card")).assertIsNotSelected()

        composeRule.onNode(hasTestTag("summary_receivable_card")).performClick()
        composeRule.mainClock.advanceTimeBy(450L)
        composeRule.waitForIdle()
        composeRule.onNode(hasTestTag("summary_receivable_card")).assertIsSelected()
        composeRule.onNode(hasTestTag("summary_receivable_card_selected_dot"), useUnmergedTree = true).assertExists()

        composeRule.onNode(hasTestTag("summary_receivable_card")).performClick()
        composeRule.mainClock.advanceTimeBy(450L)
        composeRule.waitForIdle()
        composeRule.onNode(hasTestTag("summary_receivable_card")).assertIsNotSelected()

        composeRule.onNode(hasTestTag("summary_payable_card")).performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        assertEquals(TransactionType.TAKEN, doubleTappedType)

        composeRule.onNode(hasTestTag("summary_receivable_card")).performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        assertEquals(TransactionType.GIVEN, doubleTappedType)
    }

    @Test
    fun recordDetailSheet_simplifiedLayout_rendersPrimaryActionsWithoutDuplicateEdit() {
        val tx = UdhaarTransaction(
            id = 55L,
            transactionType = TransactionType.GIVEN,
            partyName = "A Very Long Counterparty Name That Scrolls Continuously Without Clipping",
            phoneNumber = "+919876543210",
            currency = CurrencyType.INR,
            principalAmountPaise = 500_000L,
            amountPaidPaise = 100_000L,
            paymentMode = PaymentMode.UPI
        )
        val summary = InterestCalculator.calculate(tx)

        composeRule.setContent {
            UdhaarTheme {
                RecordDetailSheet(
                    summary = summary,
                    partialPayments = emptyList(),
                    onDismiss = {},
                    onRecordPartialPayment = { _, _, _ -> },
                    onDeletePartialPayment = {},
                    onToggleSettled = {},
                    onEdit = {},
                    onDeleteTransaction = {},
                    onOpenQr = {},
                    onOpenWhatsApp = {},
                    selfDisplayName = "Rohan Das"
                )
            }
        }

        composeRule.onNode(hasTestTag("header_edit_transaction_button")).assertExists()
        composeRule.onNode(hasTestTag("header_receipt_transaction_button")).assertExists()
        composeRule.onNode(hasTestTag("toggle_partial_payment_form_button")).assertExists()
        composeRule.onNode(hasTestTag("detail_open_chat_button")).assertExists()
        composeRule.onNode(hasTestTag("toggle_settled_button")).assertExists()
        composeRule.onNode(hasTestTag("detail_verification_section")).assertExists()

        composeRule.onNode(hasTestTag("detail_more_options_toggle")).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasTestTag("delete_transaction_button")).assertExists()
        composeRule.onNode(hasTestTag("edit_transaction_button")).assertDoesNotExist()
    }

    private class FakeRealtimeChatCloudRepository(
        private val incomingThread: CloudChatThread?,
        private val incomingMessages: List<CloudChatMessage>
    ) : VerificationCloudRepository(rawDb = null) {
        override val isAvailable: Boolean = true
        override suspend fun upsertChatThread(thread: CloudChatThread) {}
        override suspend fun sendChatMessage(message: CloudChatMessage) {}
        override suspend fun updateChatMessageDeliveryOrExtension(
            threadId: String,
            messageId: String,
            deliveryStatus: String,
            deliveredAtMillis: Long,
            readAtMillis: Long,
            extensionStatus: String
        ) {}
        override fun observeSharedRecord(remoteId: String): Flow<CloudSharedRecord?> = flowOf(null)
        override fun observeChatThread(threadId: String): Flow<CloudChatThread?> = flowOf(incomingThread)
        override fun observeChatMessages(threadId: String, participantUid: String): Flow<List<CloudChatMessage>> =
            flowOf(incomingMessages)
    }
}
