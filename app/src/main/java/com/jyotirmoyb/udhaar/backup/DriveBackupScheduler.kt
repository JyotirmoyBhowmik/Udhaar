package com.jyotirmoyb.udhaar.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jyotirmoyb.udhaar.data.AutoBackupFrequency
import java.util.concurrent.TimeUnit

object DriveBackupScheduler {
    const val UNIQUE_WORK_NAME = "udhaar_drive_auto_backup"

    fun scheduleOrCancel(
        context: Context,
        frequency: AutoBackupFrequency,
        wifiOnly: Boolean
    ) {
        val workManager = WorkManager.getInstance(context)
        if (frequency == AutoBackupFrequency.OFF) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val repeatIntervalDays = if (frequency == AutoBackupFrequency.DAILY) 1L else 7L
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(repeatIntervalDays, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
