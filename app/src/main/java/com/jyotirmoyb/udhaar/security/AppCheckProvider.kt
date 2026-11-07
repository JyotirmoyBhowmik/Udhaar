package com.jyotirmoyb.udhaar.security

interface AppCheckProvider {
    suspend fun getToken(): String?
    fun getTokenSync(): String?
}

class DefaultAppCheckProvider(
    private val debugToken: String? = null
) : AppCheckProvider {
    override suspend fun getToken(): String? = debugToken
    override fun getTokenSync(): String? = debugToken
}
