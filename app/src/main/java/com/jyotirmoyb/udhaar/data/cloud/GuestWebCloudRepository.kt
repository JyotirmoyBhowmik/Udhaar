package com.jyotirmoyb.udhaar.data.cloud

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.jyotirmoyb.udhaar.util.SafeLogger
import com.jyotirmoyb.udhaar.util.VerificationCryptoHelper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class GuestWebLinkCreateResult(
    val success: Boolean,
    val recordId: String = "",
    val url: String = "",
    val secret: String = "",
    val whatsappOtp: String = "",
    val expiresAt: String = "",
    val trustLevel: String = "WHATSAPP_OTP",
    val errorMessage: String? = null
)

data class GuestInboxItem(
    val inboxId: String = "",
    val recordId: String = "",
    val partyName: String = "",
    val actionType: String = "",
    val summaryText: String = "",
    val detailText: String = "",
    val amountPaise: Long = 0L,
    val amountDouble: Double = 0.0,
    val paymentMode: String = "UPI",
    val reference: String = "",
    val proofUrl: String = "",
    val proposedDateMillis: Long = 0L,
    val message: String = "",
    val guestUpiId: String = "",
    val verifiedPhoneMasked: String = "",
    val trustLevel: String = "",
    val isProcessed: Boolean = false,
    val timestampMillis: Long = System.currentTimeMillis()
)

