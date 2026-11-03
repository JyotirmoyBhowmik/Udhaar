package com.jyotirmoyb.udhaar.backup

import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import com.jyotirmoyb.udhaar.BuildConfig
import com.jyotirmoyb.udhaar.util.SafeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

enum class DriveErrorType {
    NO_NETWORK,
    EXPIRED_SIGN_IN,
    QUOTA_EXCEEDED,
    NOT_FOUND,
    UNKNOWN
}

enum class DriveBackupErrorCode {
    NONE,
    NOT_CONNECTED,
    NO_PASSPHRASE,
    AUTH_EXPIRED,
    NO_NETWORK,
    QUOTA,
    OTHER
}

sealed class DriveTokenOutcome {
    data class TokenReady(
        val accessToken: String,
        val accountEmail: String? = null
    ) : DriveTokenOutcome()

    data class NeedsResolution(
        val pendingIntent: PendingIntent?,
        val message: String = "Google Drive authorization requires permission. Tap Reconnect Google Drive."
    ) : DriveTokenOutcome()

    data class Failed(
        val errorType: DriveErrorType,
        val errorCode: String,
        val message: String,
        val cause: Throwable? = null,
        val statusCode: Int? = null
    ) : DriveTokenOutcome()
}

interface DriveTokenProvider {
    suspend fun getFreshAccessToken(forceRefreshAfter401: String? = null): DriveTokenOutcome
}

class GmsDriveTokenProvider(
    private val context: Context
) : DriveTokenProvider {

    override suspend fun getFreshAccessToken(forceRefreshAfter401: String?): DriveTokenOutcome =
        withContext(Dispatchers.IO) {
            try {
                if (!forceRefreshAfter401.isNullOrBlank()) {
                    runCatching {
                        GoogleAuthUtil.clearToken(context.applicationContext, forceRefreshAfter401)
                    }.onFailure { clearErr ->
                        SafeLogger.w("GmsDriveTokenProvider", "Failed clearing expired Drive token", clearErr)
                    }
                }

                val authRequest = AuthorizationRequest.builder()
                    .setRequestedScopes(listOf(Scope(DriveServiceHelper.DRIVE_APPDATA_SCOPE)))
                    .build()

                val authResult = Identity.getAuthorizationClient(context.applicationContext)
                    .authorize(authRequest)
                    .await()

                if (authResult.hasResolution()) {
                    return@withContext DriveTokenOutcome.NeedsResolution(
                        pendingIntent = authResult.pendingIntent,
                        message = "Google Drive access requires authorization. Please reconnect Google Drive."
                    )
                }

                val token = authResult.accessToken?.trim().orEmpty()
                if (token.isEmpty()) {
                    return@withContext DriveTokenOutcome.Failed(
                        errorType = DriveErrorType.EXPIRED_SIGN_IN,
                        errorCode = "EMPTY_ACCESS_TOKEN",
                        message = "Google Drive authorization returned an empty access token. Please reconnect."
                    )
                }

                val accountEmail = runCatching { authResult.toGoogleSignInAccount()?.email }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: runCatching { FirebaseAuth.getInstance().currentUser?.email }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }

                DriveTokenOutcome.TokenReady(
                    accessToken = token,
                    accountEmail = accountEmail
                )
            } catch (apiEx: ApiException) {
                val code = apiEx.statusCode
                val statusName = CommonStatusCodes.getStatusCodeString(code)
                val mappedType = when (code) {
                    CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT -> DriveErrorType.NO_NETWORK
                    CommonStatusCodes.SIGN_IN_REQUIRED,
                    CommonStatusCodes.RESOLUTION_REQUIRED,
                    CommonStatusCodes.CANCELED,
                    12500, 12501 -> DriveErrorType.EXPIRED_SIGN_IN
                    else -> DriveErrorType.UNKNOWN
                }
                SafeLogger.w("GmsDriveTokenProvider", "Drive authorize failed with ApiException code=$code ($statusName)", apiEx)
                DriveTokenOutcome.Failed(
                    errorType = mappedType,
                    errorCode = "GMS_$code ($statusName)",
                    message = "Google Authorization failed [code $code - $statusName]: ${apiEx.statusMessage ?: apiEx.localizedMessage ?: "Unknown error"}",
                    cause = apiEx,
                    statusCode = code
                )
            } catch (ioEx: IOException) {
                SafeLogger.w("GmsDriveTokenProvider", "Drive authorize network failure", ioEx)
                DriveTokenOutcome.Failed(
                    errorType = DriveErrorType.NO_NETWORK,
                    errorCode = "IO_ERROR",
                    message = "Network error while authorizing Google Drive: ${ioEx.localizedMessage ?: "No internet connection"}",
                    cause = ioEx
                )
            } catch (e: Exception) {
                SafeLogger.w("GmsDriveTokenProvider", "Drive authorize unexpected failure", e)
                DriveTokenOutcome.Failed(
                    errorType = DriveErrorType.UNKNOWN,
                    errorCode = e.javaClass.simpleName,
                    message = "Failed to authorize Google Drive: ${e.localizedMessage ?: "Unexpected error"}",
                    cause = e
                )
            }
        }
}

