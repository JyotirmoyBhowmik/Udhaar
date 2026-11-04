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
            if (apps.isEmpty()) {
                FirebaseApp.initializeApp(context)
            } else {
                apps.first()
            }
        } catch (_: Throwable) {
            null
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
            FirebaseFirestore.getInstance(
                app,
                context.getString(R.string.firestore_database_id)
            )
        } catch (_: Throwable) {
            null
        }
    }

    @Provides
    @Singleton
    fun provideFirebaseFunctions(app: FirebaseApp?): FirebaseFunctions? {
        if (app == null) return null
        return try {
            FirebaseFunctions.getInstance(app, "asia-south1")
        } catch (_: Throwable) {
            null
        }
    }

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
