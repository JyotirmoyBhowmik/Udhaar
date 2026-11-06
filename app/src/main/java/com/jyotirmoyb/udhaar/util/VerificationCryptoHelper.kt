package com.jyotirmoyb.udhaar.util

import com.jyotirmoyb.udhaar.data.CompoundFrequency
import com.jyotirmoyb.udhaar.data.InterestRatePeriod
import com.jyotirmoyb.udhaar.data.InterestType
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic & link utilities for Phase 3 Two-Party Verification:
 * 1. Canonical role-agnostic SHA-256 contentHash of party names, amount, currency, dates, type & interest terms.
 *    Because lenderName and borrowerName are canonicalized independently of whether the local viewer is lender
 *    (GIVEN) or borrower (TAKEN), both sides see the exact same 64-char SHA-256 contentHash.
 * 2. End-to-End Encryption (AES-256-GCM) for sensitive notes and attachment references stored in Cloud Firestore,
 *    using a key derived from the shared verification token so the backend only stores ciphertext.
 * 3. Self-contained & App-Link-ready URL encoding/decoding (`https://udhaar.jyotirmoyb.com/u/<token>`) for records
 *    and payment profiles (`https://udhaar.jyotirmoyb.com/u/p/<token>`).
 */
object VerificationCryptoHelper {

    const val VERIFICATION_URL_PREFIX = "https://udhaar.jyotirmoyb.com/u/"
    const val PROFILE_URL_PREFIX = "https://udhaar.jyotirmoyb.com/u/p/"
    private const val SHORT_ID_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    /**
     * Generates a compact 10-character alphanumeric record identifier (~60 bits of entropy)
     * so guest and verification URLs stay short and clean in WhatsApp messages.
     */
    fun generateShortId(length: Int = 10): String {
        val random = SecureRandom()
        return buildString(length) {
            repeat(length) {
                append(SHORT_ID_ALPHABET[random.nextInt(SHORT_ID_ALPHABET.length)])
            }
        }
    }

    /**
     * Generates a 16-character URL-safe Base64 token (96 bits of cryptographic randomness).
     * Meets the >= 16-char server token requirement while keeping shared links compact.
     */
    fun generateShareToken(): String {
        val randomBytes = ByteArray(12)
        SecureRandom().nextBytes(randomBytes)
        return base64UrlEncode(randomBytes)
    }

