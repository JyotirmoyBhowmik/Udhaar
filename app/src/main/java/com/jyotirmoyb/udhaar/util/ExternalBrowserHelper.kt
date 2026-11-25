package com.jyotirmoyb.udhaar.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Resolves an installed external browser package (excluding Udhaar itself so https://udhaar.jyotirmoyb.com/u
 * App Links do not loop back into MainActivity) and opens [url] cleanly.
 */
object ExternalBrowserHelper {

    fun openInExternalBrowser(context: Context, url: String): Boolean {
        val cleanUrl = VerificationCryptoHelper.sanitizeIncomingUrl(url)
        if (!cleanUrl.startsWith("http://", ignoreCase = true) &&
            !cleanUrl.startsWith("https://", ignoreCase = true)
        ) {
            return false
        }

        val targetUri = runCatching { Uri.parse(cleanUrl) }.getOrNull() ?: return false
        val probeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)

        val pm = context.packageManager
        val resolvedBrowsers = runCatching {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(probeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrDefault(emptyList())
            .mapNotNull { it.activityInfo?.packageName }
            .distinct()
            .filter { it != context.packageName }

        val candidatePackages = if (resolvedBrowsers.isNotEmpty()) {
            resolvedBrowsers
        } else {
            @Suppress("DEPRECATION")
            runCatching { pm.queryIntentActivities(probeIntent, 0) }
                .getOrDefault(emptyList())
                .mapNotNull { it.activityInfo?.packageName }
                .distinct()
                .filter { it != context.packageName }
        }

        for (pkg in candidatePackages) {
            val viewIntent = Intent(Intent.ACTION_VIEW, targetUri)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setPackage(pkg)
            val launched = runCatching {
                context.startActivity(viewIntent)
                true
            }.getOrDefault(false)
            if (launched) return true
        }

        // Last-resort selector excluding Udhaar if browser discovery returned empty
        val fallbackSelector = Intent(Intent.ACTION_VIEW, Uri.parse("https://"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val fallbackIntent = Intent(Intent.ACTION_VIEW, targetUri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        fallbackIntent.selector = fallbackSelector
        return runCatching {
            context.startActivity(fallbackIntent)
            true
        }.onFailure { err ->
            SafeLogger.w("ExternalBrowserHelper", "No external browser available: ${err.javaClass.simpleName}")
        }.getOrDefault(false)
    }
}
