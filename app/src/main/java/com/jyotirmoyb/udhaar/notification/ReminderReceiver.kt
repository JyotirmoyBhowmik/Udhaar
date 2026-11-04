package com.jyotirmoyb.udhaar.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.jyotirmoyb.udhaar.MainActivity
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.ReminderChannel
import com.jyotirmoyb.udhaar.data.ReminderEvent
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.reminder.ReminderEngine
import com.jyotirmoyb.udhaar.reminder.ReminderLevel
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.SafeLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L)

        when (action) {
            ACTION_SNOOZE -> {
                if (transactionId != -1L) {
                    val days = intent.getIntExtra(EXTRA_SNOOZE_DAYS, 1)
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = UdhaarDatabase.getInstance(context)
                            val targetMillis = ReminderEngine.calculateSnoozeDate(days)
                            db.udhaarDao().updateTransactionSnooze(transactionId, targetMillis)
                            dismissNotification(context, transactionId)
                        } catch (e: Exception) {
                            SafeLogger.e("ReminderReceiver", "Error snoozing reminder", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
            ACTION_STOP_REMINDERS -> {
                if (transactionId != -1L) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = UdhaarDatabase.getInstance(context)
                            db.udhaarDao().updateTransactionRemindersDisabled(transactionId, true)
                            dismissNotification(context, transactionId)
                        } catch (e: Exception) {
                            SafeLogger.e("ReminderReceiver", "Error stopping reminders", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
            ACTION_OPEN_WHATSAPP -> {
                val phone = intent.getStringExtra(EXTRA_PHONE).orEmpty()
                val message = intent.getStringExtra(EXTRA_PREPARED_MESSAGE).orEmpty()
                val level = intent.getIntExtra(EXTRA_REMINDER_LEVEL, 1)

                if (transactionId != -1L) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = UdhaarDatabase.getInstance(context)
                            val now = System.currentTimeMillis()
                            db.udhaarDao().insertReminderEvent(
                                ReminderEvent(
                                    recordId = transactionId,
                                    level = level,
                                    sentTimeMillis = now,
                                    channel = ReminderChannel.WHATSAPP
                                )
                            )
                            db.udhaarDao().updateTransactionLastReminder(transactionId, now, level)
                        } catch (e: Exception) {
                            SafeLogger.e("ReminderReceiver", "Error logging whatsapp reminder event", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }

                dismissNotification(context, transactionId)
                openWhatsApp(context, phone, message)
            }
            ACTION_OPEN_SMS -> {
                val phone = intent.getStringExtra(EXTRA_PHONE).orEmpty()
                val message = intent.getStringExtra(EXTRA_PREPARED_MESSAGE).orEmpty()
                val level = intent.getIntExtra(EXTRA_REMINDER_LEVEL, 1)

                if (transactionId != -1L) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = UdhaarDatabase.getInstance(context)
                            val now = System.currentTimeMillis()
                            db.udhaarDao().insertReminderEvent(
                                ReminderEvent(
                                    recordId = transactionId,
                                    level = level,
                                    sentTimeMillis = now,
                                    channel = ReminderChannel.SMS
                                )
                            )
                            db.udhaarDao().updateTransactionLastReminder(transactionId, now, level)
                        } catch (e: Exception) {
                            SafeLogger.e("ReminderReceiver", "Error logging sms reminder event", e)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }

                dismissNotification(context, transactionId)
                openSms(context, phone, message)
            }
            else -> {
                if (transactionId == -1L) return

                val partyName = intent.getStringExtra(EXTRA_PARTY_NAME) ?: "Contact"
                val formattedAmount = intent.getStringExtra(EXTRA_FORMATTED_AMOUNT) ?: ""
                val txTypeLabel = intent.getStringExtra(EXTRA_TX_TYPE) ?: "Udhaar"
                val reminderDaysBefore = intent.getIntExtra(EXTRA_DAYS_BEFORE, 0)
                val daysOverdue = intent.getIntExtra(EXTRA_DAYS_OVERDUE, 0)
                val level = intent.getIntExtra(EXTRA_REMINDER_LEVEL, 1)
                val isBorrower = intent.getBooleanExtra(EXTRA_IS_BORROWER, false)
                val dueDateStr = intent.getStringExtra(EXTRA_DUE_DATE).orEmpty()
                val phone = intent.getStringExtra(EXTRA_PHONE).orEmpty()

                showNotification(
                    context = context,
                    transactionId = transactionId,
                    partyName = partyName,
                    formattedAmount = formattedAmount,
                    txTypeLabel = txTypeLabel,
                    daysBefore = reminderDaysBefore,
                    daysOverdue = daysOverdue,
                    level = level,
                    isBorrower = isBorrower,
                    dueDateStr = dueDateStr,
                    phone = phone
                )
            }
        }
    }

    private fun dismissNotification(context: Context, transactionId: Long) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(transactionId.toInt())
    }

    private fun openWhatsApp(context: Context, phone: String, message: String) {
        try {
            val cleanPhone = phone.replace(Regex("[^0-9+]"), "").removePrefix("+")
            val uri = if (cleanPhone.isNotBlank()) {
                Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}")
            } else {
                Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(message)}")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            SafeLogger.e("ReminderReceiver", "Failed to launch WhatsApp", e)
        }
    }

    private fun openSms(context: Context, phone: String, message: String) {
        try {
            val cleanPhone = phone.replace(Regex("[^0-9+]"), "")
            val uri = if (cleanPhone.isNotBlank()) Uri.parse("smsto:$cleanPhone") else Uri.parse("smsto:")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            SafeLogger.e("ReminderReceiver", "Failed to launch SMS", e)
        }
    }

    companion object {
        const val CHANNEL_ID = "udhaar_reminders_channel"
        const val OVERDUE_CHANNEL_ID = "udhaar_overdue_digest_channel"

        const val ACTION_SNOOZE = "com.jyotirmoyb.udhaar.action.SNOOZE"
        const val ACTION_STOP_REMINDERS = "com.jyotirmoyb.udhaar.action.STOP_REMINDERS"
        const val ACTION_OPEN_WHATSAPP = "com.jyotirmoyb.udhaar.action.OPEN_WHATSAPP"
        const val ACTION_OPEN_SMS = "com.jyotirmoyb.udhaar.action.OPEN_SMS"

        const val EXTRA_TRANSACTION_ID = "extra_transaction_id"
        const val EXTRA_PARTY_NAME = "extra_party_name"
        const val EXTRA_FORMATTED_AMOUNT = "extra_formatted_amount"
        const val EXTRA_TX_TYPE = "extra_tx_type"
        const val EXTRA_DAYS_BEFORE = "extra_days_before"
        const val EXTRA_DAYS_OVERDUE = "extra_days_overdue"
        const val EXTRA_REMINDER_LEVEL = "extra_reminder_level"
        const val EXTRA_IS_BORROWER = "extra_is_borrower"
        const val EXTRA_DUE_DATE = "extra_due_date"
        const val EXTRA_PHONE = "extra_phone"
        const val EXTRA_PREPARED_MESSAGE = "extra_prepared_message"
        const val EXTRA_SNOOZE_DAYS = "extra_snooze_days"

        private const val PREFS_DIGEST = "udhaar_digest_prefs"
        private const val KEY_LAST_DIGEST_DATE = "last_digest_date"

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val reminderChannel = NotificationChannel(
                    CHANNEL_ID,
                    "Udhaar Polite Reminders",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Polite, helpful settlement reminders with WhatsApp, SMS, and snooze options"
                }
                manager.createNotificationChannel(reminderChannel)

                val overdueChannel = NotificationChannel(
                    OVERDUE_CHANNEL_ID,
                    "Overdue Today Digest",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Daily morning summary of overdue loans requiring follow-up"
                }
                manager.createNotificationChannel(overdueChannel)
            }
        }

        fun showNotification(
            context: Context,
            transactionId: Long,
            partyName: String,
            formattedAmount: String,
            txTypeLabel: String,
            daysBefore: Int = 0,
            daysOverdue: Int = 0,
            level: Int = 1,
            isBorrower: Boolean = false,
            dueDateStr: String = "",
            phone: String = ""
        ) {
            ensureNotificationChannel(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_TRANSACTION_ID, transactionId)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                transactionId.toInt(),
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title: String
            val body: String

            if (isBorrower) {
                // Borrower self-reminder (polite, helpful, never threatening)
                if (daysOverdue > 0) {
                    title = context.getString(R.string.borrower_self_reminder_overdue_fmt, formattedAmount, partyName, daysOverdue)
                    body = context.getString(R.string.borrower_self_reminder_fmt, formattedAmount, partyName, dueDateStr)
                } else {
                    title = context.getString(R.string.borrower_self_reminder_fmt, formattedAmount, partyName, dueDateStr)
                    body = if (daysBefore == 0) {
                        context.getString(R.string.time_to_remind_due_today_fmt, partyName, formattedAmount)
                    } else {
                        context.getString(R.string.time_to_remind_upcoming_fmt, partyName, formattedAmount, daysBefore)
                    }
                }
            } else {
                // Lender reminder to borrower
                title = when {
                    daysOverdue > 0 -> context.getString(R.string.time_to_remind_fmt, partyName, formattedAmount, daysOverdue)
                    daysBefore == 0 -> context.getString(R.string.time_to_remind_due_today_fmt, partyName, formattedAmount)
                    else -> context.getString(R.string.time_to_remind_upcoming_fmt, partyName, formattedAmount, daysBefore)
                }
                body = "$txTypeLabel of $formattedAmount. Tap to review message or send polite reminder."
            }

            val reminderLvl = ReminderLevel.fromLevelNumber(level)
            val template = ReminderEngine.getLocalizedTemplate(context, reminderLvl, "")
            val preparedMsg = ReminderEngine.renderDraftMessage(
                template = template,
                name = partyName,
                myName = "Me",
                amount = formattedAmount.removePrefix("₹").removePrefix("रू").trim(),
                remaining = formattedAmount.removePrefix("₹").removePrefix("रू").trim(),
                paidSoFar = "0",
                dueDate = dueDateStr,
                daysOverdue = daysOverdue,
                upiId = ""
            )

            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            // Action: Snooze 1 Day
            val snooze1DayIntent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(EXTRA_TRANSACTION_ID, transactionId)
                putExtra(EXTRA_SNOOZE_DAYS, 1)
            }
            val snooze1DayPending = PendingIntent.getBroadcast(
                context,
                (transactionId * 100 + 1).toInt(),
                snooze1DayIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notificationBuilder.addAction(
                0,
                context.getString(R.string.snooze_1_day),
                snooze1DayPending
            )

            // Action: Snooze 1 Week
            val snooze1WkIntent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_SNOOZE
                putExtra(EXTRA_TRANSACTION_ID, transactionId)
                putExtra(EXTRA_SNOOZE_DAYS, 7)
            }
            val snooze1WkPending = PendingIntent.getBroadcast(
                context,
                (transactionId * 100 + 7).toInt(),
                snooze1WkIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notificationBuilder.addAction(
                0,
                context.getString(R.string.snooze_1_week),
                snooze1WkPending
            )

            // If lender, add WhatsApp and SMS quick actions
            if (!isBorrower) {
                val waIntent = Intent(context, ReminderReceiver::class.java).apply {
                    action = ACTION_OPEN_WHATSAPP
                    putExtra(EXTRA_TRANSACTION_ID, transactionId)
                    putExtra(EXTRA_PHONE, phone)
                    putExtra(EXTRA_PREPARED_MESSAGE, preparedMsg)
                    putExtra(EXTRA_REMINDER_LEVEL, level)
                }
                val waPending = PendingIntent.getBroadcast(
                    context,
                    (transactionId * 100 + 20).toInt(),
                    waIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                notificationBuilder.addAction(
                    0,
                    context.getString(R.string.reminder_channel_whatsapp),
                    waPending
                )

                val smsIntent = Intent(context, ReminderReceiver::class.java).apply {
                    action = ACTION_OPEN_SMS
                    putExtra(EXTRA_TRANSACTION_ID, transactionId)
                    putExtra(EXTRA_PHONE, phone)
                    putExtra(EXTRA_PREPARED_MESSAGE, preparedMsg)
                    putExtra(EXTRA_REMINDER_LEVEL, level)
                }
                val smsPending = PendingIntent.getBroadcast(
                    context,
                    (transactionId * 100 + 30).toInt(),
                    smsIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                notificationBuilder.addAction(
                    0,
                    context.getString(R.string.reminder_channel_sms),
                    smsPending
                )
            }

            manager.notify(transactionId.toInt(), notificationBuilder.build())
        }

        fun checkAndSendOverdueDigest(
            context: Context,
            overdueCount: Int,
            totalReceiveInr: Double
        ) {
            if (overdueCount <= 0) return
            val prefs = context.getSharedPreferences(PREFS_DIGEST, Context.MODE_PRIVATE)
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val lastSent = prefs.getString(KEY_LAST_DIGEST_DATE, "")
            if (lastSent == todayDate) return

            ensureNotificationChannel(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val tapIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                99999,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val formattedTotal = InterestCalculator.formatCurrency(totalReceiveInr, CurrencyType.INR)
            val title = "Overdue Today: $overdueCount Payment${if (overdueCount > 1) "s" else ""}"
            val body = "You have $overdueCount overdue record${if (overdueCount > 1) "s" else ""} totaling $formattedTotal to receive. Tap to follow up."

            val notification = NotificationCompat.Builder(context, OVERDUE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            manager.notify(99999, notification)
            prefs.edit().putString(KEY_LAST_DIGEST_DATE, todayDate).apply()
        }
    }
}
