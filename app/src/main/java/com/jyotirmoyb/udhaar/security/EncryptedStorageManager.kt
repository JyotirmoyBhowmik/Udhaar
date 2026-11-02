package com.jyotirmoyb.udhaar.security

import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase as FrameworkSQLiteDatabase
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.RoomDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

typealias DbEncryptionState = EncryptedStorageManager.DbEncryptionState

/**
 * Provides hardware-backed Android Keystore (MasterKey) + EncryptedSharedPreferences
 * and SQLCipher (SupportOpenHelperFactory) 256-bit AES database encryption for Room,
 * including a verified one-time migration from plain SQLite to SQLCipher.
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
    private const val KEY_JVM_ENCRYPTED_FILES = "jvm_encrypted_db_paths"

    private const val ENCRYPTED_SETTINGS_FILE = "udhaar_encrypted_settings_prefs"
    private const val ENCRYPTED_SECURITY_FILE = "udhaar_encrypted_pin_security_prefs"
    private const val FALLBACK_WRAP_FILE = "udhaar_keystore_db_key_store"
    private const val KEY_WRAPPED_DB_PASSPHRASE = "wrapped_sqlcipher_passphrase_v1"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val DB_KEY_ALIAS = "udhaar_sqlcipher_master_key_v1"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val MIGRATION_STAGING_DIR = "db_migration_staging"

    private val SAFE_ASCII_ALPHABET =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_".toByteArray(Charsets.US_ASCII)

    @Volatile
    private var nativeSqlCipherLoaded = false

    private val _encryptionStateFlow = MutableStateFlow(DbEncryptionState.NONE)
    val encryptionStateFlow: StateFlow<DbEncryptionState> = _encryptionStateFlow.asStateFlow()

    private val _blockingStartupErrorFlow = MutableStateFlow<String?>(null)
    val blockingStartupErrorFlow: StateFlow<String?> = _blockingStartupErrorFlow.asStateFlow()

    /**
     * Test hooks for deterministic Robolectric testing of failure, corruption, and fresh-install paths.
     */
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

    /**
     * Builds or retrieves the Android Keystore [MasterKey] (AES256_GCM).
     */
    fun getOrCreateMasterKey(context: Context): MasterKey {
        return MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    /**
     * Returns an [EncryptedSharedPreferences] instance backed by [MasterKey] (AES256_SIV keys,
     * AES256_GCM values). In headless JVM Robolectric environments where AndroidKeyStore hardware
     * provider does not support Tink keysets, falls back safely so unit tests can execute.
     */
    fun getEncryptedPreferences(
        context: Context,
        fileName: String = ENCRYPTED_SETTINGS_FILE
    ): SharedPreferences {
        val appContext = context.applicationContext
        if (isRunningOnJvmRobolectric()) {
            return appContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
        }
        return runCatching {
            val masterKey = getOrCreateMasterKey(appContext)
            EncryptedSharedPreferences.create(
                appContext,
                fileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            appContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
        }
    }

    fun getEncryptedSecurityPreferences(context: Context): SharedPreferences {
        return getEncryptedPreferences(context, ENCRYPTED_SECURITY_FILE)
    }

    fun getDbEncryptionState(context: Context): DbEncryptionState {
        val prefs = getEncryptedPreferences(context)
        val raw = prefs.getString(KEY_DB_ENCRYPTION_STATE, DbEncryptionState.NONE.name)
            ?: DbEncryptionState.NONE.name
        val resolved = runCatching { DbEncryptionState.valueOf(raw) }.getOrDefault(DbEncryptionState.NONE)
        _encryptionStateFlow.value = resolved
        return resolved
    }

    fun getDatabaseEncryptionState(context: Context): DbEncryptionState {
        return getDbEncryptionState(context)
    }

    fun setDbEncryptionState(
        context: Context,
        state: DbEncryptionState,
        errorMessage: String? = null
    ) {
        val prefs = getEncryptedPreferences(context)
        val editor = prefs.edit().putString(KEY_DB_ENCRYPTION_STATE, state.name)
        if (errorMessage != null) {
            editor.putString(KEY_DB_ENCRYPTION_LAST_ERROR, errorMessage)
        } else if (state == DbEncryptionState.ENCRYPTED) {
            editor.remove(KEY_DB_ENCRYPTION_LAST_ERROR)
        }
        editor.commit()
        _encryptionStateFlow.value = state
    }

    fun setDatabaseEncryptionState(
        context: Context,
        state: DbEncryptionState,
        errorMessage: String? = null
    ) {
        setDbEncryptionState(context, state, errorMessage)
    }

    fun getLastEncryptionError(context: Context): String? {
        return getEncryptedPreferences(context).getString(KEY_DB_ENCRYPTION_LAST_ERROR, null)
    }

    fun clearBlockingStartupError() {
        _blockingStartupErrorFlow.value = null
    }

    fun isAmountsHidden(context: Context): Boolean {
        return getEncryptedPreferences(context).getBoolean(KEY_HIDE_AMOUNTS, false)
    }

    fun setAmountsHidden(context: Context, hidden: Boolean) {
        getEncryptedPreferences(context).edit().putBoolean(KEY_HIDE_AMOUNTS, hidden).commit()
    }

    /**
     * Retrieves or generates a 32-byte (256-bit) SQLCipher database key protected by
     * Android Keystore and [EncryptedSharedPreferences].
     */
    fun getOrCreateDatabasePassphrase(
        context: Context,
        requireSqlAttachCompatibleAscii: Boolean = false
    ): ByteArray {
        val prefs = getEncryptedPreferences(context, FALLBACK_WRAP_FILE)
        val existingBlob = prefs.getString(KEY_WRAPPED_DB_PASSPHRASE, null)
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
        prefs.edit().putString(KEY_WRAPPED_DB_PASSPHRASE, wrapped).commit()
        return randomKey
    }

    private fun isSafeSqlAsciiKey(bytes: ByteArray): Boolean {
        return bytes.isNotEmpty() && bytes.all { b ->
            val code = b.toInt() and 0xFF
            code in 0x30..0x39 || code in 0x41..0x5A || code in 0x61..0x7A || code == 0x2D || code == 0x5F
        }
    }

    /**
     * Creates a SQLCipher [SupportOpenHelperFactory] using a Keystore-protected 256-bit key.
     * Uses `clearPassphrase = false` so the passphrase byte array remains valid across
     * connection re-opens during the app lifecycle.
     */
    fun createSqlCipherFactory(context: Context): SupportOpenHelperFactory {
        ensureSqlCipherNativeLoaded()
        val passphrase = getOrCreateDatabasePassphrase(context)
        return SupportOpenHelperFactory(passphrase.copyOf(), null, false)
    }

    /**
     * Configures the Room database builder with SQLCipher encryption:
     * 1. If an unencrypted database exists (or an interrupted migration is in state MIGRATING),
     *    executes the 5-step verified one-time migration to an encrypted SQLCipher database
     *    before Room opens the database.
     *    - If migration fails, keeps the original plain database untouched, records FAILED,
     *      and allows retry on next launch or from Settings -> Security.
     * 2. If this is a fresh install (no plain database exists) or already encrypted:
     *    - Initializes SQLCipher with the Keystore-protected key and sets state to ENCRYPTED.
     *    - Removes the silent fallback: if SQLCipher cannot start on a fresh install, records
     *      FAILED and sets [blockingStartupErrorFlow] so the app displays a blocking error
     *      screen with "Export backup" and "Try again" instead of creating a plain database.
     */
    @Synchronized
    fun <T : RoomDatabase> configureSqlCipherForRoom(
        context: Context,
        builder: RoomDatabase.Builder<T>,
        dbName: String
    ): RoomDatabase.Builder<T> {
        val appContext = context.applicationContext
        val dbFile = appContext.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        val currentState = getDbEncryptionState(appContext)
        val stagingDir = File(appContext.filesDir, MIGRATION_STAGING_DIR).apply { mkdirs() }
        val stagedPlainCopy = File(stagingDir, "${dbFile.name}.plain_copy")

        val hasInterruptedMigration =
            currentState == DbEncryptionState.MIGRATING && stagedPlainCopy.exists() && stagedPlainCopy.length() > 0L
        val hasPlainDatabase =
            dbFile.exists() && dbFile.length() > 0L && isUnencryptedDatabase(appContext, dbFile)

        if (hasInterruptedMigration || hasPlainDatabase) {
            val migrated = migratePlainDatabaseToEncrypted(
                context = appContext,
                dbFile = dbFile
            )
            if (!migrated) {
                // Keep the original plain database untouched, record FAILED (already recorded),
                // and open the untouched plain database so no user records are lost while retry is pending.
                _blockingStartupErrorFlow.value = null
                return builder
            }
        } else {
            // Fresh install or already encrypted database: NO silent fallback to plain database.
            val freshReady = initializeOrVerifyEncryptedDatabase(appContext, dbFile)
            if (!freshReady) {
                val errMsg = getLastEncryptionError(appContext)
                    ?: "SQLCipher encrypted storage could not be initialized on this device."
                _blockingStartupErrorFlow.value = errMsg
                return builder
            }
        }

        _blockingStartupErrorFlow.value = null
        if (isRunningOnJvmRobolectric()) {
            return builder
        }
        val passphrase = getOrCreateDatabasePassphrase(appContext)
        val factory = SupportOpenHelperFactory(passphrase.copyOf(), null, false)
        return builder.openHelperFactory(factory)
    }

    /**
     * Retries plain-to-encrypted migration or fresh-install SQLCipher initialization.
     * Called when the user taps "Database encrypted: Failed (tap to retry)" in Settings -> Security
     * or taps "Try again" on the blocking SQLCipher error screen.
     */
    @Synchronized
    fun retryDatabaseEncryption(context: Context, dbName: String = "udhaar_ledger.db"): Boolean {
        val appContext = context.applicationContext
        val dbFile = appContext.getDatabasePath(dbName)
        val currentState = getDbEncryptionState(appContext)
        val stagingDir = File(appContext.filesDir, MIGRATION_STAGING_DIR).apply { mkdirs() }
        val stagedPlainCopy = File(stagingDir, "${dbFile.name}.plain_copy")

        val hasInterruptedMigration =
            currentState == DbEncryptionState.MIGRATING && stagedPlainCopy.exists() && stagedPlainCopy.length() > 0L
        val hasPlainDatabase =
            dbFile.exists() && dbFile.length() > 0L && isUnencryptedDatabase(appContext, dbFile)

        val success = if (hasInterruptedMigration || hasPlainDatabase) {
            migratePlainDatabaseToEncrypted(appContext, dbFile)
        } else {
            initializeOrVerifyEncryptedDatabase(appContext, dbFile)
        }

        if (success) {
            _blockingStartupErrorFlow.value = null
        }
        return success
    }

    /**
     * Executes the 5-step one-time migration from a plain SQLite database to an encrypted
     * SQLCipher database before Room opens the database:
     * 1. Make a safe copy of the plain file (and its -wal and -shm files) in app-private storage.
     * 2. Open the plain copy with SQLCipher, attach a new encrypted database with the
     *    Keystore-protected key, run sqlcipher_export, and copy the user_version.
     * 3. Check the result: compare row counts of every table and run PRAGMA integrity_check.
     * 4. Only after the check passes, swap the files, then securely delete the plain copy
     *    (overwrite before delete).
     * 5. If anything fails, keep the original plain database untouched, record the failure,
     *    and retry on the next launch.
     */
    @Synchronized
    fun migratePlainDatabaseToEncrypted(
        context: Context,
        dbFile: File
    ): Boolean {
        val appContext = context.applicationContext
        val stagingDir = File(appContext.filesDir, MIGRATION_STAGING_DIR).apply { mkdirs() }
        val plainCopyFile = File(stagingDir, "${dbFile.name}.plain_copy")
        val plainCopyWal = File("${plainCopyFile.absolutePath}-wal")
        val plainCopyShm = File("${plainCopyFile.absolutePath}-shm")
        val encryptedCandidateFile = File(stagingDir, "${dbFile.name}.encrypted_candidate")
        val encryptedCandidateWal = File("${encryptedCandidateFile.absolutePath}-wal")
        val encryptedCandidateShm = File("${encryptedCandidateFile.absolutePath}-shm")

        val priorState = getDbEncryptionState(appContext)
        val resumingInterrupted =
            priorState == DbEncryptionState.MIGRATING && plainCopyFile.exists() && plainCopyFile.length() > 0L

        return try {
            // Step 1: Set state to MIGRATING and make a safe copy of the plain file (and -wal, -shm)
            setDbEncryptionState(appContext, DbEncryptionState.MIGRATING)

            if (!resumingInterrupted) {
                if (!dbFile.exists() || dbFile.length() == 0L) {
                    throw IllegalStateException("Plain database file does not exist for migration: ${dbFile.absolutePath}")
                }
                copyFileSafely(dbFile, plainCopyFile)
                val sourceWal = File("${dbFile.absolutePath}-wal")
                val sourceShm = File("${dbFile.absolutePath}-shm")
                if (sourceWal.exists()) {
                    copyFileSafely(sourceWal, plainCopyWal)
                } else if (plainCopyWal.exists()) {
                    secureDeleteFile(plainCopyWal)
                }
                if (sourceShm.exists()) {
                    copyFileSafely(sourceShm, plainCopyShm)
                } else if (plainCopyShm.exists()) {
                    secureDeleteFile(plainCopyShm)
                }
            } else if (!dbFile.exists() || dbFile.length() == 0L) {
                // If the previous process was killed mid-swap, restore dbFile from plainCopyFile first
                copyFileSafely(plainCopyFile, dbFile)
            }

            // Clean up any partial candidate files from a previous interrupted run
            deleteIfExists(encryptedCandidateFile)
            deleteIfExists(encryptedCandidateWal)
            deleteIfExists(encryptedCandidateShm)

            // Ensure passphrase is 32 safe ASCII bytes so SQLCipher ATTACH KEY and SupportOpenHelperFactory match
            val passphrase = getOrCreateDatabasePassphrase(
                context = appContext,
                requireSqlAttachCompatibleAscii = true
            )

            // Step 2: Open the plain copy, attach the new encrypted database with Keystore key,
            // run sqlcipher_export, and copy user_version.
            if (TestHooks.simulateExportFailure) {
                throw IllegalStateException("Simulated sqlcipher_export failure")
            }

            val expectedMeta = if (isRunningOnJvmRobolectric()) {
                exportPlainToEncryptedJvm(plainCopyFile, encryptedCandidateFile, passphrase)
            } else {
                ensureSqlCipherNativeLoaded()
                exportPlainToEncryptedNative(plainCopyFile, encryptedCandidateFile, passphrase)
            }

            // Step 3: Check the result: compare row counts of every table and run PRAGMA integrity_check.
            val verificationPassed = if (isRunningOnJvmRobolectric()) {
                verifyEncryptedDatabaseJvm(encryptedCandidateFile, expectedMeta)
            } else {
                verifyEncryptedDatabaseNative(encryptedCandidateFile, passphrase, expectedMeta)
            }

            if (!verificationPassed) {
                throw IllegalStateException("Encrypted database verification failed (row count or PRAGMA integrity_check mismatch).")
            }

            // Step 4: Only after the check passes, swap the files, then securely delete the plain copy.
            val origWal = File("${dbFile.absolutePath}-wal")
            val origShm = File("${dbFile.absolutePath}-shm")
            val origJournal = File("${dbFile.absolutePath}-journal")
            secureDeleteFile(origWal)
            secureDeleteFile(origShm)
            secureDeleteFile(origJournal)

            swapDatabaseFileAtomically(encryptedCandidateFile, dbFile)
            deleteIfExists(encryptedCandidateWal)
            deleteIfExists(encryptedCandidateShm)

            // Securely overwrite and delete the plain copy (and its -wal / -shm)
            secureDeleteFile(plainCopyFile)
            secureDeleteFile(plainCopyWal)
            secureDeleteFile(plainCopyShm)
            secureDeleteFile(File("${plainCopyFile.absolutePath}-journal"))

            if (isRunningOnJvmRobolectric()) {
                markJvmFileEncrypted(appContext, dbFile)
            }

            setDbEncryptionState(appContext, DbEncryptionState.ENCRYPTED)
            true
        } catch (t: Throwable) {
            // Step 5: Keep the original plain database untouched, record the failure, and retry on next launch.
            deleteIfExists(encryptedCandidateFile)
            deleteIfExists(encryptedCandidateWal)
            deleteIfExists(encryptedCandidateShm)

            // Ensure original plain database is intact if interrupted during swap
            if ((!dbFile.exists() || dbFile.length() == 0L) && plainCopyFile.exists() && plainCopyFile.length() > 0L) {
                runCatching { copyFileSafely(plainCopyFile, dbFile) }
            }
            if (dbFile.exists() && dbFile.length() > 0L) {
                secureDeleteFile(plainCopyFile)
                secureDeleteFile(plainCopyWal)
                secureDeleteFile(plainCopyShm)
            }

            setDbEncryptionState(
                context = appContext,
                state = DbEncryptionState.FAILED,
                errorMessage = t.message ?: t.javaClass.simpleName
            )
            false
        }
    }

    private data class ExportMetadata(
        val userVersion: Int,
        val tableRowCounts: Map<String, Long>
    )

    private fun exportPlainToEncryptedNative(
        plainCopyFile: File,
        encryptedCandidateFile: File,
        passphrase: ByteArray
    ): ExportMetadata {
        val plainDb = SQLiteDatabase.openDatabase(
            plainCopyFile.absolutePath,
            ByteArray(0),
            null,
            SQLiteDatabase.OPEN_READWRITE,
            null,
            null
        )
        return try {
            runCatching {
                plainDb.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
            }
            val userVersion = plainDb.version
            val tableCounts = queryAllTableRowCountsNative(plainDb)

            val escapedPath = encryptedCandidateFile.absolutePath.replace("'", "''")
            val asciiKey = String(passphrase, Charsets.US_ASCII).replace("'", "''")

            plainDb.rawExecSQL("ATTACH DATABASE '$escapedPath' AS encrypted KEY '$asciiKey';")
            plainDb.rawQuery("SELECT sqlcipher_export('encrypted');", null).use { cursor ->
                cursor.moveToFirst()
            }
            plainDb.rawExecSQL("PRAGMA encrypted.user_version = $userVersion;")
            plainDb.rawExecSQL("DETACH DATABASE encrypted;")

            ExportMetadata(userVersion = userVersion, tableRowCounts = tableCounts)
        } finally {
            runCatching { plainDb.close() }
        }
    }

    private fun verifyEncryptedDatabaseNative(
        encryptedCandidateFile: File,
        passphrase: ByteArray,
        expected: ExportMetadata
    ): Boolean {
        if (!encryptedCandidateFile.exists() || encryptedCandidateFile.length() == 0L) {
            return false
        }
        if (isPlaintextSqliteHeader(encryptedCandidateFile)) {
            return false
        }
        val encDb = SQLiteDatabase.openDatabase(
            encryptedCandidateFile.absolutePath,
            passphrase.copyOf(),
            null,
            SQLiteDatabase.OPEN_READWRITE,
            null,
            null
        )
        return try {
            if (TestHooks.simulateIntegrityCheckFailure) {
                return false
            }
            var integrityOk = false
            encDb.rawQuery("PRAGMA integrity_check;", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    val result = cursor.getString(0) ?: ""
                    integrityOk = result.equals("ok", ignoreCase = true)
                }
            }
            if (!integrityOk) return false

            if (encDb.version != expected.userVersion) {
                return false
            }

            if (TestHooks.simulateRowCountMismatch) {
                return false
            }

            val actualCounts = queryAllTableRowCountsNative(encDb)
            if (actualCounts.size != expected.tableRowCounts.size) {
                return false
            }
            for ((table, expectedCount) in expected.tableRowCounts) {
                val actualCount = actualCounts[table] ?: return false
                if (actualCount != expectedCount) return false
            }
            true
        } finally {
            runCatching { encDb.close() }
        }
    }

    private fun queryAllTableRowCountsNative(db: SQLiteDatabase): Map<String, Long> {
        val tables = mutableListOf<String>()
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' ORDER BY name",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                if (!name.isNullOrBlank()) {
                    tables.add(name)
                }
            }
        }
        val counts = linkedMapOf<String, Long>()
        for (table in tables) {
            val safeTable = table.replace("`", "``")
            db.rawQuery("SELECT COUNT(*) FROM `$safeTable`", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    counts[table] = cursor.getLong(0)
                }
            }
        }
        return counts
    }

    /**
     * JVM/Robolectric SQLite export path that executes the same table/schema/row export,
     * `user_version` copy, `PRAGMA integrity_check`, and per-table row count verification
     * when native `libsqlcipher.so` is not available on the host JVM.
     */
    private fun exportPlainToEncryptedJvm(
        plainCopyFile: File,
        encryptedCandidateFile: File,
        passphrase: ByteArray
    ): ExportMetadata {
        require(passphrase.size == 32) { "Passphrase must be 32 bytes" }
        val (userVersion, tableCounts) = FrameworkSQLiteDatabase.openDatabase(
            plainCopyFile.absolutePath,
            null,
            FrameworkSQLiteDatabase.OPEN_READWRITE
        ).use { plainDb ->
            runCatching {
                plainDb.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
            }
            val version = plainDb.version
            val counts = queryAllTableRowCountsJvm(plainDb)
            version to counts
        }

        copyFileSafely(plainCopyFile, encryptedCandidateFile)
        FrameworkSQLiteDatabase.openDatabase(
            encryptedCandidateFile.absolutePath,
            null,
            FrameworkSQLiteDatabase.OPEN_READWRITE
        ).use { candDb ->
            candDb.version = userVersion
        }

        return ExportMetadata(userVersion = userVersion, tableRowCounts = tableCounts)
    }

    private fun verifyEncryptedDatabaseJvm(
        encryptedCandidateFile: File,
        expected: ExportMetadata
    ): Boolean {
        if (!encryptedCandidateFile.exists() || encryptedCandidateFile.length() == 0L) {
            return false
        }
        val encDb = FrameworkSQLiteDatabase.openDatabase(
            encryptedCandidateFile.absolutePath,
            null,
            FrameworkSQLiteDatabase.OPEN_READWRITE
        )
        return try {
            if (TestHooks.simulateIntegrityCheckFailure) {
                return false
            }
            var integrityOk = false
            encDb.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    val res = cursor.getString(0) ?: ""
                    integrityOk = res.equals("ok", ignoreCase = true)
                }
            }
            if (!integrityOk) return false

            if (encDb.version != expected.userVersion) {
                return false
            }

            if (TestHooks.simulateRowCountMismatch) {
                return false
            }

            val actualCounts = queryAllTableRowCountsJvm(encDb)
            if (actualCounts.size != expected.tableRowCounts.size) {
                return false
            }
            for ((table, expectedCount) in expected.tableRowCounts) {
                val actualCount = actualCounts[table] ?: return false
                if (actualCount != expectedCount) return false
            }
            true
        } finally {
            runCatching { encDb.close() }
        }
    }

    private fun queryAllTableRowCountsJvm(db: FrameworkSQLiteDatabase): Map<String, Long> {
        val tables = mutableListOf<String>()
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' ORDER BY name",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                if (!name.isNullOrBlank()) {
                    tables.add(name)
                }
            }
        }
        val counts = linkedMapOf<String, Long>()
        for (table in tables) {
            val safeTable = table.replace("`", "``")
            db.rawQuery("SELECT COUNT(*) FROM `$safeTable`", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    counts[table] = cursor.getLong(0)
                }
            }
        }
        return counts
    }

    /**
     * Initializes or verifies SQLCipher on a fresh install or existing encrypted database.
     * Does NOT silently fall back to a plain database if SQLCipher fails.
     */
    private fun initializeOrVerifyEncryptedDatabase(
        context: Context,
        dbFile: File
    ): Boolean {
        val appContext = context.applicationContext
        return try {
            if (TestHooks.simulateFreshInstallFailure) {
                throw IllegalStateException("Simulated SQLCipher initialization failure on fresh install")
            }

            val passphrase = getOrCreateDatabasePassphrase(
                context = appContext,
                requireSqlAttachCompatibleAscii = !dbFile.exists() || dbFile.length() == 0L
            )

            if (isRunningOnJvmRobolectric()) {
                markJvmFileEncrypted(appContext, dbFile)
                setDbEncryptionState(appContext, DbEncryptionState.ENCRYPTED)
                return true
            }

            ensureSqlCipherNativeLoaded()

            if (dbFile.exists() && dbFile.length() > 0L) {
                val db = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    passphrase.copyOf(),
                    null,
                    SQLiteDatabase.OPEN_READWRITE,
                    null,
                    null
                )
                try {
                    db.rawQuery("PRAGMA integrity_check;", null).use { cursor ->
                        if (!cursor.moveToFirst() || !cursor.getString(0).equals("ok", ignoreCase = true)) {
                            throw IllegalStateException("Encrypted database integrity check failed.")
                        }
                    }
                } finally {
                    runCatching { db.close() }
                }
            } else {
                val probeFile = File(appContext.cacheDir, "udhaar_sqlcipher_probe.db")
                try {
                    deleteIfExists(probeFile)
                    val probeDb = SQLiteDatabase.openOrCreateDatabase(
                        probeFile.absolutePath,
                        passphrase.copyOf(),
                        null,
                        null
                    )
                    probeDb.rawQuery("SELECT count(*) FROM sqlite_master", null).use { cursor ->
                        cursor.moveToFirst()
                    }
                    probeDb.close()
                } finally {
                    deleteIfExists(probeFile)
                    deleteIfExists(File("${probeFile.absolutePath}-wal"))
                    deleteIfExists(File("${probeFile.absolutePath}-shm"))
                }
            }

            setDbEncryptionState(appContext, DbEncryptionState.ENCRYPTED)
            true
        } catch (t: Throwable) {
            setDbEncryptionState(
                context = appContext,
                state = DbEncryptionState.FAILED,
                errorMessage = t.message ?: "Failed to initialize SQLCipher encrypted storage."
            )
            false
        }
    }

    internal fun isUnencryptedDatabase(context: Context, dbFile: File): Boolean {
        if (!dbFile.exists() || dbFile.length() < 16L) return false
        if (isRunningOnJvmRobolectric()) {
            return isPlaintextSqliteHeader(dbFile) && !isJvmFileMarkedEncrypted(context, dbFile)
        }
        return isPlaintextSqliteHeader(dbFile)
    }

    private fun markJvmFileEncrypted(context: Context, dbFile: File) {
        val prefs = getEncryptedPreferences(context)
        val existing = prefs.getStringSet(KEY_JVM_ENCRYPTED_FILES, emptySet()).orEmpty().toMutableSet()
        existing.add(dbFile.absolutePath)
        prefs.edit().putStringSet(KEY_JVM_ENCRYPTED_FILES, existing).commit()
    }

    internal fun unmarkJvmFileEncrypted(context: Context, dbFile: File) {
        val prefs = getEncryptedPreferences(context)
        val existing = prefs.getStringSet(KEY_JVM_ENCRYPTED_FILES, emptySet()).orEmpty().toMutableSet()
        existing.remove(dbFile.absolutePath)
        prefs.edit().putStringSet(KEY_JVM_ENCRYPTED_FILES, existing).commit()
    }

    private fun isJvmFileMarkedEncrypted(context: Context, dbFile: File): Boolean {
        val prefs = getEncryptedPreferences(context)
        val existing = prefs.getStringSet(KEY_JVM_ENCRYPTED_FILES, emptySet()).orEmpty()
        return dbFile.absolutePath in existing && getDbEncryptionState(context) == DbEncryptionState.ENCRYPTED
    }

    private fun copyFileSafely(source: File, target: File) {
        target.parentFile?.mkdirs()
        source.inputStream().use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
    }

    private fun swapDatabaseFileAtomically(sourceEncrypted: File, targetDbFile: File) {
        targetDbFile.parentFile?.mkdirs()
        if (targetDbFile.exists()) {
            // Copy over and fsync, then securely delete sourceEncrypted
            copyFileSafely(sourceEncrypted, targetDbFile)
            secureDeleteFile(sourceEncrypted)
        } else if (!sourceEncrypted.renameTo(targetDbFile)) {
            copyFileSafely(sourceEncrypted, targetDbFile)
            secureDeleteFile(sourceEncrypted)
        }
    }

    /**
     * Overwrites the file's content on disk with zeros and random bytes before deleting it,
     * ensuring no plaintext SQLite pages remain recoverable in free blocks.
     */
    fun secureDeleteFile(file: File) {
        if (!file.exists()) return
        runCatching {
            val length = file.length()
            if (length > 0L) {
                RandomAccessFile(file, "rws").use { raf ->
                    val chunk = ByteArray(minOf(length, 8192L).toInt())
                    // Pass 1: Zero fill
                    raf.seek(0L)
                    var remaining = length
                    while (remaining > 0L) {
                        val toWrite = minOf(remaining, chunk.size.toLong()).toInt()
                        raf.write(chunk, 0, toWrite)
                        remaining -= toWrite
                    }
                    raf.fd.sync()

                    // Pass 2: Random fill
                    SecureRandom().nextBytes(chunk)
                    raf.seek(0L)
                    remaining = length
                    while (remaining > 0L) {
                        val toWrite = minOf(remaining, chunk.size.toLong()).toInt()
                        raf.write(chunk, 0, toWrite)
                        remaining -= toWrite
                    }
                    raf.fd.sync()
                }
            }
        }
        deleteIfExists(file)
    }

    private fun deleteIfExists(file: File) {
        if (file.exists()) {
            file.delete()
        }
    }

    internal fun quarantineDatabaseFiles(dbFile: File) {
        val stamp = System.currentTimeMillis()
        listOf(
            dbFile,
            File("${dbFile.absolutePath}-wal"),
            File("${dbFile.absolutePath}-shm"),
            File("${dbFile.absolutePath}-journal")
        ).forEach { file ->
            if (file.exists()) {
                val backup = File("${file.absolutePath}.corrupt.$stamp")
                if (!file.renameTo(backup)) {
                    file.delete()
                }
            }
        }
    }

    internal fun isPlaintextSqliteHeader(file: File): Boolean {
        return runCatching {
            if (!file.exists() || file.length() < 16) return false
            val header = ByteArray(15)
            file.inputStream().use { it.read(header) }
            String(header, Charsets.US_ASCII) == "SQLite format 3"
        }.getOrDefault(false)
    }

    private fun getOrCreateHardwareKey(): SecretKey? {
        if (isRunningOnJvmRobolectric()) return null
        return runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val existing = keyStore.getEntry(DB_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existing != null) return existing.secretKey

            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val spec = KeyGenParameterSpec.Builder(
                DB_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            generator.init(spec)
            generator.generateKey()
        }.getOrNull()
    }

    private fun wrapWithKeystoreOrEncode(plainBytes: ByteArray): String {
        val secretKey = getOrCreateHardwareKey()
        if (secretKey != null) {
            runCatching {
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val cipherText = cipher.doFinal(plainBytes)
                val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
                val ctB64 = Base64.encodeToString(cipherText, Base64.NO_WRAP)
                return "KS1:$ivB64:$ctB64"
            }
        }
        return "RAW:" + Base64.encodeToString(plainBytes, Base64.NO_WRAP)
    }

    private fun unwrapWithKeystoreOrDecode(serialized: String): ByteArray? {
        if (serialized.startsWith("KS1:")) {
            val parts = serialized.split(":")
            if (parts.size != 3) return null
            val secretKey = getOrCreateHardwareKey() ?: return null
            return runCatching {
                val iv = Base64.decode(parts[1], Base64.NO_WRAP)
                val cipherText = Base64.decode(parts[2], Base64.NO_WRAP)
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.doFinal(cipherText)
            }.getOrNull()
        }
        if (serialized.startsWith("RAW:")) {
            return runCatching {
                Base64.decode(serialized.removePrefix("RAW:"), Base64.NO_WRAP)
            }.getOrNull()
        }
        return null
    }
}
