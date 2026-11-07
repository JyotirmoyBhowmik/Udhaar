package com.jyotirmoyb.udhaar.security

import android.content.Context
import java.util.UUID

interface TokenStore {
    fun getAuthToken(): String?
    fun getUserId(): String?
    fun getPhoneNumber(): String?
    fun getEmail(): String?
    fun getDeviceId(): String
    fun isCloudSyncEnabled(): Boolean
    fun setCloudSyncEnabled(enabled: Boolean)
    fun saveSession(authToken: String, userId: String, phoneNumber: String?, email: String?)
    fun updateEmail(email: String)
    fun clearSession()
    fun isSignedIn(): Boolean
    fun getLastSyncTimestamp(): Long
    fun setLastSyncTimestamp(timestamp: Long)
}

class EncryptedTokenStore(
    context: Context,
    prefsName: String = PREFS_NAME
) : TokenStore {

    private val prefs = EncryptedStorageManager.getEncryptedPreferences(context, prefsName)

    companion object {
        private const val PREFS_NAME = "udhaar_token_store_prefs"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_PHONE_NUMBER = "phone_number"
        private const val KEY_EMAIL = "email"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_CLOUD_SYNC_ENABLED = "cloud_sync_enabled"
        private const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
    }

    override fun getAuthToken(): String? = prefs.getString(KEY_AUTH_TOKEN, null)

    override fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)

    override fun getPhoneNumber(): String? = prefs.getString(KEY_PHONE_NUMBER, null)

    override fun getEmail(): String? = prefs.getString(KEY_EMAIL, null)

    @Synchronized
    override fun getDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val newId = "dev_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        prefs.edit().putString(KEY_DEVICE_ID, newId).commit()
        return newId
    }

    override fun isCloudSyncEnabled(): Boolean = prefs.getBoolean(KEY_CLOUD_SYNC_ENABLED, false)

    override fun setCloudSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLOUD_SYNC_ENABLED, enabled).commit()
    }

    override fun saveSession(authToken: String, userId: String, phoneNumber: String?, email: String?) {
        prefs.edit()
            .putString(KEY_AUTH_TOKEN, authToken)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_PHONE_NUMBER, phoneNumber)
            .putString(KEY_EMAIL, email)
            .commit()
    }

    override fun updateEmail(email: String) {
        prefs.edit().putString(KEY_EMAIL, email).commit()
    }

    override fun clearSession() {
        prefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_PHONE_NUMBER)
            .remove(KEY_EMAIL)
            .putBoolean(KEY_CLOUD_SYNC_ENABLED, false)
            .remove(KEY_LAST_SYNC_TIMESTAMP)
            .commit()
    }

    override fun isSignedIn(): Boolean {
        return !getAuthToken().isNullOrBlank() && !getUserId().isNullOrBlank()
    }

    override fun getLastSyncTimestamp(): Long = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)

    override fun setLastSyncTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, timestamp).commit()
    }
}
