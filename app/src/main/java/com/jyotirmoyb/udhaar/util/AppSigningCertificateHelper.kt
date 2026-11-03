package com.jyotirmoyb.udhaar.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import java.util.Locale

data class AppSigningInfo(
    val packageName: String,
    val sha1: String,
    val sha256: String,
    val rawError: String? = null
) {
    val sha1Fingerprint: String get() = sha1
    val sha256Fingerprint: String get() = sha256
}

object AppSigningCertificateHelper {

    /**
     * Formats a byte array fingerprint into a colon-separated uppercase hex string.
     * Example: [0xFB.toByte(), 0xF9.toByte()] -> "FB:F9"
     */
    fun formatFingerprint(bytes: ByteArray): String {
        return bytes.joinToString(":") { "%02X".format(Locale.US, it) }
    }

    /**
     * Computes the cryptographic digest of the raw certificate bytes and returns
     * it as a colon-separated uppercase hex string.
     */
    fun computeDigest(bytes: ByteArray, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm).digest(bytes)
        return formatFingerprint(digest)
    }

    /**
     * Reads the application's own signing certificates using [PackageManager]
     * (supporting both API 28+ [PackageManager.GET_SIGNING_CERTIFICATES] and legacy [PackageManager.GET_SIGNATURES])
     * and returns the package name, SHA-1, and SHA-256 fingerprints.
     */
    fun getSigningInfo(context: Context): AppSigningInfo {
        val packageName = context.packageName
        return runCatching {
            val pm = context.packageManager
            val signatures: Array<android.content.pm.Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = packageInfo.signingInfo
                if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                } else {
                    emptyArray()
                }
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                packageInfo.signatures ?: emptyArray()
            } ?: emptyArray()

            val primaryCert = signatures.firstOrNull()?.toByteArray()
                ?: return AppSigningInfo(packageName, "", "", "No signing certificates found")

            val sha1 = computeDigest(primaryCert, "SHA-1")
            val sha256 = computeDigest(primaryCert, "SHA-256")
            AppSigningInfo(
                packageName = packageName,
                sha1 = sha1,
                sha256 = sha256
            )
        }.getOrElse { e ->
            AppSigningInfo(
                packageName = packageName,
                sha1 = "",
                sha256 = "",
                rawError = e.message ?: "Failed to read certificate"
            )
        }
    }
}
