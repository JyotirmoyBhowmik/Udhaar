package com.jyotirmoyb.udhaar.security

import android.content.Context
import android.database.sqlite.SQLiteDatabase as FrameworkSQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

/**
 * Native SQLCipher and JVM/Robolectric database migration, integrity verification,
 * and fresh-install probe engine used by [EncryptedStorageManager].
 */
internal object SqlCipherMigrationEngine {

    private const val KEY_JVM_ENCRYPTED_FILES = "jvm_encrypted_db_paths"
    internal const val MIGRATION_STAGING_DIR = "db_migration_staging"

    private data class ExportMetadata(
        val userVersion: Int,
        val tableRowCounts: Map<String, Long>
    )

    fun migratePlainDatabaseToEncrypted(
        context: Context,
        dbFile: File,
        ensureNativeLoaded: () -> Unit
    ): Boolean {
        val appContext = context.applicationContext
        val stagingDir = File(appContext.filesDir, MIGRATION_STAGING_DIR).apply { mkdirs() }
        val plainCopyFile = File(stagingDir, "${dbFile.name}.plain_copy")
        val plainCopyWal = File("${plainCopyFile.absolutePath}-wal")
        val plainCopyShm = File("${plainCopyFile.absolutePath}-shm")
        val encryptedCandidateFile = File(stagingDir, "${dbFile.name}.encrypted_candidate")
        val encryptedCandidateWal = File("${encryptedCandidateFile.absolutePath}-wal")
        val encryptedCandidateShm = File("${encryptedCandidateFile.absolutePath}-shm")

        val priorState = EncryptedStorageManager.getDbEncryptionState(appContext)
        val resumingInterrupted =
            priorState == EncryptedStorageManager.DbEncryptionState.MIGRATING &&
                plainCopyFile.exists() && plainCopyFile.length() > 0L

        return try {
            EncryptedStorageManager.setDbEncryptionState(appContext, EncryptedStorageManager.DbEncryptionState.MIGRATING)
            preparePlainCopyForMigration(dbFile, plainCopyFile, plainCopyWal, plainCopyShm, resumingInterrupted)

            KeystoreCryptoAndFileHelper.deleteIfExists(encryptedCandidateFile)
            KeystoreCryptoAndFileHelper.deleteIfExists(encryptedCandidateWal)
            KeystoreCryptoAndFileHelper.deleteIfExists(encryptedCandidateShm)

            val passphrase = EncryptedStorageManager.getOrCreateDatabasePassphrase(
                context = appContext,
                requireSqlAttachCompatibleAscii = true
            )

            check(!EncryptedStorageManager.TestHooks.simulateExportFailure) { "Simulated sqlcipher_export failure" }

            val isJvm = EncryptedStorageManager.isRunningOnJvmRobolectric()
            val expectedMeta = if (isJvm) {
                exportPlainToEncryptedJvm(plainCopyFile, encryptedCandidateFile, passphrase)
            } else {
                ensureNativeLoaded()
                exportPlainToEncryptedNative(plainCopyFile, encryptedCandidateFile, passphrase)
            }

            val verificationPassed = if (isJvm) {
                verifyEncryptedDatabaseJvm(encryptedCandidateFile, expectedMeta)
            } else {
                verifyEncryptedDatabaseNative(encryptedCandidateFile, passphrase, expectedMeta)
            }

            check(verificationPassed) {
                "Encrypted database verification failed (row count or PRAGMA integrity_check mismatch)."
            }

            finalizeMigratedDatabase(
                appContext = appContext,
                dbFile = dbFile,
                encryptedCandidateFile = encryptedCandidateFile,
                encryptedCandidateWal = encryptedCandidateWal,
                encryptedCandidateShm = encryptedCandidateShm,
                plainCopyFile = plainCopyFile,
                plainCopyWal = plainCopyWal,
                plainCopyShm = plainCopyShm,
                isJvm = isJvm
            )
            true
        } catch (t: Throwable) {
            rollbackFailedMigration(
                appContext = appContext,
                dbFile = dbFile,
                encryptedCandidateFile = encryptedCandidateFile,
                encryptedCandidateWal = encryptedCandidateWal,
                encryptedCandidateShm = encryptedCandidateShm,
                plainCopyFile = plainCopyFile,
                plainCopyWal = plainCopyWal,
                plainCopyShm = plainCopyShm,
                cause = t
            )
            false
        }
    }

