package com.jyotirmoyb.udhaar.di

import android.content.Context
import com.jyotirmoyb.udhaar.backup.DriveServiceHelper
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.data.UdhaarRepository
import com.jyotirmoyb.udhaar.data.cloud.VerificationCloudRepository
import com.jyotirmoyb.udhaar.data.repository.BackupRepository
import com.jyotirmoyb.udhaar.data.repository.ChatRepository
import com.jyotirmoyb.udhaar.data.repository.LedgerRepository
import com.jyotirmoyb.udhaar.data.repository.SettingsRepository
import com.jyotirmoyb.udhaar.data.repository.VerificationRepository
import com.jyotirmoyb.udhaar.ui.UdhaarViewModel
import com.jyotirmoyb.udhaar.ui.viewmodel.BackupViewModel
import com.jyotirmoyb.udhaar.ui.viewmodel.ChatViewModel
import com.jyotirmoyb.udhaar.ui.viewmodel.LedgerViewModel
import com.jyotirmoyb.udhaar.ui.viewmodel.SettingsViewModel
import com.jyotirmoyb.udhaar.ui.viewmodel.VerificationViewModel

/**
 * Dependency Injection container providing constructor-injected repositories and ViewModel factories
 * for the application and test harnesses.
 */
class UdhaarAppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val database: UdhaarDatabase by lazy {
        UdhaarDatabase.getInstance(appContext)
    }

    val cloudRepository: VerificationCloudRepository by lazy {
        VerificationCloudRepository(appContext)
    }

    val driveServiceHelper: DriveServiceHelper by lazy {
        DriveServiceHelper(appContext)
    }

    val udhaarRepository: UdhaarRepository by lazy {
        UdhaarRepository(
            dao = database.udhaarDao(),
            context = appContext,
            cloudRepositoryOverride = cloudRepository
        )
    }

    val ledgerRepository: LedgerRepository by lazy {
        LedgerRepository(udhaarRepository)
    }

    val verificationRepository: VerificationRepository by lazy {
        VerificationRepository(udhaarRepository)
    }

    val chatRepository: ChatRepository by lazy {
        ChatRepository(udhaarRepository)
    }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(udhaarRepository, driveServiceHelper)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(udhaarRepository)
    }

    fun provideMainViewModelFactory(): UdhaarViewModel.Factory {
        return UdhaarViewModel.Factory(udhaarRepository, appContext)
    }

    fun provideLedgerViewModelFactory(): LedgerViewModel.Factory {
        return LedgerViewModel.Factory(ledgerRepository)
    }

    fun provideVerificationViewModelFactory(): VerificationViewModel.Factory {
        return VerificationViewModel.Factory(verificationRepository)
    }

    fun provideChatViewModelFactory(): ChatViewModel.Factory {
        return ChatViewModel.Factory(chatRepository)
    }

    fun provideBackupViewModelFactory(): BackupViewModel.Factory {
        return BackupViewModel.Factory(backupRepository)
    }

    fun provideSettingsViewModelFactory(): SettingsViewModel.Factory {
        return SettingsViewModel.Factory(settingsRepository)
    }
}
