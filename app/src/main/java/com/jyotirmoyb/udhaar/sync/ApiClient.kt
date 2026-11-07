package com.jyotirmoyb.udhaar.sync

import com.jyotirmoyb.udhaar.security.AppCheckProvider
import com.jyotirmoyb.udhaar.security.DefaultAppCheckProvider
import com.jyotirmoyb.udhaar.security.TokenStore
import com.jyotirmoyb.udhaar.util.SafeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.TlsVersion
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

class ApiException(val statusCode: Int, override val message: String) : IOException("HTTP $statusCode: $message")

class ApiClient(
    val baseUrl: String,
    private val tokenStore: TokenStore,
    private val appCheckProvider: AppCheckProvider = DefaultAppCheckProvider(),
    private val allowCleartextForTests: Boolean = false,
    customClient: OkHttpClient? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient = customClient ?: buildDefaultClient()

    private fun buildDefaultClient(): OkHttpClient {
        val tlsSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
            .build()

        val specs = if (allowCleartextForTests) {
            listOf(ConnectionSpec.CLEARTEXT, tlsSpec)
        } else {
            listOf(tlsSpec)
        }

        return OkHttpClient.Builder()
            .connectionSpecs(specs)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(RetryInterceptor(maxRetries = 3))
            .addInterceptor(HeaderInterceptor(tokenStore, appCheckProvider))
            .build()
    }

    private class RetryInterceptor(private val maxRetries: Int = 3) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var attempt = 0
            var lastException: IOException? = null
            while (attempt < maxRetries) {
                try {
                    return chain.proceed(chain.request())
                } catch (e: IOException) {
                    lastException = e
                    attempt++
                    if (attempt >= maxRetries) break
                    val backoffMs = min(1000L * 2.0.pow(attempt - 1).toLong(), 5000L)
                    val jitter = (Math.random() * 200).toLong()
                    try {
                        Thread.sleep(backoffMs + jitter)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw e
                    }
                }
            }
            throw lastException ?: IOException("Request failed after $maxRetries attempts")
        }
    }

    private class HeaderInterceptor(
        private val tokenStore: TokenStore,
        private val appCheckProvider: AppCheckProvider
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val builder = original.newBuilder()
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .header("X-Device-Id", tokenStore.getDeviceId())

            val token = tokenStore.getAuthToken()
            if (!token.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $token")
            }

            val appCheckToken = appCheckProvider.getTokenSync()
            if (!appCheckToken.isNullOrBlank()) {
                builder.header("X-Firebase-AppCheck", appCheckToken)
            }

            return chain.proceed(builder.build())
        }
    }

    private fun sanitizeUrl(path: String): String {
        val trimmedBase = baseUrl.trimEnd('/')
        val trimmedPath = path.trimStart('/')
        return "$trimmedBase/$trimmedPath"
    }

    // -------------------------------------------------------------------------
    // Auth Operations
    // -------------------------------------------------------------------------
    suspend fun startSignup(phone: String, channel: String = "WHATSAPP"): AuthOtpStartResponse = withContext(Dispatchers.IO) {
        val reqBody = json.encodeToString(AuthOtpStartRequest(phone = phone, channel = channel))
        executePost("/api/v1/auth/signup/start", reqBody)
    }

    suspend fun verifySignupPhone(flowId: String, code: String): AuthPhoneVerifyResponse = withContext(Dispatchers.IO) {
        val reqBody = json.encodeToString(AuthPhoneVerifyRequest(flowId = flowId, code = code))
        executePost("/api/v1/auth/signup/verify-phone", reqBody)
    }

    suspend fun sendSignupEmail(flowId: String, email: String): AuthEmailSendResponse = withContext(Dispatchers.IO) {
        val reqBody = json.encodeToString(AuthEmailSendRequest(flowId = flowId, email = email))
        executePost("/api/v1/auth/signup/send-email", reqBody)
    }

    suspend fun verifySignupEmail(flowId: String, code: String): AuthSessionResponse = withContext(Dispatchers.IO) {
        val reqBody = json.encodeToString(AuthEmailVerifyRequest(flowId = flowId, code = code))
        val res: AuthSessionResponse = executePost("/api/v1/auth/signup/verify-email", reqBody)
        if (!res.token.isNullOrBlank()) {
            tokenStore.saveSession(
                authToken = res.token,
                userId = res.uid,
                phoneNumber = res.user?.phoneMasked,
                email = res.user?.emailMasked
            )
        }
        res
    }

    suspend fun startSignin(phone: String, channel: String = "WHATSAPP"): AuthOtpStartResponse = withContext(Dispatchers.IO) {
        val reqBody = json.encodeToString(AuthOtpStartRequest(phone = phone, channel = channel))
        executePost("/api/v1/auth/signin/start", reqBody)
    }

    suspend fun verifySignin(flowId: String, code: String): AuthSessionResponse = withContext(Dispatchers.IO) {
        val reqBody = json.encodeToString(AuthSigninVerifyRequest(flowId = flowId, code = code))
        val res: AuthSessionResponse = executePost("/api/v1/auth/signin/verify", reqBody)
        if (!res.token.isNullOrBlank()) {
            tokenStore.saveSession(
                authToken = res.token,
                userId = res.uid,
                phoneNumber = res.user?.phoneMasked,
                email = res.user?.emailMasked
            )
        }
        res
    }

    suspend fun signOut(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(sanitizeUrl("/api/v1/auth/signout"))
                .post("{}".toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(request).execute().use { response ->
                tokenStore.clearSession()
                response.isSuccessful
            }
        } catch (e: Exception) {
            SafeLogger.w("ApiClient", "Sign-out network call encountered error: ${e.message}")
            tokenStore.clearSession()
            true
        }
    }

    suspend fun getDevices(): DeviceListResponse = withContext(Dispatchers.IO) {
        executeGet("/api/v1/auth/devices")
    }

    suspend fun revokeDevice(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(sanitizeUrl("/api/v1/auth/devices/$deviceId"))
            .delete()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException(response.code, response.body?.string() ?: "Revoke device failed")
            }
            true
        }
    }

    // -------------------------------------------------------------------------
    // Sync Operations
    // -------------------------------------------------------------------------
    suspend fun pushSync(request: SyncPushRequest): SyncPushResponse = withContext(Dispatchers.IO) {
        val reqBody = json.encodeToString(request)
        executePost("/api/v1/sync/push", reqBody)
    }

    suspend fun pullSync(cursor: Long, limit: Int = 100): SyncPullResponse = withContext(Dispatchers.IO) {
        val url = sanitizeUrl("/api/v1/sync/pull").toHttpUrl().newBuilder()
            .addQueryParameter("cursor", cursor.toString())
            .addQueryParameter("limit", limit.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw ApiException(response.code, bodyString)
            }
            json.decodeFromString<SyncPullResponse>(bodyString)
        }
    }

    // -------------------------------------------------------------------------
    // Helper Execution Methods
    // -------------------------------------------------------------------------
    private inline fun <reified T> executePost(path: String, jsonPayload: String): T {
        val request = Request.Builder()
            .url(sanitizeUrl(path))
            .post(jsonPayload.toRequestBody(jsonMediaType))
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw ApiException(response.code, bodyString)
            }
            json.decodeFromString(bodyString)
        }
    }

    private inline fun <reified T> executeGet(path: String): T {
        val request = Request.Builder()
            .url(sanitizeUrl(path))
            .get()
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw ApiException(response.code, bodyString)
            }
            json.decodeFromString(bodyString)
        }
    }
}
