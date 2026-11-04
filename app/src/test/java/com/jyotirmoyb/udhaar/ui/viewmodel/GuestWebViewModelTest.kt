package com.jyotirmoyb.udhaar.ui.viewmodel

import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.cloud.GuestWebCloudRepository
import com.jyotirmoyb.udhaar.data.repository.FakeUdhaarDao
import com.jyotirmoyb.udhaar.data.repository.GuestWebRepository
import com.jyotirmoyb.udhaar.data.repository.InstallmentRepository
import com.jyotirmoyb.udhaar.data.repository.PaymentRepository
import com.jyotirmoyb.udhaar.data.repository.SettingsRepository
import com.jyotirmoyb.udhaar.data.repository.VerificationRepository
import com.jyotirmoyb.udhaar.ui.state.AppStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class GuestWebViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeDao: FakeUdhaarDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var installmentRepository: InstallmentRepository
    private lateinit var guestWebRepository: GuestWebRepository
    private lateinit var verificationRepository: VerificationRepository
    private lateinit var appStateHolder: AppStateHolder
    private lateinit var viewModel: VerificationViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeUdhaarDao()
        settingsRepository = SettingsRepository(null, null)
        installmentRepository = InstallmentRepository(fakeDao, settingsRepository, null)
        paymentRepository = PaymentRepository(fakeDao, settingsRepository, null, installmentRepository)
        verificationRepository = VerificationRepository(fakeDao, settingsRepository, null, null)
        guestWebRepository = GuestWebRepository(
            dao = fakeDao,
            settingsRepository = settingsRepository,
            paymentRepository = paymentRepository,
            installmentRepository = installmentRepository,
            guestWebCloudRepository = GuestWebCloudRepository(null, null, null),
            context = null
        )
        appStateHolder = AppStateHolder()
        viewModel = VerificationViewModel(
            verificationRepository = verificationRepository,
            guestWebRepository = guestWebRepository,
            paymentRepository = paymentRepository,
            settingsRepository = settingsRepository,
            appStateHolder = appStateHolder
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun webShareModal_openAndClose_updatesUiState() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.openWebShareModal(123L)
        advanceUntilIdle()
        assertEquals(123L, viewModel.uiState.value.webShareTransactionId)

        viewModel.closeWebShareModal()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.webShareTransactionId)
        assertNull(viewModel.uiState.value.webShareResult)
    }

    @Test
    fun updatesScreen_openAndClose_updatesUiState() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        viewModel.openUpdatesScreen()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showUpdatesScreen)

        viewModel.closeUpdatesScreen()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showUpdatesScreen)
    }

    @Test
    fun generateWebLink_executesAndStoresResultInState() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(
                partyName = "Pooja",
                principalAmount = 2500.0,
                currency = CurrencyType.INR,
                transactionType = TransactionType.GIVEN
            )
        )

        viewModel.openWebShareModal(txId)
        viewModel.generateWebLink(
            transactionId = txId,
            expiryDays = 30,
            trustLevel = "WHATSAPP_OTP",
            allowNote = true,
            allowAttachments = false,
            showUpi = true,
            allowedActions = listOf("view", "confirm", "pay_claim")
        )
        advanceUntilIdle()

        val res = viewModel.uiState.value.webShareResult
        assertNotNull(res)
        assertTrue(res!!.success)
        assertTrue(res.url.startsWith("https://udhaar.jyotirmoyb.com/u/r/"))
    }

    @Test
    fun confirmPaymentClaim_updatesTransactionAndClearsPending() = runTest(testDispatcher) {
        val claimJson = JSONObject().apply {
            put("amount", 1200.0)
            put("amountDouble", 1200.0)
            put("paymentMode", "UPI")
            put("reference", "REF123456")
        }.toString()

        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(
                partyName = "Aman",
                principalAmount = 3000.0,
                pendingPaymentClaim = claimJson
            )
        )

        viewModel.confirmPaymentClaim(txId)
        advanceUntilIdle()

        val updated = fakeDao.getTransactionById(txId)!!
        assertEquals("", updated.pendingPaymentClaim)
        assertEquals("PAYMENT_CONFIRMED", updated.lastGuestAction)
        assertEquals(1200.0, updated.amountPaid, 0.01)
    }

    @Test
    fun rejectPaymentClaim_marksRejected() = runTest(testDispatcher) {
        val claimJson = JSONObject().apply {
            put("amount", 500.0)
            put("amountDouble", 500.0)
            put("paymentMode", "CASH")
        }.toString()

        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(
                partyName = "Aman",
                principalAmount = 1000.0,
                pendingPaymentClaim = claimJson
            )
        )

        viewModel.rejectPaymentClaim(txId, "No cash handed over")
        advanceUntilIdle()

        val updated = fakeDao.getTransactionById(txId)!!
        assertEquals("", updated.pendingPaymentClaim)
        assertEquals("PAYMENT_REJECTED", updated.lastGuestAction)
    }

    @Test
    fun acceptDateProposal_updatesDueDate() = runTest(testDispatcher) {
        val futureDate = System.currentTimeMillis() + 86400000L * 7
        val proposalJson = JSONObject().apply {
            put("proposedDateMillis", futureDate)
        }.toString()

        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(
                partyName = "Karan",
                principalAmount = 4000.0,
                pendingDateProposal = proposalJson
            )
        )

        viewModel.acceptDateProposal(txId)
        advanceUntilIdle()

        val updated = fakeDao.getTransactionById(txId)!!
        assertEquals(futureDate, updated.expiryDateMillis)
        assertEquals("", updated.pendingDateProposal)
        assertEquals("DATE_ACCEPTED", updated.lastGuestAction)
    }

    @Test
    fun declineDateProposal_marksDeclined() = runTest(testDispatcher) {
        val proposalJson = JSONObject().apply {
            put("proposedDateMillis", System.currentTimeMillis() + 86400000L * 10)
        }.toString()

        val txId = fakeDao.insertTransaction(
            UdhaarTransaction(
                partyName = "Karan",
                principalAmount = 4000.0,
                pendingDateProposal = proposalJson
            )
        )

        viewModel.declineDateProposal(txId, "Cannot extend further")
        advanceUntilIdle()

        val updated = fakeDao.getTransactionById(txId)!!
        assertEquals("", updated.pendingDateProposal)
        assertEquals("DATE_DECLINED", updated.lastGuestAction)
    }
}
