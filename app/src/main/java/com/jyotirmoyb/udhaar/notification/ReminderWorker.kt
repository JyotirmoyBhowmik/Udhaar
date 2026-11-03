package com.jyotirmoyb.udhaar.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jyotirmoyb.udhaar.data.ReminderChannel
import com.jyotirmoyb.udhaar.data.ReminderEvent
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.data.UdhaarRepository
import com.jyotirmoyb.udhaar.reminder.ReminderEngine
import com.jyotirmoyb.udhaar.reminder.ReminderPolicy
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.SafeLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val injectedRepository: UdhaarRepository,
    private val injectedDatabase: UdhaarDatabase
) : CoroutineWorker(appContext, workerParams) {

    constructor(appContext: Context, workerParams: WorkerParameters) : this(
        appContext,
        workerParams,
        UdhaarRepository(UdhaarDatabase.getInstance(appContext).udhaarDao(), appContext),
        UdhaarDatabase.getInstance(appContext)
    )

    override suspend fun doWork(): Result {
        return try {
            val db = injectedDatabase
            val dao = db.udhaarDao()
            val transactions = dao.getAllTransactions().first()
            val parties = dao.getAllParties().first().associateBy { it.partyId }

            val repo = injectedRepository
            val settings = repo.userSettings.first()

            val now = System.currentTimeMillis()
            val policy = ReminderPolicy(
                level1DaysBefore = settings.reminderLevel1DaysBefore,
                level2MaxOverdueDays = settings.reminderLevel2MaxOverdueDays,
                level3MaxOverdueDays = settings.reminderLevel3MaxOverdueDays,
                quietHoursEnabled = settings.quietHoursEnabled,
                quietHoursStartHour = settings.quietHoursStartHour,
                quietHoursEndHour = settings.quietHoursEndHour
            )

            for (tx in transactions) {
                if (ReminderEngine.shouldStopReminders(tx, now)) continue
                if (!ReminderEngine.canSendReminderToday(tx, now)) continue

                val party = tx.partyId?.let { parties[it] }
                val partyOverride = party?.quietHoursOverride ?: false

                if (policy.quietHoursEnabled && ReminderEngine.isInQuietHours(now, policy.quietHoursStartHour, policy.quietHoursEndHour, partyOverride)) {
                    continue
                }

                val level = ReminderEngine.computeEscalationLevel(tx, policy, now) ?: continue
                val overdueDays = ReminderEngine.calculateOverdueDays(tx.expiryDateMillis, now) ?: 0
                val summary = InterestCalculator.calculate(tx, now)
                val formattedAmount = InterestCalculator.formatCurrency(summary.netBalanceDue, tx.currency)

                val dueDateFormatted = tx.expiryDateMillis?.let {
                    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it))
                }.orEmpty()

                ReminderReceiver.showNotification(
                    context = applicationContext,
                    transactionId = tx.id,
                    partyName = tx.partyName,
                    formattedAmount = formattedAmount,
                    txTypeLabel = if (tx.transactionType == TransactionType.GIVEN) "Given (Lent)" else "Taken (Borrowed)",
                    daysBefore = if (overdueDays < 0) -overdueDays else 0,
                    daysOverdue = if (overdueDays > 0) overdueDays else 0,
                    level = level.levelNumber,
                    isBorrower = tx.transactionType == TransactionType.TAKEN,
                    dueDateStr = dueDateFormatted
                )

                dao.insertReminderEvent(
                    ReminderEvent(
                        recordId = tx.id,
                        level = level.levelNumber,
                        sentTimeMillis = now,
                        channel = ReminderChannel.NOTIFICATION
                    )
                )
                dao.updateTransactionLastReminder(tx.id, now, level.levelNumber)
            }

            Result.success()
        } catch (e: Exception) {
            SafeLogger.e("ReminderWorker", "Error executing daily reminder check", e)
            Result.retry()
        }
    }
}
