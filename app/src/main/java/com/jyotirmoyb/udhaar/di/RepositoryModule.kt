package com.jyotirmoyb.udhaar.di

import android.content.Context
import com.jyotirmoyb.udhaar.backup.DriveServiceHelper
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.data.UdhaarRepository
import com.jyotirmoyb.udhaar.data.cloud.VerificationCloudRepository
import com.jyotirmoyb.udhaar.data.repository.AttachmentRepository
import com.jyotirmoyb.udhaar.data.repository.BackupRepository
import com.jyotirmoyb.udhaar.data.repository.ChatRepository
import com.jyotirmoyb.udhaar.data.repository.InstallmentRepository
import com.jyotirmoyb.udhaar.data.repository.LedgerRepository
import com.jyotirmoyb.udhaar.data.repository.PartyRepository
import com.jyotirmoyb.udhaar.data.repository.PaymentRepository
import com.jyotirmoyb.udhaar.data.repository.SettingsRepository
import com.jyotirmoyb.udhaar.data.repository.VerificationRepository
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
    fun provideAppStateHolder(): com.jyotirmoyb.udhaar.ui.state.AppStateHolder {
        return com.jyotirmoyb.udhaar.ui.state.AppStateHolder()
    }

    @Provides
    @Singleton
    fun provideDriveServiceHelper(@ApplicationContext context: Context): DriveServiceHelper {
        return DriveServiceHelper(context)
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context,
        cloudRepository: VerificationCloudRepository?
    ): SettingsRepository {
        return SettingsRepository(context, cloudRepository)
    }

    @Provides
    @Singleton
    fun providePartyRepository(dao: UdhaarDao): PartyRepository {
        return PartyRepository(dao)
    }

    @Provides
    @Singleton
    fun provideAttachmentRepository(
        dao: UdhaarDao,
        @ApplicationContext context: Context
    ): AttachmentRepository {
        return AttachmentRepository(dao, context)
    }

    @Provides
    @Singleton
    fun provideInstallmentRepository(
        dao: UdhaarDao,
        settingsRepository: SettingsRepository,
        cloudRepository: VerificationCloudRepository?
    ): InstallmentRepository {
        return InstallmentRepository(dao, settingsRepository, cloudRepository)
    }

    @Provides
    @Singleton
    fun providePaymentRepository(
        dao: UdhaarDao,
        settingsRepository: SettingsRepository,
        cloudRepository: VerificationCloudRepository?,
        installmentRepository: InstallmentRepository
    ): PaymentRepository {
        return PaymentRepository(dao, settingsRepository, cloudRepository, installmentRepository)
    }

    @Provides
    @Singleton
    fun provideLedgerRepository(
        dao: UdhaarDao,
        settingsRepository: SettingsRepository,
        cloudRepository: VerificationCloudRepository?,
        @ApplicationContext context: Context
    ): LedgerRepository {
        return LedgerRepository(dao, settingsRepository, cloudRepository, context)
    }

    @Provides
    @Singleton
    fun provideVerificationRepository(
        dao: UdhaarDao,
        settingsRepository: SettingsRepository,
        cloudRepository: VerificationCloudRepository?,
        @ApplicationContext context: Context
    ): VerificationRepository {
        return VerificationRepository(dao, settingsRepository, cloudRepository, context)
    }

    @Provides
    @Singleton
    fun provideChatRepository(
        dao: UdhaarDao,
        settingsRepository: SettingsRepository,
        cloudRepository: VerificationCloudRepository?,
        @ApplicationContext context: Context
    ): ChatRepository {
        return ChatRepository(dao, settingsRepository, cloudRepository, context)
    }

    @Provides
    @Singleton
    fun provideBackupRepository(
        dao: UdhaarDao,
        settingsRepository: SettingsRepository,
        attachmentRepository: AttachmentRepository,
        driveServiceHelper: DriveServiceHelper,
        @ApplicationContext context: Context
    ): BackupRepository {
        return BackupRepository(
            dao = dao,
            settingsRepository = settingsRepository,
            attachmentRepository = attachmentRepository,
            driveServiceHelper = driveServiceHelper,
            context = context
        )
    }

    @Provides
    @Singleton
    fun provideGuestWebRepository(
        dao: UdhaarDao,
        settingsRepository: SettingsRepository,
        paymentRepository: PaymentRepository,
        installmentRepository: InstallmentRepository,
        guestWebCloudRepository: com.jyotirmoyb.udhaar.data.cloud.GuestWebCloudRepository,
        @ApplicationContext context: Context
    ): com.jyotirmoyb.udhaar.data.repository.GuestWebRepository {
        return com.jyotirmoyb.udhaar.data.repository.GuestWebRepository(
            dao = dao,
            settingsRepository = settingsRepository,
            paymentRepository = paymentRepository,
            installmentRepository = installmentRepository,
            guestWebCloudRepository = guestWebCloudRepository,
            context = context
        )
    }

    @Provides
    @Singleton
    fun provideUdhaarRepository(
        ledgerRepository: LedgerRepository,
        partyRepository: PartyRepository,
        paymentRepository: PaymentRepository,
        installmentRepository: InstallmentRepository,
        attachmentRepository: AttachmentRepository,
        verificationRepository: VerificationRepository,
        chatRepository: ChatRepository,
        backupRepository: BackupRepository,
        settingsRepository: SettingsRepository,
        guestWebRepository: com.jyotirmoyb.udhaar.data.repository.GuestWebRepository,
        dao: UdhaarDao
    ): UdhaarRepository {
        return UdhaarRepository(
            ledgerRepository = ledgerRepository,
            partyRepository = partyRepository,
            paymentRepository = paymentRepository,
            installmentRepository = installmentRepository,
            attachmentRepository = attachmentRepository,
            verificationRepository = verificationRepository,
            chatRepository = chatRepository,
            backupRepository = backupRepository,
            settingsRepository = settingsRepository,
            guestWebRepository = guestWebRepository,
            dao = dao
        )
    }

    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context): com.jyotirmoyb.udhaar.security.TokenStore {
        return com.jyotirmoyb.udhaar.security.EncryptedTokenStore(context)
    }

    @Provides
    @Singleton
    fun provideApiClient(
        tokenStore: com.jyotirmoyb.udhaar.security.TokenStore
    ): com.jyotirmoyb.udhaar.sync.ApiClient {
        return com.jyotirmoyb.udhaar.sync.ApiClient(
            baseUrl = "https://app.udhaar.jyotirmoyb.com",
            tokenStore = tokenStore
        )
    }

    @Provides
    @Singleton
    fun provideSyncRepository(
        dao: UdhaarDao,
        outboxDao: com.jyotirmoyb.udhaar.data.SyncOutboxDao,
        tokenStore: com.jyotirmoyb.udhaar.security.TokenStore,
        apiClient: com.jyotirmoyb.udhaar.sync.ApiClient
    ): com.jyotirmoyb.udhaar.sync.SyncRepository {
        val transport: com.jyotirmoyb.udhaar.sync.SyncCloudTransport =
            if (com.jyotirmoyb.udhaar.BuildConfig.UDHAAR_TIER.equals("blaze", ignoreCase = true)) {
                com.jyotirmoyb.udhaar.sync.HttpSyncCloudTransport(apiClient)
            } else {
                com.jyotirmoyb.udhaar.sync.FirestoreSyncTransport()
            }
        return com.jyotirmoyb.udhaar.sync.SyncRepository(dao, outboxDao, transport, tokenStore)
    }
}
