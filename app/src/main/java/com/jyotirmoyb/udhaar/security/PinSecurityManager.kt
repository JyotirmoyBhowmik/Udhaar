package com.jyotirmoyb.udhaar.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.SecretKeyFactory
import kotlin.math.min

data class PinVerificationResult(
    val isSuccess: Boolean,
    val failedAttempts: Int,
    val lockoutRemainingMillis: Long,
    val errorMessage: String? = null
)

/**
 * Manages App Lock PIN storage using PBKDF2WithHmacSHA256 salted hashing and
 * Android Keystore (AES-256-GCM) envelope encryption, plus exponential backoff
 * after 5 failed attempts.
 */
class PinSecurityManager(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_FILE_NAME,
        Context.MODE_PRIVATE
    )

    fun hasPinConfigured(): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null)
        val encryptedHash = prefs.getString(KEY_PIN_HASH_BLOB, null)
        return !salt.isNullOrBlank() && !encryptedHash.isNullOrBlank()
    }

    fun getFailedAttempts(): Int = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)

    fun getLockoutUntilMillis(): Long = prefs.getLong(KEY_LOCKOUT_UNTIL_MILLIS, 0L)

    fun getRemainingLockoutMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val until = getLockoutUntilMillis()
        return if (until > nowMillis) until - nowMillis else 0L
    }

    /**
     * Validates that a new PIN is 4..8 digits.
     */
    fun validateNewPinFormat(pin: String): String? {
        val trimmed = pin.trim()
        if (trimmed.length !in 4..8 || !trimmed.all { it.isDigit() }) {
            return "PIN must be 4 to 8 digits."
        }
        return null
    }

    /**
     * Hashes the PIN with a random 16-byte salt using PBKDF2WithHmacSHA256,
     * encrypts the resulting hash with AndroidKeyStore AES-256-GCM (when available),
     * and stores it in private storage. Resets failed attempt counters.
     */
    fun setPin(rawPin: String): Boolean {
        val cleanPin = rawPin.trim()
        if (validateNewPinFormat(cleanPin) != null) return false

        val salt = ByteArray(SALT_LENGTH_BYTES)
        SecureRandom().nextBytes(salt)
        val derivedHash = pbkdf2Hash(cleanPin, salt)
        val wrappedBlob = encryptWithKeystoreOrEncode(derivedHash)

        prefs.edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH_BLOB, wrappedBlob)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MILLIS, 0L)
            .apply()
        return true
    }

    fun clearPin() {
        prefs.edit()
            .remove(KEY_PIN_SALT)
            .remove(KEY_PIN_HASH_BLOB)
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MILLIS, 0L)
            .apply()
    }

    fun resetFailedAttempts() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MILLIS, 0L)
            .apply()
    }

    /**
     * Verifies the candidate PIN against the stored salted PBKDF2 hash.
     * Enforces a 5-attempt threshold with growing lockout delay.
     */
    fun verifyPin(
        candidatePin: String,
        nowMillis: Long = System.currentTimeMillis()
    ): PinVerificationResult {
        val remainingLockout = getRemainingLockoutMillis(nowMillis)
        val currentFailures = getFailedAttempts()
        if (remainingLockout > 0L) {
            val seconds = ((remainingLockout + 999L) / 1000L). coerceAtLeast(1L)
            return PinVerificationResult(
                isSuccess = false,
                failedAttempts = currentFailures,
                lockoutRemainingMillis = remainingLockout,
                errorMessage = "Too many wrong attempts ($currentFailures). Try again in ${seconds}s."
            )
        }

        val saltBase64 = prefs.getString(KEY_PIN_SALT, null)
        val hashBlob = prefs.getString(KEY_PIN_HASH_BLOB, null)
        if (saltBase64.isNullOrBlank() || hashBlob.isNullOrBlank()) {
            return PinVerificationResult(
                isSuccess = false,
                failedAttempts = currentFailures,
                lockoutRemainingMillis = 0L,
                errorMessage = "No App PIN is set. Unlock with Biometric or Phone PIN."
            )
        }

        val salt = runCatching { Base64.decode(saltBase64, Base64.NO_WRAP) }.getOrNull()
        val expectedHash = decryptWithKeystoreOrDecode(hashBlob)
        if (salt == null || expectedHash == null) {
            return PinVerificationResult(
                isSuccess = false,
                failedAttempts = currentFailures,
                lockoutRemainingMillis = 0L,
                errorMessage = "Stored PIN credential could not be read. Use Biometric or Phone PIN."
            )
        }

        val candidateHash = pbkdf2Hash(candidatePin.trim(), salt)
        val matches = MessageDigest.isEqual(expectedHash, candidateHash)

        if (matches) {
            resetFailedAttempts()
            return PinVerificationResult(
                isSuccess = true,
                failedAttempts = 0,
                lockoutRemainingMillis = 0L,
                errorMessage = null
            )
        } else {
            val newFailures = currentFailures + 1
            val delayMillis = calculateBackoffDelayMillis(newFailures)
            val lockoutUntil = if (delayMillis > 0L) nowMillis + delayMillis else 0L
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, newFailures)
                .putLong(KEY_LOCKOUT_UNTIL_MILLIS, lockoutUntil)
                .apply()

            val msg = if (delayMillis > 0L) {
                val secs = delayMillis / 1000L
                "Wrong PIN ($newFailures/$MAX_FREE_ATTEMPTS attempts). Locked for ${secs}s."
            } else {
                val attemptsLeft = (MAX_FREE_ATTEMPTS - newFailures).coerceAtLeast(0)
                if (attemptsLeft > 0) {
                    "Incorrect PIN. $attemptsLeft attempt(s) remaining before lockout."
                } else {
                    "Incorrect PIN."
                }
            }
            return PinVerificationResult(
                isSuccess = false,
                failedAttempts = newFailures,
                lockoutRemainingMillis = delayMillis,
                errorMessage = msg
            )
        }
    }

    /**
     * Growing delay starting at the 5th failed attempt:
     * < 5 attempts -> 0ms
     * 5 attempts   -> 30s
     * 6 attempts   -> 60s
     * 7 attempts   -> 120s (2m)
     * 8 attempts   -> 300s (5m)
     * 9+ attempts  -> 900s (15m)
     */
    fun calculateBackoffDelayMillis(failedAttempts: Int): Long {
        if (failedAttempts < MAX_FREE_ATTEMPTS) return 0L
        val step = min(failedAttempts - MAX_FREE_ATTEMPTS, BACKOFF_STEPS_SECONDS.lastIndex)
        return BACKOFF_STEPS_SECONDS[step] * 1000L
    }

    private fun pbkdf2Hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_BIT_LENGTH)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun getOrCreateKeystoreSecretKey(): SecretKey? {
        return runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existingKey = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
            if (existingKey != null) return@runCatching existingKey

            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        }.getOrNull()
    }

    private fun encryptWithKeystoreOrEncode(rawHash: ByteArray): String {
        val secretKey = getOrCreateKeystoreSecretKey()
        if (secretKey != null) {
            runCatching {
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val cipherText = cipher.doFinal(rawHash)
                val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
                val ctB64 = Base64.encodeToString(cipherText, Base64.NO_WRAP)
                return "KS1:$ivB64:$ctB64"
            }
        }
        return "RAW1:" + Base64.encodeToString(rawHash, Base64.NO_WRAP)
    }

    private fun decryptWithKeystoreOrDecode(blob: String): ByteArray? {
        return when {
            blob.startsWith("KS1:") -> {
                val parts = blob.split(":")
                if (parts.size != 3) return null
                val secretKey = getOrCreateKeystoreSecretKey() ?: return null
                runCatching {
                    val iv = Base64.decode(parts[1], Base64.NO_WRAP)
                    val cipherText = Base64.decode(parts[2], Base64.NO_WRAP)
                    val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
                    cipher.doFinal(cipherText)
                }.getOrNull()
            }
            blob.startsWith("RAW1:") -> {
                val b64 = blob.removePrefix("RAW1:")
                runCatching { Base64.decode(b64, Base64.NO_WRAP) }.getOrNull()
            }
            else -> null
        }
    }

    companion object {
        private const val PREFS_FILE_NAME = "udhaar_secure_pin_vault"
        private const val KEY_PIN_SALT = "pin_salt_b64"
        private const val KEY_PIN_HASH_BLOB = "pin_hash_blob"
        private const val KEY_FAILED_ATTEMPTS = "pin_failed_attempts"
        private const val KEY_LOCKOUT_UNTIL_MILLIS = "pin_lockout_until_millis"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "udhaar_pin_master_key_v1"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128

        private const val PBKDF2_ITERATIONS = 65_536
        private const val SALT_LENGTH_BYTES = 16
        private const val HASH_BIT_LENGTH = 256

        const val MAX_FREE_ATTEMPTS = 5
        private val BACKOFF_STEPS_SECONDS = longArrayOf(30L, 60L, 120L, 300L, 900L)
    }
}
