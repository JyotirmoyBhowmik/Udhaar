package com.jyotirmoyb.udhaar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.jyotirmoyb.udhaar.MainActivity
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.notification.ReminderReceiver
import com.jyotirmoyb.udhaar.util.InterestCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class UdhaarWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgetsInternal(context, appWidgetManager, appWidgetIds)
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, UdhaarWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (allWidgetIds.isNotEmpty()) {
                updateWidgetsInternal(context, appWidgetManager, allWidgetIds)
            }
        }

        private fun updateWidgetsInternal(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val pendingResult = CoroutineScope(Dispatchers.IO).launch {
                val db = UdhaarDatabase.getInstance(context)
                val dao = db.udhaarDao()
                val transactions = dao.getAllTransactions().first()
                val allPayments = dao.getAllPartialPayments().first().groupBy { it.transactionId }

                var toReceiveInr = 0.0
                var toPayInr = 0.0
                var overdueCount = 0
                val now = System.currentTimeMillis()

                for (tx in transactions) {
                    if (tx.isSettled) continue
                    val summary = InterestCalculator.calculate(tx)

                    if (tx.currency == CurrencyType.INR) {
                        if (tx.transactionType == TransactionType.GIVEN) {
                            toReceiveInr += summary.netBalanceDue
                        } else {
                            toPayInr += summary.netBalanceDue
                        }
                    }

                    val exp = tx.expiryDateMillis
                    if (exp != null && exp > 0 && exp < now) {
                        overdueCount++
                    }
                }

                val formattedReceive = InterestCalculator.formatCurrency(toReceiveInr, CurrencyType.INR)
                val formattedPay = InterestCalculator.formatCurrency(toPayInr, CurrencyType.INR)

                for (widgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_udhaar_summary)
                    views.setTextViewText(R.id.widget_total_receive, formattedReceive)
                    views.setTextViewText(R.id.widget_total_pay, formattedPay)
                    views.setTextViewText(
                        R.id.widget_overdue_badge,
                        if (overdueCount > 0) "$overdueCount OVERDUE" else "ALL CLEAR ✓"
                    )

                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                    appWidgetManager.updateAppWidget(widgetId, views)
                }

                // If any transactions are overdue, trigger the overdue digest check
                if (overdueCount > 0) {
                    ReminderReceiver.checkAndSendOverdueDigest(context, overdueCount, toReceiveInr)
                }
            }
        }
    }
}
