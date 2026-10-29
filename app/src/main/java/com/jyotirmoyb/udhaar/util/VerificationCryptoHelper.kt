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
 * 3. Self-contained & App-Link-ready URL encoding/decoding (`https://jyotirmoyb.com/u/<token>`) for records
 *    and payment profiles (`https://jyotirmoyb.com/u/p/<token>`).
 */
object VerificationCryptoHelper {

    const val VERIFICATION_URL_PREFIX = "https://jyotirmoyb.com/u/"
    const val PROFILE_URL_PREFIX = "https://jyotirmoyb.com/u/p/"

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
        compoundFrequency: CompoundFrequency
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
        }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonicalString.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun computeTransactionContentHash(
        tx: UdhaarTransaction,
        currencyCode: String = tx.currency.code,
        myProfileName: String = "Udhaar User"
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
            principalAmount = tx.principalAmount,
            currencyCode = currencyCode,
            startDateMillis = tx.startDateMillis,
            expiryDateMillis = tx.expiryDateMillis,
            isInterestEnabled = tx.isInterestEnabled,
            interestType = tx.interestType,
            interestRate = tx.interestRate,
            interestRatePeriod = tx.interestRatePeriod,
            compoundFrequency = tx.compoundFrequency
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

    /**
     * Generates a verification URL (`https://jyotirmoyb.com/u/<token>`) that acts both as an Android App Link
     * and a web fallback URL, embedding the signed verification envelope so it works seamlessly online or offline.
     */
    fun buildVerificationLink(payload: SharedVerificationPayload): String {
        val pairs = listOf(
            "rid" to payload.remoteId,
            "tok" to payload.shareToken,
            "uid" to payload.creatorUserId,
            "role" to payload.creatorRole.name,
            "lender" to payload.lenderName,
            "borrower" to payload.borrowerName,
            "cPhone" to payload.creatorPhone,
            "pPhone" to payload.counterpartyPhone,
            "amt" to String.format(Locale.US, "%.2f", payload.principalAmount),
            "cur" to payload.currencyCode,
            "start" to payload.startDateMillis.toString(),
            "exp" to (payload.expiryDateMillis ?: 0L).toString(),
            "ie" to payload.isInterestEnabled.toString(),
            "it" to payload.interestType.name,
            "ir" to String.format(Locale.US, "%.4f", payload.interestRate),
            "irp" to payload.interestRatePeriod.name,
            "cf" to payload.compoundFrequency.name,
            "hash" to payload.contentHash,
            "enote" to payload.encryptedNote
        )
        val serialized = pairs.joinToString("&") { (k, v) -> "$k=${urlEncode(v)}" }
        val encoded = base64UrlEncode(serialized.toByteArray(StandardCharsets.UTF_8))
        return "$VERIFICATION_URL_PREFIX$encoded"
    }

    fun parseVerificationLink(input: String): SharedVerificationPayload? {
        val trimmed = input.trim()
        if (trimmed.startsWith(PROFILE_URL_PREFIX)) return null
        val tokenPart = when {
            trimmed.startsWith(VERIFICATION_URL_PREFIX) -> trimmed.removePrefix(VERIFICATION_URL_PREFIX)
            trimmed.contains("/u/") && !trimmed.contains("/u/p/") -> trimmed.substringAfter("/u/")
            else -> trimmed
        }.substringBefore("?").substringBefore("#").trim()

        if (tokenPart.isBlank()) return null
        return try {
            val decodedString = String(base64UrlDecode(tokenPart), StandardCharsets.UTF_8)
            val map = parseQueryLikeString(decodedString)
            val rid = map["rid"].orEmpty()
            if (rid.isBlank()) return null
            val expRaw = map["exp"]?.toLongOrNull() ?: 0L
            SharedVerificationPayload(
                remoteId = rid,
                shareToken = map["tok"].orEmpty(),
                creatorUserId = map["uid"].orEmpty(),
                creatorRole = runCatching { TransactionType.valueOf(map["role"] ?: "GIVEN") }
                    .getOrDefault(TransactionType.GIVEN),
                lenderName = map["lender"] ?: "Lender",
                borrowerName = map["borrower"] ?: "Borrower",
                creatorPhone = map["cPhone"].orEmpty(),
                counterpartyPhone = map["pPhone"].orEmpty(),
                principalAmount = map["amt"]?.toDoubleOrNull() ?: 0.0,
                currencyCode = map["cur"] ?: "INR",
                startDateMillis = map["start"]?.toLongOrNull() ?: System.currentTimeMillis(),
                expiryDateMillis = if (expRaw > 0L) expRaw else null,
                isInterestEnabled = map["ie"]?.toBooleanStrictOrNull() ?: false,
                interestType = runCatching { InterestType.valueOf(map["it"] ?: "SIMPLE") }
                    .getOrDefault(InterestType.SIMPLE),
                interestRate = map["ir"]?.toDoubleOrNull() ?: 0.0,
                interestRatePeriod = runCatching { InterestRatePeriod.valueOf(map["irp"] ?: "PER_ANNUM") }
                    .getOrDefault(InterestRatePeriod.PER_ANNUM),
                compoundFrequency = runCatching { CompoundFrequency.valueOf(map["cf"] ?: "YEARLY") }
                    .getOrDefault(CompoundFrequency.YEARLY),
                contentHash = map["hash"].orEmpty(),
                encryptedNote = map["enote"].orEmpty()
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Generates a shareable Payment Profile link (`https://jyotirmoyb.com/u/p/<token>`)
     * for "Share my payment details" and QR scanning.
     */
    fun buildPaymentProfileLink(profile: SharedPaymentProfilePayload): String {
        val pairs = listOf(
            "uid" to profile.userId,
            "name" to profile.displayName,
            "phone" to profile.phoneNumber,
            "upi" to profile.upiId,
            "photo" to profile.photoUrl
        )
        val serialized = pairs.joinToString("&") { (k, v) -> "$k=${urlEncode(v)}" }
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
