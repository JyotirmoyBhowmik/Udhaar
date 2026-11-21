package com.jyotirmoyb.udhaar.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.cloud.GuestWebCloudRepository
import com.jyotirmoyb.udhaar.data.cloud.VerificationCloudRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseApp(@ApplicationContext context: Context): FirebaseApp? {
        return try {
            val apps = FirebaseApp.getApps(context)
            val app = if (apps.isEmpty()) {
                FirebaseApp.initializeApp(context)
            } else {
                apps.first()
            }
            app?.let { initAppCheck(it) }
            app
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Initializes Firebase App Check using the build-type-specific [AppCheckProviderInstaller]:
     * - `debug` source set installs the debug App Check provider (`debugImplementation` only)
     * - `release` source set installs PlayIntegrityAppCheckProviderFactory
     */
    fun initAppCheck(app: FirebaseApp) {
        runCatching {
            AppCheckProviderInstaller.install(app)
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(app: FirebaseApp?): FirebaseAuth? {
        if (app == null) return null
        return try {
            FirebaseAuth.getInstance(app)
        } catch (_: Throwable) {
            null
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(
        app: FirebaseApp?,
        @ApplicationContext context: Context
    ): FirebaseFirestore? {
        if (app == null) return null
        return try {
            // [BLAZE-DISABLED START] reason: free tier covers only one database | restore: uncomment FirebaseFirestore.getInstance(app, context.getString(R.string.firestore_database_id)) when upgraded to Blaze plan
            // FirebaseFirestore.getInstance(
            //     app,
            //     context.getString(R.string.firestore_database_id)
            // )
            // [BLAZE-DISABLED END]
            FirebaseFirestore.getInstance()
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Single provider accessor for non-injected contexts (Workers, Receivers, Utility helpers, Tests).
     * Routes strictly through this single Firestore provider to ensure only the (default) database is used.
     */
    fun getFirestore(context: Context? = null): FirebaseFirestore? {
        val app = if (context != null) {
            provideFirebaseApp(context.applicationContext)
        } else {
            runCatching { FirebaseApp.getInstance() }.getOrNull()
        }
        return if (app != null) {
            provideFirebaseFirestore(app, context ?: app.applicationContext)
        } else {
            try {
                FirebaseFirestore.getInstance()
            } catch (_: Throwable) {
                null
            }
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseFunctions(app: FirebaseApp?): FirebaseFunctions? {
        if (!com.jyotirmoyb.udhaar.BuildConfig.BLAZE_FEATURES_ENABLED) {
            return null
        }
        // [BLAZE-DISABLED START] reason: Cloud Functions unavailable on Spark plan | restore: toggle BLAZE_FEATURES_ENABLED = true
        if (app == null) return null
        return try {
            FirebaseFunctions.getInstance(app, "asia-south1")
        } catch (_: Throwable) {
            null
        }
        // [BLAZE-DISABLED END]
    }

    // [BLAZE-DISABLED START] reason: Cloud Storage unavailable on Spark free plan (calls return 402/403) | restore: Blaze: signed URLs, server-validated ownership, storage.rules in blaze_disabled/storage.rules
    // @Provides
    // @Singleton
    // fun provideFirebaseStorage(app: FirebaseApp?): com.google.firebase.storage.FirebaseStorage? {
    //     if (!com.jyotirmoyb.udhaar.BuildConfig.BLAZE_FEATURES_ENABLED) return null
    //     if (app == null) return null
    //     return try {
    //         com.google.firebase.storage.FirebaseStorage.getInstance(app)
    //     } catch (_: Throwable) {
    //         null
    //     }
    // }
    // [BLAZE-DISABLED END]

    @Provides
    @Singleton
    fun provideFirebaseMessaging(app: FirebaseApp?): FirebaseMessaging? {
        if (app == null) return null
        return try {
            FirebaseMessaging.getInstance()
        } catch (_: Throwable) {
            null
        }
    }

    @Provides
    @Singleton
    fun provideVerificationCloudRepository(
        firestore: FirebaseFirestore?
    ): VerificationCloudRepository? {
        return if (firestore != null) {
            VerificationCloudRepository(firestore)
        } else {
            null
        }
    }

    @Provides
    @Singleton
    fun provideGuestWebCloudRepository(
        firestore: FirebaseFirestore?,
        functions: FirebaseFunctions?,
        auth: FirebaseAuth?
    ): GuestWebCloudRepository {
        return GuestWebCloudRepository(firestore, functions, auth)
    }
}