@Singleton
class GuestWebCloudRepository @Inject constructor(
    private val firestore: FirebaseFirestore?,
    private val functions: FirebaseFunctions?,
    private val auth: FirebaseAuth?
) {
    val isAvailable: Boolean get() = firestore != null || functions != null

    suspend fun createGuestWebLink(
        recordId: String,
        counterpartyRole: String = "BORROWER",
        intendedPhone: String = "",
        expiryDays: Int = 30,
        trustLevelRequired: String = "WHATSAPP_OTP",
        allowedActions: List<String> = listOf("view", "confirm", "dispute", "pay_claim", "propose_date", "set_upi", "show_pay_qr"),
        allowNote: Boolean = true,
        allowAttachments: Boolean = false,
        showUpiQr: Boolean = true,
        lenderName: String = "",
        borrowerName: String = "",
        principalAmountPaise: Long = 0L,
        amountPaidPaise: Long = 0L,
        currency: String = "INR",
        startDateMillis: Long = 0L,
        expiryDateMillis: Long? = null,
        note: String = "",
        contentHash: String = "",
        lenderUpiId: String = "",
        creatorPhone: String = ""
    ): GuestWebLinkCreateResult {
        fun buildLocalFallbackResult(errMessage: String? = null): GuestWebLinkCreateResult {
            val baseToken = VerificationCryptoHelper.generateShareToken()
            val localOtp = ((Math.random() * 900000).toInt() + 100000).toString()
            val selfContainedFragment = VerificationCryptoHelper.buildSelfContainedGuestFragment(
                recordId = recordId,
                secretToken = baseToken,
                counterpartyRole = counterpartyRole,
                lenderName = lenderName,
                borrowerName = borrowerName,
                principalAmountPaise = principalAmountPaise,
                currency = currency,
                startDateMillis = startDateMillis,
                expiryDateMillis = expiryDateMillis,
                creatorPhone = creatorPhone,
                lenderUpiId = lenderUpiId,
                trustLevelRequired = trustLevelRequired,
                whatsappOtp = localOtp,
                contentHash = contentHash
            )
            val principalRupees = principalAmountPaise.toDouble() / 100.0
            val amountParam = if (principalRupees % 1.0 == 0.0) principalRupees.toLong().toString() else "%.2f".format(java.util.Locale.US, principalRupees)
            val encFrom = java.net.URLEncoder.encode(lenderName, "UTF-8")
            val encTo = java.net.URLEncoder.encode(borrowerName, "UTF-8")
            val encCur = java.net.URLEncoder.encode(currency, "UTF-8")
            val queryStr = "?amt=$amountParam&from=$encFrom&to=$encTo&cur=$encCur"
            val localUrl = "https://udhaar.jyotirmoyb.com/u/r/$recordId$queryStr#$selfContainedFragment"
            return GuestWebLinkCreateResult(
                success = true,
                recordId = recordId,
                url = localUrl,
                secret = selfContainedFragment,
                whatsappOtp = localOtp,
                expiresAt = "$expiryDays days",
                trustLevel = trustLevelRequired,
                errorMessage = errMessage
            )
        }

        if (functions == null) {
            return buildLocalFallbackResult()
        }
        return try {
            val payload = hashMapOf<String, Any?>(
                "recordId" to recordId,
                "counterpartyRole" to counterpartyRole,
                "intendedPhone" to intendedPhone,
                "expiryDays" to expiryDays,
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
                "note" to note,
                "contentHash" to contentHash,
                "lenderUpiId" to lenderUpiId,
                "creatorPhone" to creatorPhone
            )
            val result = functions.getHttpsCallable("createGuestWebLink").call(payload).await()
            @Suppress("UNCHECKED_CAST")
            val data = result.getData() as? Map<String, Any> ?: emptyMap()
            val remoteUrl = data["url"] as? String ?: ""
            if (remoteUrl.isBlank()) {
                return buildLocalFallbackResult()
            }
            val remoteSecret = data["secret"] as? String ?: VerificationCryptoHelper.generateShareToken()
            val remoteOtp = data["whatsappOtp"] as? String ?: ((Math.random() * 900000).toInt() + 100000).toString()
            val selfContainedFragment = VerificationCryptoHelper.buildSelfContainedGuestFragment(
                recordId = data["recordId"] as? String ?: recordId,
                secretToken = remoteSecret,
                counterpartyRole = counterpartyRole,
                lenderName = lenderName,
                borrowerName = borrowerName,
                principalAmountPaise = principalAmountPaise,
                currency = currency,
                startDateMillis = startDateMillis,
                expiryDateMillis = expiryDateMillis,
                creatorPhone = creatorPhone,
                lenderUpiId = lenderUpiId,
                trustLevelRequired = data["trustLevel"] as? String ?: trustLevelRequired,
                whatsappOtp = remoteOtp,
                contentHash = contentHash
            )
            val baseUrlWithoutFragment = remoteUrl.substringBefore("#")
            val enrichedUrl = "$baseUrlWithoutFragment#$selfContainedFragment"
            GuestWebLinkCreateResult(
                success = data["success"] as? Boolean ?: true,
                recordId = data["recordId"] as? String ?: recordId,
                url = enrichedUrl,
                secret = remoteSecret,
                whatsappOtp = remoteOtp,
                expiresAt = data["expiresAt"] as? String ?: "",
                trustLevel = data["trustLevel"] as? String ?: trustLevelRequired
            )
        } catch (e: Exception) {
            SafeLogger.e("GuestWebCloudRepo", "Failed creating guest web link via Cloud Functions", e)
            buildLocalFallbackResult(e.message)
        }
    }

    suspend fun revokeGuestWebLink(recordId: String): Boolean {
        if (functions == null) return true
        return try {
            val payload = hashMapOf("recordId" to recordId)
            val result = functions.getHttpsCallable("revokeGuestWebLink").call(payload).await()
            @Suppress("UNCHECKED_CAST")
            val data = result.getData() as? Map<String, Any>
            data?.get("success") as? Boolean ?: true
        } catch (e: Exception) {
            SafeLogger.e("GuestWebCloudRepo", "Failed revoking guest web link", e)
            false
        }
    }

    suspend fun claimGuestActions(recordId: String, secret: String): Boolean {
        if (functions == null) return false
        return try {
            val payload = hashMapOf("recordId" to recordId, "secret" to secret)
            val result = functions.getHttpsCallable("claimGuestActions").call(payload).await()
            @Suppress("UNCHECKED_CAST")
            val data = result.getData() as? Map<String, Any>
            data?.get("success") as? Boolean ?: false
        } catch (e: Exception) {
            SafeLogger.e("GuestWebCloudRepo", "Failed claiming guest actions", e)
            false
        }
    }

    suspend fun deleteGuestData(recordId: String): Boolean {
        if (functions == null) return true
        return try {
            val payload = hashMapOf("recordId" to recordId)
            val result = functions.getHttpsCallable("deleteGuestData").call(payload).await()
            @Suppress("UNCHECKED_CAST")
            val data = result.getData() as? Map<String, Any>
            data?.get("success") as? Boolean ?: true
        } catch (e: Exception) {
            SafeLogger.e("GuestWebCloudRepo", "Failed deleting guest data", e)
            false
        }
    }

    suspend fun revokeAllGuestWebLinks(recordIds: List<String>): Int {
        var revokedCount = 0
        for (recId in recordIds) {
            if (recId.isNotBlank()) {
                val ok = revokeGuestWebLink(recId)
                if (ok) revokedCount++
            }
        }
        return revokedCount
    }
}
