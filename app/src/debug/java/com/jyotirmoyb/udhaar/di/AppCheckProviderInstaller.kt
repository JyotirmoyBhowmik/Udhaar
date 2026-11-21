package com.jyotirmoyb.udhaar.di

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug-only Firebase App Check provider installer.
 * Lives exclusively in the `debug` source set so `firebase-appcheck-debug` is never
 * referenced or packaged in `release` builds.
 */
object AppCheckProviderInstaller {
    const val PROVIDER_NAME: String = "DebugAppCheckProviderFactory"

    fun install(app: FirebaseApp) {
        val appCheck = FirebaseAppCheck.getInstance(app)
        appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
