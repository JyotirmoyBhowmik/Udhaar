package com.jyotirmoyb.udhaar.di

import android.content.Context
import com.jyotirmoyb.udhaar.data.SyncOutboxDao
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideUdhaarDatabase(@ApplicationContext context: Context): UdhaarDatabase {
        return UdhaarDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideUdhaarDao(database: UdhaarDatabase): UdhaarDao {
        return database.udhaarDao()
    }

    @Provides
    @Singleton
    fun provideSyncOutboxDao(database: UdhaarDatabase): SyncOutboxDao {
        return database.syncOutboxDao()
    }
}
