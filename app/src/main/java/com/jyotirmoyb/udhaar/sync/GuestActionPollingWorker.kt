package com.jyotirmoyb.udhaar.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.security.EncryptedTokenStore
import com.jyotirmoyb.udhaar.security.TokenStore
import com.jyotirmoyb.udhaar.util.SafeLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Background Guest Action Polling Worker (Spark Mode)
 *
 * In Spark Mode, server push (Cloud Functions FCM Admin) is not available.
 * This worker polls Firestore for new proposals submitted via guest capability links
 * (/guestLinks/{linkId}/actions) and displays a device-local notification.
 *
 * Runs every ~30 minutes when network is available and sync is enabled.
 */
@HiltWorker
class GuestActionPollingWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tokenStore: TokenStore
) : CoroutineWorker(appContext, workerParams) {

    constructor(appContext: Context, workerParams: WorkerParameters) : this(
        appContext,
        workerParams,
        EncryptedTokenStore(appContext)
    )

    override suspend fun doWork(): Result {
        if (!tokenStore.isSignedIn() || !tokenStore.isCloudSyncEnabled()) {
            return Result.success()
        }

        val currentUser = FirebaseAuth.getInstance().currentUser ?: return Result.success()
        val ownerUid = currentUser.uid

        return try {
            val firestore = FirebaseFirestore.getInstance()
            val snap = firestore.collectionGroup("actions")
                .whereEqualTo("ownerUid", ownerUid)
                .get()
                .await()

            if (!snap.isEmpty) {
                val pendingCount = snap.size()
                SafeLogger.i(TAG, "Discovered $pendingCount guest action proposals for owner: $ownerUid")
                showLocalNotification(pendingCount)
            }
            Result.success()
        } catch (t: Throwable) {
            SafeLogger.w(TAG, "Guest action polling failed, will retry: ${t.message}")
            Result.retry()
        }
    }

    private fun showLocalNotification(count: Int) {
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val channelId = "udhaar_guest_actions"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Guest Proposals & Confirmations",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when someone confirms, disputes, or proposes a repayment date via web links."
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("New proposals on your web links")
            .setContentText("You have $count pending proposal(s) from parties to review.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "GuestActionPolling"
        private const val PERIODIC_WORK_NAME = "udhaar_guest_action_polling"
        private const val NOTIFICATION_ID = 20261108

        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<GuestActionPollingWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }
    }
}
