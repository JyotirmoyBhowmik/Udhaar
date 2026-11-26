package com.jyotirmoyb.udhaar.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.room.RoomDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.jyotirmoyb.udhaar.util.SafeLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom

typealias DbEncryptionState = EncryptedStorageManager.DbEncryptionState

class SecureStorageException(message: String, cause: Throwable? = null) : SecurityException(message, cause)

/**
 * Provides hardware-backed Android Keystore (MasterKey) + EncryptedSharedPreferences
 * and SQLCipher (SupportOpenHelperFactory) 256-bit AES database encryption for Room,
 * delegating migration internals to [SqlCipherMigrationEngine] and key/file crypto
 * to [KeystoreCryptoAndFileHelper].
 */
object EncryptedStorageManager {

    enum class DbEncryptionState(val settingsLabel: String) {
        NONE("Failed (tap to retry)"),
        MIGRATING("Migrating"),
        ENCRYPTED("Yes"),
        FAILED("Failed (tap to retry)")
    }

    const val KEY_DB_ENCRYPTION_STATE = "db_encryption_state"
    const val KEY_DB_ENCRYPTION_LAST_ERROR = "db_encryption_last_error"
    const val KEY_HIDE_AMOUNTS = "hide_amounts_enabled"
    const val KEY_CLOUD_GATE_SKIPPED = "cloud_verification_gate_skipped"

    private const val ENCRYPTED_SETTINGS_FILE = "udhaar_encrypted_settings_prefs"
    private const val ENCRYPTED_SECURITY_FILE = "udhaar_encrypted_pin_security_prefs"
    private const val FALLBACK_WRAP_FILE = "udhaar_keystore_db_key_store"
    private const val KEY_WRAPPED_DB_PASSPHRASE = "wrapped_sqlcipher_passphrase_v1"

    private val SAFE_ASCII_ALPHABET =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_".toByteArray(Charsets.US_ASCII)

    @Volatile
    private var nativeSqlCipherLoaded = false

    private val _encryptionStateFlow = MutableStateFlow(DbEncryptionState.NONE)
    val encryptionStateFlow: StateFlow<DbEncryptionState> = _encryptionStateFlow.asStateFlow()

    private val _blockingStartupErrorFlow = MutableStateFlow<String?>(null)
    val blockingStartupErrorFlow: StateFlow<String?> = _blockingStartupErrorFlow.asStateFlow()

    internal object TestHooks {
        @Volatile
        var simulateExportFailure: Boolean = false

        @Volatile
        var simulateRowCountMismatch: Boolean = false

        @Volatile
        var simulateIntegrityCheckFailure: Boolean = false

        @Volatile
        var simulateFreshInstallFailure: Boolean = false

        fun reset() {
            simulateExportFailure = false
            simulateRowCountMismatch = false
            simulateIntegrityCheckFailure = false
            simulateFreshInstallFailure = false
        }
    }

    fun isRunningOnJvmRobolectric(): Boolean {
        val fingerprint = Build.FINGERPRINT ?: ""
        return fingerprint.equals("robolectric", ignoreCase = true) ||
            System.getProperty("java.vm.name")?.contains("Dalvik", ignoreCase = true) == false
    }

    @Synchronized
    private fun ensureSqlCipherNativeLoaded() {
        if (!nativeSqlCipherLoaded) {
            System.loadLibrary("sqlcipher")
            nativeSqlCipherLoaded = true
        }
    }

