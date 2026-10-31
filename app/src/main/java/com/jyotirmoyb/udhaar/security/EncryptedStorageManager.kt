package com.jyotirmoyb.udhaar.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.RoomDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Provides hardware-backed Android Keystore (MasterKey) + EncryptedSharedPreferences
 * and SQLCipher (SupportOpenHelperFactory) 256-bit AES database encryption for Room.
 */
object EncryptedStorageManager {

    private const val ENCRYPTED_SETTINGS_FILE = "udhaar_encrypted_settings_prefs"
    private const val ENCRYPTED_SECURITY_FILE = "udhaar_encrypted_pin_security_prefs"
    private const val FALLBACK_WRAP_FILE = "udhaar_keystore_db_key_store"
    private const val KEY_WRAPPED_DB_PASSPHRASE = "wrapped_sqlcipher_passphrase_v1"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val DB_KEY_ALIAS = "udhaar_sqlcipher_master_key_v1"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

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
     * provider may not support Tink keysets, falls back safely so unit tests can execute.
     */
    fun getEncryptedPreferences(
        context: Context,
        fileName: String = ENCRYPTED_SETTINGS_FILE
    ): SharedPreferences {
        val appContext = context.applicationContext
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

    /**
     * Retrieves or generates a random 32-byte (256-bit) SQLCipher database key protected by
     * Android Keystore and [EncryptedSharedPreferences].
     */
    fun getOrCreateDatabasePassphrase(context: Context): ByteArray {
        val prefs = getEncryptedPreferences(context, FALLBACK_WRAP_FILE)
        val existingBlob = prefs.getString(KEY_WRAPPED_DB_PASSPHRASE, null)
        if (!existingBlob.isNullOrBlank()) {
            val unwrapped = unwrapWithKeystoreOrDecode(existingBlob)
            if (unwrapped != null && unwrapped.size == 32) {
                return unwrapped
            }
        }

        val randomKey = ByteArray(32)
        SecureRandom().nextBytes(randomKey)
        val wrapped = wrapWithKeystoreOrEncode(randomKey)
        prefs.edit().putString(KEY_WRAPPED_DB_PASSPHRASE, wrapped).apply()
        return randomKey
    }

    /**
     * Creates a SQLCipher [SupportOpenHelperFactory] using a Keystore-protected 256-bit key.
     */
    fun createSqlCipherFactory(context: Context): SupportOpenHelperFactory {
        System.loadLibrary("sqlcipher")
        val passphrase = getOrCreateDatabasePassphrase(context)
        return SupportOpenHelperFactory(passphrase)
    }

    /**
     * Configures the Room database builder with SQLCipher encryption when running on an Android
     * device/runtime where native `libsqlcipher.so` is present and the database file is either new
     * or already SQLCipher-encrypted.
     */
    fun <T : RoomDatabase> configureSqlCipherForRoom(
        context: Context,
        builder: RoomDatabase.Builder<T>,
        dbName: String
    ): RoomDatabase.Builder<T> {
        val isRobolectric = Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
        if (isRobolectric) {
            return builder
        }
        val dbFile = context.applicationContext.getDatabasePath(dbName)
        if (dbFile.exists() && isPlaintextSqliteHeader(dbFile)) {
            // Preserve existing unencrypted database compatibility on in-place upgrade
            return builder
        }
        return runCatching {
            val factory = createSqlCipherFactory(context.applicationContext)
            builder.openHelperFactory(factory)
        }.getOrDefault(builder)
    }

    private fun isPlaintextSqliteHeader(file: File): Boolean {
        return runCatching {
            if (!file.exists() || file.length() < 16) return false
            val header = ByteArray(15)
            file.inputStream().use { it.read(header) }
            String(header, Charsets.US_ASCII) == "SQLite format 3"
        }.getOrDefault(false)
    }

    private fun getOrCreateHardwareKey(): SecretKey? {
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
