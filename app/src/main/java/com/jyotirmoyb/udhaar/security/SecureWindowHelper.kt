package com.jyotirmoyb.udhaar.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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

/**
 * Blocks screenshots and screen recordings (WindowManager.LayoutParams.FLAG_SECURE)
 * on the active Activity Window and any Dialog Window while [enabled] is true.
 */
@Composable
fun SecureScreenEffect(enabled: Boolean = true) {
    val context = LocalContext.current
    val view = LocalView.current

    DisposableEffect(context, view, enabled) {
        val activityWindow = context.findActivity()?.window
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window

        val activityHadSecure = activityWindow?.attributes?.flags?.let {
            (it and WindowManager.LayoutParams.FLAG_SECURE) != 0
        } ?: false
        val dialogHadSecure = dialogWindow?.attributes?.flags?.let {
            (it and WindowManager.LayoutParams.FLAG_SECURE) != 0
        } ?: false

        if (enabled) {
            activityWindow?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialogWindow?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        onDispose {
            if (enabled) {
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
