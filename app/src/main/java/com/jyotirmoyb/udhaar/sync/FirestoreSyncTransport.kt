package com.jyotirmoyb.udhaar.sync

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.jyotirmoyb.udhaar.util.SafeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Direct Cloud Firestore Sync Transport (Spark Mode)
 *
 * Implements SyncCloudTransport directly against the Cloud Firestore Android SDK:
 * - Operates 100% serverless on the Firebase Spark tier (zero Cloud Functions calls).
 * - Enforces owner-only scoping (/users/{uid}/records, /people, /events).
 * - Enforces monotonic revision increments: rev = existing.rev + 1.
 * - Respects email-verified requirement for cloud synchronization.
 * - Preserves Room + SQLCipher as the offline-first source of truth.
 */
class FirestoreSyncTransport(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : SyncCloudTransport {

    companion object {
        private const val TAG = "FirestoreSyncTransport"
    }

    override suspend fun pushMutations(request: SyncPushRequest): SyncPushResponse = withContext(Dispatchers.IO) {
        val user = auth.currentUser
        if (user == null) {
            SafeLogger.w(TAG, "Sync push aborted: User is not signed in.")
            throw IllegalStateException("Authentication required for sync push.")
        }

        if (!user.isEmailVerified) {
            SafeLogger.w(TAG, "Sync push aborted: User email is not verified.")
            throw IllegalStateException("Email verification required before cloud sync can be performed.")
        }

        val uid = user.uid
        val results = mutableListOf<SyncPushResultDto>()

        try {
            for (change in request.changes) {
                try {
                    when (change.entity.lowercase()) {
                        "record", "transaction" -> {
                            val docRef = firestore.collection("users")
                                .document(uid)
                                .collection("records")
                                .document(change.syncId)

                            val snap = docRef.get().await()
                            val currentRev = if (snap.exists()) snap.getLong("rev") ?: 1L else 0L
                            val nextRev = currentRev + 1L

                            val fields = change.fields.toMutableMap()
                            fields["id"] = change.syncId
                            fields["recordId"] = change.syncId
                            fields["uid"] = uid
                            fields["rev"] = nextRev
                            fields["updatedAtMs"] = System.currentTimeMillis()
                            if (!snap.exists()) {
                                fields["createdAtMs"] = System.currentTimeMillis()
                            }

                            docRef.set(fields, SetOptions.merge()).await()
                            results.add(
                                SyncPushResultDto(
                                    syncId = change.syncId,
                                    status = "ACCEPTED",
                                    serverRev = nextRev
                                )
                            )
                        }

                        "person", "people" -> {
                            val docRef = firestore.collection("users")
                                .document(uid)
                                .collection("people")
                                .document(change.syncId)

                            val snap = docRef.get().await()
                            val currentRev = if (snap.exists()) snap.getLong("rev") ?: 1L else 0L
                            val nextRev = currentRev + 1L

                            val fields = change.fields.toMutableMap()
                            fields["id"] = change.syncId
                            fields["personId"] = change.syncId
                            fields["uid"] = uid
                            fields["rev"] = nextRev
                            fields["updatedAtMs"] = System.currentTimeMillis()
                            if (!snap.exists()) {
                                fields["createdAtMs"] = System.currentTimeMillis()
                            }

                            docRef.set(fields, SetOptions.merge()).await()
                            results.add(
                                SyncPushResultDto(
                                    syncId = change.syncId,
                                    status = "ACCEPTED",
                                    serverRev = nextRev
                                )
                            )
                        }

                        "payment", "event" -> {
                            // Events are strictly append-only in Spark Mode
                            val docRef = firestore.collection("users")
                                .document(uid)
                                .collection("events")
                                .document(change.syncId)

                            val fields = change.fields.toMutableMap()
                            fields["id"] = change.syncId
                            fields["eventId"] = change.syncId
                            fields["uid"] = uid
                            fields["createdAtMs"] = System.currentTimeMillis()

                            docRef.set(fields).await()
                            results.add(
                                SyncPushResultDto(
                                    syncId = change.syncId,
                                    status = "ACCEPTED",
                                    serverRev = 1L
                                )
                            )
                        }

                        else -> {
                            SafeLogger.w(TAG, "Unknown sync entity: ${change.entity}")
                            results.add(
                                SyncPushResultDto(
                                    syncId = change.syncId,
                                    status = "REJECTED",
                                    reason = "Unsupported entity type: ${change.entity}"
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    SafeLogger.e(TAG, "Failed to apply mutation for ${change.syncId}", e)
                    results.add(
                        SyncPushResultDto(
                            syncId = change.syncId,
                            status = "CONFLICT",
                            reason = e.message
                        )
                    )
                }
            }
            SyncPushResponse(results = results)
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
                SafeLogger.w(TAG, "Firestore daily write quota reached for Spark plan. Degrading gracefully.")
                throw IllegalStateException("Free daily sync quota exhausted. Records remain safe locally on device.", e)
            }
            throw e
        }
    }

    override suspend fun pullDeltas(cursor: Long, limit: Int): SyncPullResponse = withContext(Dispatchers.IO) {
        val user = auth.currentUser
        if (user == null || !user.isEmailVerified) {
            return@withContext SyncPullResponse(deltas = emptyList(), nextCursor = cursor, hasMore = false)
        }

        val uid = user.uid
        val deltas = mutableListOf<SyncDeltaEntryDto>()
        var maxCursor = cursor

        try {
            // Pull records updated after cursor
            val recQuery = firestore.collection("users")
                .document(uid)
                .collection("records")
                .whereGreaterThan("updatedAtMs", cursor)
                .limit(limit.toLong())
                .get()
                .await()

            for (doc in recQuery.documents) {
                val data = doc.data ?: continue
                val updatedAt = doc.getLong("updatedAtMs") ?: cursor
                if (updatedAt > maxCursor) maxCursor = updatedAt

                deltas.add(
                    SyncDeltaEntryDto(
                        seq = updatedAt,
                        entity = "record",
                        syncId = doc.id,
                        op = if (data["isDeleted"] == true || data["deleted"] == true) "DELETE" else "UPSERT",
                        fields = data,
                        serverRev = doc.getLong("rev") ?: 1L,
                        hlc = data["hlc"]?.toString() ?: ""
                    )
                )
            }

            SyncPullResponse(
                deltas = deltas,
                nextCursor = maxCursor,
                hasMore = recQuery.size() >= limit
            )
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
                SafeLogger.w(TAG, "Firestore daily read quota reached for Spark plan.")
                return@withContext SyncPullResponse(deltas = emptyList(), nextCursor = cursor, hasMore = false)
            }
            throw e
        }
    }
}