    private fun preparePlainCopyForMigration(
        dbFile: File,
        plainCopyFile: File,
        plainCopyWal: File,
        plainCopyShm: File,
        resumingInterrupted: Boolean
    ) {
        if (!resumingInterrupted) {
            check(dbFile.exists() && dbFile.length() > 0L) {
                "Plain database file does not exist for migration: ${dbFile.absolutePath}"
            }
            KeystoreCryptoAndFileHelper.copyFileSafely(dbFile, plainCopyFile)
            syncSidecarFile(File("${dbFile.absolutePath}-wal"), plainCopyWal)
            syncSidecarFile(File("${dbFile.absolutePath}-shm"), plainCopyShm)
        } else if (!dbFile.exists() || dbFile.length() == 0L) {
            KeystoreCryptoAndFileHelper.copyFileSafely(plainCopyFile, dbFile)
        }
    }

    private fun syncSidecarFile(source: File, target: File) {
        if (source.exists()) {
            KeystoreCryptoAndFileHelper.copyFileSafely(source, target)
        } else if (target.exists()) {
            KeystoreCryptoAndFileHelper.secureDeleteFile(target)
        }
    }

    private fun finalizeMigratedDatabase(
        appContext: Context,
        dbFile: File,
        encryptedCandidateFile: File,
        encryptedCandidateWal: File,
        encryptedCandidateShm: File,
        plainCopyFile: File,
        plainCopyWal: File,
        plainCopyShm: File,
        isJvm: Boolean
    ) {
        KeystoreCryptoAndFileHelper.secureDeleteFile(File("${dbFile.absolutePath}-wal"))
        KeystoreCryptoAndFileHelper.secureDeleteFile(File("${dbFile.absolutePath}-shm"))
        KeystoreCryptoAndFileHelper.secureDeleteFile(File("${dbFile.absolutePath}-journal"))

        KeystoreCryptoAndFileHelper.swapDatabaseFileAtomically(encryptedCandidateFile, dbFile)
        KeystoreCryptoAndFileHelper.deleteIfExists(encryptedCandidateWal)
        KeystoreCryptoAndFileHelper.deleteIfExists(encryptedCandidateShm)

        KeystoreCryptoAndFileHelper.secureDeleteFile(plainCopyFile)
        KeystoreCryptoAndFileHelper.secureDeleteFile(plainCopyWal)
        KeystoreCryptoAndFileHelper.secureDeleteFile(plainCopyShm)
        KeystoreCryptoAndFileHelper.secureDeleteFile(File("${plainCopyFile.absolutePath}-journal"))

        if (isJvm) {
            markJvmFileEncrypted(appContext, dbFile)
        }
        EncryptedStorageManager.setDbEncryptionState(appContext, EncryptedStorageManager.DbEncryptionState.ENCRYPTED)
    }

    private fun rollbackFailedMigration(
        appContext: Context,
        dbFile: File,
        encryptedCandidateFile: File,
        encryptedCandidateWal: File,
        encryptedCandidateShm: File,
        plainCopyFile: File,
        plainCopyWal: File,
        plainCopyShm: File,
        cause: Throwable
    ) {
        KeystoreCryptoAndFileHelper.deleteIfExists(encryptedCandidateFile)
        KeystoreCryptoAndFileHelper.deleteIfExists(encryptedCandidateWal)
        KeystoreCryptoAndFileHelper.deleteIfExists(encryptedCandidateShm)

        val dbMissingOrEmpty = !dbFile.exists() || dbFile.length() == 0L
        val hasPlainBackup = plainCopyFile.exists() && plainCopyFile.length() > 0L
        if (dbMissingOrEmpty && hasPlainBackup) {
            runCatching { KeystoreCryptoAndFileHelper.copyFileSafely(plainCopyFile, dbFile) }
        }
        if (dbFile.exists() && dbFile.length() > 0L) {
            KeystoreCryptoAndFileHelper.secureDeleteFile(plainCopyFile)
            KeystoreCryptoAndFileHelper.secureDeleteFile(plainCopyWal)
            KeystoreCryptoAndFileHelper.secureDeleteFile(plainCopyShm)
        }

        EncryptedStorageManager.setDbEncryptionState(
            context = appContext,
            state = EncryptedStorageManager.DbEncryptionState.FAILED,
            errorMessage = cause.message ?: cause.javaClass.simpleName
        )
    }

