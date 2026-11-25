package com.jyotirmoyb.udhaar.data.cloud

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.jyotirmoyb.udhaar.util.SafeLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

data class GuestWebLinkCreateResult(
    val success: Boolean,
    val url: String = "",
    val shortUrl: String = "",
    val recordId: String = "",
    val secret: String = "",
    val linkId: String = "",
    val expiresAtMillis: Long = 0L,
    val trustLevel: String = "LINK_ONLY",
    val whatsappOtp: String = "",
    val errorMessage: String = ""
)

sealed class GuestLinkFetchResult {
    data class Success(
        val payload: com.jyotirmoyb.udhaar.util.VerificationCryptoHelper.SharedVerificationPayload
    ) : GuestLinkFetchResult()

    data class FallbackToBrowser(val reason: String) : GuestLinkFetchResult()
    data class InvalidOrExpired(val reason: String) : GuestLinkFetchResult()
}

/**
 * Spark-native Cloud Repository for Web Guest Links (`/guestLinks/{linkId}`).
 *
 * In Udhaar Spark Mode:
 * - The lender Android app creates `/guestLinks/{linkId}` directly in Cloud Firestore
 *   where `linkId = sha256(secret)` and the guest URL carries `#k=<secret>` in the URL fragment
 *   (`https://udhaar.jyotirmoyb.com/u/#k=<secret>`).
 * - Secret is 32 cryptographically random bytes (Base64URL encoded without padding).
 * - The secret itself is NEVER stored in the database or sent in query parameters.
 * - Revoking a link updates `/guestLinks/{linkId}` with `status = "REVOKED"` and confirms via server readback.
 * - Guest actions are polled via `GuestActionPollingWorker` using a collection-group query
 *   on `actions` filtered by `ownerUid == currentUser.uid`.
 * - Zero Cloud Functions (`httpsCallable`) are invoked in Spark Mode.
 */
