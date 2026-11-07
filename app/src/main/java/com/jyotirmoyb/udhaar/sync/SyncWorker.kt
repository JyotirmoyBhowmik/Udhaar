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
import com.jyotirmoyb.udhaar.util.SafeLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepository
) : CoroutineWorker(appContext, workerParams) {

    // Fallback constructor for environments without Hilt worker factory
    constructor(appContext: Context, workerParams: WorkerParameters) : this(
        appContext,
        workerParams,
        SyncRepository(
            dao = UdhaarDatabase.getInstance(appContext).udhaarDao(),
            outboxDao = UdhaarDatabase.getInstance(appContext).syncOutboxDao()
        )
    )

    override suspend fun doWork(): Result {
        SafeLogger.i("SyncWorker", "Starting background sync work...")
        return try {
            val success = syncRepository.sync()
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
    }
}
