package com.jyotirmoyb.udhaar.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.CurrencyType

object WhatsAppReminderLauncher {

    private val WHATSAPP_PACKAGES = listOf(
        "com.whatsapp",
        "com.whatsapp.w4b"
    )

    /**
     * Returns the package name of an installed WhatsApp client ("com.whatsapp" or "com.whatsapp.w4b"),
     * or null if neither is installed on the device.
     */
    fun findInstalledWhatsAppPackage(context: Context): String? {
        val pm = context.packageManager
        for (pkg in WHATSAPP_PACKAGES) {
            val isInstalled = runCatching {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0) != null
            }.getOrDefault(false)
            if (isInstalled) {
                return pkg
            }
        }
        return null
    }

    /**
     * Launches WhatsApp directly with the pre-filled reminder message if WhatsApp (or WhatsApp Business)
     * is installed; otherwise falls back to the standard Android share sheet with the pre-filled text.
     *
     * @return true if either the WhatsApp intent or the fallback share sheet was launched.
     */
    fun launchReminder(
        context: Context,
        rawPhoneNumber: String,
        currency: CurrencyType,
        message: String
    ): Boolean {
        val cleanMessage = message.trim()
        val sanitizedPhone = InterestCalculator.sanitizePhoneForWhatsApp(rawPhoneNumber, currency)
        val whatsAppPackage = findInstalledWhatsAppPackage(context)

        if (whatsAppPackage != null) {
            val launchedWhatsApp = runCatching {
                val waIntent = if (sanitizedPhone.isNotBlank()) {
                    val encodedMessage = Uri.encode(cleanMessage)
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://wa.me/$sanitizedPhone?text=$encodedMessage")
                    ).apply {
                        setPackage(whatsAppPackage)
                    }
                } else {
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, cleanMessage)
                        setPackage(whatsAppPackage)
                    }
                }
                if (context !is Activity) {
                    waIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(waIntent)
                true
            }.getOrDefault(false)

            if (launchedWhatsApp) {
                return true
            }
        }

        return launchShareSheetFallback(context, cleanMessage)
    }

    /**
     * Launches the standard Android share sheet pre-filled with the reminder text.
     */
    fun launchShareSheetFallback(
        context: Context,
        message: String
    ): Boolean {
        return runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            val chooserTitle = context.getString(R.string.ui_str_send_reminder_1e475a)
            val chooserIntent = Intent.createChooser(sendIntent, chooserTitle).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(chooserIntent)
            true
        }.onFailure {
            Toast.makeText(
                context,
                context.getString(R.string.contact_error_no_app),
                Toast.LENGTH_SHORT
            ).show()
        }.getOrDefault(false)
    }
}
