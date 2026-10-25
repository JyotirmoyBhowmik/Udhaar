package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.CurrencyType
import com.example.data.PartialPayment
import com.example.data.PartyEntity
import com.example.data.TransactionType
import com.example.data.UdhaarTransaction
import com.example.util.LedgerBackupManager
import com.example.util.UpiQrGenerator
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
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Udhaar", appName)
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
    fun `ledger backup json roundtrip preserves parties transactions and payments`() {
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
    }
}
