package com.jyotirmoyb.udhaar.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jyotirmoyb.udhaar.widget.UdhaarWidgetProvider

class BootRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            RecurringReminderScheduler.rescheduleAllActiveReminders(context)
            UdhaarWidgetProvider.updateAllWidgets(context)
        }
    }
}
