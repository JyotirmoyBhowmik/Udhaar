package com.jyotirmoyb.udhaar.backup

import android.util.Base64
import org.json.JSONObject
import java.security.SecureRandom
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
    val appVersion: String = "7.0",
    val createdAtMillis: Long = System.currentTimeMillis()
)

class InvalidPassphraseException(message: String, cause: Throwable? = null) : Exception(message, cause)

object DriveBackupCrypto {
    private const val MAGIC = "UDHAAR_ENC_V1"
    private const val KDF_ALGO = "PBKDF2WithHmacSHA256"
    private const val CIPHER_ALGO = "AES/GCM/NoPadding"
    private const val ITERATIONS = 65536
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

        val json = JSONObject().apply {
            put("magic", MAGIC)
            put("kdf", KDF_ALGO)
            put("iterations", ITERATIONS)
            put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            put("createdAtMillis", preview.createdAtMillis)
            put("preview", JSONObject().apply {
                put("partiesCount", preview.partiesCount)
                put("transactionsCount", preview.transactionsCount)
                put("paymentsCount", preview.paymentsCount)
                put("attachmentsCount", preview.attachmentsCount)
                put("appVersion", preview.appVersion)
                put("createdAtMillis", preview.createdAtMillis)
            })
        }
        return json.toString().toByteArray(Charsets.UTF_8)
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

        val iterations = json.optInt("iterations", ITERATIONS)
        val saltStr = json.optString("salt")
        val ivStr = json.optString("iv")
        val ciphertextStr = json.optString("ciphertext")

        if (saltStr.isBlank() || ivStr.isBlank() || ciphertextStr.isBlank()) {
            throw InvalidPassphraseException("Corrupted backup: missing encryption parameters.")
        }

        val salt = Base64.decode(saltStr, Base64.NO_WRAP)
        val iv = Base64.decode(ivStr, Base64.NO_WRAP)
        val ciphertext = Base64.decode(ciphertextStr, Base64.NO_WRAP)

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
                appVersion = p.optString("appVersion", "7.0"),
                createdAtMillis = p.optLong("createdAtMillis", json.optLong("createdAtMillis", System.currentTimeMillis()))
            )
        } catch (_: Exception) {
            null
        }
    }
}