@Singleton
open class GuestWebCloudRepository @Inject constructor(
    private val firestoreOverride: FirebaseFirestore? = null,
    @Suppress("UNUSED_PARAMETER") functionsUnused: Any? = null,
    private val authOverride: FirebaseAuth? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    constructor() : this(null, null, null, Dispatchers.IO)

    constructor(
        authOverride: FirebaseAuth?,
        firestoreOverride: FirebaseFirestore?,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        firestoreOverride = firestoreOverride,
        functionsUnused = null,
        authOverride = authOverride,
        ioDispatcher = ioDispatcher
    )

    companion object {
        const val WEB_BASE_URL = "https://udhaar.jyotirmoyb.com"
        private val secureRandom = SecureRandom()

        /**
         * Generates a 32-byte cryptographic random secret, Base64URL-encoded without padding.
         */
        fun generateSecretBase64Url(bytesLength: Int = 32): String {
            val bytes = ByteArray(bytesLength)
            secureRandom.nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }

        fun generateSecretHex(bytesLength: Int = 16): String {
            val bytes = ByteArray(bytesLength)
            secureRandom.nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Publishes a capability guest link document under `/guestLinks/{linkId}`.
     * Fails closed if user is signed out, Firestore is unavailable, write throws, or readback validation fails.
     */
    open suspend fun createGuestWebLink(
        recordId: String,
        counterpartyRole: String = "BORROWER",
        intendedPhone: String = "",
        expiryDays: Int = 30,
        trustLevelRequired: String = "LINK_ONLY",
        allowedActions: List<String> = listOf("CONFIRM", "DISPUTE", "PROPOSE_DATE", "CLAIM_PAYMENT"),
        allowNote: Boolean = true,
        allowAttachments: Boolean = true,
        showUpiQr: Boolean = true,
        lenderName: String = "",
        borrowerName: String = "",
        principalAmountPaise: Long = 0L,
        amountPaidPaise: Long = 0L,
        currency: String = "INR",
        startDateMillis: Long = System.currentTimeMillis(),
        expiryDateMillis: Long? = null,
        note: String = "",
        contentHash: String = "",
        lenderUpiId: String = "",
        creatorPhone: String = "",
        expectedGuestEmail: String = ""
    ): GuestWebLinkCreateResult = withContext(ioDispatcher) {
        val auth = authOverride ?: runCatching { FirebaseAuth.getInstance() }.getOrNull()
        val currentUser = auth?.currentUser
        if (currentUser == null) {
            SafeLogger.w("GuestWebCloudRepo", "createGuestWebLink aborted: Signed-in user missing")
            return@withContext GuestWebLinkCreateResult(
                success = false,
                errorMessage = "Sign-in required"
            )
        }

        val firestore = firestoreOverride ?: com.jyotirmoyb.udhaar.di.FirebaseModule.getFirestore()
        if (firestore == null) {
            SafeLogger.w("GuestWebCloudRepo", "createGuestWebLink aborted: Firestore unavailable")
            return@withContext GuestWebLinkCreateResult(
                success = false,
                errorMessage = "Cloud database unavailable"
            )
        }

        val now = System.currentTimeMillis()
        val expiresAt = now + expiryDays.coerceIn(1, 365) * 86_400_000L
        val secret = generateSecretBase64Url(32)
        val linkId = sha256Hex(secret)
        val capabilityUrl = "$WEB_BASE_URL/u/#k=$secret"

        val allowedActionsCanonical = allowedActions.map {
            when (it.lowercase()) {
                "confirm" -> "CONFIRM"
                "dispute" -> "DISPUTE"
                "propose_date", "date" -> "PROPOSE_DATE"
                "pay_claim", "claim_payment", "claim" -> "CLAIM_PAYMENT"
                else -> it.uppercase()
            }
        }.filter { it in setOf("CONFIRM", "DISPUTE", "PROPOSE_DATE", "CLAIM_PAYMENT") }.distinct()
        val finalAllowedActions = if (allowedActionsCanonical.isEmpty()) {
            listOf("CONFIRM", "DISPUTE", "PROPOSE_DATE", "CLAIM_PAYMENT")
        } else {
            allowedActionsCanonical
        }

        val verificationLevel = if (trustLevelRequired.contains("GOOGLE", ignoreCase = true)) {
            "GOOGLE_VERIFIED"
        } else {
            "PRIVATE_LINK"
        }

        val payableAmount = (principalAmountPaise - amountPaidPaise).coerceAtLeast(0L)
        val cleanEmail = expectedGuestEmail.trim().lowercase()
        val emailHash = if (cleanEmail.isNotBlank()) sha256Hex(cleanEmail) else null

        val docData = mutableMapOf<String, Any?>(
            "schemaVersion" to 1L,
            "ownerUid" to currentUser.uid,
            "recordId" to recordId,
            "status" to "ACTIVE",
            "createdAtMs" to now,
            "updatedAtMs" to now,
            "expiresAtMs" to expiresAt,
            "allowedActions" to finalAllowedActions,
            "borrowerDisplayName" to borrowerName,
            "lenderDisplayName" to lenderName,
            "principalPaise" to principalAmountPaise,
            "paidPaise" to amountPaidPaise,
            "payableAmountPaise" to payableAmount,
            "dueDateMs" to expiryDateMillis,
            "lenderUpiId" to if (showUpiQr && lenderUpiId.isNotBlank()) lenderUpiId else null,
            "verificationLevel" to verificationLevel,
            "expectedGuestEmailHash" to emailHash,
            "allowScreenshotProof" to allowAttachments,
            "counterpartyRole" to counterpartyRole
        )

        // [BLAZE-DISABLED START] reason: an OTP stored in a readable document provides no security on Spark | restore: restore server-generated OTP when upgrading to Blaze plan with secure Cloud Functions verification
        // val otp = (100000 + secureRandom.nextInt(900000)).toString()
        // docData["whatsappOtp"] = otp
        // [BLAZE-DISABLED END]

        try {
            firestore.collection("guestLinks")
                .document(linkId)
                .set(docData)
                .await()
        } catch (e: Exception) {
            if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
                SafeLogger.w("GuestWebCloudRepo", "Daily Firestore write quota reached for Spark plan")
                SafeLogger.recordNonFatal("GuestWebCloudRepo", "RESOURCE_EXHAUSTED", e, mapOf("linkId" to linkId))
                return@withContext GuestWebLinkCreateResult(
                    success = false,
                    errorMessage = "Daily free cloud limit reached. Please try again tomorrow."
                )
            }
            SafeLogger.e("GuestWebCloudRepo", "Unable to publish guest link: ${e.javaClass.simpleName}")
            SafeLogger.recordNonFatal("GuestWebCloudRepo", "PUBLISH_FAILED", e, mapOf("linkId" to linkId))
            return@withContext GuestWebLinkCreateResult(
                success = false,
                errorMessage = "Unable to publish guest link"
            )
        }

        val snapshot = try {
            firestore.collection("guestLinks")
                .document(linkId)
                .get(Source.SERVER)
                .await()
        } catch (e: Exception) {
            if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
                SafeLogger.w("GuestWebCloudRepo", "Daily Firestore read quota reached for Spark plan")
                SafeLogger.recordNonFatal("GuestWebCloudRepo", "RESOURCE_EXHAUSTED", e, mapOf("linkId" to linkId))
                return@withContext GuestWebLinkCreateResult(
                    success = false,
                    errorMessage = "Daily free cloud limit reached. Please try again tomorrow."
                )
            }
            SafeLogger.e("GuestWebCloudRepo", "Failed to verify published link from server: ${e.javaClass.simpleName}")
            SafeLogger.recordNonFatal("GuestWebCloudRepo", "READBACK_FAILED", e, mapOf("linkId" to linkId))
            return@withContext GuestWebLinkCreateResult(
                success = false,
                errorMessage = "Unable to publish guest link"
            )
        }

        if (!snapshot.exists() ||
            snapshot.getString("ownerUid") != currentUser.uid ||
            snapshot.getString("recordId") != recordId ||
            snapshot.getString("status") != "ACTIVE"
        ) {
            SafeLogger.w("GuestWebCloudRepo", "Server readback validation failed for link $linkId")
            SafeLogger.recordNonFatal("GuestWebCloudRepo", "READBACK_MISMATCH", metadata = mapOf("linkId" to linkId))
            return@withContext GuestWebLinkCreateResult(
                success = false,
                errorMessage = "Unable to publish guest link"
            )
        }

        GuestWebLinkCreateResult(
            success = true,
            url = capabilityUrl,
            shortUrl = capabilityUrl,
            recordId = recordId,
            secret = secret,
            linkId = linkId,
            expiresAtMillis = expiresAt,
            trustLevel = verificationLevel,
            whatsappOtp = ""
        )
    }

    /**
     * Alias for createGuestWebLink with schemaVersion 1 parameters.
     */
    open suspend fun createGuestLink(
        recordId: String,
        borrowerDisplayName: String = "",
        lenderDisplayName: String = "",
        principalPaise: Long = 0L,
        paidPaise: Long = 0L,
        dueDateMs: Long? = null,
        expiryDays: Int = 30,
        lenderUpiId: String? = null,
        verificationLevel: String = "PRIVATE_LINK",
        expectedGuestEmail: String? = null,
        allowedActions: List<String> = listOf("CONFIRM", "DISPUTE", "PROPOSE_DATE", "CLAIM_PAYMENT"),
        allowScreenshotProof: Boolean = true
    ): GuestWebLinkCreateResult = createGuestWebLink(
        recordId = recordId,
        counterpartyRole = "BORROWER",
        intendedPhone = "",
        expiryDays = expiryDays,
        trustLevelRequired = verificationLevel,
        allowedActions = allowedActions,
        allowNote = true,
        allowAttachments = allowScreenshotProof,
        showUpiQr = !lenderUpiId.isNullOrBlank(),
        lenderName = lenderDisplayName,
        borrowerName = borrowerDisplayName,
        principalAmountPaise = principalPaise,
        amountPaidPaise = paidPaise,
        currency = "INR",
        startDateMillis = System.currentTimeMillis(),
        expiryDateMillis = dueDateMs,
        note = "",
        contentHash = "",
        lenderUpiId = lenderUpiId.orEmpty(),
        creatorPhone = "",
        expectedGuestEmail = expectedGuestEmail.orEmpty()
    )

    /**
     * Revokes a guest link: sets status="REVOKED", revokedAtMs, updatedAtMs, and verifies with Source.SERVER readback.
     */
    open suspend fun revokeGuestWebLink(recordIdOrSecretOrLinkId: String): Boolean = withContext(ioDispatcher) {
        val auth = authOverride ?: runCatching { FirebaseAuth.getInstance() }.getOrNull()
        val currentUser = auth?.currentUser ?: return@withContext false
        val firestore = firestoreOverride ?: com.jyotirmoyb.udhaar.di.FirebaseModule.getFirestore() ?: return@withContext false

        try {
            val resolvedLinkId = when {
                recordIdOrSecretOrLinkId.length == 64 && recordIdOrSecretOrLinkId.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' } ->
                    recordIdOrSecretOrLinkId.lowercase()
                recordIdOrSecretOrLinkId.length == 32 || recordIdOrSecretOrLinkId.length == 43 ->
                    sha256Hex(recordIdOrSecretOrLinkId)
                else -> {
                    val querySnap = firestore.collection("guestLinks")
                        .whereEqualTo("ownerUid", currentUser.uid)
                        .whereEqualTo("recordId", recordIdOrSecretOrLinkId)
                        .get(Source.SERVER)
                        .await()
                    querySnap.documents.firstOrNull()?.id ?: return@withContext false
                }
            }

            val now = System.currentTimeMillis()
            firestore.collection("guestLinks")
                .document(resolvedLinkId)
                .set(
                    mapOf(
                        "ownerUid" to currentUser.uid,
                        "status" to "REVOKED",
                        "revokedAtMs" to now,
                        "updatedAtMs" to now
                    ),
                    SetOptions.merge()
                )
                .await()

            val verifySnap = try {
                firestore.collection("guestLinks")
                    .document(resolvedLinkId)
                    .get(Source.SERVER)
                    .await()
            } catch (e: Exception) {
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
                    SafeLogger.w("GuestWebCloudRepo", "Daily Firestore quota reached during revoke verify")
                } else {
                    SafeLogger.e("GuestWebCloudRepo", "Failed to verify revoked link from server: ${e.javaClass.simpleName}")
                }
                SafeLogger.recordNonFatal("GuestWebCloudRepo", "REVOKE_VERIFY_FAILED", e, mapOf("linkId" to resolvedLinkId))
                return@withContext false
            }

            verifySnap.exists() && verifySnap.getString("status") == "REVOKED"
        } catch (e: Exception) {
            if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
                SafeLogger.w("GuestWebCloudRepo", "Daily Firestore write quota reached during revoke")
            } else {
                SafeLogger.e("GuestWebCloudRepo", "Failed to revoke guest link: ${e.javaClass.simpleName}")
            }
            SafeLogger.recordNonFatal("GuestWebCloudRepo", "REVOKE_FAILED", e)
            false
        }
    }

    /**
     * Alias for revokeGuestWebLink.
     */
    open suspend fun revokeGuestLink(recordIdOrSecretOrLinkId: String): Boolean =
        revokeGuestWebLink(recordIdOrSecretOrLinkId)

    open suspend fun revokeAllGuestWebLinks(recordIdsOrTokens: List<String>): Boolean = withContext(ioDispatcher) {
        var allSuccess = true
        for (item in recordIdsOrTokens) {
            val ok = revokeGuestWebLink(item)
            if (!ok) allSuccess = false
        }
        allSuccess
    }

    open suspend fun claimGuestActions(recordId: String, secret: String): Boolean = withContext(ioDispatcher) {
        try {
            val auth = authOverride ?: runCatching { FirebaseAuth.getInstance() }.getOrNull()
            val currentUser = auth?.currentUser ?: return@withContext true
            val firestore = firestoreOverride ?: com.jyotirmoyb.udhaar.di.FirebaseModule.getFirestore() ?: return@withContext true

            if (secret.isNotBlank()) {
                val linkId = sha256Hex(secret)
                val actionsSnap = runCatching {
                    firestore.collection("guestLinks").document(linkId).collection("actions").get().await()
                }.getOrNull()
                if (actionsSnap != null && !actionsSnap.isEmpty) {
                    return@withContext true
                }
            }

            val linksSnapshot = runCatching {
                firestore.collection("guestLinks")
                    .whereEqualTo("ownerUid", currentUser.uid)
                    .limit(50)
                    .get()
                    .await()
            }.getOrNull()

            val hasActions = linksSnapshot?.documents?.any { linkDoc ->
                runCatching {
                    !linkDoc.reference.collection("actions").get().await().isEmpty
                }.getOrDefault(false)
            } ?: false

            hasActions || recordId.isNotBlank() || secret.isNotBlank()
        } catch (e: Exception) {
            SafeLogger.w("GuestWebCloudRepo", "claimGuestActions fallback", e)
            true
        }
    }

    /**
     * Fetches `/guestLinks/{sha256(secret)}` from Cloud Firestore for native in-app verification.
     * - Returns [GuestLinkFetchResult.FallbackToBrowser] when user is signed out or Firestore is unreachable.
     * - Returns [GuestLinkFetchResult.InvalidOrExpired] when link does not exist, is revoked, expired,
     *   or restricted to a different Google email address.
     */
    open suspend fun fetchGuestLinkBySecret(
        secret: String,
        fallbackRecordId: String = ""
    ): GuestLinkFetchResult = withContext(ioDispatcher) {
        val cleanSecret = secret.trim()
        if (cleanSecret.isBlank()) {
            return@withContext GuestLinkFetchResult.InvalidOrExpired("This verification link is incomplete.")
        }

        val auth = authOverride ?: runCatching { FirebaseAuth.getInstance() }.getOrNull()
        val currentUser = auth?.currentUser
        if (currentUser == null || currentUser.isAnonymous) {
            return@withContext GuestLinkFetchResult.FallbackToBrowser(
                "Sign in to Cloud Verification to review this link in-app."
            )
        }

        val firestore = firestoreOverride ?: com.jyotirmoyb.udhaar.di.FirebaseModule.getFirestore()
        if (firestore == null) {
            return@withContext GuestLinkFetchResult.FallbackToBrowser(
                "Cloud service unavailable; opening in browser."
            )
        }

        val linkId = sha256Hex(cleanSecret)
        val snap = try {
            kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                firestore.collection("guestLinks")
                    .document(linkId)
                    .get(Source.SERVER)
                    .await()
            } ?: return@withContext GuestLinkFetchResult.FallbackToBrowser(
                "Cloud timed out; opening in browser."
            )
        } catch (e: Exception) {
            val code = (e as? FirebaseFirestoreException)?.code
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                SafeLogger.w("GuestWebCloudRepo", "fetchGuestLinkBySecret permission denied for linkId=$linkId")
                return@withContext GuestLinkFetchResult.InvalidOrExpired(
                    "This Udhaar link is expired, revoked, or restricted to another account."
                )
            }
            if (code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
                SafeLogger.recordNonFatal("GuestWebCloudRepo", "FETCH_LINK_QUOTA", e, mapOf("linkId" to linkId))
            } else {
                SafeLogger.w("GuestWebCloudRepo", "fetchGuestLinkBySecret unreachable: ${e.javaClass.simpleName}")
            }
            return@withContext GuestLinkFetchResult.FallbackToBrowser(
                "Cloud unreachable right now; opening in browser."
            )
        }

        if (!snap.exists()) {
            return@withContext GuestLinkFetchResult.InvalidOrExpired(
                "This verification link is invalid or no longer exists."
            )
        }

        val status = snap.getString("status").orEmpty()
        val expiresAtMs = snap.getLong("expiresAtMs") ?: Long.MAX_VALUE
        val nowMs = System.currentTimeMillis()
        if (status.equals("REVOKED", ignoreCase = true)) {
            return@withContext GuestLinkFetchResult.InvalidOrExpired(
                "This Udhaar link was revoked by its creator."
            )
        }
        if (status != "ACTIVE" || expiresAtMs <= nowMs) {
            return@withContext GuestLinkFetchResult.InvalidOrExpired(
                "This verification link has expired."
            )
        }

        val verificationLevel = snap.getString("verificationLevel").orEmpty()
        val expectedEmailHash = snap.getString("expectedGuestEmailHash").orEmpty()
        if (verificationLevel == "GOOGLE_VERIFIED" && expectedEmailHash.isNotBlank()) {
            val myEmailHash = currentUser.email.orEmpty().trim().lowercase()
                .takeIf { it.isNotBlank() }
                ?.let { sha256Hex(it) }
                .orEmpty()
            if (myEmailHash != expectedEmailHash.lowercase()) {
                return@withContext GuestLinkFetchResult.InvalidOrExpired(
                    "This verification link is restricted to a different Google account."
                )
            }
        }

        val recordId = snap.getString("recordId")?.ifBlank { fallbackRecordId } ?: fallbackRecordId.ifBlank { linkId.take(12) }
        val ownerUid = snap.getString("ownerUid").orEmpty()
        val lenderName = snap.getString("lenderDisplayName").orEmpty().ifBlank { "Lender" }
        val borrowerName = snap.getString("borrowerDisplayName").orEmpty().ifBlank { "Borrower" }
        val counterpartyRole = snap.getString("counterpartyRole").orEmpty()
        val creatorRole = if (counterpartyRole.equals("LENDER", ignoreCase = true)) {
            com.jyotirmoyb.udhaar.data.TransactionType.TAKEN
        } else {
            com.jyotirmoyb.udhaar.data.TransactionType.GIVEN
        }
        val principalPaise = snap.getLong("principalPaise") ?: 0L
        val principalRupees = com.jyotirmoyb.udhaar.util.InterestCalculator.paiseToRupees(principalPaise)
        val currencyCode = snap.getString("currency").orEmpty().ifBlank { "INR" }
        val createdAtMs = snap.getLong("createdAtMs") ?: nowMs
        val dueDateMs = snap.getLong("dueDateMs")?.takeIf { it > 0L }

        val contentHash = com.jyotirmoyb.udhaar.util.VerificationCryptoHelper.computeContentHash(
            lenderName = lenderName,
            borrowerName = borrowerName,
            principalAmount = principalRupees,
            currencyCode = currencyCode,
            startDateMillis = createdAtMs,
            expiryDateMillis = dueDateMs,
            isInterestEnabled = false,
            interestType = com.jyotirmoyb.udhaar.data.InterestType.SIMPLE,
            interestRate = 0.0,
            interestRatePeriod = com.jyotirmoyb.udhaar.data.InterestRatePeriod.PER_MONTH,
            compoundFrequency = com.jyotirmoyb.udhaar.data.CompoundFrequency.MONTHLY
        )

        GuestLinkFetchResult.Success(
            com.jyotirmoyb.udhaar.util.VerificationCryptoHelper.SharedVerificationPayload(
                remoteId = recordId,
                shareToken = cleanSecret,
                creatorUserId = ownerUid,
                creatorRole = creatorRole,
                lenderName = lenderName,
                borrowerName = borrowerName,
                creatorPhone = "",
                counterpartyPhone = "",
                principalAmount = principalRupees,
                currencyCode = currencyCode,
                startDateMillis = createdAtMs,
                expiryDateMillis = dueDateMs,
                isInterestEnabled = false,
                interestType = com.jyotirmoyb.udhaar.data.InterestType.SIMPLE,
                interestRate = 0.0,
                interestRatePeriod = com.jyotirmoyb.udhaar.data.InterestRatePeriod.PER_MONTH,
                compoundFrequency = com.jyotirmoyb.udhaar.data.CompoundFrequency.MONTHLY,
                contentHash = contentHash,
                encryptedNote = "",
                guestLinkId = linkId,
                guestLinkSecret = cleanSecret,
                guestOwnerUid = ownerUid,
                expectedGuestEmailHash = expectedEmailHash,
                guestVerificationLevel = verificationLevel
            )
        )
    }

    /**
     * Submits a counterparty action (`CONFIRM` or `DISPUTE`) to `/guestLinks/{linkId}/actions/{actionType}`
     * conforming strictly to `firestore.rules`.
     */
    open suspend fun submitGuestActionFromApp(
        linkId: String,
        actionType: String,
        reason: String = ""
    ): Result<Unit> = withContext(ioDispatcher) {
        val cleanLinkId = linkId.trim()
        val canonicalAction = actionType.trim().uppercase()
        if (cleanLinkId.isBlank() || canonicalAction !in setOf("CONFIRM", "DISPUTE")) {
            return@withContext Result.failure(IllegalArgumentException("Unsupported guest action"))
        }

        val auth = authOverride ?: runCatching { FirebaseAuth.getInstance() }.getOrNull()
        val currentUser = auth?.currentUser
            ?: return@withContext Result.failure(IllegalStateException("Sign-in required"))
        val firestore = firestoreOverride ?: com.jyotirmoyb.udhaar.di.FirebaseModule.getFirestore()
            ?: return@withContext Result.failure(IllegalStateException("Cloud database unavailable"))

        val nowMs = System.currentTimeMillis()
        val data = mutableMapOf<String, Any>(
            "schemaVersion" to 1L,
            "actionType" to canonicalAction,
            "guestUid" to currentUser.uid,
            "status" to "PENDING",
            "createdAtMs" to nowMs
        )
        if (canonicalAction == "DISPUTE") {
            data["reason"] = reason.trim().ifBlank { "Terms disputed" }.take(500)
        }

        try {
            kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                firestore.collection("guestLinks")
                    .document(cleanLinkId)
                    .collection("actions")
                    .document(canonicalAction)
                    .set(data)
                    .await()
            } ?: return@withContext Result.failure(IllegalStateException("Request timed out"))
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
                SafeLogger.recordNonFatal("GuestWebCloudRepo", "SUBMIT_ACTION_QUOTA", e, mapOf("linkId" to cleanLinkId))
            } else {
                SafeLogger.w("GuestWebCloudRepo", "Failed to submit $canonicalAction: ${e.javaClass.simpleName}")
            }
            Result.failure(e)
        }
    }
}
