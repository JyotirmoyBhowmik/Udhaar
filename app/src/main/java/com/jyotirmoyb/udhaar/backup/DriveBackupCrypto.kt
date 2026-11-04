package com.jyotirmoyb.udhaar.backup

import com.jyotirmoyb.udhaar.BuildConfig
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupPreviewSummary(
    val partiesCount: Int = 0,
    val transactionsCount: Int = 0,
    val paymentsCount: Int = 0,
    val attachmentsCount: Int = 0,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val createdAtMillis: Long = System.currentTimeMillis()
)

class InvalidPassphraseException(message: String, cause: Throwable? = null) : Exception(message, cause)

object DriveBackupCrypto {
    private const val MAGIC = "UDHAAR_ENC_V1"
    private const val KDF_ALGO = "PBKDF2WithHmacSHA256"
    private const val CIPHER_ALGO = "AES/GCM/NoPadding"
    private const val ITERATIONS = 600_000
    private const val LEGACY_ITERATIONS = 65_536
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    fun encrypt(
        plainBytes: ByteArray,
        passphrase: String,
        preview: BackupPreviewSummary
    ): ByteArray {
        require(passphrase.isNotBlank()) { "Passphrase must not be blank" }
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH_BYTES).also { random.nextBytes(it) }

        val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val secretKeyFactory = SecretKeyFactory.getInstance(KDF_ALGO)
        val keyBytes = secretKeyFactory.generateSecret(keySpec).encoded
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance(CIPHER_ALGO)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plainBytes)

        val encoder = Base64.getEncoder()
        val saltB64 = encoder.encodeToString(salt)
        val ivB64 = encoder.encodeToString(iv)
        val cipherB64 = encoder.encodeToString(ciphertext)

        val jsonStr = runCatching {
            JSONObject().apply {
                put("magic", MAGIC)
                put("kdf", KDF_ALGO)
                put("iterations", ITERATIONS)
                put("salt", saltB64)
                put("iv", ivB64)
                put("ciphertext", cipherB64)
                put("createdAtMillis", preview.createdAtMillis)
                put("preview", JSONObject().apply {
                    put("partiesCount", preview.partiesCount)
                    put("transactionsCount", preview.transactionsCount)
                    put("paymentsCount", preview.paymentsCount)
                    put("attachmentsCount", preview.attachmentsCount)
                    put("appVersion", preview.appVersion)
                    put("createdAtMillis", preview.createdAtMillis)
                })
            }.toString()
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: """{"magic":"$MAGIC","kdf":"$KDF_ALGO","iterations":$ITERATIONS,"salt":"$saltB64","iv":"$ivB64","ciphertext":"$cipherB64","createdAtMillis":${preview.createdAtMillis}}"""
        return jsonStr.toByteArray(Charsets.UTF_8)
    }

    fun decrypt(encryptedBytes: ByteArray, passphrase: String): ByteArray {
        require(passphrase.isNotBlank()) { "Passphrase must not be blank" }
        val json = try {
            JSONObject(String(encryptedBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw InvalidPassphraseException("File is not a valid encrypted Udhaar backup.", e)
        }

        val magic = json.optString("magic")
        if (magic != MAGIC) {
            throw InvalidPassphraseException("Unsupported or corrupt backup format: $magic")
        }

        val iterations = json.optInt("iterations", LEGACY_ITERATIONS)
        val saltStr = json.optString("salt")
        val ivStr = json.optString("iv")
        val ciphertextStr = json.optString("ciphertext")

        if (saltStr.isBlank() || ivStr.isBlank() || ciphertextStr.isBlank()) {
            throw InvalidPassphraseException("Corrupted backup: missing encryption parameters.")
        }

        val decoder = Base64.getMimeDecoder()
        val salt = decoder.decode(saltStr.trim())
        val iv = decoder.decode(ivStr.trim())
        val ciphertext = decoder.decode(ciphertextStr.trim())

        return try {
            val keySpec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
            val secretKeyFactory = SecretKeyFactory.getInstance(KDF_ALGO)
            val keyBytes = secretKeyFactory.generateSecret(keySpec).encoded
            val secretKey = SecretKeySpec(keyBytes, "AES")

            val cipher = Cipher.getInstance(CIPHER_ALGO)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw InvalidPassphraseException(
                "Incorrect passphrase or corrupted backup. Lost passphrases cannot be recovered.",
                e
            )
        }
    }

    fun isEncryptedBackupContainer(encryptedBytes: ByteArray): Boolean {
        return try {
            val json = JSONObject(String(encryptedBytes, Charsets.UTF_8))
            json.optString("magic") == MAGIC
        } catch (e: Exception) {
            com.jyotirmoyb.udhaar.util.SafeLogger.d("DriveBackupCrypto", "Byte array is not an encrypted container")
            false
        }
    }

    fun isEncryptedBackupContainer(text: String): Boolean {
        return try {
            val json = JSONObject(text)
            json.optString("magic") == MAGIC
        } catch (e: Exception) {
            com.jyotirmoyb.udhaar.util.SafeLogger.d("DriveBackupCrypto", "Text is not an encrypted container")
            false
        }
    }

    fun peekPreview(encryptedBytes: ByteArray): BackupPreviewSummary? {
        return try {
            val json = JSONObject(String(encryptedBytes, Charsets.UTF_8))
            if (json.optString("magic") != MAGIC) return null
            val p = json.optJSONObject("preview") ?: return null
            BackupPreviewSummary(
                partiesCount = p.optInt("partiesCount", 0),
                transactionsCount = p.optInt("transactionsCount", 0),
                paymentsCount = p.optInt("paymentsCount", 0),
                attachmentsCount = p.optInt("attachmentsCount", 0),
                appVersion = p.optString("appVersion", "13.0"),
                createdAtMillis = p.optLong("createdAtMillis", json.optLong("createdAtMillis", System.currentTimeMillis()))
            )
        } catch (e: Exception) {
            com.jyotirmoyb.udhaar.util.SafeLogger.w("DriveBackupCrypto", "Failed reading backup preview metadata", e)
            null
        }
    }
}

