package com.jyotirmoyb.udhaar.di

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Release-only Firebase App Check provider installer.
 * Uses Play Integrity attestation exclusively; debug App Check providers are excluded from release builds.
 */
object AppCheckProviderInstaller {
    const val PROVIDER_NAME: String = "PlayIntegrityAppCheckProviderFactory"

    fun install(app: FirebaseApp) {
        val appCheck = FirebaseAppCheck.getInstance(app)
        appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    }
}
