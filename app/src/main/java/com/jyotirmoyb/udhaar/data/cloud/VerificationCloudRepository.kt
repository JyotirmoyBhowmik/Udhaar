package com.jyotirmoyb.udhaar.data.cloud

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.jyotirmoyb.udhaar.R
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

enum class OperationType {
    CREATE, UPDATE, DELETE, GET, LIST, WRITE
}

data class CloudUserProfile(
    val userId: String = "",
    val displayName: String = "",
    val phoneNumber: String = "",
    val upiId: String = "",
    val photoUri: String = "",
    val createdAt: Timestamp = Timestamp(Date()),
    val updatedAt: Timestamp = Timestamp(Date())
)

data class CloudSharedRecord(
    val remoteId: String = "",
    val creatorUid: String = "",
    val counterpartyUid: String = "",
    val participantUids: List<String> = emptyList(),
    val lenderName: String = "",
    val borrowerName: String = "",
    val creatorRole: String = "GIVEN",
    val currency: String = "INR",
    val principalAmount: Double = 0.0,
    val amountPaid: Double = 0.0,
    val paymentMode: String = "CASH",
    val isInterestEnabled: Boolean = false,
    val interestType: String = "SIMPLE",
    val interestRate: Double = 0.0,
    val interestRatePeriod: String = "PER_ANNUM",
    val compoundFrequency: String = "YEARLY",
    val startDateMillis: Long = 0L,
    val expiryDateMillis: Long = -1L,
    val contentHash: String = "",
    val verificationStatus: String = "PENDING",
    val verifiedAtMillis: Long = -1L,
    val disputeReason: String = "",
    val encryptedNotes: String = "",
    val attachmentRefs: List<String> = emptyList(),
    val isSettled: Boolean = false,
    val createdAt: Timestamp = Timestamp(Date()),
    val updatedAt: Timestamp = Timestamp(Date())
)

data class CloudPartialPayment(
    val paymentId: String = "",
    val remoteId: String = "",
    val recordedByUid: String = "",
    val recordedByName: String = "",
    val participantUids: List<String> = emptyList(),
    val amount: Double = 0.0,
    val paymentMode: String = "CASH",
    val paidAtMillis: Long = 0L,
    val encryptedNote: String = "",
    val isAcknowledged: Boolean = false,
    val acknowledgedByUid: String = "",
    val acknowledgedAtMillis: Long = -1L,
    val createdAt: Timestamp = Timestamp(Date()),
    val updatedAt: Timestamp = Timestamp(Date())
)

data class CloudAuditEvent(
    val eventId: String = "",
    val remoteId: String = "",
    val eventType: String = "CREATED",
    val actorUid: String = "",
    val actorName: String = "",
    val participantUids: List<String> = emptyList(),
    val summaryText: String = "",
    val contentHashSnapshot: String = "",
    val acknowledged: Boolean = false,
    val eventTimeMillis: Long = 0L,
    val createdAt: Timestamp = Timestamp(Date())
)

/**
 * Cloud Firestore repository for Phase 3 Two-Party Verification.
 * - Matches `firestore.rules` schema and security invariants 1:1.
 * - Accepts a constructor-injected [FirebaseFirestore] for unit/emulator testing, with a secondary
 *   [Context] constructor that resolves `R.string.firestore_database_id`.
 * - Implements structured error reporting via [handleFirestoreError].
 */
class VerificationCloudRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            FirebaseApp.getInstance(),
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun handleFirestoreError(
        exception: Exception,
        operationType: OperationType,
        path: String?
    ) {
        val currentUser = try {
            FirebaseAuth.getInstance().currentUser
        } catch (_: Exception) {
            null
        }
        val errorJson = JSONObject().apply {
            put("error", exception.message ?: "Unknown Firestore error")
            put("operationType", operationType.name)
            put("path", path ?: JSONObject.NULL)
            put("authInfo", JSONObject().apply {
                put("userId", currentUser?.uid ?: JSONObject.NULL)
                put("email", currentUser?.email ?: JSONObject.NULL)
                put("emailVerified", currentUser?.isEmailVerified ?: false)
                put("isAnonymous", currentUser?.isAnonymous ?: false)
                put("tenantId", currentUser?.tenantId ?: JSONObject.NULL)
                val providers = JSONArray()
                currentUser?.providerData?.forEach { p ->
                    providers.put(JSONObject().apply {
                        put("providerId", p.providerId)
                        put("displayName", p.displayName ?: JSONObject.NULL)
                        put("email", p.email ?: JSONObject.NULL)
                        put("photoUrl", p.photoUrl?.toString() ?: JSONObject.NULL)
                    })
                }
                put("providerInfo", providers)
            })
        }
        Log.e("FirestoreError", errorJson.toString(), exception)
        throw exception
    }

    suspend fun upsertUserProfile(profile: CloudUserProfile) {
        val path = "user_profiles/${profile.userId}"
        try {
            val docRef = db.collection("user_profiles").document(profile.userId)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val data = mapOf(
                    "userId" to profile.userId,
                    "displayName" to profile.displayName,
                    "phoneNumber" to profile.phoneNumber,
                    "upiId" to profile.upiId,
                    "photoUri" to profile.photoUri,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(data).await()
            } else {
                val updates = mapOf(
                    "displayName" to profile.displayName,
                    "phoneNumber" to profile.phoneNumber,
                    "upiId" to profile.upiId,
                    "photoUri" to profile.photoUri,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
        }
    }

    suspend fun getUserProfile(userId: String): CloudUserProfile? {
        val path = "user_profiles/$userId"
        return try {
            val snap = db.collection("user_profiles").document(userId).get().await()
            if (!snap.exists()) return null
            CloudUserProfile(
                userId = snap.getString("userId") ?: userId,
                displayName = snap.getString("displayName") ?: "",
                phoneNumber = snap.getString("phoneNumber") ?: "",
                upiId = snap.getString("upiId") ?: "",
                photoUri = snap.getString("photoUri") ?: "",
                createdAt = snap.getTimestamp("createdAt") ?: Timestamp.now(),
                updatedAt = snap.getTimestamp("updatedAt") ?: Timestamp.now()
            )
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            null
        }
    }

    suspend fun deleteUserProfile(userId: String) {
        val path = "user_profiles/$userId"
        try {
            db.collection("user_profiles").document(userId).delete().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
        }
    }

    suspend fun createSharedRecord(record: CloudSharedRecord) {
        val path = "shared_records/${record.remoteId}"
        try {
            val data = mapOf(
                "remoteId" to record.remoteId,
                "creatorUid" to record.creatorUid,
                "counterpartyUid" to record.counterpartyUid,
                "participantUids" to record.participantUids.ifEmpty { listOf(record.creatorUid) },
                "lenderName" to record.lenderName,
                "borrowerName" to record.borrowerName,
                "creatorRole" to record.creatorRole,
                "currency" to record.currency,
                "principalAmount" to record.principalAmount,
                "amountPaid" to record.amountPaid,
                "paymentMode" to record.paymentMode,
                "isInterestEnabled" to record.isInterestEnabled,
                "interestType" to record.interestType,
                "interestRate" to record.interestRate,
                "interestRatePeriod" to record.interestRatePeriod,
                "compoundFrequency" to record.compoundFrequency,
                "startDateMillis" to record.startDateMillis,
                "expiryDateMillis" to record.expiryDateMillis,
                "contentHash" to record.contentHash,
                "verificationStatus" to record.verificationStatus,
                "verifiedAtMillis" to record.verifiedAtMillis,
                "disputeReason" to record.disputeReason,
                "encryptedNotes" to record.encryptedNotes,
                "attachmentRefs" to record.attachmentRefs,
                "isSettled" to record.isSettled,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("shared_records").document(record.remoteId).set(data).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
        }
    }

    suspend fun claimAndRespondSharedRecord(
        remoteId: String,
        creatorUid: String,
        counterpartyUid: String,
        verificationStatus: String,
        verifiedAtMillis: Long,
        disputeReason: String = ""
    ) {
        val path = "shared_records/$remoteId"
        try {
            val updates = mapOf(
                "counterpartyUid" to counterpartyUid,
                "participantUids" to listOf(creatorUid, counterpartyUid),
                "verificationStatus" to verificationStatus,
                "verifiedAtMillis" to verifiedAtMillis,
                "disputeReason" to disputeReason,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("shared_records").document(remoteId).update(updates).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    suspend fun updateSharedRecordTermsOrStatus(
        remoteId: String,
        updates: Map<String, Any>
    ) {
        val path = "shared_records/$remoteId"
        try {
            val fullUpdates = updates.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection("shared_records").document(remoteId).update(fullUpdates).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    suspend fun getSharedRecord(remoteId: String): CloudSharedRecord? {
        val path = "shared_records/$remoteId"
        return try {
            val snap = db.collection("shared_records").document(remoteId).get().await()
            if (!snap.exists()) return null
            mapToRecord(snap.data.orEmpty(), remoteId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            null
        }
    }

    suspend fun deleteSharedRecord(remoteId: String) {
        val path = "shared_records/$remoteId"
        try {
            db.collection("shared_records").document(remoteId).delete().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
        }
    }

    fun observeSharedRecord(remoteId: String): Flow<CloudSharedRecord?> = callbackFlow {
        val path = "shared_records/$remoteId"
        val registration: ListenerRegistration = db.collection("shared_records")
            .document(remoteId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    try {
                        handleFirestoreError(error, OperationType.GET, path)
                    } catch (_: Exception) {
                    }
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(mapToRecord(snapshot.data.orEmpty(), remoteId))
                } else {
                    trySend(null)
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun createPartialPayment(payment: CloudPartialPayment) {
        val path = "shared_records/${payment.remoteId}/partial_payments/${payment.paymentId}"
        try {
            val data = mapOf(
                "paymentId" to payment.paymentId,
                "remoteId" to payment.remoteId,
                "recordedByUid" to payment.recordedByUid,
                "recordedByName" to payment.recordedByName,
                "participantUids" to payment.participantUids.ifEmpty { listOf(payment.recordedByUid) },
                "amount" to payment.amount,
                "paymentMode" to payment.paymentMode,
                "paidAtMillis" to payment.paidAtMillis,
                "encryptedNote" to payment.encryptedNote,
                "isAcknowledged" to payment.isAcknowledged,
                "acknowledgedByUid" to payment.acknowledgedByUid,
                "acknowledgedAtMillis" to payment.acknowledgedAtMillis,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("shared_records")
                .document(payment.remoteId)
                .collection("partial_payments")
                .document(payment.paymentId)
                .set(data)
                .await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
        }
    }

    suspend fun acknowledgePartialPayment(
        remoteId: String,
        paymentId: String,
        acknowledgedByUid: String,
        acknowledgedAtMillis: Long
    ) {
        val path = "shared_records/$remoteId/partial_payments/$paymentId"
        try {
            val updates = mapOf(
                "isAcknowledged" to true,
                "acknowledgedByUid" to acknowledgedByUid,
                "acknowledgedAtMillis" to acknowledgedAtMillis,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("shared_records")
                .document(remoteId)
                .collection("partial_payments")
                .document(paymentId)
                .update(updates)
                .await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
        }
    }

    suspend fun listPartialPayments(remoteId: String, participantUid: String): List<CloudPartialPayment> {
        val path = "shared_records/$remoteId/partial_payments"
        return try {
            val snap = db.collection("shared_records")
                .document(remoteId)
                .collection("partial_payments")
                .whereArrayContains("participantUids", participantUid)
                .get()
                .await()
            snap.documents.map { doc ->
                val d = doc.data.orEmpty()
                @Suppress("UNCHECKED_CAST")
                CloudPartialPayment(
                    paymentId = d["paymentId"] as? String ?: doc.id,
                    remoteId = d["remoteId"] as? String ?: remoteId,
                    recordedByUid = d["recordedByUid"] as? String ?: "",
                    recordedByName = d["recordedByName"] as? String ?: "",
                    participantUids = (d["participantUids"] as? List<String>).orEmpty(),
                    amount = (d["amount"] as? Number)?.toDouble() ?: 0.0,
                    paymentMode = d["paymentMode"] as? String ?: "CASH",
                    paidAtMillis = (d["paidAtMillis"] as? Number)?.toLong() ?: 0L,
                    encryptedNote = d["encryptedNote"] as? String ?: "",
                    isAcknowledged = d["isAcknowledged"] as? Boolean ?: false,
                    acknowledgedByUid = d["acknowledgedByUid"] as? String ?: "",
                    acknowledgedAtMillis = (d["acknowledgedAtMillis"] as? Number)?.toLong() ?: -1L
                )
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            emptyList()
        }
    }

    suspend fun appendAuditEvent(event: CloudAuditEvent) {
        val path = "shared_records/${event.remoteId}/audit_trail/${event.eventId}"
        try {
            val data = mapOf(
                "eventId" to event.eventId,
                "remoteId" to event.remoteId,
                "eventType" to event.eventType,
                "actorUid" to event.actorUid,
                "actorName" to event.actorName,
                "participantUids" to event.participantUids.ifEmpty { listOf(event.actorUid) },
                "summaryText" to event.summaryText,
                "contentHashSnapshot" to event.contentHashSnapshot,
                "acknowledged" to event.acknowledged,
                "eventTimeMillis" to event.eventTimeMillis,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("shared_records")
                .document(event.remoteId)
                .collection("audit_trail")
                .document(event.eventId)
                .set(data)
                .await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
        }
    }

    suspend fun listAuditEvents(remoteId: String, participantUid: String): List<CloudAuditEvent> {
        val path = "shared_records/$remoteId/audit_trail"
        return try {
            val snap = db.collection("shared_records")
                .document(remoteId)
                .collection("audit_trail")
                .whereArrayContains("participantUids", participantUid)
                .get()
                .await()
            snap.documents.map { doc ->
                val d = doc.data.orEmpty()
                @Suppress("UNCHECKED_CAST")
                CloudAuditEvent(
                    eventId = d["eventId"] as? String ?: doc.id,
                    remoteId = d["remoteId"] as? String ?: remoteId,
                    eventType = d["eventType"] as? String ?: "CREATED",
                    actorUid = d["actorUid"] as? String ?: "",
                    actorName = d["actorName"] as? String ?: "",
                    participantUids = (d["participantUids"] as? List<String>).orEmpty(),
                    summaryText = d["summaryText"] as? String ?: "",
                    contentHashSnapshot = d["contentHashSnapshot"] as? String ?: "",
                    acknowledged = d["acknowledged"] as? Boolean ?: false,
                    eventTimeMillis = (d["eventTimeMillis"] as? Number)?.toLong() ?: 0L
                )
            }.sortedBy { it.eventTimeMillis }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            emptyList()
        }
    }

    /**
     * Deletes all cloud data owned by [userId] (profile + creator shared records).
     */
    suspend fun deleteAllMyCloudData(userId: String, createdRemoteIds: List<String>) {
        for (remoteId in createdRemoteIds) {
            if (remoteId.isNotBlank()) {
                try {
                    val payments = db.collection("shared_records")
                        .document(remoteId)
                        .collection("partial_payments")
                        .whereArrayContains("participantUids", userId)
                        .get()
                        .await()
                    for (doc in payments.documents) {
                        if (doc.getString("recordedByUid") == userId) {
                            doc.reference.delete().await()
                        }
                    }
                    val audits = db.collection("shared_records")
                        .document(remoteId)
                        .collection("audit_trail")
                        .whereArrayContains("participantUids", userId)
                        .get()
                        .await()
                    for (doc in audits.documents) {
                        if (doc.getString("actorUid") == userId) {
                            doc.reference.delete().await()
                        }
                    }
                    deleteSharedRecord(remoteId)
                } catch (_: Exception) {
                }
            }
        }
        try {
            deleteUserProfile(userId)
        } catch (_: Exception) {
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToRecord(d: Map<String, Any>, fallbackId: String): CloudSharedRecord {
        return CloudSharedRecord(
            remoteId = d["remoteId"] as? String ?: fallbackId,
            creatorUid = d["creatorUid"] as? String ?: "",
            counterpartyUid = d["counterpartyUid"] as? String ?: "",
            participantUids = (d["participantUids"] as? List<String>).orEmpty(),
            lenderName = d["lenderName"] as? String ?: "",
            borrowerName = d["borrowerName"] as? String ?: "",
            creatorRole = d["creatorRole"] as? String ?: "GIVEN",
            currency = d["currency"] as? String ?: "INR",
            principalAmount = (d["principalAmount"] as? Number)?.toDouble() ?: 0.0,
            amountPaid = (d["amountPaid"] as? Number)?.toDouble() ?: 0.0,
            paymentMode = d["paymentMode"] as? String ?: "CASH",
            isInterestEnabled = d["isInterestEnabled"] as? Boolean ?: false,
            interestType = d["interestType"] as? String ?: "SIMPLE",
            interestRate = (d["interestRate"] as? Number)?.toDouble() ?: 0.0,
            interestRatePeriod = d["interestRatePeriod"] as? String ?: "PER_ANNUM",
            compoundFrequency = d["compoundFrequency"] as? String ?: "YEARLY",
            startDateMillis = (d["startDateMillis"] as? Number)?.toLong() ?: 0L,
            expiryDateMillis = (d["expiryDateMillis"] as? Number)?.toLong() ?: -1L,
            contentHash = d["contentHash"] as? String ?: "",
            verificationStatus = d["verificationStatus"] as? String ?: "PENDING",
            verifiedAtMillis = (d["verifiedAtMillis"] as? Number)?.toLong() ?: -1L,
            disputeReason = d["disputeReason"] as? String ?: "",
            encryptedNotes = d["encryptedNotes"] as? String ?: "",
            attachmentRefs = (d["attachmentRefs"] as? List<String>).orEmpty(),
            isSettled = d["isSettled"] as? Boolean ?: false,
            createdAt = d["createdAt"] as? Timestamp ?: Timestamp.now(),
            updatedAt = d["updatedAt"] as? Timestamp ?: Timestamp.now()
        )
    }
}