    fun initializeOrVerifyEncryptedDatabase(
        context: Context,
        dbFile: File,
        ensureNativeLoaded: () -> Unit
    ): Boolean {
        val appContext = context.applicationContext
        return try {
            check(!EncryptedStorageManager.TestHooks.simulateFreshInstallFailure) {
                "Simulated SQLCipher initialization failure on fresh install"
            }

            val passphrase = EncryptedStorageManager.getOrCreateDatabasePassphrase(
                context = appContext,
                requireSqlAttachCompatibleAscii = !dbFile.exists() || dbFile.length() == 0L
            )

            if (EncryptedStorageManager.isRunningOnJvmRobolectric()) {
                markJvmFileEncrypted(appContext, dbFile)
                EncryptedStorageManager.setDbEncryptionState(appContext, EncryptedStorageManager.DbEncryptionState.ENCRYPTED)
                return true
            }

            ensureNativeLoaded()

            if (dbFile.exists() && dbFile.length() > 0L) {
                val verifyExisting = { flags: Int ->
                    val db = SQLiteDatabase.openDatabase(
                        dbFile.absolutePath,
                        passphrase.copyOf(),
                        null,
                        flags,
                        null,
                        null
                    )
                    try {
                        db.rawQuery("SELECT count(*) FROM sqlite_master;", null).use { cursor ->
                            cursor.moveToFirst()
                        }
                    } finally {
                        runCatching { db.close() }
                    }
                }

                try {
                    verifyExisting(SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING)
                } catch (_: Throwable) {
                    KeystoreCryptoAndFileHelper.deleteIfExists(File("${dbFile.absolutePath}-shm"))
                    try {
                        verifyExisting(SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING)
                    } catch (_: Throwable) {
                        KeystoreCryptoAndFileHelper.deleteIfExists(File("${dbFile.absolutePath}-wal"))
                        KeystoreCryptoAndFileHelper.deleteIfExists(File("${dbFile.absolutePath}-shm"))
                        verifyExisting(SQLiteDatabase.OPEN_READWRITE)
                    }
                }
            } else {
                val probeFile = File(appContext.cacheDir, "udhaar_sqlcipher_probe.db")
                try {
                    KeystoreCryptoAndFileHelper.deleteIfExists(probeFile)
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
                    KeystoreCryptoAndFileHelper.deleteIfExists(probeFile)
                    KeystoreCryptoAndFileHelper.deleteIfExists(File("${probeFile.absolutePath}-wal"))
                    KeystoreCryptoAndFileHelper.deleteIfExists(File("${probeFile.absolutePath}-shm"))
                }
            }

            EncryptedStorageManager.setDbEncryptionState(appContext, EncryptedStorageManager.DbEncryptionState.ENCRYPTED)
            true
        } catch (t: Throwable) {
            EncryptedStorageManager.setDbEncryptionState(
                context = appContext,
                state = EncryptedStorageManager.DbEncryptionState.FAILED,
                errorMessage = t.message ?: "Failed to initialize SQLCipher encrypted storage."
            )
            false
        }
    }

    fun isUnencryptedDatabase(context: Context, dbFile: File): Boolean {
        if (!dbFile.exists() || dbFile.length() < 16L) return false
        if (EncryptedStorageManager.isRunningOnJvmRobolectric()) {
            return KeystoreCryptoAndFileHelper.isPlaintextSqliteHeader(dbFile) &&
                !isJvmFileMarkedEncrypted(context, dbFile)
        }
        return KeystoreCryptoAndFileHelper.isPlaintextSqliteHeader(dbFile)
    }

    fun markJvmFileEncrypted(context: Context, dbFile: File) {
        val prefs = EncryptedStorageManager.getEncryptedPreferences(context)
        val existing = prefs.getStringSet(KEY_JVM_ENCRYPTED_FILES, emptySet()).orEmpty().toMutableSet()
        existing.add(dbFile.absolutePath)
        prefs.edit().putStringSet(KEY_JVM_ENCRYPTED_FILES, existing).commit()
    }

    fun unmarkJvmFileEncrypted(context: Context, dbFile: File) {
        val prefs = EncryptedStorageManager.getEncryptedPreferences(context)
        val existing = prefs.getStringSet(KEY_JVM_ENCRYPTED_FILES, emptySet()).orEmpty().toMutableSet()
        existing.remove(dbFile.absolutePath)
        prefs.edit().putStringSet(KEY_JVM_ENCRYPTED_FILES, existing).commit()
    }

    private fun isJvmFileMarkedEncrypted(context: Context, dbFile: File): Boolean {
        val prefs = EncryptedStorageManager.getEncryptedPreferences(context)
        val existing = prefs.getStringSet(KEY_JVM_ENCRYPTED_FILES, emptySet()).orEmpty()
        return dbFile.absolutePath in existing &&
            EncryptedStorageManager.getDbEncryptionState(context) == EncryptedStorageManager.DbEncryptionState.ENCRYPTED
    }

