package com.jyotirmoyb.udhaar.di

import android.content.Context
import com.jyotirmoyb.udhaar.backup.DriveServiceHelper
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.data.UdhaarRepository
import com.jyotirmoyb.udhaar.data.cloud.VerificationCloudRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideDriveServiceHelper(@ApplicationContext context: Context): DriveServiceHelper {
        return DriveServiceHelper(context)
    }

    @Provides
    @Singleton
    fun provideUdhaarRepository(
        dao: UdhaarDao,
        @ApplicationContext context: Context,
        cloudRepository: VerificationCloudRepository?
    ): UdhaarRepository {
        return UdhaarRepository(
            dao = dao,
            context = context,
            cloudRepositoryOverride = cloudRepository
        )
    }
}