    fun getOrCreateMasterKey(context: Context): MasterKey {
        return MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    fun getEncryptedPreferences(
        context: Context,
        fileName: String = ENCRYPTED_SETTINGS_FILE
    ): SharedPreferences {
        val appContext = context.applicationContext
        if (isRunningOnJvmRobolectric()) {
            return appContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
        }
        return try {
            val masterKey = getOrCreateMasterKey(appContext)
            EncryptedSharedPreferences.create(
                appContext,
                fileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (t: Throwable) {
            SafeLogger.e("EncryptedStorageManager", "EncryptedSharedPreferences creation failed ($fileName)", t)
            val isKeyInvalidated = t is android.security.keystore.KeyPermanentlyInvalidatedException ||
                t.cause is android.security.keystore.KeyPermanentlyInvalidatedException ||
                t is android.security.keystore.UserNotAuthenticatedException ||
                t.cause is android.security.keystore.UserNotAuthenticatedException
            val errDetail = if (isKeyInvalidated) {
                SafeLogger.recordNonFatal("EncryptedStorageManager", "KEYSTORE_KEY_INVALIDATED", t)
                "Android Keystore key invalidated (device lock state changed). Local secure storage is locked."
            } else {
                SafeLogger.recordNonFatal("EncryptedStorageManager", "SECURE_STORAGE_UNAVAILABLE", t)
                "Secure storage unavailable: ${t.message ?: t.javaClass.simpleName}"
            }
            _blockingStartupErrorFlow.value = errDetail
            throw SecureStorageException(errDetail, t)
        }
    }

    fun getEncryptedSecurityPreferences(context: Context): SharedPreferences =
        getEncryptedPreferences(context, ENCRYPTED_SECURITY_FILE)

    fun getDbEncryptionState(context: Context): DbEncryptionState {
        val raw = runCatching {
            val prefs = getEncryptedPreferences(context)
            prefs.getString(KEY_DB_ENCRYPTION_STATE, DbEncryptionState.NONE.name)
                ?: DbEncryptionState.NONE.name
        }.getOrDefault(DbEncryptionState.NONE.name)
        val resolved = runCatching { DbEncryptionState.valueOf(raw) }.getOrDefault(DbEncryptionState.NONE)
        _encryptionStateFlow.value = resolved
        return resolved
    }

    fun getDatabaseEncryptionState(context: Context): DbEncryptionState = getDbEncryptionState(context)

    fun setDbEncryptionState(
        context: Context,
        state: DbEncryptionState,
        errorMessage: String? = null
    ) {
        runCatching {
            val prefs = getEncryptedPreferences(context)
            val editor = prefs.edit().putString(KEY_DB_ENCRYPTION_STATE, state.name)
            if (errorMessage != null) {
                editor.putString(KEY_DB_ENCRYPTION_LAST_ERROR, errorMessage)
            } else if (state == DbEncryptionState.ENCRYPTED) {
                editor.remove(KEY_DB_ENCRYPTION_LAST_ERROR)
            }
            editor.commit()
        }
        _encryptionStateFlow.value = state
    }

    fun setDatabaseEncryptionState(
        context: Context,
        state: DbEncryptionState,
        errorMessage: String? = null
    ) = setDbEncryptionState(context, state, errorMessage)

    fun getLastEncryptionError(context: Context): String? = runCatching {
        getEncryptedPreferences(context).getString(KEY_DB_ENCRYPTION_LAST_ERROR, null)
    }.getOrNull()

    fun recordStartupOpenFailure(context: Context, errorMessage: String) {
        setDbEncryptionState(context, DbEncryptionState.FAILED, errorMessage)
        _blockingStartupErrorFlow.value = errorMessage
    }

    fun clearBlockingStartupError() {
        _blockingStartupErrorFlow.value = null
    }

    fun isAmountsHidden(context: Context): Boolean = runCatching {
        getEncryptedPreferences(context).getBoolean(KEY_HIDE_AMOUNTS, false)
    }.getOrDefault(false)

    fun setAmountsHidden(context: Context, hidden: Boolean) {
        runCatching {
            getEncryptedPreferences(context).edit().putBoolean(KEY_HIDE_AMOUNTS, hidden).commit()
        }
    }

    fun isCloudGateSkipped(context: Context): Boolean = runCatching {
        getEncryptedPreferences(context).getBoolean(KEY_CLOUD_GATE_SKIPPED, false)
    }.getOrDefault(false)

    fun setCloudGateSkipped(context: Context, skipped: Boolean) {
        runCatching {
            getEncryptedPreferences(context).edit().putBoolean(KEY_CLOUD_GATE_SKIPPED, skipped).commit()
        }
    }

    fun getOrCreateDatabasePassphrase(
        context: Context,
        requireSqlAttachCompatibleAscii: Boolean = false
    ): ByteArray {
        val prefs = getEncryptedPreferences(context, FALLBACK_WRAP_FILE)
        val existingBlob = runCatching { prefs.getString(KEY_WRAPPED_DB_PASSPHRASE, null) }.getOrNull()
        if (!existingBlob.isNullOrBlank()) {
            val unwrapped = unwrapWithKeystoreOrDecode(existingBlob)
            if (unwrapped != null && unwrapped.size == 32) {
                if (!requireSqlAttachCompatibleAscii || isSafeSqlAsciiKey(unwrapped)) {
                    return unwrapped
                }
            }
        }

        val random = SecureRandom()
        val randomKey = ByteArray(32) {
            SAFE_ASCII_ALPHABET[random.nextInt(SAFE_ASCII_ALPHABET.size)]
        }
        val wrapped = wrapWithKeystoreOrEncode(randomKey)
        runCatching {
            prefs.edit().putString(KEY_WRAPPED_DB_PASSPHRASE, wrapped).commit()
        }
        return randomKey
    }

    private fun wrapWithKeystoreOrEncode(plainBytes: ByteArray): String =
        KeystoreCryptoAndFileHelper.wrapWithKeystoreOrEncode(
            plainBytes = plainBytes,
            isJvmRobolectric = isRunningOnJvmRobolectric(),
            onFailure = { err -> _blockingStartupErrorFlow.value = err }
        )

    private fun unwrapWithKeystoreOrDecode(encoded: String): ByteArray? =
        KeystoreCryptoAndFileHelper.unwrapWithKeystoreOrDecode(
            serialized = encoded,
            isJvmRobolectric = isRunningOnJvmRobolectric()
        )

    private fun isSafeSqlAsciiKey(bytes: ByteArray): Boolean =
        bytes.isNotEmpty() && bytes.all { b ->
            val code = b.toInt() and 0xFF
            code in 0x30..0x39 || code in 0x41..0x5A || code in 0x61..0x7A || code == 0x2D || code == 0x5F
        }

    fun createSqlCipherFactory(context: Context): SupportOpenHelperFactory {
        ensureSqlCipherNativeLoaded()
        val passphrase = getOrCreateDatabasePassphrase(context)
        return SupportOpenHelperFactory(passphrase.copyOf(), null, true)
    }

    @Synchronized
    fun <T : RoomDatabase> configureSqlCipherForRoom(
        context: Context,
        builder: RoomDatabase.Builder<T>,
        dbName: String
    ): RoomDatabase.Builder<T> {
        val appContext = context.applicationContext
        val dbFile = appContext.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        if (dbFile.exists() && dbFile.length() == 0L) {
            KeystoreCryptoAndFileHelper.deleteIfExists(dbFile)
        }

        val currentState = getDbEncryptionState(appContext)
        val stagingDir = File(appContext.filesDir, SqlCipherMigrationEngine.MIGRATION_STAGING_DIR).apply { mkdirs() }
        val stagedPlainCopy = File(stagingDir, "${dbFile.name}.plain_copy")

        if (!dbFile.exists() && stagedPlainCopy.exists() && stagedPlainCopy.length() > 0L) {
            runCatching { KeystoreCryptoAndFileHelper.copyFileSafely(stagedPlainCopy, dbFile) }
        }

        val hasInterruptedMigration =
            currentState == DbEncryptionState.MIGRATING && stagedPlainCopy.exists() && stagedPlainCopy.length() > 0L
        val hasPlainDatabase =
            dbFile.exists() && dbFile.length() > 0L && isUnencryptedDatabase(appContext, dbFile)

        if (hasInterruptedMigration || hasPlainDatabase) {
            val migrated = migratePlainDatabaseToEncrypted(appContext, dbFile)
            if (!migrated) {
                val errMsg = getLastEncryptionError(appContext)
                    ?: "Database encryption migration failed. Plain database cannot be opened."
                _blockingStartupErrorFlow.value = errMsg
                SafeLogger.recordNonFatal("EncryptedStorageManager", "SQLCIPHER_MIGRATION_FAILED")
                throw SecureStorageException(errMsg)
            }
        } else {
            val freshReady = SqlCipherMigrationEngine.initializeOrVerifyEncryptedDatabase(
                context = appContext,
                dbFile = dbFile,
                ensureNativeLoaded = ::ensureSqlCipherNativeLoaded
            )
            if (!freshReady) {
                val errMsg = getLastEncryptionError(appContext)
                    ?: "SQLCipher encrypted storage could not be initialized on this device."
                _blockingStartupErrorFlow.value = errMsg
                SafeLogger.recordNonFatal("EncryptedStorageManager", "SQLCIPHER_INIT_FAILED")
                throw SecureStorageException(errMsg)
            }
        }

        _blockingStartupErrorFlow.value = null
        if (isRunningOnJvmRobolectric()) {
            return builder
        }
        val passphrase = getOrCreateDatabasePassphrase(appContext)
        val factory = SupportOpenHelperFactory(passphrase.copyOf(), null, true)
        return builder.openHelperFactory(factory)
    }

    @Synchronized
    fun retryDatabaseEncryption(context: Context, dbName: String = "udhaar_ledger.db"): Boolean {
        val appContext = context.applicationContext
        val dbFile = appContext.getDatabasePath(dbName)
        if (dbFile.exists() && dbFile.length() == 0L) {
            KeystoreCryptoAndFileHelper.deleteIfExists(dbFile)
        }
        val currentState = getDbEncryptionState(appContext)
        val stagingDir = File(appContext.filesDir, SqlCipherMigrationEngine.MIGRATION_STAGING_DIR).apply { mkdirs() }
        val stagedPlainCopy = File(stagingDir, "${dbFile.name}.plain_copy")

        val hasInterruptedMigration =
            currentState == DbEncryptionState.MIGRATING && stagedPlainCopy.exists() && stagedPlainCopy.length() > 0L
        val hasPlainDatabase =
            dbFile.exists() && dbFile.length() > 0L && isUnencryptedDatabase(appContext, dbFile)

        val success = if (hasInterruptedMigration || hasPlainDatabase) {
            migratePlainDatabaseToEncrypted(appContext, dbFile)
        } else {
            SqlCipherMigrationEngine.initializeOrVerifyEncryptedDatabase(
                context = appContext,
                dbFile = dbFile,
                ensureNativeLoaded = ::ensureSqlCipherNativeLoaded
            )
        }

        if (success) {
            _blockingStartupErrorFlow.value = null
        }
        return success
    }

    @Synchronized
    fun migratePlainDatabaseToEncrypted(context: Context, dbFile: File): Boolean =
        SqlCipherMigrationEngine.migratePlainDatabaseToEncrypted(
            context = context,
            dbFile = dbFile,
            ensureNativeLoaded = ::ensureSqlCipherNativeLoaded
        )

    internal fun isUnencryptedDatabase(context: Context, dbFile: File): Boolean =
        SqlCipherMigrationEngine.isUnencryptedDatabase(context, dbFile)

    internal fun unmarkJvmFileEncrypted(context: Context, dbFile: File) =
        SqlCipherMigrationEngine.unmarkJvmFileEncrypted(context, dbFile)

    fun secureDeleteFile(file: File) = KeystoreCryptoAndFileHelper.secureDeleteFile(file)

    internal fun quarantineDatabaseFiles(dbFile: File) =
        KeystoreCryptoAndFileHelper.quarantineDatabaseFiles(dbFile)

    internal fun isPlaintextSqliteHeader(file: File): Boolean =
        KeystoreCryptoAndFileHelper.isPlaintextSqliteHeader(file)

    @Synchronized
    fun resetAppDataSecurely(context: Context) {
        val appContext = context.applicationContext
        val dbFile = appContext.getDatabasePath("udhaar_ledger.db")
        listOf(
            dbFile,
            File(dbFile.parentFile, "${dbFile.name}-wal"),
            File(dbFile.parentFile, "${dbFile.name}-shm"),
            File(dbFile.parentFile, "${dbFile.name}.pre_restore_safety")
        ).forEach { f ->
            if (f.exists()) secureDeleteFile(f)
        }

        val attachDir = File(appContext.filesDir, "attachments")
        if (attachDir.exists()) {
            attachDir.listFiles()?.forEach { secureDeleteFile(it) }
        }

        runCatching {
            val keyStore = KeyStore.getInstance(KeystoreCryptoAndFileHelper.KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(KeystoreCryptoAndFileHelper.DB_KEY_ALIAS)) {
                keyStore.deleteEntry(KeystoreCryptoAndFileHelper.DB_KEY_ALIAS)
            }
            if (keyStore.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
        }

        listOf(
            FALLBACK_WRAP_FILE,
            ENCRYPTED_SETTINGS_FILE,
            ENCRYPTED_SECURITY_FILE,
            "udhaar_token_store_prefs"
        ).forEach { prefName ->
            runCatching {
                val prefFile = File(appContext.filesDir.parentFile, "shared_prefs/$prefName.xml")
                if (prefFile.exists()) secureDeleteFile(prefFile)
            }
        }

        _encryptionStateFlow.value = DbEncryptionState.NONE
        _blockingStartupErrorFlow.value = null
    }
}