    private fun base64UrlEncode(bytes: ByteArray): String {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun base64UrlDecode(encoded: String): ByteArray {
        return Base64.getUrlDecoder().decode(encoded)
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun urlDecode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private fun escapePipeField(value: String): String =
        value.replace("%", "%25").replace("|", "%7C")

    private fun unescapePipeField(value: String): String =
        value.replace("%7C", "|").replace("%7c", "|").replace("%25", "%")

    private fun formatCompactNumber(value: Double): String {
        val rounded2 = String.format(Locale.US, "%.2f", value)
        return if (rounded2.endsWith(".00")) {
            rounded2.removeSuffix(".00")
        } else if (rounded2.endsWith("0")) {
            rounded2.removeSuffix("0")
        } else {
            rounded2
        }
    }

    /**
     * Resolves canonical (lenderName, borrowerName) for a transaction so that a GIVEN record on Lender's phone
     * and the mirrored TAKEN record on Borrower's phone produce the identical SHA-256 contentHash.
     */
    fun resolveLenderAndBorrowerNames(
        type: TransactionType,
        partyName: String,
        myProfileName: String,
        explicitLenderName: String = "",
        explicitBorrowerName: String = ""
    ): Pair<String, String> {
        if (explicitLenderName.isNotBlank() && explicitBorrowerName.isNotBlank()) {
            return explicitLenderName.trim() to explicitBorrowerName.trim()
        }
        val cleanParty = partyName.trim().ifEmpty { "Counterparty" }
        val cleanSelf = myProfileName.trim().ifEmpty { "Udhaar User" }
        return if (type == TransactionType.GIVEN) {
            cleanSelf to cleanParty
        } else {
            cleanParty to cleanSelf
        }
    }

    /**
     * Computes deterministic 64-character lowercase hex SHA-256 hash of canonical transaction terms:
     * party (lender + borrower), amount, currency, dates, canonical direction, and interest terms.
     */
    fun computeContentHash(
        lenderName: String,
        borrowerName: String,
        principalAmount: Double,
        currencyCode: String,
        startDateMillis: Long,
        expiryDateMillis: Long?,
        isInterestEnabled: Boolean,
        interestType: InterestType,
        interestRate: Double,
        interestRatePeriod: InterestRatePeriod,
        compoundFrequency: CompoundFrequency,
        plan: com.jyotirmoyb.udhaar.data.InstallmentPlan? = null,
        installments: List<com.jyotirmoyb.udhaar.data.Installment> = emptyList()
    ): String {
        val normalizedLender = lenderName.trim().lowercase(Locale.ROOT)
        val normalizedBorrower = borrowerName.trim().lowercase(Locale.ROOT)
        val formattedAmount = String.format(Locale.US, "%.2f", principalAmount)
        val normalizedCurrency = currencyCode.trim().uppercase(Locale.ROOT)
        val normalizedExpiry = expiryDateMillis ?: 0L
        val interestSegment = if (isInterestEnabled) {
            String.format(
                Locale.US,
                "1|%s|%.4f|%s|%s",
                interestType.name,
                interestRate,
                interestRatePeriod.name,
                compoundFrequency.name
            )
        } else {
            "0|NONE|0.0000|NONE|NONE"
        }

        val canonicalString = buildString {
            append("v1|")
            append("lender=").append(normalizedLender).append('|')
            append("borrower=").append(normalizedBorrower).append('|')
            append("amount=").append(formattedAmount).append('|')
            append("currency=").append(normalizedCurrency).append('|')
            append("start=").append(startDateMillis).append('|')
            append("expiry=").append(normalizedExpiry).append('|')
            append("interest=").append(interestSegment)
            if (plan != null && installments.isNotEmpty()) {
                append('|').append("plan=")
                    .append(plan.installmentCount).append(';')
                    .append(plan.frequency.name).append(';')
                    .append(plan.interestMode.name).append(';')
                    .append(plan.totalAmountDue)
                append('|').append("inst=")
                append(
                    installments.sortedBy { it.sequence }
                        .joinToString(";") { "${it.sequence}:${it.dueDate}:${it.amountDue}" }
                )
            }
        }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonicalString.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun computeTransactionContentHash(
        tx: UdhaarTransaction,
        currencyCode: String = tx.currency.code,
        myProfileName: String = "Udhaar User",
        plan: com.jyotirmoyb.udhaar.data.InstallmentPlan? = null,
        installments: List<com.jyotirmoyb.udhaar.data.Installment> = emptyList()
    ): String {
        val (lender, borrower) = resolveLenderAndBorrowerNames(
            type = tx.transactionType,
            partyName = tx.partyName,
            myProfileName = myProfileName,
            explicitLenderName = tx.lenderName,
            explicitBorrowerName = tx.borrowerName
        )
        return computeContentHash(
            lenderName = lender,
            borrowerName = borrower,
            principalAmount = InterestCalculator.paiseToRupees(tx.principalAmountPaise),
            currencyCode = currencyCode,
            startDateMillis = tx.startDateMillis,
            expiryDateMillis = tx.expiryDateMillis,
            isInterestEnabled = tx.isInterestEnabled,
            interestType = tx.interestType,
            interestRate = tx.interestRate,
            interestRatePeriod = tx.interestRatePeriod,
            compoundFrequency = tx.compoundFrequency,
            plan = plan,
            installments = installments
        )
    }

    /**
     * End-to-End Encryption (AES-256-GCM) using a key derived from the record's secret shareToken + remoteId.
     * Ensures Cloud Firestore only sees authenticated ciphertext for notes and attachment references.
     */
    fun encryptE2eeField(plainText: String, remoteId: String, shareToken: String): String {
        if (plainText.isBlank()) return ""
        return try {
            val keyBytes = deriveAesKey(remoteId, shareToken)
            val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
            val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(iv.size + cipherBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherBytes, 0, combined, iv.size, cipherBytes.size)
            base64UrlEncode(combined)
        } catch (e: Exception) {
            ""
        }
    }

    fun decryptE2eeField(cipherText: String, remoteId: String, shareToken: String): String {
        if (cipherText.isBlank()) return ""
        return try {
            val combined = base64UrlDecode(cipherText)
            if (combined.size <= 12) return ""
            val iv = combined.copyOfRange(0, 12)
            val encrypted = combined.copyOfRange(12, combined.size)
            val keyBytes = deriveAesKey(remoteId, shareToken)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    private fun deriveAesKey(remoteId: String, shareToken: String): ByteArray {
        val material = "udhaar-e2ee-v1|$remoteId|$shareToken"
        return MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(StandardCharsets.UTF_8))
    }

    data class SharedVerificationPayload(
        val remoteId: String,
        val shareToken: String,
        val creatorUserId: String,
        val creatorRole: TransactionType,
        val lenderName: String,
        val borrowerName: String,
        val creatorPhone: String,
        val counterpartyPhone: String,
        val principalAmount: Double,
        val currencyCode: String,
        val startDateMillis: Long,
        val expiryDateMillis: Long?,
        val isInterestEnabled: Boolean,
        val interestType: InterestType,
        val interestRate: Double,
        val interestRatePeriod: InterestRatePeriod,
        val compoundFrequency: CompoundFrequency,
        val contentHash: String,
        val encryptedNote: String = ""
    )

    data class SharedPaymentProfilePayload(
        val userId: String,
        val displayName: String,
        val phoneNumber: String,
        val upiId: String,
        val photoUrl: String = ""
    )

    data class GuestWebLinkData(
        val recordId: String,
        val secret: String
    )

    /**
     * Generates a compact verification URL (`https://udhaar.jyotirmoyb.com/u/<token>`) that acts both
     * as an Android App Link and a web fallback URL.
     * Uses compact `v2|` pipe-delimited serialization that omits default/recomputable fields
     * (reducing URL length by ~70% compared to legacy v1 query strings).
     */
    fun buildVerificationLink(payload: SharedVerificationPayload): String {
        val roleCode = if (payload.creatorRole == TransactionType.TAKEN) "T" else "G"
        val curCode = if (payload.currencyCode.equals("INR", ignoreCase = true)) "" else payload.currencyCode
        val start36 = payload.startDateMillis.toString(36)
        val exp36 = payload.expiryDateMillis?.takeIf { it > 0L }?.toString(36).orEmpty()
        val intSpec = if (payload.isInterestEnabled) {
            val typeShort = if (payload.interestType == InterestType.COMPOUND) "C" else "S"
            val periodShort = when (payload.interestRatePeriod) {
                InterestRatePeriod.PER_MONTH -> "M"
                InterestRatePeriod.PER_ANNUM -> "A"
            }
            val freqShort = when (payload.compoundFrequency) {
                CompoundFrequency.MONTHLY -> "M"
                CompoundFrequency.QUARTERLY -> "Q"
                CompoundFrequency.YEARLY -> "Y"
            }
            "$typeShort:${formatCompactNumber(payload.interestRate)}:$periodShort:$freqShort"
        } else {
            ""
        }
        val expectedHash = computeContentHash(
            lenderName = payload.lenderName,
            borrowerName = payload.borrowerName,
            principalAmount = payload.principalAmount,
            currencyCode = payload.currencyCode,
            startDateMillis = payload.startDateMillis,
            expiryDateMillis = payload.expiryDateMillis,
            isInterestEnabled = payload.isInterestEnabled,
            interestType = payload.interestType,
            interestRate = payload.interestRate,
            interestRatePeriod = payload.interestRatePeriod,
            compoundFrequency = payload.compoundFrequency
        )
        val customHash = if (payload.contentHash.isNotBlank() && !payload.contentHash.equals(expectedHash, ignoreCase = true)) {
            payload.contentHash
        } else {
            ""
        }

        val fields = mutableListOf(
            "v2",
            escapePipeField(payload.remoteId),
            escapePipeField(payload.shareToken),
            roleCode,
            escapePipeField(payload.lenderName),
            escapePipeField(payload.borrowerName),
            formatCompactNumber(payload.principalAmount),
            escapePipeField(curCode),
            start36,
            exp36,
            intSpec,
            escapePipeField(payload.creatorPhone),
            escapePipeField(payload.counterpartyPhone),
            escapePipeField(payload.creatorUserId),
            escapePipeField(payload.encryptedNote),
            escapePipeField(customHash)
        )
        while (fields.size > 9 && fields.last().isEmpty()) {
            fields.removeAt(fields.lastIndex)
        }
        val serialized = fields.joinToString("|")
        val encoded = base64UrlEncode(serialized.toByteArray(StandardCharsets.UTF_8))
        return "$VERIFICATION_URL_PREFIX$encoded"
    }

    fun parseVerificationLink(input: String): SharedVerificationPayload? {
        val trimmed = input.trim()
        if (trimmed.startsWith(PROFILE_URL_PREFIX)) return null
        val tokenPart = when {
            trimmed.startsWith(VERIFICATION_URL_PREFIX) -> trimmed.removePrefix(VERIFICATION_URL_PREFIX)
            trimmed.contains("/u/") && !trimmed.contains("/u/p/") && !trimmed.contains("/u/r/") ->
                trimmed.substringAfter("/u/")
            else -> trimmed
        }.substringBefore("?").substringBefore("#").trim()

        if (tokenPart.isBlank()) return null
        return try {
            val decodedString = String(base64UrlDecode(tokenPart), StandardCharsets.UTF_8)
            if (decodedString.startsWith("v2|")) {
                parseCompactV2VerificationLink(decodedString)
            } else {
                parseLegacyVerificationLink(decodedString)
            }
        } catch (e: Exception) {
            SafeLogger.w("VerificationCrypto", "Failed to parse verification link", e)
            null
        }
    }

    private fun parseCompactV2VerificationLink(decodedString: String): SharedVerificationPayload? {
        val parts = decodedString.split("|")
        if (parts.size < 9) return null
        val rid = unescapePipeField(parts.getOrElse(1) { "" })
        if (rid.isBlank()) return null
        val tok = unescapePipeField(parts.getOrElse(2) { "" })
        val role = if (parts.getOrElse(3) { "G" } == "T") TransactionType.TAKEN else TransactionType.GIVEN
        val lender = unescapePipeField(parts.getOrElse(4) { "" }).ifBlank { "Lender" }
        val borrower = unescapePipeField(parts.getOrElse(5) { "" }).ifBlank { "Borrower" }
        val amt = parts.getOrElse(6) { "0" }.toDoubleOrNull() ?: 0.0
        val cur = unescapePipeField(parts.getOrElse(7) { "" }).ifBlank { "INR" }
        val startMs = parts.getOrElse(8) { "" }.toLongOrNull(36) ?: System.currentTimeMillis()
        val expMs = parts.getOrElse(9) { "" }.takeIf { it.isNotBlank() }?.toLongOrNull(36)?.takeIf { it > 0L }
        val intSpec = parts.getOrElse(10) { "" }
        val isInterestEnabled = intSpec.isNotBlank()
        var interestType = InterestType.SIMPLE
        var interestRate = 0.0
        var interestRatePeriod = InterestRatePeriod.PER_ANNUM
        var compoundFrequency = CompoundFrequency.YEARLY
        if (isInterestEnabled) {
            val iParts = intSpec.split(":")
            interestType = if (iParts.getOrElse(0) { "S" } == "C") InterestType.COMPOUND else InterestType.SIMPLE
            interestRate = iParts.getOrElse(1) { "0" }.toDoubleOrNull() ?: 0.0
            interestRatePeriod = when (iParts.getOrElse(2) { "A" }) {
                "M" -> InterestRatePeriod.PER_MONTH
                else -> InterestRatePeriod.PER_ANNUM
            }
            compoundFrequency = when (iParts.getOrElse(3) { "Y" }) {
                "M" -> CompoundFrequency.MONTHLY
                "Q" -> CompoundFrequency.QUARTERLY
                else -> CompoundFrequency.YEARLY
            }
        }
        val cPhone = unescapePipeField(parts.getOrElse(11) { "" })
        val pPhone = unescapePipeField(parts.getOrElse(12) { "" })
        val uid = unescapePipeField(parts.getOrElse(13) { "" })
        val enote = unescapePipeField(parts.getOrElse(14) { "" })
        val customHash = unescapePipeField(parts.getOrElse(15) { "" })
        val hash = customHash.ifBlank {
            computeContentHash(
                lenderName = lender,
                borrowerName = borrower,
                principalAmount = amt,
                currencyCode = cur,
                startDateMillis = startMs,
                expiryDateMillis = expMs,
                isInterestEnabled = isInterestEnabled,
                interestType = interestType,
                interestRate = interestRate,
                interestRatePeriod = interestRatePeriod,
                compoundFrequency = compoundFrequency
            )
        }
        return SharedVerificationPayload(
            remoteId = rid,
            shareToken = tok,
            creatorUserId = uid,
            creatorRole = role,
            lenderName = lender,
            borrowerName = borrower,
            creatorPhone = cPhone,
            counterpartyPhone = pPhone,
            principalAmount = amt,
            currencyCode = cur,
            startDateMillis = startMs,
            expiryDateMillis = expMs,
            isInterestEnabled = isInterestEnabled,
            interestType = interestType,
            interestRate = interestRate,
            interestRatePeriod = interestRatePeriod,
            compoundFrequency = compoundFrequency,
            contentHash = hash,
            encryptedNote = enote
        )
    }

    private fun parseLegacyVerificationLink(decodedString: String): SharedVerificationPayload? {
        val map = parseQueryLikeString(decodedString)
        val rid = map["rid"].orEmpty()
        if (rid.isBlank()) return null
        val expRaw = map["exp"]?.toLongOrNull() ?: 0L
        val lender = map["lender"] ?: "Lender"
        val borrower = map["borrower"] ?: "Borrower"
        val amt = map["amt"]?.toDoubleOrNull() ?: 0.0
        val cur = map["cur"] ?: "INR"
        val startMs = map["start"]?.toLongOrNull() ?: System.currentTimeMillis()
        val expMs = if (expRaw > 0L) expRaw else null
        val isInterestEnabled = map["ie"]?.toBooleanStrictOrNull() ?: false
        val interestType = runCatching { InterestType.valueOf(map["it"] ?: "SIMPLE") }
            .getOrDefault(InterestType.SIMPLE)
        val interestRate = map["ir"]?.toDoubleOrNull() ?: 0.0
        val interestRatePeriod = runCatching { InterestRatePeriod.valueOf(map["irp"] ?: "PER_ANNUM") }
            .getOrDefault(InterestRatePeriod.PER_ANNUM)
        val compoundFrequency = runCatching { CompoundFrequency.valueOf(map["cf"] ?: "YEARLY") }
            .getOrDefault(CompoundFrequency.YEARLY)
        val hash = map["hash"].orEmpty().ifBlank {
            computeContentHash(
                lenderName = lender,
                borrowerName = borrower,
                principalAmount = amt,
                currencyCode = cur,
                startDateMillis = startMs,
                expiryDateMillis = expMs,
                isInterestEnabled = isInterestEnabled,
                interestType = interestType,
                interestRate = interestRate,
                interestRatePeriod = interestRatePeriod,
                compoundFrequency = compoundFrequency
            )
        }
        return SharedVerificationPayload(
            remoteId = rid,
            shareToken = map["tok"].orEmpty(),
            creatorUserId = map["uid"].orEmpty(),
            creatorRole = runCatching { TransactionType.valueOf(map["role"] ?: "GIVEN") }
                .getOrDefault(TransactionType.GIVEN),
            lenderName = lender,
            borrowerName = borrower,
            creatorPhone = map["cPhone"].orEmpty(),
            counterpartyPhone = map["pPhone"].orEmpty(),
            principalAmount = amt,
            currencyCode = cur,
            startDateMillis = startMs,
            expiryDateMillis = expMs,
            isInterestEnabled = isInterestEnabled,
            interestType = interestType,
            interestRate = interestRate,
            interestRatePeriod = interestRatePeriod,
            compoundFrequency = compoundFrequency,
            contentHash = hash,
            encryptedNote = map["enote"].orEmpty()
        )
    }

    /**
     * Parses a guest web link of format: https://udhaar.jyotirmoyb.com/u/r/<recordId>#<secret>
     * Extracts recordId from the path and the 128-bit secret from the URL fragment.
     */
    fun parseGuestWebLink(input: String): GuestWebLinkData? {
        val trimmed = input.trim()
        if (!trimmed.contains("/u/r/")) return null
        val afterPrefix = trimmed.substringAfter("/u/r/")
        val recordId = afterPrefix.substringBefore("#").substringBefore("?").substringBefore("/").trim()
        val secret = if (trimmed.contains("#")) {
            trimmed.substringAfter("#").substringBefore("?").trim()
        } else {
            ""
        }
        if (recordId.isBlank()) return null
        return GuestWebLinkData(recordId = recordId, secret = secret)
    }

    /**
     * Constructs a guest web link with random base64url secret in the fragment:
     * https://udhaar.jyotirmoyb.com/u/r/<recordId>#<secret>
     */
    fun buildGuestWebLink(recordId: String, secret: String): String {
        return "https://udhaar.jyotirmoyb.com/u/r/$recordId#$secret"
    }

    /**
     * Generates a compact shareable Payment Profile link (`https://udhaar.jyotirmoyb.com/u/p/<token>`)
     * for "Share my payment details" and QR scanning.
     */
    fun buildPaymentProfileLink(profile: SharedPaymentProfilePayload): String {
        val fields = mutableListOf(
            "p2",
            escapePipeField(profile.displayName),
            escapePipeField(profile.upiId),
            escapePipeField(profile.phoneNumber),
            escapePipeField(profile.userId),
            escapePipeField(profile.photoUrl)
        )
        while (fields.size > 3 && fields.last().isEmpty()) {
            fields.removeAt(fields.lastIndex)
        }
        val serialized = fields.joinToString("|")
        val encoded = base64UrlEncode(serialized.toByteArray(StandardCharsets.UTF_8))
        return "$PROFILE_URL_PREFIX$encoded"
    }

    fun parsePaymentProfileLink(input: String): SharedPaymentProfilePayload? {
        val trimmed = input.trim()
        val tokenPart = when {
            trimmed.startsWith(PROFILE_URL_PREFIX) -> trimmed.removePrefix(PROFILE_URL_PREFIX)
            trimmed.contains("/u/p/") -> trimmed.substringAfter("/u/p/")
            else -> return null
        }.substringBefore("?").substringBefore("#").trim()

        if (tokenPart.isBlank()) return null
        return try {
            val decodedString = String(base64UrlDecode(tokenPart), StandardCharsets.UTF_8)
            if (decodedString.startsWith("p2|")) {
                val parts = decodedString.split("|")
                val name = unescapePipeField(parts.getOrElse(1) { "" })
                val upi = unescapePipeField(parts.getOrElse(2) { "" })
                if (name.isBlank() && upi.isBlank()) return null
                SharedPaymentProfilePayload(
                    userId = unescapePipeField(parts.getOrElse(4) { "" }),
                    displayName = name,
                    phoneNumber = unescapePipeField(parts.getOrElse(3) { "" }),
                    upiId = upi,
                    photoUrl = unescapePipeField(parts.getOrElse(5) { "" })
                )
            } else {
                val map = parseQueryLikeString(decodedString)
                val name = map["name"].orEmpty()
                val upi = map["upi"].orEmpty()
                if (name.isBlank() && upi.isBlank()) return null
                SharedPaymentProfilePayload(
                    userId = map["uid"].orEmpty(),
                    displayName = name,
                    phoneNumber = map["phone"].orEmpty(),
                    upiId = upi,
                    photoUrl = map["photo"].orEmpty()
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseQueryLikeString(serialized: String): Map<String, String> {
        return serialized.split("&")
            .mapNotNull { entry ->
                val idx = entry.indexOf('=')
                if (idx <= 0) null
                else entry.substring(0, idx) to urlDecode(entry.substring(idx + 1))
            }
            .toMap()
    }
}
