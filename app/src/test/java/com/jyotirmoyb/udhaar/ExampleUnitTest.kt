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
import kotlinx.coroutines.flow.first
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
        assertTrue(summary.subtitleBreakdown.contains("Amount lent: रू10,000"))
        assertTrue(summary.subtitleBreakdown.contains("Paid: रू2,000"))
        assertEquals("रू •••••", InterestCalculator.formatCurrencyPrivacy(10_000.0, CurrencyType.NPR, isHidden = true))
        assertEquals("रू10,000", InterestCalculator.formatCurrencyPrivacy(10_000.0, CurrencyType.NPR, isHidden = false))
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
        assertTrue(profileLink.startsWith("https://udhaar.jyotirmoyb.com/u/p/"))
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
    fun interestCalculator_quarterlyAndMonthlyCompounding_andSettledFreeze() {
        val principal = 20_000.0
        val rate = 10.0
        val days = 365L

        val monthly = InterestCalculator.computeInterestAmount(
            principal = principal,
            rate = rate,
            ratePeriod = InterestRatePeriod.PER_ANNUM,
            interestType = InterestType.COMPOUND,
            compoundFrequency = CompoundFrequency.MONTHLY,
            elapsedDays = days
        )

        val quarterly = InterestCalculator.computeInterestAmount(
            principal = principal,
            rate = rate,
            ratePeriod = InterestRatePeriod.PER_ANNUM,
            interestType = InterestType.COMPOUND,
            compoundFrequency = CompoundFrequency.QUARTERLY,
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
        assertTrue("Monthly compound ($monthly) > Quarterly ($quarterly)", monthly > quarterly)
        assertTrue("Quarterly compound ($quarterly) > Simple ($simple)", quarterly > simple)

        // Settled freeze test
        val start = 1_700_000_000_000L
        val settledAt = start + 15L * 86_400_000L
        val txSettled = UdhaarTransaction(
            partyName = "Sita",
            principalAmount = 10_000.0,
            interestRate = 12.0,
            interestRatePeriod = InterestRatePeriod.PER_ANNUM,
            isInterestEnabled = true,
            isSettled = true,
            settledAtMillis = settledAt,
            startDateMillis = start
        )
        val atSettled = InterestCalculator.calculate(txSettled, asOfMillis = settledAt)
        val muchLater = InterestCalculator.calculate(txSettled, asOfMillis = start + 90L * 86_400_000L)
        assertEquals(atSettled.accruedInterest, muchLater.accruedInterest, 0.001)
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
            isInterestEnabled = true,
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

    @Test
    fun maskPhoneNumberAndMaskUpiId_masksAsFirstTwoAndLastTwoWithMiddleStars() {
        assertEquals("98*** ***10", InterestCalculator.maskPhoneNumber("9876543210"))
        assertEquals("98*** ***10", InterestCalculator.maskPhoneNumber("+91 98765-43210"))
        assertEquals("98*** ***10", InterestCalculator.maskPhoneNumber("09876543210"))
        assertEquals("98*** ***67", InterestCalculator.maskPhoneNumber("+977 9841234567"))

        assertEquals("98*** ***10@ybl", InterestCalculator.maskUpiId("9876543210@ybl"))
        assertEquals("me*** ***nt@okicici", InterestCalculator.maskUpiId("merchant@okicici"))
        assertEquals("ba*** ***an@okaxis", InterestCalculator.maskUpiId("bapan@okaxis"))
    }

    @Test
    fun noHardcodedTextLiteralsRemainInUiFiles_andAllFiveLanguagesHaveFullTranslations() {
        val projectRootCandidates = listOf(
            java.io.File("src/main"),
            java.io.File("app/src/main"),
            java.io.File("../app/src/main")
        )
        val mainDir = projectRootCandidates.firstOrNull { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Could not locate app/src/main directory from ${java.io.File(".").absolutePath}")

        val uiDir = java.io.File(mainDir, "java/com/jyotirmoyb/udhaar/ui")
        assertTrue("UI directory must exist: ${uiDir.absolutePath}", uiDir.exists())

        val hardcodedTextRegex = Regex("""\bText\s*\(\s*(?:text\s*=\s*)?"((?:[^"\\]|\\.)*)"""")
        val violations = mutableListOf<String>()

        uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { ktFile ->
                val content = ktFile.readText()
                hardcodedTextRegex.findAll(content).forEach { match ->
                    violations.add("${ktFile.name}: ${match.value}")
                }
            }

        assertTrue(
            "Found hard-coded Text(\"...\") literals in UI files (must be 0):\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )

        val resDir = java.io.File(mainDir, "res")
        val localeDirs = listOf("values", "values-hi", "values-bn", "values-as", "values-ne")
        val stringNameRegex = Regex("""<string\s+name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

        val keysByLocale = mutableMapOf<String, Map<String, String>>()
        for (locale in localeDirs) {
            val xmlFile = java.io.File(resDir, "$locale/strings.xml")
            assertTrue("Missing $locale/strings.xml", xmlFile.exists())
            val xmlText = xmlFile.readText()
            val map = mutableMapOf<String, String>()
            stringNameRegex.findAll(xmlText).forEach { m ->
                map[m.groupValues[1]] = m.groupValues[2].trim()
            }
            keysByLocale[locale] = map
        }

        val defaultStrings = keysByLocale["values"].orEmpty()
        assertTrue(
            "Expected at least 500 strings in values/strings.xml after extracting all UI literals, found ${defaultStrings.size}",
            defaultStrings.size >= 500
        )

        val nonTranslatableKeys = setOf("default_web_client_id")
        val requiredKeys = defaultStrings.keys - nonTranslatableKeys

        for (locale in listOf("values-hi", "values-bn", "values-as", "values-ne")) {
            val localeMap = keysByLocale[locale].orEmpty()
            val missing = requiredKeys - localeMap.keys
            val emptyKeys = requiredKeys.filter { localeMap[it].isNullOrBlank() }
            assertTrue("Missing keys in $locale/strings.xml: $missing", missing.isEmpty())
            assertTrue("Blank translations in $locale/strings.xml: $emptyKeys", emptyKeys.isEmpty())
        }
    }

    @Test
    fun fmtDate_showsTimeWhenTimestampIncludesSpecificHourAndMinute() {
        val midnightCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.APRIL, 30, 0, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        assertEquals("30 Apr 2026", InterestCalculator.fmtDate(midnightCal.timeInMillis))

        val auditCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.APRIL, 30, 14, 35, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val formattedAudit = InterestCalculator.fmtDate(auditCal.timeInMillis)
        assertTrue(
            "Expected audit timestamp with hour and minute to include 02:35 PM, got: $formattedAudit",
            formattedAudit.contains("30 Apr 2026") && formattedAudit.contains("02:35")
        )
        assertEquals("Open-ended", InterestCalculator.fmtDate(0L))
    }

    @Test
    fun safeLogger_redactsPhonesUpiIdsAmountsTokensAndPins() {
        val raw = "Failed sync for +91 9876543210, upi merchant@okicici, amount ₹12,500.50, pin=4829, url https://udhaar.jyotirmoyb.com/u/eyJ0eXAiOiJKV1QifQ"
        val redacted = com.jyotirmoyb.udhaar.util.SafeLogger.redact(raw)

        assertFalse("Phone number must be redacted: $redacted", redacted.contains("9876543210"))
        assertFalse("UPI ID must be redacted: $redacted", redacted.contains("merchant@okicici"))
        assertFalse("Amount must be redacted: $redacted", redacted.contains("12,500.50"))
        assertFalse("PIN must be redacted: $redacted", redacted.contains("4829"))
        assertFalse("Token URL must be redacted: $redacted", redacted.contains("eyJ0eXAiOiJKV1QifQ"))
        assertTrue(redacted.contains("[REDACTED_PHONE]"))
        assertTrue(redacted.contains("[REDACTED_ID]"))
        assertTrue(redacted.contains("[REDACTED_AMOUNT]"))
    }

    @Test
    fun udhaarAppError_mapsExceptionsToClearUserMessages() {
        val netErr = com.jyotirmoyb.udhaar.util.UdhaarAppError.fromThrowable(java.net.UnknownHostException("Unable to resolve host"))
        assertEquals(com.jyotirmoyb.udhaar.util.UdhaarAppError.NoNetwork, netErr)

        val passErr = com.jyotirmoyb.udhaar.util.UdhaarAppError.fromThrowable(javax.crypto.AEADBadTagException("Tag mismatch"))
        assertEquals(com.jyotirmoyb.udhaar.util.UdhaarAppError.WrongPassphrase, passErr)

        val authErr = com.jyotirmoyb.udhaar.util.UdhaarAppError.fromThrowable(IllegalStateException("User is not signed in (401)"))
        assertEquals(com.jyotirmoyb.udhaar.util.UdhaarAppError.SignedOut, authErr)

        val quotaErr = com.jyotirmoyb.udhaar.util.UdhaarAppError.fromThrowable(RuntimeException("RESOURCE_EXHAUSTED: storageQuotaExceeded"))
        assertEquals(com.jyotirmoyb.udhaar.util.UdhaarAppError.QuotaExceeded, quotaErr)

        val corruptErr = com.jyotirmoyb.udhaar.util.UdhaarAppError.fromThrowable(IllegalArgumentException("Malformed or tampered JSON payload"))
        assertEquals(com.jyotirmoyb.udhaar.util.UdhaarAppError.CorruptedFile, corruptErr)
    }

    @Test
    fun backupPreviewSummary_usesBuildConfigVersionName() {
        val summary = com.jyotirmoyb.udhaar.backup.BackupPreviewSummary(
            partiesCount = 3,
            transactionsCount = 7
        )
        assertEquals(BuildConfig.VERSION_NAME, summary.appVersion)
    }

    @Test
    fun driveServiceHelper_fetchesFreshTokenAndRetriesOnceOnHttp401() = kotlinx.coroutines.runBlocking {
        val requestedForceRefreshTokens = mutableListOf<String?>()
        val callTokens = mutableListOf<String>()
        var nextTokenIndex = 0
        val tokenSequence = listOf("stale-token-1", "refreshed-token-2", "token-3", "token-4")

        val fakeTokenProvider = object : com.jyotirmoyb.udhaar.backup.DriveTokenProvider {
            override suspend fun getFreshAccessToken(forceRefreshAfter401: String?): com.jyotirmoyb.udhaar.backup.DriveTokenOutcome {
                requestedForceRefreshTokens.add(forceRefreshAfter401)
                val token = tokenSequence.getOrElse(nextTokenIndex) { "fallback-token" }
                nextTokenIndex++
                return com.jyotirmoyb.udhaar.backup.DriveTokenOutcome.TokenReady(
                    accessToken = token,
                    accountEmail = "merchant@example.com"
                )
            }
        }

        val helper = object : com.jyotirmoyb.udhaar.backup.DriveServiceHelper(
            tokenProvider = fakeTokenProvider
        ) {
            override suspend fun listBackupsWithToken(accessToken: String): com.jyotirmoyb.udhaar.backup.DriveResult<List<com.jyotirmoyb.udhaar.backup.DriveRemoteBackup>> {
                callTokens.add(accessToken)
                return if (accessToken == "stale-token-1") {
                    com.jyotirmoyb.udhaar.backup.DriveResult.Error(
                        errorType = com.jyotirmoyb.udhaar.backup.DriveErrorType.EXPIRED_SIGN_IN,
                        message = "Unauthorized",
                        httpCode = 401,
                        errorCode = "HTTP_401"
                    )
                } else {
                    com.jyotirmoyb.udhaar.backup.DriveResult.Success(
                        listOf(
                            com.jyotirmoyb.udhaar.backup.DriveRemoteBackup(
                                fileId = "file-123",
                                fileName = "udhaar_backup.udhaar",
                                sizeBytes = 512L,
                                createdTimeMillis = 1_700_000_000_000L,
                                preview = null
                            )
                        )
                    )
                }
            }

            override suspend fun uploadBackupWithToken(
                accessToken: String,
                fileName: String,
                encryptedBytes: ByteArray,
                preview: com.jyotirmoyb.udhaar.backup.BackupPreviewSummary
            ): com.jyotirmoyb.udhaar.backup.DriveResult<com.jyotirmoyb.udhaar.backup.DriveRemoteBackup> {
                callTokens.add(accessToken)
                // Simulate persistent 401 on both initial and retry calls
                return com.jyotirmoyb.udhaar.backup.DriveResult.Error(
                    errorType = com.jyotirmoyb.udhaar.backup.DriveErrorType.EXPIRED_SIGN_IN,
                    message = "Still 401",
                    httpCode = 401,
                    errorCode = "HTTP_401"
                )
            }
        }

        // 1. First call gets 401 with "stale-token-1", refreshes once passing forceRefreshAfter401="stale-token-1", and succeeds with "refreshed-token-2"
        val listResult = helper.listBackups()
        assertTrue("Expected Success after single 401 token refresh, got $listResult", listResult is com.jyotirmoyb.udhaar.backup.DriveResult.Success)
        assertEquals(listOf(null, "stale-token-1"), requestedForceRefreshTokens)
        assertEquals(listOf("stale-token-1", "refreshed-token-2"), callTokens)

        // 2. Upload call gets 401 twice -> refreshes only once and fails with needsReconnect = true and HTTP_401_AFTER_RETRY
        requestedForceRefreshTokens.clear()
        callTokens.clear()
        val uploadResult = helper.uploadBackup(
            fileName = "backup.udhaar",
            encryptedBytes = byteArrayOf(1, 2, 3),
            preview = com.jyotirmoyb.udhaar.backup.BackupPreviewSummary()
        )
        assertTrue(uploadResult is com.jyotirmoyb.udhaar.backup.DriveResult.Error)
        val err = uploadResult as com.jyotirmoyb.udhaar.backup.DriveResult.Error
        assertEquals(com.jyotirmoyb.udhaar.backup.DriveErrorType.EXPIRED_SIGN_IN, err.errorType)
        assertEquals("HTTP_401_AFTER_RETRY", err.errorCode)
        assertTrue("Should require reconnect after retry still returns 401", err.needsReconnect)
        assertEquals(2, callTokens.size)
        assertEquals(listOf(null, "token-3"), requestedForceRefreshTokens)

        // 3. When authorize() returns hasResolution, NeedsResolution maps to RESOLUTION_REQUIRED & needsReconnect = true
        val resolutionHelper = com.jyotirmoyb.udhaar.backup.DriveServiceHelper(
            tokenProvider = object : com.jyotirmoyb.udhaar.backup.DriveTokenProvider {
                override suspend fun getFreshAccessToken(forceRefreshAfter401: String?): com.jyotirmoyb.udhaar.backup.DriveTokenOutcome {
                    return com.jyotirmoyb.udhaar.backup.DriveTokenOutcome.NeedsResolution(
                        pendingIntent = null,
                        message = "Consent required"
                    )
                }
            }
        )
        val resOutcome = resolutionHelper.listBackups()
        assertTrue(resOutcome is com.jyotirmoyb.udhaar.backup.DriveResult.Error)
        val resErr = resOutcome as com.jyotirmoyb.udhaar.backup.DriveResult.Error
        assertEquals("RESOLUTION_REQUIRED", resErr.errorCode)
        assertTrue(resErr.needsReconnect)
    }

    @Test
    fun driveBackupWorker_handlesAllErrorPathsAndConsecutiveFailureNotification() = kotlinx.coroutines.runBlocking {
        val fakeDao = java.lang.reflect.Proxy.newProxyInstance(
            com.jyotirmoyb.udhaar.data.UdhaarDao::class.java.classLoader,
            arrayOf(com.jyotirmoyb.udhaar.data.UdhaarDao::class.java)
        ) { _, method, _ ->
            when (method.returnType) {
                List::class.java -> emptyList<Any>()
                kotlinx.coroutines.flow.Flow::class.java -> kotlinx.coroutines.flow.flowOf(emptyList<Any>())
                java.lang.Boolean.TYPE, Boolean::class.java -> false
                java.lang.Integer.TYPE, Int::class.java -> 0
                java.lang.Long.TYPE, Long::class.java -> 0L
                else -> null
            }
        } as com.jyotirmoyb.udhaar.data.UdhaarDao

        val repo = object : com.jyotirmoyb.udhaar.data.UdhaarRepository(fakeDao, context = null) {
            override suspend fun getFullLedgerBackupSnapshot(): com.jyotirmoyb.udhaar.data.FullLedgerSnapshot {
                return com.jyotirmoyb.udhaar.data.FullLedgerSnapshot(
                    parties = emptyList(),
                    transactions = emptyList(),
                    partialPayments = emptyList(),
                    chatThreads = emptyList(),
                    chatMessages = emptyList(),
                    attachments = emptyList()
                )
            }
        }
        val notificationsSent = mutableListOf<Pair<com.jyotirmoyb.udhaar.backup.DriveBackupErrorCode, Int>>()

        var simulatedUploadResult: com.jyotirmoyb.udhaar.backup.DriveResult<com.jyotirmoyb.udhaar.backup.DriveRemoteBackup> =
            com.jyotirmoyb.udhaar.backup.DriveResult.Success(
                com.jyotirmoyb.udhaar.backup.DriveRemoteBackup(
                    fileId = "auto-1",
                    fileName = "udhaar_auto_backup.udhaar",
                    sizeBytes = 1024L,
                    createdTimeMillis = 1_700_000_000_000L,
                    preview = null
                )
            )

        val fakeDriveHelper = object : com.jyotirmoyb.udhaar.backup.DriveServiceHelper(tokenProvider = null) {
            override suspend fun uploadBackup(
                accessToken: String,
                fileName: String,
                encryptedBytes: ByteArray,
                preview: com.jyotirmoyb.udhaar.backup.BackupPreviewSummary
            ): com.jyotirmoyb.udhaar.backup.DriveResult<com.jyotirmoyb.udhaar.backup.DriveRemoteBackup> {
                return simulatedUploadResult
            }

            override suspend fun listBackups(accessToken: String): com.jyotirmoyb.udhaar.backup.DriveResult<List<com.jyotirmoyb.udhaar.backup.DriveRemoteBackup>> {
                return com.jyotirmoyb.udhaar.backup.DriveResult.Success(emptyList())
            }
        }

        // Path 1: NOT_CONNECTED (email is blank) -> Failure #1 (no notification yet)
        val resNotConnected = com.jyotirmoyb.udhaar.backup.DriveBackupWorker.executeBackupWork(
            repository = repo,
            driveHelper = fakeDriveHelper,
            onConsecutiveFailureNotification = { code, count -> notificationsSent.add(code to count) }
        )
        assertTrue(resNotConnected is androidx.work.ListenableWorker.Result.Failure)
        val settingsAfter1 = repo.userSettings.first()
        assertEquals(com.jyotirmoyb.udhaar.backup.DriveBackupErrorCode.NOT_CONNECTED, settingsAfter1.driveLastErrorCode)
        assertEquals(1, settingsAfter1.driveConsecutiveFailures)
        assertTrue("Notification should not fire on 1st failure", notificationsSent.isEmpty())

        // Path 2: NO_PASSPHRASE (email connected, passphrase not saved) -> Failure #2 (fires notification!)
        repo.updateDriveAccount("merchant@example.com")
        val resNoPassphrase = com.jyotirmoyb.udhaar.backup.DriveBackupWorker.executeBackupWork(
            repository = repo,
            driveHelper = fakeDriveHelper,
            onConsecutiveFailureNotification = { code, count -> notificationsSent.add(code to count) }
        )
        assertTrue(resNoPassphrase is androidx.work.ListenableWorker.Result.Failure)
        val settingsAfter2 = repo.userSettings.first()
        assertEquals(com.jyotirmoyb.udhaar.backup.DriveBackupErrorCode.NO_PASSPHRASE, settingsAfter2.driveLastErrorCode)
        assertEquals(2, settingsAfter2.driveConsecutiveFailures)
        assertEquals(1, notificationsSent.size)
        assertEquals(com.jyotirmoyb.udhaar.backup.DriveBackupErrorCode.NO_PASSPHRASE to 2, notificationsSent.last())

        // Save passphrase for remaining network/API error tests
        repo.saveAutoBackupPassphrase("CorrectHorseBatteryStaple!")

        // Path 3: NO_NETWORK -> returns Result.retry() and records NO_NETWORK
        simulatedUploadResult = com.jyotirmoyb.udhaar.backup.DriveResult.Error(
            errorType = com.jyotirmoyb.udhaar.backup.DriveErrorType.NO_NETWORK,
            message = "Unable to resolve host www.googleapis.com",
            errorCode = "NETWORK_IO_ERROR"
        )
        val resNoNetwork = com.jyotirmoyb.udhaar.backup.DriveBackupWorker.executeBackupWork(
            repository = repo,
            driveHelper = fakeDriveHelper,
            onConsecutiveFailureNotification = { code, count -> notificationsSent.add(code to count) }
        )
        assertEquals(androidx.work.ListenableWorker.Result.retry(), resNoNetwork)
        val settingsAfterNet = repo.userSettings.first()
        assertEquals(com.jyotirmoyb.udhaar.backup.DriveBackupErrorCode.NO_NETWORK, settingsAfterNet.driveLastErrorCode)

        // Path 4: AUTH_EXPIRED -> returns Result.failure(), sets driveNeedsReconnect = true
        simulatedUploadResult = com.jyotirmoyb.udhaar.backup.DriveResult.Error(
            errorType = com.jyotirmoyb.udhaar.backup.DriveErrorType.EXPIRED_SIGN_IN,
            message = "Token expired",
            httpCode = 401,
            errorCode = "HTTP_401_AFTER_RETRY",
            needsReconnect = true
        )
        val resAuthExpired = com.jyotirmoyb.udhaar.backup.DriveBackupWorker.executeBackupWork(
            repository = repo,
            driveHelper = fakeDriveHelper,
            onConsecutiveFailureNotification = { code, count -> notificationsSent.add(code to count) }
        )
        assertTrue(resAuthExpired is androidx.work.ListenableWorker.Result.Failure)
        val settingsAfterAuth = repo.userSettings.first()
        assertEquals(com.jyotirmoyb.udhaar.backup.DriveBackupErrorCode.AUTH_EXPIRED, settingsAfterAuth.driveLastErrorCode)
        assertTrue(settingsAfterAuth.driveNeedsReconnect)

        // Path 5: QUOTA -> returns Result.failure(), records QUOTA
        simulatedUploadResult = com.jyotirmoyb.udhaar.backup.DriveResult.Error(
            errorType = com.jyotirmoyb.udhaar.backup.DriveErrorType.QUOTA_EXCEEDED,
            message = "Drive storage full",
            httpCode = 403,
            errorCode = "HTTP_403_storageQuotaExceeded"
        )
        val resQuota = com.jyotirmoyb.udhaar.backup.DriveBackupWorker.executeBackupWork(
            repository = repo,
            driveHelper = fakeDriveHelper,
            onConsecutiveFailureNotification = { code, count -> notificationsSent.add(code to count) }
        )
        assertTrue(resQuota is androidx.work.ListenableWorker.Result.Failure)
        val settingsAfterQuota = repo.userSettings.first()
        assertEquals(com.jyotirmoyb.udhaar.backup.DriveBackupErrorCode.QUOTA, settingsAfterQuota.driveLastErrorCode)

        // Path 6: OTHER -> returns Result.failure(), records OTHER
        simulatedUploadResult = com.jyotirmoyb.udhaar.backup.DriveResult.Error(
            errorType = com.jyotirmoyb.udhaar.backup.DriveErrorType.UNKNOWN,
            message = "Drive API not enabled",
            httpCode = 403,
            errorCode = "HTTP_403_accessNotConfigured"
        )
        val resOther = com.jyotirmoyb.udhaar.backup.DriveBackupWorker.executeBackupWork(
            repository = repo,
            driveHelper = fakeDriveHelper,
            onConsecutiveFailureNotification = { code, count -> notificationsSent.add(code to count) }
        )
        assertTrue(resOther is androidx.work.ListenableWorker.Result.Failure)
        val settingsAfterOther = repo.userSettings.first()
        assertEquals(com.jyotirmoyb.udhaar.backup.DriveBackupErrorCode.OTHER, settingsAfterOther.driveLastErrorCode)

        // Path 7: SUCCESS -> returns Result.success(), resets consecutiveFailures to 0 and errorCode to NONE
        simulatedUploadResult = com.jyotirmoyb.udhaar.backup.DriveResult.Success(
            com.jyotirmoyb.udhaar.backup.DriveRemoteBackup(
                fileId = "auto-ok",
                fileName = "udhaar_auto_backup_ok.udhaar",
                sizeBytes = 2048L,
                createdTimeMillis = 1_710_000_000_000L,
                preview = null
            )
        )
        val resSuccess = com.jyotirmoyb.udhaar.backup.DriveBackupWorker.executeBackupWork(
            repository = repo,
            driveHelper = fakeDriveHelper,
            onConsecutiveFailureNotification = { code, count -> notificationsSent.add(code to count) }
        )
        assertEquals(androidx.work.ListenableWorker.Result.success(), resSuccess)
        val settingsAfterSuccess = repo.userSettings.first()
        assertEquals(com.jyotirmoyb.udhaar.backup.DriveBackupErrorCode.NONE, settingsAfterSuccess.driveLastErrorCode)
        assertEquals(0, settingsAfterSuccess.driveConsecutiveFailures)
        assertFalse(settingsAfterSuccess.driveNeedsReconnect)
        assertTrue(settingsAfterSuccess.driveLastBackupTimeMillis > 0L)
    }

    @Test
    fun appSigningCertificateHelper_formatsAndComputesFingerprintsCorrectly() {
        val testBytes = byteArrayOf(0x01, 0x0A, 0x10, 0xFF.toByte())
        val formatted = com.jyotirmoyb.udhaar.util.AppSigningCertificateHelper.formatFingerprint(testBytes)
        assertEquals("01:0A:10:FF", formatted)

        val emptyFormatted = com.jyotirmoyb.udhaar.util.AppSigningCertificateHelper.formatFingerprint(byteArrayOf())
        assertEquals("", emptyFormatted)

        val helloBytes = "hello".toByteArray(Charsets.UTF_8)
        val sha256 = com.jyotirmoyb.udhaar.util.AppSigningCertificateHelper.computeDigest(helloBytes, "SHA-256")
        assertEquals(
            "2C:F2:4D:BA:5F:B0:A3:0E:26:E8:3B:2A:C5:B9:E2:9E:1B:16:1E:5C:1F:A7:42:5E:73:04:33:62:93:8B:98:24",
            sha256
        )

        val sha1 = com.jyotirmoyb.udhaar.util.AppSigningCertificateHelper.computeDigest(helloBytes, "SHA-1")
        assertEquals(
            "AA:F4:C6:1D:DC:C5:E8:A2:DA:BE:DE:0F:3B:48:2C:D9:AE:A9:43:4D",
            sha1
        )
    }

    @Test
    fun driveConnectionErrorMapper_diagnosesAllRequestedErrorPatternsCorrectly() {
        val pkg = "com.jyotirmoyb.udhaar"

        // 1. ApiException 10 (DEVELOPER_ERROR)
        val diag10 = com.jyotirmoyb.udhaar.backup.DriveConnectionErrorMapper.diagnose(
            statusCode = 10,
            httpCode = null,
            rawMessage = "10: Developer error",
            packageName = pkg
        )
        assertTrue(diag10.plainLanguageFix.contains("Package name or SHA-1 not registered"))
        assertTrue(diag10.plainLanguageFix.contains(pkg))
        assertTrue(diag10.technicalDetails.contains("10"))

        // 2. ApiException 12500 (SIGN_IN_FAILED)
        val diag12500 = com.jyotirmoyb.udhaar.backup.DriveConnectionErrorMapper.diagnose(
            statusCode = 12500,
            httpCode = null,
            rawMessage = "12500: Sign in failed",
            packageName = pkg
        )
        assertTrue(diag12500.plainLanguageFix.contains("Package name or SHA-1 not registered"))
        assertTrue(diag12500.plainLanguageFix.contains(pkg))

        // 3. access_denied / access blocked
        val diagDenied = com.jyotirmoyb.udhaar.backup.DriveConnectionErrorMapper.diagnose(
            statusCode = null,
            httpCode = null,
            rawMessage = "OAuth error: access_denied - Access blocked: app not verified",
            packageName = pkg
        )
        assertTrue(diagDenied.plainLanguageFix.contains("Add your Gmail as a test user, or publish the consent screen"))

        // 4. HTTP 403 accessNotConfigured / SERVICE_DISABLED
        val diag403 = com.jyotirmoyb.udhaar.backup.DriveConnectionErrorMapper.diagnose(
            statusCode = null,
            httpCode = 403,
            rawMessage = "Google Drive API has not been used in project 1234 before or it is disabled. Enable it by visiting https://console.developers.google.com/apis/api/drive.googleapis.com/overview",
            packageName = pkg
        )
        assertTrue(diag403.plainLanguageFix.contains("Enable the Google Drive API in the Google Cloud project"))

        // 5. HTTP 401 unauthorized
        val diag401 = com.jyotirmoyb.udhaar.backup.DriveConnectionErrorMapper.diagnose(
            statusCode = null,
            httpCode = 401,
            rawMessage = "401 Unauthorized",
            packageName = pkg
        )
        assertTrue(diag401.plainLanguageFix.contains("Reconnect Google Drive"))

        // 6. Network error
        val diagNetwork = com.jyotirmoyb.udhaar.backup.DriveConnectionErrorMapper.diagnose(
            statusCode = null,
            httpCode = null,
            rawMessage = "java.net.UnknownHostException: Unable to resolve host oauth2.googleapis.com: No address associated with hostname",
            packageName = pkg
        )
        assertTrue(diagNetwork.plainLanguageFix.contains("Unable to connect to Google servers"))
    }

    @Test
    fun reminderEscalationLevels_matchAllDueAndOverdueRanges() {
        val nowCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 10, 12, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nowMillis = nowCal.timeInMillis
        val dayMillis = 86_400_000L

        fun makeTx(dueOffsetDays: Int, isSettled: Boolean = false, remindersDisabled: Boolean = false): UdhaarTransaction {
            return UdhaarTransaction(
                id = 101L,
                transactionType = TransactionType.GIVEN,
                partyName = "Ravi",
                principalAmount = 5_000.0,
                expiryDateMillis = nowMillis + (dueOffsetDays * dayMillis),
                isSettled = isSettled,
                remindersDisabled = remindersDisabled
            )
        }

        val defaultPolicy = com.jyotirmoyb.udhaar.reminder.ReminderPolicy()

        // 4 days before due date -> too early, returns null
        val txBeforeWindow = makeTx(dueOffsetDays = 4)
        val levelTooEarly = com.jyotirmoyb.udhaar.reminder.ReminderEngine.computeEscalationLevel(txBeforeWindow, defaultPolicy, nowMillis)
        org.junit.Assert.assertNull("4 days before due date should have no reminder", levelTooEarly)

        // 3 days before due date -> Level 1 (Gentle)
        val tx3DaysBefore = makeTx(dueOffsetDays = 3)
        assertEquals(
            com.jyotirmoyb.udhaar.reminder.ReminderLevel.LEVEL_1_GENTLE,
            com.jyotirmoyb.udhaar.reminder.ReminderEngine.computeEscalationLevel(tx3DaysBefore, defaultPolicy, nowMillis)
        )

        // 1 day before due date -> Level 1 (Gentle)
        val tx1DayBefore = makeTx(dueOffsetDays = 1)
        assertEquals(
            com.jyotirmoyb.udhaar.reminder.ReminderLevel.LEVEL_1_GENTLE,
            com.jyotirmoyb.udhaar.reminder.ReminderEngine.computeEscalationLevel(tx1DayBefore, defaultPolicy, nowMillis)
        )

        // Due today (0 days offset) -> Level 1 (Gentle)
        val txDueToday = makeTx(dueOffsetDays = 0)
        assertEquals(
            com.jyotirmoyb.udhaar.reminder.ReminderLevel.LEVEL_1_GENTLE,
            com.jyotirmoyb.udhaar.reminder.ReminderEngine.computeEscalationLevel(txDueToday, defaultPolicy, nowMillis)
        )

        // 1 day overdue -> Level 2 (Friendly follow-up)
        val tx1DayOverdue = makeTx(dueOffsetDays = -1)
        assertEquals(
            com.jyotirmoyb.udhaar.reminder.ReminderLevel.LEVEL_2_FRIENDLY,
            com.jyotirmoyb.udhaar.reminder.ReminderEngine.computeEscalationLevel(tx1DayOverdue, defaultPolicy, nowMillis)
        )

        // 7 days overdue -> Level 2 (Friendly follow-up boundary)
        val tx7DaysOverdue = makeTx(dueOffsetDays = -7)
        assertEquals(
            com.jyotirmoyb.udhaar.reminder.ReminderLevel.LEVEL_2_FRIENDLY,
            com.jyotirmoyb.udhaar.reminder.ReminderEngine.computeEscalationLevel(tx7DaysOverdue, defaultPolicy, nowMillis)
        )

        // 8 days overdue -> Level 3 (Firm)
        val tx8DaysOverdue = makeTx(dueOffsetDays = -8)
        assertEquals(
            com.jyotirmoyb.udhaar.reminder.ReminderLevel.LEVEL_3_FIRM,
            com.jyotirmoyb.udhaar.reminder.ReminderEngine.computeEscalationLevel(tx8DaysOverdue, defaultPolicy, nowMillis)
        )

        // 30 days overdue -> Level 3 (Firm boundary)
        val tx30DaysOverdue = makeTx(dueOffsetDays = -30)
        assertEquals(
            com.jyotirmoyb.udhaar.reminder.ReminderLevel.LEVEL_3_FIRM,
            com.jyotirmoyb.udhaar.reminder.ReminderEngine.computeEscalationLevel(tx30DaysOverdue, defaultPolicy, nowMillis)
        )

        // 31 days overdue -> Level 4 (Final notice)
        val tx31DaysOverdue = makeTx(dueOffsetDays = -31)
        assertEquals(
            com.jyotirmoyb.udhaar.reminder.ReminderLevel.LEVEL_4_FINAL,
            com.jyotirmoyb.udhaar.reminder.ReminderEngine.computeEscalationLevel(tx31DaysOverdue, defaultPolicy, nowMillis)
        )

        // 100 days overdue -> Level 4 (Final notice)
        val tx100DaysOverdue = makeTx(dueOffsetDays = -100)
        assertEquals(
            com.jyotirmoyb.udhaar.reminder.ReminderLevel.LEVEL_4_FINAL,
            com.jyotirmoyb.udhaar.reminder.ReminderEngine.computeEscalationLevel(tx100DaysOverdue, defaultPolicy, nowMillis)
        )

        // Custom policy day ranges (editable in Settings)
        val customPolicy = com.jyotirmoyb.udhaar.reminder.ReminderPolicy(
            level1DaysBefore = 5,
            level2MaxOverdueDays = 14,
            level3MaxOverdueDays = 60
        )
        // 5 days before due date -> Level 1 with custom policy
        val tx5DaysBefore = makeTx(dueOffsetDays = 5)
        assertEquals(
            com.jyotirmoyb.udhaar.reminder.ReminderLevel.LEVEL_1_GENTLE,
            com.jyotirmoyb.udhaar.reminder.ReminderEngine.computeEscalationLevel(tx5DaysBefore, customPolicy, nowMillis)
        )
        // 6 days before -> null with custom policy
        val tx6DaysBefore = makeTx(dueOffsetDays = 6)
        org.junit.Assert.assertNull(
            com.jyotirmoyb.udhaar.reminder.ReminderEngine.computeEscalationLevel(tx6DaysBefore, customPolicy, nowMillis)
        )
        // 14 days overdue -> Level 2 with custom policy
        val tx14DaysOverdue = makeTx(dueOffsetDays = -14)
        assertEquals(
            com.jyotirmoyb.udhaar.reminder.ReminderLevel.LEVEL_2_FRIENDLY,
            com.jyotirmoyb.udhaar.reminder.ReminderEngine.computeEscalationLevel(tx14DaysOverdue, customPolicy, nowMillis)
        )
        // 15 days overdue -> Level 3 with custom policy
        val tx15DaysOverdue = makeTx(dueOffsetDays = -15)
        assertEquals(
            com.jyotirmoyb.udhaar.reminder.ReminderLevel.LEVEL_3_FIRM,
            com.jyotirmoyb.udhaar.reminder.ReminderEngine.computeEscalationLevel(tx15DaysOverdue, customPolicy, nowMillis)
        )
    }

    @Test
    fun reminderQuietHours_crossMidnightAndRespectPerPersonOverride() {
        val baseCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 10, 0, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        fun timeAtHour(hour: Int, minute: Int = 0): Long {
            return (baseCal.clone() as java.util.Calendar).apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
            }.timeInMillis
        }

        // Default quiet hours: 21:00 (9 PM) to 08:00 (8 AM)
        val defaultStart = 21
        val defaultEnd = 8

        // Evening quiet hours (e.g. 21:00, 22:30, 23:59) -> true
        assertTrue(com.jyotirmoyb.udhaar.reminder.ReminderEngine.isInQuietHours(timeAtHour(21, 0), defaultStart, defaultEnd))
        assertTrue(com.jyotirmoyb.udhaar.reminder.ReminderEngine.isInQuietHours(timeAtHour(22, 30), defaultStart, defaultEnd))
        assertTrue(com.jyotirmoyb.udhaar.reminder.ReminderEngine.isInQuietHours(timeAtHour(23, 59), defaultStart, defaultEnd))

        // Midnight and early morning (e.g. 00:00, 04:15, 07:59) -> true
        assertTrue(com.jyotirmoyb.udhaar.reminder.ReminderEngine.isInQuietHours(timeAtHour(0, 0), defaultStart, defaultEnd))
        assertTrue(com.jyotirmoyb.udhaar.reminder.ReminderEngine.isInQuietHours(timeAtHour(4, 15), defaultStart, defaultEnd))
        assertTrue(com.jyotirmoyb.udhaar.reminder.ReminderEngine.isInQuietHours(timeAtHour(7, 59), defaultStart, defaultEnd))

        // Daytime (e.g. 08:00, 12:00, 20:59) -> false
        assertFalse(com.jyotirmoyb.udhaar.reminder.ReminderEngine.isInQuietHours(timeAtHour(8, 0), defaultStart, defaultEnd))
        assertFalse(com.jyotirmoyb.udhaar.reminder.ReminderEngine.isInQuietHours(timeAtHour(12, 0), defaultStart, defaultEnd))
        assertFalse(com.jyotirmoyb.udhaar.reminder.ReminderEngine.isInQuietHours(timeAtHour(20, 59), defaultStart, defaultEnd))

        // Per-person quiet hours override -> allows sending even during quiet hours
        assertFalse(
            "Per-person override must bypass quiet hours at 23:00",
            com.jyotirmoyb.udhaar.reminder.ReminderEngine.isInQuietHours(timeAtHour(23, 0), defaultStart, defaultEnd, partyOverride = true)
        )
        assertFalse(
            "Per-person override must bypass quiet hours at 03:00",
            com.jyotirmoyb.udhaar.reminder.ReminderEngine.isInQuietHours(timeAtHour(3, 0), defaultStart, defaultEnd, partyOverride = true)
        )

        // calculateQuietHoursEnd correctly sets next 8 AM
        val nightTime = timeAtHour(22, 0)
        val morningEnd = com.jyotirmoyb.udhaar.reminder.ReminderEngine.calculateQuietHoursEnd(nightTime, endHour = 8)
        val endCal = java.util.Calendar.getInstance().apply { timeInMillis = morningEnd }
        assertEquals(8, endCal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(0, endCal.get(java.util.Calendar.MINUTE))
        assertEquals(11, endCal.get(java.util.Calendar.DAY_OF_MONTH)) // Next day Nov 11
    }

    @Test
    fun reminderSnoozeCalculation_andBadges() {
        val baseCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 3, 15, 30, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nowMillis = baseCal.timeInMillis

        // Snooze 1 day -> sets to tomorrow at 9 AM
        val snooze1D = com.jyotirmoyb.udhaar.reminder.ReminderEngine.calculateSnoozeDate(1, nowMillis)
        val cal1D = java.util.Calendar.getInstance().apply { timeInMillis = snooze1D }
        assertEquals(4, cal1D.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(9, cal1D.get(java.util.Calendar.HOUR_OF_DAY))

        // Snooze 3 days -> sets to Nov 6 at 9 AM
        val snooze3D = com.jyotirmoyb.udhaar.reminder.ReminderEngine.calculateSnoozeDate(3, nowMillis)
        val cal3D = java.util.Calendar.getInstance().apply { timeInMillis = snooze3D }
        assertEquals(6, cal3D.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(9, cal3D.get(java.util.Calendar.HOUR_OF_DAY))

        // Snooze 1 week (7 days) -> sets to Nov 10 at 9 AM
        val snooze7D = com.jyotirmoyb.udhaar.reminder.ReminderEngine.calculateSnoozeDate(7, nowMillis)
        val cal7D = java.util.Calendar.getInstance().apply { timeInMillis = snooze7D }
        assertEquals(10, cal7D.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(9, cal7D.get(java.util.Calendar.HOUR_OF_DAY))

        // formatSnoozedUntil produces "10 Nov" format
        val formatted = com.jyotirmoyb.udhaar.reminder.ReminderEngine.formatSnoozedUntil(snooze7D)
        assertTrue("Expected '10 Nov' or localized date, got: $formatted", formatted.contains("10") && (formatted.contains("Nov") || formatted.contains("11")))

        // Snoozing a transaction does NOT delete the transaction or change its due date
        val originalTx = UdhaarTransaction(
            id = 42L,
            transactionType = TransactionType.GIVEN,
            partyName = "Suresh",
            principalAmount = 8_000.0,
            expiryDateMillis = nowMillis + 5 * 86_400_000L
        )
        val snoozedTx = originalTx.copy(snoozedUntilMillis = snooze7D)
        assertEquals(originalTx.id, snoozedTx.id)
        assertEquals(originalTx.principalAmount, snoozedTx.principalAmount, 0.001)
        assertEquals(originalTx.expiryDateMillis, snoozedTx.expiryDateMillis)
        assertEquals(snooze7D, snoozedTx.snoozedUntilMillis)
    }

    @Test
    fun reminderStopRules_automaticCancellationConditions() {
        val now = 1_760_000_000_000L

        val activeTx = UdhaarTransaction(
            id = 1L,
            transactionType = TransactionType.GIVEN,
            partyName = "Anita",
            principalAmount = 10_000.0,
            expiryDateMillis = now - 86_400_000L,
            isSettled = false,
            remindersDisabled = false,
            snoozedUntilMillis = null,
            needsMyConfirmation = false
        )
        // Active transaction should NOT stop reminders
        assertFalse(com.jyotirmoyb.udhaar.reminder.ReminderEngine.shouldStopReminders(activeTx, now))

        // 1. Settled transaction -> must stop reminders
        val settledTx = activeTx.copy(isSettled = true)
        assertTrue(com.jyotirmoyb.udhaar.reminder.ReminderEngine.shouldStopReminders(settledTx, now))

        // 2. Explicitly disabled reminders -> must stop reminders
        val disabledTx = activeTx.copy(remindersDisabled = true)
        assertTrue(com.jyotirmoyb.udhaar.reminder.ReminderEngine.shouldStopReminders(disabledTx, now))

        // 3. Snoozed until future date -> must stop reminders
        val activeSnoozedTx = activeTx.copy(snoozedUntilMillis = now + 86_400_000L)
        assertTrue(com.jyotirmoyb.udhaar.reminder.ReminderEngine.shouldStopReminders(activeSnoozedTx, now))

        // 4. Snooze expired -> should resume reminders (shouldStopReminders returns false)
        val expiredSnoozedTx = activeTx.copy(snoozedUntilMillis = now - 1_000L)
        assertFalse(com.jyotirmoyb.udhaar.reminder.ReminderEngine.shouldStopReminders(expiredSnoozedTx, now))

        // 5. Waiting for payment proof confirmation from borrower -> must stop reminders
        val pendingProofTx = activeTx.copy(needsMyConfirmation = true)
        assertTrue(com.jyotirmoyb.udhaar.reminder.ReminderEngine.shouldStopReminders(pendingProofTx, now))
    }

    @Test
    fun reminderFrequencyLimit_maxOneReminderPerRecordPerDay() {
        val baseCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 10, 14, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nowMillis = baseCal.timeInMillis

        // Case 1: No previous reminder sent -> can send today
        val txNeverSent = UdhaarTransaction(id = 1L, partyName = "Priya", principalAmount = 1_000.0, lastReminderSentMillis = null)
        assertTrue(com.jyotirmoyb.udhaar.reminder.ReminderEngine.canSendReminderToday(txNeverSent, nowMillis))

        // Case 2: Reminder already sent today (e.g. 2 hours ago) -> CANNOT send again
        val twoHoursAgo = nowMillis - (2 * 3600_000L)
        val txSentToday = txNeverSent.copy(lastReminderSentMillis = twoHoursAgo)
        assertFalse(
            "Maximum one reminder per record per day must prevent duplicate sends on same day",
            com.jyotirmoyb.udhaar.reminder.ReminderEngine.canSendReminderToday(txSentToday, nowMillis)
        )

        // Case 3: Reminder sent yesterday (calendar day difference) -> CAN send today
        val yesterdayCal = (baseCal.clone() as java.util.Calendar).apply {
            add(java.util.Calendar.DAY_OF_YEAR, -1)
            set(java.util.Calendar.HOUR_OF_DAY, 20)
        }
        val txSentYesterday = txNeverSent.copy(lastReminderSentMillis = yesterdayCal.timeInMillis)
        assertTrue(
            "New day must allow sending reminder",
            com.jyotirmoyb.udhaar.reminder.ReminderEngine.canSendReminderToday(txSentYesterday, nowMillis)
        )
    }

    @Test
    fun reminderDrafts_renderAllPlaceholdersCorrectly() {
        val template = "Hi {name}, reminder from {my_name} for ₹{amount} (remaining ₹{remaining}, paid ₹{paid_so_far}) due {due_date}. Overdue {days_overdue} days. UPI: {upi_id}. New date: {new_date}."

        val rendered = com.jyotirmoyb.udhaar.reminder.ReminderEngine.renderDraftMessage(
            template = template,
            name = "Ravi",
            myName = "Jyotirmoy",
            amount = "5,000",
            remaining = "3,000",
            paidSoFar = "2,000",
            dueDate = "10 Nov 2026",
            daysOverdue = 4,
            upiId = "jyotus@upi",
            newDate = "25 Nov 2026"
        )

        assertEquals(
            "Hi Ravi, reminder from Jyotirmoy for ₹5,000 (remaining ₹3,000, paid ₹2,000) due 10 Nov 2026. Overdue 4 days. UPI: jyotus@upi. New date: 25 Nov 2026.",
            rendered
        )
        assertFalse("Rendered message should have no unresolved placeholders", rendered.contains("{"))
    }

    @Test
    fun plainLanguageStyleGuide_andNoBannedWordsOrAllCapsRemain() {
        val resDir = listOf(
            java.io.File("src/main/res"),
            java.io.File("app/src/main/res"),
            java.io.File("../app/src/main/res")
        ).firstOrNull { it.exists() && it.isDirectory }
            ?: error("Could not locate res directory")

        val allowList = setOf("UPI", "INR", "NPR", "OTP", "PDF", "QR", "OK", "SMS", "ID", "JSON", "CSV", "PIN", "APP")
        val bannedWords = listOf(
            "hash", "sha-256", "e.164", "keystore", "sqlcipher", "payload",
            "token", "counterparty", "audit", "vpa", "principal", "amend",
            "ledger", "immutable", "cryptographic"
        )

        val stringXmlRegex = Regex("""<string\s+name="([^"]+)"(?:\s+translatable="([^"]+)")?[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

        val enFile = java.io.File(resDir, "values/strings.xml")
        assertTrue("values/strings.xml must exist", enFile.exists())
        val enContent = enFile.readText()

        data class StringEntry(val key: String, val text: String, val translatable: Boolean)
        val enStrings = mutableMapOf<String, StringEntry>()
        stringXmlRegex.findAll(enContent).forEach { match ->
            val key = match.groupValues[1]
            val translatable = match.groupValues[2] != "false"
            val text = match.groupValues[3]
            enStrings[key] = StringEntry(key, text, translatable)
        }

        // 1. No user-visible strings are ALL CAPS outside allow-list
        val allCapsFailures = mutableListOf<String>()
        enStrings.values.filter { it.translatable }.forEach { entry ->
            val clean = entry.text.replace(Regex("""%[0-9]*\$?[a-zA-Z]"""), "")
                .replace(Regex("""&[a-z]+;"""), "")
                .replace(Regex("""\\['"]"""), "")
            val letters = clean.filter { it.isLetter() }
            if (letters.length >= 3 && letters == letters.uppercase()) {
                val words = clean.split(Regex("""[^a-zA-Z0-9]+""")).filter { it.any { c -> c.isLetter() } }
                val allAllowed = words.all { it.uppercase() in allowList }
                if (!allAllowed) {
                    allCapsFailures.add("${entry.key}: \"${entry.text}\"")
                }
            }
        }
        assertTrue("No user-visible strings should be ALL CAPS (found: $allCapsFailures)", allCapsFailures.isEmpty())

        // 2. No banned words in visible strings across all locales
        val bannedFailures = mutableListOf<String>()
        val locales = listOf("values", "values-hi", "values-bn", "values-as", "values-ne")
        locales.forEach { loc ->
            val file = java.io.File(resDir, "$loc/strings.xml")
            if (file.exists()) {
                stringXmlRegex.findAll(file.readText()).forEach { match ->
                    val key = match.groupValues[1]
                    val translatable = match.groupValues[2] != "false"
                    val text = match.groupValues[3]
                    if (translatable) {
                        bannedWords.forEach { bw ->
                            val r = Regex("""\b$bw\b""", RegexOption.IGNORE_CASE)
                            if (r.containsMatchIn(text)) {
                                bannedFailures.add("$loc / $key: contains '$bw' in \"$text\"")
                            }
                        }
                    }
                }
            }
        }
        assertTrue("No banned words should appear in visible strings (found: $bannedFailures)", bannedFailures.isEmpty())

        // 3. No untranslated keys in values-hi, values-bn, values-as, values-ne
        val untranslatedFailures = mutableListOf<String>()
        listOf("values-hi", "values-bn", "values-as", "values-ne").forEach { loc ->
            val file = java.io.File(resDir, "$loc/strings.xml")
            assertTrue("$loc/strings.xml must exist", file.exists())
            val locMap = stringXmlRegex.findAll(file.readText()).associate {
                it.groupValues[1] to StringEntry(it.groupValues[1], it.groupValues[3], it.groupValues[2] != "false")
            }

            enStrings.values.filter { it.translatable }.forEach { enEntry ->
                val locEntry = locMap[enEntry.key]
                if (locEntry == null) {
                    untranslatedFailures.add("$loc missing key: ${enEntry.key}")
                } else if (locEntry.text.isBlank()) {
                    untranslatedFailures.add("$loc blank key: ${enEntry.key}")
                } else if (locEntry.text.trim() == enEntry.text.trim()) {
                    val cleanLetters = locEntry.text.replace(Regex("""%[0-9]*\$?[a-zA-Z]"""), "").filter { it.isLetter() }
                    if (cleanLetters.isNotEmpty() && cleanLetters.uppercase() !in allowList) {
                        untranslatedFailures.add("$loc identical to English: ${enEntry.key} => \"${locEntry.text}\"")
                    }
                }
            }
        }
        assertTrue("No untranslated strings outside allow-list (found: $untranslatedFailures)", untranslatedFailures.isEmpty())

        // 4. Zero hardcoded UI literals in Kotlin UI files
        val uiDir = listOf(
            java.io.File("src/main/java/com/jyotirmoyb/udhaar/ui"),
            java.io.File("app/src/main/java/com/jyotirmoyb/udhaar/ui"),
            java.io.File("../app/src/main/java/com/jyotirmoyb/udhaar/ui")
        ).firstOrNull { it.exists() && it.isDirectory }
            ?: error("Could not locate ui directory")

        val uiLiterals = mutableListOf<String>()
        val textRegex = Regex("""\bText\(\s*(?:text\s*=\s*)?"([^"$]+)"""")
        val toastRegex = Regex("""Toast\.makeText\([^,]+,\s*"([^"$]+)"""")
        val clipRegex = Regex("""ClipData\.newPlainText\(\s*"([^"$]+)"""")

        uiDir.walkTopDown().filter { it.extension == "kt" }.forEach { ktFile ->
            ktFile.readLines().forEachIndexed { lineNum, line ->
                textRegex.find(line)?.let { m ->
                    val text = m.groupValues[1].trim()
                    if (text.isNotEmpty() && !text.startsWith("test_") && !text.all { it in "• -/\\,.:|0123456789" }) {
                        uiLiterals.add("${ktFile.name}:${lineNum + 1} Text: $text")
                    }
                }
                toastRegex.find(line)?.let { m ->
                    uiLiterals.add("${ktFile.name}:${lineNum + 1} Toast: ${m.groupValues[1]}")
                }
                clipRegex.find(line)?.let { m ->
                    uiLiterals.add("${ktFile.name}:${lineNum + 1} ClipData: ${m.groupValues[1]}")
                }
            }
        }
        assertTrue("No hardcoded UI literals in Kotlin UI files (found: $uiLiterals)", uiLiterals.isEmpty())
    }
}
