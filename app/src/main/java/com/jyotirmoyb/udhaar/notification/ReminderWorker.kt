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
            val activePlansByTx = dao.getAllInstallmentPlansSnapshot()
                .filter { it.status == com.jyotirmoyb.udhaar.data.InstallmentPlanStatus.ACTIVE }
                .associateBy { it.transactionId }
            val installmentsByPlan = dao.getAllInstallmentsSnapshot().groupBy { it.planId }

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

                val activePlan = activePlansByTx[tx.id]
                val nextUnpaidInstallment = activePlan?.let { plan ->
                    installmentsByPlan[plan.id]
                        .orEmpty()
                        .sortedBy { it.sequence }
                        .firstOrNull { it.amountPaid < it.amountDue && it.status != com.jyotirmoyb.udhaar.data.InstallmentStatus.PAID }
                }

                // If the record has an active installment plan and all installments are paid, stop reminders
                if (activePlan != null && nextUnpaidInstallment == null) continue

                val level = if (nextUnpaidInstallment != null) {
                    ReminderEngine.computeInstallmentEscalationLevel(nextUnpaidInstallment, policy, now) ?: continue
                } else {
                    ReminderEngine.computeEscalationLevel(tx, policy, now) ?: continue
                }

                val effectiveDueMillis = nextUnpaidInstallment?.dueDate ?: tx.expiryDateMillis
                val overdueDays = ReminderEngine.calculateOverdueDays(effectiveDueMillis, now) ?: 0
                val summary = InterestCalculator.calculate(tx, now)
                val formattedAmount = if (nextUnpaidInstallment != null) {
                    val instRemaining = com.jyotirmoyb.udhaar.util.InstallmentCalculator.paiseToDouble(
                        nextUnpaidInstallment.amountDue - nextUnpaidInstallment.amountPaid
                    )
                    InterestCalculator.formatCurrency(instRemaining, tx.currency)
                } else {
                    InterestCalculator.formatCurrency(summary.netBalanceDue, tx.currency)
                }

                val dueDateFormatted = effectiveDueMillis?.let {
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
