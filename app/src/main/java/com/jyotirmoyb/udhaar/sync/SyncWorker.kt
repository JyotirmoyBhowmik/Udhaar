package com.jyotirmoyb.udhaar.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.security.EncryptedTokenStore
import com.jyotirmoyb.udhaar.security.TokenStore
import com.jyotirmoyb.udhaar.util.SafeLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val tokenStore: TokenStore
) : CoroutineWorker(appContext, workerParams) {

    // Fallback constructor for environments without Hilt worker factory
    constructor(appContext: Context, workerParams: WorkerParameters) : this(
        appContext,
        workerParams,
        SyncRepository(
            dao = UdhaarDatabase.getInstance(appContext).udhaarDao(),
            outboxDao = UdhaarDatabase.getInstance(appContext).syncOutboxDao()
        ),
        EncryptedTokenStore(appContext)
    )

    override suspend fun doWork(): Result {
        val store = tokenStore

        // Strict Feature Flag Check: Off by default.
        // With flag off or signed out, make NO network calls and behave exactly as before.
        if (!store.isSignedIn() || !store.isCloudSyncEnabled()) {
            SafeLogger.i("SyncWorker", "Cloud sync disabled or user signed out; skipping all network sync operations.")
            return Result.success()
        }

        SafeLogger.i("SyncWorker", "Starting background sync work for user: ${store.getUserId()}")
        return try {
            val success = syncRepository.sync()
            store.setLastSyncTimestamp(System.currentTimeMillis())
            SafeLogger.i("SyncWorker", "Sync work finished successfully: $success")
            Result.success()
        } catch (t: Throwable) {
            SafeLogger.e("SyncWorker", "Sync worker encountered an error, scheduling retry", t)
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_SYNC_WORK_NAME = "udhaar_periodic_sync"
        private const val IMMEDIATE_SYNC_WORK_NAME = "udhaar_immediate_sync"

        fun enqueueImmediate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_SYNC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancelSync(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_SYNC_WORK_NAME)
            WorkManager.getInstance(context).cancelUniqueWork(IMMEDIATE_SYNC_WORK_NAME)
        }
    }
}