sealed class DriveResult<out T> {
    data class Success<T>(val data: T) : DriveResult<T>()
    data class Error(
        val errorType: DriveErrorType,
        val message: String,
        val cause: Throwable? = null,
        val httpCode: Int? = null,
        val apiReason: String = "",
        val errorCode: String = "",
        val needsReconnect: Boolean = false,
        val resolutionPendingIntent: PendingIntent? = null
    ) : DriveResult<Nothing>()
}

data class DriveRemoteBackup(
    val fileId: String,
    val fileName: String,
    val sizeBytes: Long,
    val createdTimeMillis: Long,
    val preview: BackupPreviewSummary?
)

data class DriveConnectionTestReport(
    val passed: Boolean,
    val authStepPassed: Boolean,
    val apiStepPassed: Boolean,
    val failedStep: String? = null,
    val errorCode: String = "",
    val errorMessage: String = "",
    val plainLanguageFix: String = "",
    val technicalDetails: String = "",
    val connectedEmail: String? = null,
    val visibleBackupCount: Int = 0,
    val needsReconnect: Boolean = false,
    val resolutionPendingIntent: PendingIntent? = null
)

open class DriveServiceHelper(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val tokenProvider: DriveTokenProvider? = null,
    private val context: Context? = null
) {
    constructor(context: Context) : this(
        tokenProvider = GmsDriveTokenProvider(context.applicationContext),
        context = context.applicationContext
    )

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val DRIVE_FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_ABOUT_ENDPOINT = "https://www.googleapis.com/drive/v3/about?fields=user(emailAddress,displayName),storageQuota"
        private const val DRIVE_UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
    }

    /**
     * Acquires a fresh token via [DriveTokenProvider] (`Identity.getAuthorizationClient(context).authorize()`),
     * executes [block], and if HTTP 401 is returned, refreshes the token once and retries [block] once.
     */
    protected open suspend fun <T> executeWithFreshToken(
        fallbackToken: String? = null,
        block: suspend (accessToken: String) -> DriveResult<T>
    ): DriveResult<T> {
        val provider = tokenProvider
        if (provider == null) {
            val token = fallbackToken?.trim().orEmpty()
            if (token.isEmpty()) {
                return DriveResult.Error(
                    errorType = DriveErrorType.EXPIRED_SIGN_IN,
                    message = "Google Drive is not authorized. Please reconnect Google Drive.",
                    errorCode = "NO_TOKEN_PROVIDER",
                    needsReconnect = true
                )
            }
            return block(token)
        }

        return when (val initialOutcome = provider.getFreshAccessToken(forceRefreshAfter401 = null)) {
            is DriveTokenOutcome.NeedsResolution -> DriveResult.Error(
                errorType = DriveErrorType.EXPIRED_SIGN_IN,
                message = initialOutcome.message,
                errorCode = "RESOLUTION_REQUIRED",
                needsReconnect = true,
                resolutionPendingIntent = initialOutcome.pendingIntent
            )
            is DriveTokenOutcome.Failed -> DriveResult.Error(
                errorType = initialOutcome.errorType,
                message = initialOutcome.message,
                cause = initialOutcome.cause,
                errorCode = initialOutcome.errorCode,
                needsReconnect = initialOutcome.errorType == DriveErrorType.EXPIRED_SIGN_IN
            )
            is DriveTokenOutcome.TokenReady -> {
                val firstResult = block(initialOutcome.accessToken)
                if (firstResult is DriveResult.Error && firstResult.httpCode == 401) {
                    // Refresh once on HTTP 401 and retry once
                    when (val refreshedOutcome = provider.getFreshAccessToken(forceRefreshAfter401 = initialOutcome.accessToken)) {
                        is DriveTokenOutcome.TokenReady -> {
                            val retryResult = block(refreshedOutcome.accessToken)
                            if (retryResult is DriveResult.Error && retryResult.httpCode == 401) {
                                retryResult.copy(
                                    errorType = DriveErrorType.EXPIRED_SIGN_IN,
                                    message = "Google Drive session expired after token refresh (HTTP 401). Please tap Reconnect Google Drive.",
                                    errorCode = "HTTP_401_AFTER_RETRY",
                                    needsReconnect = true
                                )
                            } else {
                                retryResult
                            }
                        }
                        is DriveTokenOutcome.NeedsResolution -> DriveResult.Error(
                            errorType = DriveErrorType.EXPIRED_SIGN_IN,
                            message = refreshedOutcome.message,
                            httpCode = 401,
                            errorCode = "RESOLUTION_REQUIRED_AFTER_401",
                            needsReconnect = true,
                            resolutionPendingIntent = refreshedOutcome.pendingIntent
                        )
                        is DriveTokenOutcome.Failed -> DriveResult.Error(
                            errorType = DriveErrorType.EXPIRED_SIGN_IN,
                            message = refreshedOutcome.message,
                            cause = refreshedOutcome.cause,
                            httpCode = 401,
                            errorCode = refreshedOutcome.errorCode,
                            needsReconnect = true
                        )
                    }
                } else {
                    firstResult
                }
            }
        }
    }

    open suspend fun listBackups(accessToken: String = ""): DriveResult<List<DriveRemoteBackup>> =
        executeWithFreshToken(fallbackToken = accessToken) { freshToken ->
            listBackupsWithToken(freshToken)
        }

    protected open suspend fun listBackupsWithToken(accessToken: String): DriveResult<List<DriveRemoteBackup>> =
        withContext(Dispatchers.IO) {
            val url = "$DRIVE_FILES_ENDPOINT?spaces=appDataFolder&fields=files(id,name,size,createdTime,description)&orderBy=createdTime%20desc"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val root = JSONObject(body.ifBlank { "{}" })
                        val filesArr = root.optJSONArray("files") ?: JSONArray()
                        val list = mutableListOf<DriveRemoteBackup>()
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                        for (i in 0 until filesArr.length()) {
                            val f = filesArr.getJSONObject(i)
                            val name = f.optString("name")
                            if (!name.endsWith(".udhaar") && !name.contains("backup")) continue
                            val id = f.optString("id")
                            val size = f.optLong("size", 0L)
                            val createdStr = f.optString("createdTime")
                            val createdTime = runCatching {
                                dateFormat.parse(createdStr)?.time
                            }.getOrNull() ?: System.currentTimeMillis()

                            val desc = f.optString("description")
                            val preview = if (desc.isNotBlank()) {
                                runCatching {
                                    val dj = JSONObject(desc)
                                    BackupPreviewSummary(
                                        partiesCount = dj.optInt("partiesCount", 0),
                                        transactionsCount = dj.optInt("transactionsCount", 0),
                                        paymentsCount = dj.optInt("paymentsCount", 0),
                                        attachmentsCount = dj.optInt("attachmentsCount", 0),
                                        appVersion = dj.optString("appVersion", BuildConfig.VERSION_NAME),
                                        createdAtMillis = dj.optLong("createdAtMillis", createdTime)
                                    )
                                }.getOrNull()
                            } else null

                            list.add(
                                DriveRemoteBackup(
                                    fileId = id,
                                    fileName = name,
                                    sizeBytes = size,
                                    createdTimeMillis = createdTime,
                                    preview = preview
                                )
                            )
                        }
                        DriveResult.Success(list)
                    } else {
                        parseDriveHttpError(response.code, response.message, body, "Failed to list Google Drive backups")
                    }
                }
            } catch (e: Exception) {
                handleException(e)
            }
        }

    open suspend fun uploadBackup(
        fileName: String,
        encryptedBytes: ByteArray,
        preview: BackupPreviewSummary
    ): DriveResult<DriveRemoteBackup> = uploadBackup(
        accessToken = "",
        fileName = fileName,
        encryptedBytes = encryptedBytes,
        preview = preview
    )

    open suspend fun uploadBackup(
        accessToken: String,
        fileName: String,
        encryptedBytes: ByteArray,
        preview: BackupPreviewSummary
    ): DriveResult<DriveRemoteBackup> = executeWithFreshToken(fallbackToken = accessToken) { freshToken ->
        uploadBackupWithToken(freshToken, fileName, encryptedBytes, preview)
    }

    protected open suspend fun uploadBackupWithToken(
        accessToken: String,
        fileName: String,
        encryptedBytes: ByteArray,
        preview: BackupPreviewSummary
    ): DriveResult<DriveRemoteBackup> = withContext(Dispatchers.IO) {
        val boundary = "==UdhaarDriveBoundary${System.currentTimeMillis()}=="
        val descriptionJson = JSONObject().apply {
            put("partiesCount", preview.partiesCount)
            put("transactionsCount", preview.transactionsCount)
            put("paymentsCount", preview.paymentsCount)
            put("attachmentsCount", preview.attachmentsCount)
            put("appVersion", preview.appVersion)
            put("createdAtMillis", preview.createdAtMillis)
        }.toString()

        val metadataJson = JSONObject().apply {
            put("name", fileName)
            put("parents", JSONArray().apply { put("appDataFolder") })
            put("description", descriptionJson)
        }.toString()

        val headerPart = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadataJson).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/octet-stream\r\n\r\n")
        }.toByteArray(Charsets.UTF_8)

        val footerPart = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val combined = ByteArray(headerPart.size + encryptedBytes.size + footerPart.size)
        System.arraycopy(headerPart, 0, combined, 0, headerPart.size)
        System.arraycopy(encryptedBytes, 0, combined, headerPart.size, encryptedBytes.size)
        System.arraycopy(footerPart, 0, combined, headerPart.size + encryptedBytes.size, footerPart.size)

        val requestBody = combined.toRequestBody("multipart/related; boundary=$boundary".toMediaType())
        val request = Request.Builder()
            .url(DRIVE_UPLOAD_ENDPOINT)
            .addHeader("Authorization", "Bearer $accessToken")
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(body.ifBlank { "{}" })
                    val id = json.optString("id")
                    val name = json.optString("name", fileName)
                    val size = json.optLong("size", encryptedBytes.size.toLong())
                    DriveResult.Success(
                        DriveRemoteBackup(
                            fileId = id,
                            fileName = name,
                            sizeBytes = size,
                            createdTimeMillis = System.currentTimeMillis(),
                            preview = preview
                        )
                    )
                } else {
                    parseDriveHttpError(response.code, response.message, body, "Failed to upload backup to Google Drive")
                }
            }
        } catch (e: Exception) {
            handleException(e)
        }
    }

    open suspend fun downloadBackup(fileId: String): DriveResult<ByteArray> =
        downloadBackup(accessToken = "", fileId = fileId)

    open suspend fun downloadBackup(accessToken: String, fileId: String): DriveResult<ByteArray> =
        executeWithFreshToken(fallbackToken = accessToken) { freshToken ->
            downloadBackupWithToken(freshToken, fileId)
        }

    protected open suspend fun downloadBackupWithToken(
        accessToken: String,
        fileId: String
    ): DriveResult<ByteArray> = withContext(Dispatchers.IO) {
        val url = "$DRIVE_FILES_ENDPOINT/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        DriveResult.Success(bytes)
                    } else {
                        DriveResult.Error(DriveErrorType.UNKNOWN, "Downloaded empty backup file.")
                    }
                } else {
                    val errBody = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                    parseDriveHttpError(response.code, response.message, errBody, "Failed to download backup")
                }
            }
        } catch (e: Exception) {
            handleException(e)
        }
    }

    open suspend fun deleteBackup(fileId: String): DriveResult<Unit> =
        deleteBackup(accessToken = "", fileId = fileId)

    open suspend fun deleteBackup(accessToken: String, fileId: String): DriveResult<Unit> =
        executeWithFreshToken(fallbackToken = accessToken) { freshToken ->
            deleteBackupWithToken(freshToken, fileId)
        }

    protected open suspend fun deleteBackupWithToken(
        accessToken: String,
        fileId: String
    ): DriveResult<Unit> = withContext(Dispatchers.IO) {
        val url = "$DRIVE_FILES_ENDPOINT/$fileId"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .delete()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful || response.code == 404) {
                    DriveResult.Success(Unit)
                } else {
                    val errBody = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                    parseDriveHttpError(response.code, response.message, errBody, "Failed to delete backup")
                }
            }
        } catch (e: Exception) {
            handleException(e)
        }
    }

    /**
     * Deletes all backup files found in the user's Google Drive `appDataFolder`.
     * Returns the count of successfully deleted backup files.
     */
    open suspend fun deleteAllBackups(): DriveResult<Int> =
        executeWithFreshToken { freshToken ->
            deleteAllBackupsWithToken(freshToken)
        }

    protected open suspend fun deleteAllBackupsWithToken(accessToken: String): DriveResult<Int> {
        val listRes = listBackupsWithToken(accessToken)
        if (listRes is DriveResult.Error) {
            return listRes
        }
        val files = (listRes as DriveResult.Success).data
        var deletedCount = 0
        for (file in files) {
            val del = deleteBackupWithToken(accessToken, file.fileId)
            if (del is DriveResult.Success) {
                deletedCount++
            }
        }
        return DriveResult.Success(deletedCount)
    }

    /**
     * Resolves the signed-in Google account email properly via Drive `about` endpoint,
     * `AuthorizationResult`, or Firebase Auth.
     */
    open suspend fun fetchConnectedAccountEmail(): String? {
        val provider = tokenProvider ?: return runCatching { FirebaseAuth.getInstance().currentUser?.email }.getOrNull()
        val outcome = provider.getFreshAccessToken(forceRefreshAfter401 = null)
        if (outcome is DriveTokenOutcome.TokenReady) {
            val fromAbout = fetchEmailFromDriveAbout(outcome.accessToken)
            if (!fromAbout.isNullOrBlank()) {
                return fromAbout
            }
            if (!outcome.accountEmail.isNullOrBlank()) {
                return outcome.accountEmail
            }
        }
        return runCatching { FirebaseAuth.getInstance().currentUser?.email }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    protected open suspend fun fetchEmailFromDriveAbout(accessToken: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(DRIVE_ABOUT_ENDPOINT)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                val userObj = JSONObject(body.ifBlank { "{}" }).optJSONObject("user")
                userObj?.optString("emailAddress")?.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    /**
     * Diagnostic connection test:
     * 1. Checks `Identity.getAuthorizationClient(context).authorize()` with `drive.appdata`.
     * 2. Calls the Google Drive API (`files.list` on `appDataFolder`) once (with 401 refresh-once retry).
     * 3. Reports exactly which step failed with the real error code and message.
     */
    open suspend fun runConnectionTest(): DriveConnectionTestReport {
        val appPkg = context?.packageName ?: "com.jyotirmoyb.udhaar"
        val provider = tokenProvider
            ?: return DriveConnectionTestReport(
                passed = false,
                authStepPassed = false,
                apiStepPassed = false,
                failedStep = "Step 1: Google Drive Authorization",
                errorCode = "NO_TOKEN_PROVIDER",
                errorMessage = "Drive token provider is not initialized.",
                plainLanguageFix = "Google Play Services Authorization Client is not available on this device.",
                technicalDetails = "DriveTokenProvider instance is null."
            )

        return when (val authOutcome = provider.getFreshAccessToken(forceRefreshAfter401 = null)) {
            is DriveTokenOutcome.NeedsResolution -> {
                val diagnosis = DriveConnectionErrorMapper.diagnose(
                    rawMessage = authOutcome.message,
                    packageName = appPkg
                )
                DriveConnectionTestReport(
                    passed = false,
                    authStepPassed = false,
                    apiStepPassed = false,
                    failedStep = "Step 1: Google Drive Authorization (authorize)",
                    errorCode = "RESOLUTION_REQUIRED",
                    errorMessage = authOutcome.message,
                    plainLanguageFix = diagnosis.plainFix,
                    technicalDetails = diagnosis.technicalDetails.ifBlank { "Google Drive permission requires interactive approval via StartIntentSenderForResult." },
                    needsReconnect = true,
                    resolutionPendingIntent = authOutcome.pendingIntent
                )
            }
            is DriveTokenOutcome.Failed -> {
                val diagnosis = DriveConnectionErrorMapper.diagnose(
                    statusCode = authOutcome.statusCode,
                    rawMessage = authOutcome.message,
                    packageName = appPkg
                )
                DriveConnectionTestReport(
                    passed = false,
                    authStepPassed = false,
                    apiStepPassed = false,
                    failedStep = "Step 1: Google Drive Authorization (authorize)",
                    errorCode = authOutcome.errorCode,
                    errorMessage = authOutcome.message,
                    plainLanguageFix = diagnosis.plainFix,
                    technicalDetails = diagnosis.technicalDetails.ifBlank { authOutcome.message },
                    needsReconnect = authOutcome.errorType == DriveErrorType.EXPIRED_SIGN_IN
                )
            }
            is DriveTokenOutcome.TokenReady -> {
                var tokenToUse = authOutcome.accessToken
                var listResult = listBackupsWithToken(tokenToUse)
                if (listResult is DriveResult.Error && listResult.httpCode == 401) {
                    val refreshed = provider.getFreshAccessToken(forceRefreshAfter401 = tokenToUse)
                    if (refreshed is DriveTokenOutcome.TokenReady) {
                        tokenToUse = refreshed.accessToken
                        listResult = listBackupsWithToken(tokenToUse)
                    } else if (refreshed is DriveTokenOutcome.NeedsResolution) {
                        val diag = DriveConnectionErrorMapper.diagnose(
                            httpCode = 401,
                            rawMessage = refreshed.message,
                            packageName = appPkg
                        )
                        return DriveConnectionTestReport(
                            passed = false,
                            authStepPassed = false,
                            apiStepPassed = false,
                            failedStep = "Step 1: Token Refresh After HTTP 401",
                            errorCode = "HTTP_401_RESOLUTION_REQUIRED",
                            errorMessage = refreshed.message,
                            plainLanguageFix = diag.plainFix,
                            technicalDetails = diag.technicalDetails,
                            needsReconnect = true,
                            resolutionPendingIntent = refreshed.pendingIntent
                        )
                    }
                }

                when (listResult) {
                    is DriveResult.Success -> {
                        val resolvedEmail = fetchEmailFromDriveAbout(tokenToUse)
                            ?: authOutcome.accountEmail
                            ?: runCatching { FirebaseAuth.getInstance().currentUser?.email }.getOrNull()
                        DriveConnectionTestReport(
                            passed = true,
                            authStepPassed = true,
                            apiStepPassed = true,
                            failedStep = null,
                            errorCode = "HTTP 200 OK",
                            errorMessage = "Step 1 (Authorization) & Step 2 (Drive appDataFolder API) succeeded. ${listResult.data.size} backup file(s) found.",
                            plainLanguageFix = "Connection test passed! Your Google Drive AppData folder is connected and ready for encrypted backups.",
                            technicalDetails = "Step 1: Authorization passed with scope https://www.googleapis.com/auth/drive.appdata\nStep 2: Google Drive API call (files.list appDataFolder) returned HTTP 200 OK (${listResult.data.size} backup files).",
                            connectedEmail = resolvedEmail,
                            visibleBackupCount = listResult.data.size,
                            needsReconnect = false
                        )
                    }
                    is DriveResult.Error -> {
                        val codeStr = buildString {
                            if (listResult.httpCode != null) {
                                append("HTTP ").append(listResult.httpCode)
                            } else if (listResult.errorCode.isNotBlank()) {
                                append(listResult.errorCode)
                            } else {
                                append(listResult.errorType.name)
                            }
                            if (listResult.apiReason.isNotBlank()) {
                                append(" (").append(listResult.apiReason).append(")")
                            }
                        }
                        val diag = DriveConnectionErrorMapper.diagnose(
                            rawMessage = listResult.message,
                            httpCode = listResult.httpCode,
                            apiReason = listResult.apiReason,
                            packageName = appPkg
                        )
                        DriveConnectionTestReport(
                            passed = false,
                            authStepPassed = true,
                            apiStepPassed = false,
                            failedStep = "Step 2: Google Drive API Call (files.list appDataFolder)",
                            errorCode = codeStr,
                            errorMessage = listResult.message,
                            plainLanguageFix = diag.plainFix,
                            technicalDetails = diag.technicalDetails.ifBlank { listResult.message },
                            connectedEmail = authOutcome.accountEmail,
                            needsReconnect = listResult.needsReconnect || listResult.httpCode == 401,
                            resolutionPendingIntent = listResult.resolutionPendingIntent
                        )
                    }
                }
            }
        }
    }

    protected fun parseDriveHttpError(
        code: Int,
        httpMessage: String,
        body: String,
        prefix: String
    ): DriveResult.Error {
        var apiMessage = ""
        var apiReason = ""
        var apiStatus = ""
        if (body.isNotBlank()) {
            runCatching {
                val errObj = JSONObject(body).optJSONObject("error")
                if (errObj != null) {
                    apiMessage = errObj.optString("message", "")
                    apiStatus = errObj.optString("status", "")
                    val errorsArr = errObj.optJSONArray("errors")
                    if (errorsArr != null && errorsArr.length() > 0) {
                        apiReason = errorsArr.getJSONObject(0).optString("reason", "")
                    }
                }
            }
        }
        val effectiveReason = apiReason.ifBlank { apiStatus }
        val detail = apiMessage.ifBlank { httpMessage.ifBlank { "HTTP $code" } }

        val isQuota = code == 507 || (
            code == 403 && (
                effectiveReason.contains("quota", ignoreCase = true) ||
                    effectiveReason.contains("storageQuotaExceeded", ignoreCase = true) ||
                    detail.contains("quota", ignoreCase = true)
                )
            )

        return when {
            code == 401 -> DriveResult.Error(
                errorType = DriveErrorType.EXPIRED_SIGN_IN,
                message = "Google Drive authorization expired (HTTP 401: $detail). Please reconnect Google Drive.",
                httpCode = 401,
                apiReason = effectiveReason.ifBlank { "authError" },
                errorCode = "HTTP_401",
                needsReconnect = true
            )
            isQuota -> DriveResult.Error(
                errorType = DriveErrorType.QUOTA_EXCEEDED,
                message = "Google Drive storage quota exceeded (HTTP $code: $detail).",
                httpCode = code,
                apiReason = effectiveReason.ifBlank { "storageQuotaExceeded" },
                errorCode = "HTTP_$code"
            )
            code == 404 -> DriveResult.Error(
                errorType = DriveErrorType.NOT_FOUND,
                message = "The requested backup file was not found on Google Drive (HTTP 404).",
                httpCode = 404,
                apiReason = effectiveReason.ifBlank { "notFound" },
                errorCode = "HTTP_404"
            )
            else -> DriveResult.Error(
                errorType = DriveErrorType.UNKNOWN,
                message = "$prefix (HTTP $code${if (effectiveReason.isNotBlank()) " $effectiveReason" else ""}): $detail",
                httpCode = code,
                apiReason = effectiveReason,
                errorCode = "HTTP_$code"
            )
        }
    }

    private fun <T> handleException(e: Exception): DriveResult<T> {
        return when (e) {
            is UnknownHostException, is SocketTimeoutException, is ConnectException -> {
                DriveResult.Error(
                    errorType = DriveErrorType.NO_NETWORK,
                    message = "No internet connection (${e.javaClass.simpleName}). Please check your Wi-Fi or mobile data.",
                    cause = e,
                    errorCode = e.javaClass.simpleName
                )
            }
            is IOException -> {
                DriveResult.Error(
                    errorType = DriveErrorType.NO_NETWORK,
                    message = "Network I/O error: ${e.localizedMessage ?: e.javaClass.simpleName}",
                    cause = e,
                    errorCode = "IO_ERROR"
                )
            }
            else -> {
                DriveResult.Error(
                    errorType = DriveErrorType.UNKNOWN,
                    message = "Unexpected error (${e.javaClass.simpleName}): ${e.localizedMessage ?: "Unknown"}",
                    cause = e,
                    errorCode = e.javaClass.simpleName
                )
            }
        }
    }
}
