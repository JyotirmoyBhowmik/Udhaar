package com.jyotirmoyb.udhaar

import com.jyotirmoyb.udhaar.data.CompoundFrequency
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.InterestRatePeriod
import com.jyotirmoyb.udhaar.data.InterestType
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode
import com.jyotirmoyb.udhaar.ui.theme.paletteForTheme
import com.jyotirmoyb.udhaar.util.InterestCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun simpleInterestAndPartialPayment_calculatesCorrectNetBalance() {
        val now = 1_700_000_000_000L
        val days365Millis = (365.25 * 86_400_000L).toLong()
        val tx = UdhaarTransaction(
            id = 1L,
            transactionType = TransactionType.GIVEN,
            partyName = "Aarav Thapa",
            currency = CurrencyType.NPR,
            principalAmount = 10_000.0,
            amountPaid = 2_000.0,
            paymentMode = PaymentMode.CASH,
            isInterestEnabled = true,
            interestType = InterestType.SIMPLE,
            interestRate = 12.0,
            interestRatePeriod = InterestRatePeriod.PER_ANNUM,
            startDateMillis = now - days365Millis
        )

        val summary = InterestCalculator.calculate(tx, now)
        assertEquals(10_000.0, summary.principal, 1.0)
        assertEquals(1_200.0, summary.accruedInterest, 5.0)
        assertEquals(9_200.0, summary.netBalanceDue, 5.0)
        assertTrue(summary.subtitleBreakdown.contains("Principal: रू10,000"))
        assertTrue(summary.subtitleBreakdown.contains("Paid: रू2,000"))
    }

    @Test
    fun compoundInterestFrequency_affectsAccruedInterest() {
        val monthlyInterest = InterestCalculator.computeInterestAmount(
            principal = 10_000.0,
            rate = 12.0,
            ratePeriod = InterestRatePeriod.PER_ANNUM,
            interestType = InterestType.COMPOUND,
            compoundFrequency = CompoundFrequency.MONTHLY,
            elapsedDays = 365L
        )
        val yearlyInterest = InterestCalculator.computeInterestAmount(
            principal = 10_000.0,
            rate = 12.0,
            ratePeriod = InterestRatePeriod.PER_ANNUM,
            interestType = InterestType.COMPOUND,
            compoundFrequency = CompoundFrequency.YEARLY,
            elapsedDays = 365L
        )
        assertTrue("Monthly compounding should yield more than yearly compounding", monthlyInterest > yearlyInterest)
    }

    @Test
    fun whatsAppReminderTemplate_respectsPrivacyToggle() {
        val tx = UdhaarTransaction(
            id = 2L,
            transactionType = TransactionType.GIVEN,
            partyName = "Sita Verma",
            currency = CurrencyType.INR,
            principalAmount = 5_000.0,
            paymentMode = PaymentMode.UPI
        )
        val summary = InterestCalculator.calculate(tx)

        val visibleMsg = InterestCalculator.buildWhatsAppReminderMessage(
            summary = summary,
            hideExactAmount = false,
            upiId = "udhaar@upi"
        )
        assertTrue(visibleMsg.contains("₹5,000"))
        assertTrue(visibleMsg.contains("udhaar@upi"))

        val hiddenMsg = InterestCalculator.buildWhatsAppReminderMessage(
            summary = summary,
            hideExactAmount = true,
            upiId = "udhaar@upi"
        )
        assertFalse(hiddenMsg.contains("₹5,000"))
        assertTrue(hiddenMsg.contains("Hello Sita Verma"))
    }

    @Test
    fun phoneFormatting_autoPrefixesCountryCodeForTenDigits() {
        assertEquals("+91 9876543210", InterestCalculator.formatPhoneWithCountryCode("9876543210", CurrencyType.INR))
        assertEquals("+977 9841234567", InterestCalculator.formatPhoneWithCountryCode("9841234567", CurrencyType.NPR))
    }

    @Test
    fun userSettings_defaultsToInrEnabledAndNprDisabled() {
        val defaultSettings = UserSettings()
        assertTrue(defaultSettings.inrEnabled)
        assertFalse(defaultSettings.nprEnabled)
        assertEquals(listOf(CurrencyType.INR), defaultSettings.enabledCurrencies)

        val bothEnabled = defaultSettings.copy(nprEnabled = true)
        assertEquals(listOf(CurrencyType.INR, CurrencyType.NPR), bothEnabled.enabledCurrencies)
    }

    @Test
    fun partyEntityAndAppThemes_workAsExpected() {
        val party = PartyEntity(
            partyId = 10L,
            name = "Ramesh Sharma",
            phoneNumber = "+91 9876543210",
            upiId = "ramesh@upi",
            email = "ramesh@example.com",
            tagOrNotes = "Supplier",
            preferredCurrency = CurrencyType.INR
        )
        assertEquals("Ramesh Sharma", party.name)
        assertEquals("ramesh@upi", party.upiId)

        assertEquals(4, AppThemeMode.entries.size)
        assertTrue(paletteForTheme(AppThemeMode.MONOCHROME_LIGHT).primaryInk != paletteForTheme(AppThemeMode.OBSIDIAN_DARK).primaryInk)
        assertTrue(paletteForTheme(AppThemeMode.PAPER_LEDGER).backgroundSurface != paletteForTheme(AppThemeMode.EMERALD_FINANCE).backgroundSurface)
    }
}
