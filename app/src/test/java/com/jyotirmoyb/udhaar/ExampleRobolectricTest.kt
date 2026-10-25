package com.jyotirmoyb.udhaar

import android.content.Context
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
        assertEquals("1.1.0", BuildConfig.VERSION_NAME)
        assertEquals(2, BuildConfig.VERSION_CODE)
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
}
