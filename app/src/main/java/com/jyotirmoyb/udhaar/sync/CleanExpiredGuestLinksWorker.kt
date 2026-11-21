package com.jyotirmoyb.udhaar.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.di.FirebaseModule
import com.jyotirmoyb.udhaar.util.SafeLogger
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Weekly quota-guard worker that automatically cleans up expired or revoked guestLinks
 * and their action/proof subcollections for the current authenticated lender.
 * This frees Firestore free-tier document quota and keeps cloud storage minimal.
 */
class CleanExpiredGuestLinksWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val user = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
            ?: return Result.success()
        if (!user.isEmailVerified && !user.isAnonymous) return Result.success()
        val ownerUid = user.uid
        if (ownerUid.isBlank()) return Result.success()

        val firestore = FirebaseModule.getFirestore(applicationContext) ?: return Result.success()
        val now = System.currentTimeMillis()

        return try {
            // Query lender's links (limit 50 as permitted by rules)
            val linksSnap = withTimeoutOrNull(10000L) {
                firestore.collection("guestLinks")
                    .whereEqualTo("ownerUid", ownerUid)
                    .limit(50)
                    .get()
                    .await()
            } ?: return Result.success()

            val dao = runCatching {
                UdhaarDatabase.getInstance(applicationContext).udhaarDao()
            }.getOrNull()

            for (linkDoc in linksSnap.documents) {
                val status = linkDoc.getString("status") ?: ""
                val expiresAtMs = linkDoc.getLong("expiresAtMs") ?: Long.MAX_VALUE
                val isExpired = expiresAtMs <= now || status == "REVOKED" || status == "EXPIRED"

                if (isExpired) {
                    val linkId = linkDoc.id

                    // 1. Delete all actions and their proof documents
                    runCatching {
                        withTimeoutOrNull(5000L) {
                            val actionsSnap = linkDoc.reference.collection("actions").get().await()
                            for (actionDoc in actionsSnap.documents) {
                                runCatching {
                                    actionDoc.reference.collection("proof").document("image").delete().await()
                                }
                                runCatching {
                                    actionDoc.reference.delete().await()
                                }
                            }
                        }
                    }

                    // 2. Delete the parent guestLink document to reclaim free tier quota
                    runCatching {
                        withTimeoutOrNull(5000L) {
                            linkDoc.reference.delete().await()
                        }
                    }

                    // 3. Mark matching local transaction as EXPIRED if it was active
                    if (dao != null) {
                        val allTx = dao.getAllTransactionsSnapshot()
                        val matchedTx = allTx.firstOrNull { tx ->
                            tx.shareToken.isNotBlank() &&
                                com.jyotirmoyb.udhaar.data.cloud.GuestWebCloudRepository.sha256Hex(tx.shareToken) == linkId
                        }
                        if (matchedTx != null && matchedTx.webLinkStatus == "ACTIVE") {
                            dao.updateTransaction(matchedTx.copy(webLinkStatus = "EXPIRED"))
                        }
                    }
                }
            }

            SafeLogger.i(TAG, "Expired guest links cleanup completed successfully")
            Result.success()
        } catch (e: Exception) {
            if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) {
                SafeLogger.w(TAG, "Spark Firestore quota exhausted during expired links cleanup. Backing off.")
                return Result.success()
            }
            SafeLogger.recordNonFatal(TAG, "CLEANUP_FAILED", e)
            Result.success()
        }
    }

    companion object {
        const val TAG = "CleanExpiredLinksWorker"
        const val WORK_NAME_WEEKLY = "udhaar_clean_expired_guest_links_weekly"

        fun enqueueWeekly(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<CleanExpiredGuestLinksWorker>(7, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_WEEKLY,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_WEEKLY)
        }
    }
}
