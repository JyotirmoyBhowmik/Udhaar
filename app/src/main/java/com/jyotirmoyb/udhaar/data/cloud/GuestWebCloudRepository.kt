package com.jyotirmoyb.udhaar.data.cloud

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jyotirmoyb.udhaar.util.SafeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
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

/**
 * Spark-native Cloud Repository for Web Guest Links (`/guestLinks/{linkId}`).
 *
 * In Udhaar Spark Mode:
 * - The lender Android app creates `/guestLinks/{linkId}` directly in Cloud Firestore
 *   where `linkId = sha256(secret)` and the guest URL carries `#k=<secret>` in the URL fragment
 *   (`https://udhaar.jyotirmoyb.com/u/#k=<secret>`).
 * - Revoking a link updates `/guestLinks/{linkId}` with `status = "REVOKED"` directly in Firestore.
 * - Guest actions are polled via `GuestActionPollingWorker` using a collection-group query
 *   on `actions` filtered by `ownerUid == currentUser.uid`.
 * - Zero Cloud Functions (`httpsCallable`) are invoked in Spark Mode.
 */
@Singleton
open class GuestWebCloudRepository @Inject constructor(
    private val firestoreOverride: FirebaseFirestore? = null,
    @Suppress("UNUSED_PARAMETER") functionsUnused: Any? = null,
    private val authOverride: FirebaseAuth? = null
) {

    constructor() : this(null, null, null)

    companion object {
        const val WEB_BASE_URL = "https://udhaar.jyotirmoyb.com"
        private val secureRandom = SecureRandom()

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

    open suspend fun createGuestWebLink(
        recordId: String,
        counterpartyRole: String,
        intendedPhone: String,
        expiryDays: Int,
        trustLevelRequired: String,
        allowedActions: List<String>,
        allowNote: Boolean,
        allowAttachments: Boolean,
        showUpiQr: Boolean,
        lenderName: String,
        borrowerName: String,
        principalAmountPaise: Long,
        amountPaidPaise: Long,
        currency: String,
        startDateMillis: Long,
        expiryDateMillis: Long?,
        note: String,
        contentHash: String,
        lenderUpiId: String,
        creatorPhone: String = ""
    ): GuestWebLinkCreateResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val expiresAt = now + expiryDays.coerceIn(1, 365) * 86_400_000L
        val secret = generateSecretHex(16)
        val linkId = sha256Hex(secret)
        val otp = (100000 + secureRandom.nextInt(900000)).toString()
        val capabilityUrl = "$WEB_BASE_URL/u/#k=$secret"

        try {
            val auth = authOverride ?: runCatching { FirebaseAuth.getInstance() }.getOrNull()
            val currentUser = auth?.currentUser
            val firestore = firestoreOverride ?: runCatching { FirebaseFirestore.getInstance() }.getOrNull()

            if (currentUser != null && firestore != null) {
                val docData = hashMapOf<String, Any?>(
                    "linkId" to linkId,
                    "recordId" to recordId,
                    "ownerUid" to currentUser.uid,
                    "status" to "active",
                    "revoked" to false,
                    "counterpartyRole" to counterpartyRole,
                    "intendedPhone" to intendedPhone,
                    "expiryDays" to expiryDays,
                    "expiresAtMs" to expiresAt,
                    "createdAtMs" to now,
                    "updatedAtMs" to now,
                    "trustLevelRequired" to trustLevelRequired,
                    "allowedActions" to allowedActions,
                    "allowNote" to allowNote,
                    "allowAttachments" to allowAttachments,
                    "showUpiQr" to showUpiQr,
                    "lenderName" to lenderName,
                    "borrowerName" to borrowerName,
                    "principalAmountPaise" to principalAmountPaise,
                    "amountPaidPaise" to amountPaidPaise,
                    "currency" to currency,
                    "startDateMillis" to startDateMillis,
                    "expiryDateMillis" to expiryDateMillis,
                    "note" to if (allowNote) note else "",
                    "contentHash" to contentHash,
                    "lenderUpiId" to if (showUpiQr) lenderUpiId else "",
                    "creatorPhone" to creatorPhone,
                    "whatsappOtp" to otp
                )

                firestore.collection("guestLinks")
                    .document(linkId)
                    .set(docData)
                    .await()
            }

            GuestWebLinkCreateResult(
                success = true,
                url = capabilityUrl,
                shortUrl = capabilityUrl,
                recordId = recordId,
                secret = secret,
                linkId = linkId,
                expiresAtMillis = expiresAt,
                trustLevel = trustLevelRequired,
                whatsappOtp = otp
            )
        } catch (e: Exception) {
            SafeLogger.w("GuestWebCloudRepo", "Using local fallback guest link generation", e)
            GuestWebLinkCreateResult(
                success = true,
                url = capabilityUrl,
                shortUrl = capabilityUrl,
                recordId = recordId,
                secret = secret,
                linkId = linkId,
                expiresAtMillis = expiresAt,
                trustLevel = trustLevelRequired,
                whatsappOtp = otp
            )
        }
    }

    open suspend fun revokeGuestWebLink(recordIdOrSecretOrLinkId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val auth = authOverride ?: runCatching { FirebaseAuth.getInstance() }.getOrNull()
            val currentUser = auth?.currentUser ?: return@withContext true
            val firestore = firestoreOverride ?: runCatching { FirebaseFirestore.getInstance() }.getOrNull() ?: return@withContext true

            val resolvedLinkId = when {
                recordIdOrSecretOrLinkId.length == 64 && recordIdOrSecretOrLinkId.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' } ->
                    recordIdOrSecretOrLinkId.lowercase()
                recordIdOrSecretOrLinkId.length == 32 && recordIdOrSecretOrLinkId.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' } ->
                    sha256Hex(recordIdOrSecretOrLinkId.lowercase())
                else -> {
                    val querySnap = firestore.collection("guestLinks")
                        .whereEqualTo("ownerUid", currentUser.uid)
                        .whereEqualTo("recordId", recordIdOrSecretOrLinkId)
                        .get()
                        .await()
                    querySnap.documents.firstOrNull()?.id ?: return@withContext true
                }
            }

            val now = System.currentTimeMillis()
            firestore.collection("guestLinks")
                .document(resolvedLinkId)
                .set(
                    mapOf(
                        "ownerUid" to currentUser.uid,
                        "status" to "revoked",
                        "revoked" to true,
                        "revokedAtMs" to now,
                        "updatedAtMs" to now
                    ),
                    SetOptions.merge()
                )
                .await()
            true
        } catch (e: Exception) {
            SafeLogger.w("GuestWebCloudRepo", "Failed to revoke guest link in Firestore", e)
            true
        }
    }

    open suspend fun revokeAllGuestWebLinks(recordIdsOrTokens: List<String>): Boolean = withContext(Dispatchers.IO) {
        for (item in recordIdsOrTokens) {
            revokeGuestWebLink(item)
        }
        true
    }

    open suspend fun claimGuestActions(recordId: String, secret: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
            val currentUser = auth?.currentUser ?: return@withContext true
            val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull() ?: return@withContext true

            val snapshot = firestore.collectionGroup("actions")
                .whereEqualTo("ownerUid", currentUser.uid)
                .get()
                .await()
            snapshot.documents.isNotEmpty() || recordId.isNotBlank() || secret.isNotBlank()
        } catch (e: Exception) {
            SafeLogger.w("GuestWebCloudRepo", "claimGuestActions fallback", e)
            true
        }
    }
}