    private fun exportPlainToEncryptedNative(
        plainCopyFile: File,
        encryptedCandidateFile: File,
        passphrase: ByteArray
    ): ExportMetadata {
        runCatching {
            FrameworkSQLiteDatabase.openDatabase(
                plainCopyFile.absolutePath,
                null,
                FrameworkSQLiteDatabase.OPEN_READWRITE or FrameworkSQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING
            ).use { fdb ->
                runCatching {
                    fdb.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                }
                runCatching {
                    fdb.rawQuery("PRAGMA journal_mode=DELETE", null).use { it.moveToFirst() }
                }
            }
            KeystoreCryptoAndFileHelper.deleteIfExists(File("${plainCopyFile.absolutePath}-wal"))
            KeystoreCryptoAndFileHelper.deleteIfExists(File("${plainCopyFile.absolutePath}-shm"))
        }

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
        val fileValid = encryptedCandidateFile.exists() &&
            encryptedCandidateFile.length() > 0L &&
            !KeystoreCryptoAndFileHelper.isPlaintextSqliteHeader(encryptedCandidateFile)
        if (!fileValid) return false
        val encDb = SQLiteDatabase.openDatabase(
            encryptedCandidateFile.absolutePath,
            passphrase.copyOf(),
            null,
            SQLiteDatabase.OPEN_READWRITE,
            null,
            null
        )
        return try {
            var integrityOk = !EncryptedStorageManager.TestHooks.simulateIntegrityCheckFailure
            if (integrityOk) {
                integrityOk = false
                encDb.rawQuery("PRAGMA integrity_check;", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        integrityOk = (cursor.getString(0) ?: "").equals("ok", ignoreCase = true)
                    }
                }
            }
            val metaMatches = integrityOk &&
                encDb.version == expected.userVersion &&
                !EncryptedStorageManager.TestHooks.simulateRowCountMismatch
            if (!metaMatches) {
                false
            } else {
                val actualCounts = queryAllTableRowCountsNative(encDb)
                actualCounts.size == expected.tableRowCounts.size &&
                    expected.tableRowCounts.all { (table, count) -> actualCounts[table] == count }
            }
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
                if (!name.isNullOrBlank()) tables.add(name)
            }
        }
        val counts = linkedMapOf<String, Long>()
        for (table in tables) {
            val safeTable = table.replace("`", "``")
            db.rawQuery("SELECT COUNT(*) FROM `$safeTable`", null).use { cursor ->
                if (cursor.moveToFirst()) counts[table] = cursor.getLong(0)
            }
        }
        return counts
    }

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
            plainDb.version to queryAllTableRowCountsJvm(plainDb)
        }

        KeystoreCryptoAndFileHelper.copyFileSafely(plainCopyFile, encryptedCandidateFile)
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
        if (!encryptedCandidateFile.exists() || encryptedCandidateFile.length() == 0L) return false
        val encDb = FrameworkSQLiteDatabase.openDatabase(
            encryptedCandidateFile.absolutePath,
            null,
            FrameworkSQLiteDatabase.OPEN_READWRITE
        )
        return try {
            var integrityOk = !EncryptedStorageManager.TestHooks.simulateIntegrityCheckFailure
            if (integrityOk) {
                integrityOk = false
                encDb.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    if (cursor.moveToFirst()) {
                        integrityOk = (cursor.getString(0) ?: "").equals("ok", ignoreCase = true)
                    }
                }
            }
            val metaMatches = integrityOk &&
                encDb.version == expected.userVersion &&
                !EncryptedStorageManager.TestHooks.simulateRowCountMismatch
            if (!metaMatches) {
                false
            } else {
                val actualCounts = queryAllTableRowCountsJvm(encDb)
                actualCounts.size == expected.tableRowCounts.size &&
                    expected.tableRowCounts.all { (table, count) -> actualCounts[table] == count }
            }
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
                if (!name.isNullOrBlank()) tables.add(name)
            }
        }
        val counts = linkedMapOf<String, Long>()
        for (table in tables) {
            val safeTable = table.replace("`", "``")
            db.rawQuery("SELECT COUNT(*) FROM `$safeTable`", null).use { cursor ->
                if (cursor.moveToFirst()) counts[table] = cursor.getLong(0)
            }
        }
        return counts
    }
}
