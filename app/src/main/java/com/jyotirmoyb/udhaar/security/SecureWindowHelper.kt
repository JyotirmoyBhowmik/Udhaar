package com.jyotirmoyb.udhaar.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun isRunningOnEmulator(): Boolean {
    return Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.lowercase().contains("emulator") ||
        Build.MODEL.contains("google_sdk") ||
        Build.MODEL.lowercase().contains("emulator") ||
        Build.MODEL.contains("Android SDK built for") ||
        Build.MODEL.contains("sdk_gphone") ||
        Build.HARDWARE.contains("goldfish") ||
        Build.HARDWARE.contains("ranchu") ||
        Build.PRODUCT.contains("sdk") ||
        Build.PRODUCT.contains("emulator")
}

/**
 * Blocks screenshots and screen recordings (WindowManager.LayoutParams.FLAG_SECURE)
 * on the active Activity Window and any Dialog Window while [enabled] is true.
 * Automatically skipped on emulators/streaming displays so the screen never turns black.
 */
@Composable
fun SecureScreenEffect(enabled: Boolean = true) {
    val context = LocalContext.current
    val view = LocalView.current
    val effectiveEnabled = enabled && !isRunningOnEmulator()

    DisposableEffect(context, view, effectiveEnabled) {
        val activityWindow = context.findActivity()?.window
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window

        val activityHadSecure = activityWindow?.attributes?.flags?.let {
            (it and WindowManager.LayoutParams.FLAG_SECURE) != 0
        } ?: false
        val dialogHadSecure = dialogWindow?.attributes?.flags?.let {
            (it and WindowManager.LayoutParams.FLAG_SECURE) != 0
        } ?: false

        if (effectiveEnabled) {
            activityWindow?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialogWindow?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activityWindow?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        onDispose {
            if (effectiveEnabled) {
                if (!activityHadSecure) {
                    activityWindow?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                if (!dialogHadSecure) {
                    dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
    }
}
