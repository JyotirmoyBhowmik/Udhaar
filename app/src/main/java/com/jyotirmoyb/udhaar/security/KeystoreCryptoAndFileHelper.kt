package com.jyotirmoyb.udhaar.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.jyotirmoyb.udhaar.util.SafeLogger
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware Android Keystore AES-GCM key wrapping and secure disk file utilities
 * used by [EncryptedStorageManager].
 */
internal object KeystoreCryptoAndFileHelper {

    internal const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    internal const val DB_KEY_ALIAS = "udhaar_sqlcipher_master_key_v1"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    private var jvmTestSoftwareKey: SecretKey? = null

    fun copyFileSafely(source: File, target: File) {
        target.parentFile?.mkdirs()
        source.inputStream().use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
    }

    fun swapDatabaseFileAtomically(sourceEncrypted: File, targetDbFile: File) {
        targetDbFile.parentFile?.mkdirs()
        if (targetDbFile.exists()) {
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
                    raf.seek(0L)
                    var remaining = length
                    while (remaining > 0L) {
                        val toWrite = minOf(remaining, chunk.size.toLong()).toInt()
                        raf.write(chunk, 0, toWrite)
                        remaining -= toWrite
                    }
                    raf.fd.sync()

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

    fun deleteIfExists(file: File) {
        if (file.exists()) {
            file.delete()
        }
    }

    fun quarantineDatabaseFiles(dbFile: File) {
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

    fun isPlaintextSqliteHeader(file: File): Boolean {
        return runCatching {
            if (!file.exists() || file.length() < 16) return false
            val header = ByteArray(15)
            file.inputStream().use { it.read(header) }
            String(header, Charsets.US_ASCII) == "SQLite format 3"
        }.getOrDefault(false)
    }

    private fun getOrCreateHardwareKey(isJvmRobolectric: Boolean): SecretKey? {
        if (isJvmRobolectric) {
            if (jvmTestSoftwareKey == null) {
                val keyGen = KeyGenerator.getInstance("AES")
                keyGen.init(256)
                jvmTestSoftwareKey = keyGen.generateKey()
            }
            return jvmTestSoftwareKey
        }
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

    fun wrapWithKeystoreOrEncode(
        plainBytes: ByteArray,
        isJvmRobolectric: Boolean,
        onFailure: (String) -> Unit
    ): String {
        val secretKey = getOrCreateHardwareKey(isJvmRobolectric)
        if (secretKey != null) {
            val wrapped = runCatching {
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val cipherText = cipher.doFinal(plainBytes)
                val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
                val ctB64 = Base64.encodeToString(cipherText, Base64.NO_WRAP)
                "KS1:$ivB64:$ctB64"
            }.getOrNull()
            if (wrapped != null) {
                return wrapped
            }
        }
        SafeLogger.recordNonFatal("EncryptedStorageManager", "KEYSTORE_WRAP_FAILED")
        onFailure("Hardware keystore encryption unavailable for database key wrapping.")
        throw SecureStorageException("Android Keystore unavailable for database key wrapping")
    }

    fun unwrapWithKeystoreOrDecode(
        serialized: String,
        isJvmRobolectric: Boolean
    ): ByteArray? {
        if (serialized.startsWith("KS1:")) {
            val parts = serialized.split(":")
            if (parts.size != 3) return null
            val secretKey = getOrCreateHardwareKey(isJvmRobolectric) ?: return null
            return runCatching {
                val iv = Base64.decode(parts[1], Base64.NO_WRAP)
                val cipherText = Base64.decode(parts[2], Base64.NO_WRAP)
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                cipher.doFinal(cipherText)
            }.getOrNull()
        }
        return null
    }
}
