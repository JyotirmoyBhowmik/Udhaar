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

        assertEquals(5, AppThemeMode.entries.size)
        assertTrue(paletteForTheme(AppThemeMode.MONOCHROME_LIGHT).primaryInk != paletteForTheme(AppThemeMode.OBSIDIAN_DARK).primaryInk)
        assertTrue(paletteForTheme(AppThemeMode.PAPER_LEDGER).backgroundSurface != paletteForTheme(AppThemeMode.EMERALD_FINANCE).backgroundSurface)
    }

    @Test
    fun contactPickerHelper_cleanPhoneNumberFormatsCorrectly() {
        assertEquals("+919876543210", com.jyotirmoyb.udhaar.util.ContactPickerHelper.cleanPhoneNumber("+91 (987) 654-3210"))
        assertEquals("9841234567", com.jyotirmoyb.udhaar.util.ContactPickerHelper.cleanPhoneNumber("9841-234-567"))
    }

    @Test
    fun twoPartyVerification_mirroredRoleContentHashAndE2eeLinksWork() {
        val lenderTx = UdhaarTransaction(
            id = 100L,
            transactionType = TransactionType.GIVEN,
            partyName = "Bapan",
            lenderName = "Jyotirmoy",
            borrowerName = "Bapan",
            currency = CurrencyType.INR,
            principalAmount = 15_000.0,
            startDateMillis = 1_710_000_000_000L,
            expiryDateMillis = 1_715_000_000_000L,
            isInterestEnabled = true,
            interestType = InterestType.SIMPLE,
            interestRate = 2.0,
            interestRatePeriod = InterestRatePeriod.PER_MONTH
        )
        val borrowerMirroredTx = lenderTx.copy(
            id = 200L,
            transactionType = TransactionType.TAKEN,
            partyName = "Jyotirmoy"
        )

        val lenderHash = com.jyotirmoyb.udhaar.util.VerificationCryptoHelper.computeTransactionContentHash(
            lenderTx,
            myProfileName = "Jyotirmoy"
        )
        val borrowerHash = com.jyotirmoyb.udhaar.util.VerificationCryptoHelper.computeTransactionContentHash(
            borrowerMirroredTx,
            myProfileName = "Bapan"
        )
        assertEquals(64, lenderHash.length)
        assertEquals("Mirrored GIVEN and TAKEN records must share the exact same SHA-256 contentHash", lenderHash, borrowerHash)

        val cipher = com.jyotirmoyb.udhaar.util.VerificationCryptoHelper.encryptE2eeField(
            plainText = "Confidential shop invoice #42",
            remoteId = "uuid-1234",
            shareToken = "secret-token-99"
        )
        val decrypted = com.jyotirmoyb.udhaar.util.VerificationCryptoHelper.decryptE2eeField(
            cipherText = cipher,
            remoteId = "uuid-1234",
            shareToken = "secret-token-99"
        )
        assertEquals("Confidential shop invoice #42", decrypted)

        val profileLink = com.jyotirmoyb.udhaar.util.VerificationCryptoHelper.buildPaymentProfileLink(
            com.jyotirmoyb.udhaar.util.VerificationCryptoHelper.SharedPaymentProfilePayload(
                userId = "uid-bapan",
                displayName = "Bapan",
                phoneNumber = "+919876543210",
                upiId = "bapan@okaxis"
            )
        )
        assertTrue(profileLink.startsWith("https://jyotirmoyb.com/u/p/"))
        val parsedProfile = com.jyotirmoyb.udhaar.util.VerificationCryptoHelper.parsePaymentProfileLink(profileLink)
        assertEquals("bapan@okaxis", parsedProfile?.upiId)
    }

    @Test
    fun phoneNormaliser_correctlyHandlesVariationsAndDetectsDuplicates() {
        // E.164 normalization for India (+91)
        val variant1 = "98765 43210"
        val variant2 = "+91 98765-43210"
        val variant3 = "09876543210"
        val variant4 = "+919876543210"

        val norm1 = InterestCalculator.normalizeToE164(variant1, CurrencyType.INR)
        val norm2 = InterestCalculator.normalizeToE164(variant2, CurrencyType.INR)
        val norm3 = InterestCalculator.normalizeToE164(variant3, CurrencyType.INR)
        val norm4 = InterestCalculator.normalizeToE164(variant4, CurrencyType.INR)

        assertEquals("+919876543210", norm1)
        assertEquals("+919876543210", norm2)
        assertEquals("+919876543210", norm3)
        assertEquals("+919876543210", norm4)

        assertTrue(InterestCalculator.arePhonesEquivalent(variant1, variant2, CurrencyType.INR))
        assertTrue(InterestCalculator.arePhonesEquivalent(variant2, variant3, CurrencyType.INR))
        assertTrue(InterestCalculator.arePhonesEquivalent(variant3, variant4, CurrencyType.INR))

        // E.164 normalization for Nepal (+977)
        val nepal1 = "9841234567"
        val nepal2 = "+977 984-123-4567"
        val normNepal1 = InterestCalculator.normalizeToE164(nepal1, CurrencyType.NPR)
        val normNepal2 = InterestCalculator.normalizeToE164(nepal2, CurrencyType.NPR)
        assertEquals("+9779841234567", normNepal1)
        assertEquals("+9779841234567", normNepal2)
        assertTrue(InterestCalculator.arePhonesEquivalent(nepal1, nepal2, CurrencyType.NPR))
    }

    @Test
    fun interestCalculator_quarterlyAndHalfYearlyCompounding_andGracePeriod() {
        val principal = 20_000.0
        val rate = 10.0
        val days = 365L

        val quarterly = InterestCalculator.computeInterestAmount(
            principal = principal,
            rate = rate,
            ratePeriod = InterestRatePeriod.PER_ANNUM,
            interestType = InterestType.COMPOUND,
            compoundFrequency = CompoundFrequency.QUARTERLY,
            elapsedDays = days
        )

        val halfYearly = InterestCalculator.computeInterestAmount(
            principal = principal,
            rate = rate,
            ratePeriod = InterestRatePeriod.PER_ANNUM,
            interestType = InterestType.COMPOUND,
            compoundFrequency = CompoundFrequency.HALF_YEARLY,
            elapsedDays = days
        )

        val simple = InterestCalculator.computeInterestAmount(
            principal = principal,
            rate = rate,
            ratePeriod = InterestRatePeriod.PER_ANNUM,
            interestType = InterestType.SIMPLE,
            compoundFrequency = CompoundFrequency.YEARLY,
            elapsedDays = days
        )

        // Compound interest with more frequent intervals yields more than simple interest
        assertTrue("Quarterly compound ($quarterly) > Half-yearly ($halfYearly)", quarterly > halfYearly)
        assertTrue("Half-yearly compound ($halfYearly) > Simple ($simple)", halfYearly > simple)

        // Grace period test
        val txWithGrace = UdhaarTransaction(
            principalAmount = 10_000.0,
            interestRate = 12.0,
            interestRatePeriod = InterestRatePeriod.PER_ANNUM,
            isInterestEnabled = true,
            gracePeriodDays = 15,
            startDateMillis = 1_700_000_000_000L
        )
        // 10 days elapsed (within 15-day grace period): interest must be 0
        val withinGrace = InterestCalculator.calculate(txWithGrace, nowMillis = 1_700_000_000_000L + 10L * 86_400_000L)
        assertEquals(0.0, withinGrace.accruedInterest, 0.001)

        // 20 days elapsed (outside 15-day grace period): interest starts accruing
        val outsideGrace = InterestCalculator.calculate(txWithGrace, nowMillis = 1_700_000_000_000L + 20L * 86_400_000L)
        assertTrue("Interest should accrue after grace period", outsideGrace.accruedInterest > 0.0)
    }

    @Test
    fun contentHash_detectsTamperingOfAnyField() {
        val original = UdhaarTransaction(
            id = 50L,
            transactionType = TransactionType.GIVEN,
            partyName = "Dev",
            lenderName = "Owner",
            borrowerName = "Dev",
            currency = CurrencyType.INR,
            principalAmount = 10_000.0,
            startDateMillis = 1_700_000_000_000L,
            expiryDateMillis = 1_720_000_000_000L,
            interestRate = 5.0
        )
        val originalHash = com.jyotirmoyb.udhaar.util.VerificationCryptoHelper.computeTransactionContentHash(original, "Owner")

        // Tampering principalAmount changes hash
        val tamperedAmount = original.copy(principalAmount = 10_001.0)
        val tamperedAmountHash = com.jyotirmoyb.udhaar.util.VerificationCryptoHelper.computeTransactionContentHash(tamperedAmount, "Owner")
        org.junit.Assert.assertNotEquals(originalHash, tamperedAmountHash)

        // Tampering interestRate changes hash
        val tamperedRate = original.copy(interestRate = 5.1)
        val tamperedRateHash = com.jyotirmoyb.udhaar.util.VerificationCryptoHelper.computeTransactionContentHash(tamperedRate, "Owner")
        org.junit.Assert.assertNotEquals(originalHash, tamperedRateHash)

        // Tampering dueDate changes hash
        val tamperedDue = original.copy(expiryDateMillis = 1_725_000_000_000L)
        val tamperedDueHash = com.jyotirmoyb.udhaar.util.VerificationCryptoHelper.computeTransactionContentHash(tamperedDue, "Owner")
        org.junit.Assert.assertNotEquals(originalHash, tamperedDueHash)
    }
}

