package com.jyotirmoyb.udhaar.backup

import kotlinx.coroutines.Dispatchers
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
import java.util.Date
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

sealed class DriveResult<out T> {
    data class Success<T>(val data: T) : DriveResult<T>()
    data class Error(val errorType: DriveErrorType, val message: String, val cause: Throwable? = null) : DriveResult<Nothing>()
}

data class DriveRemoteBackup(
    val fileId: String,
    val fileName: String,
    val sizeBytes: Long,
    val createdTimeMillis: Long,
    val preview: BackupPreviewSummary?
)

class DriveServiceHelper(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val DRIVE_FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
    }

    suspend fun listBackups(accessToken: String): DriveResult<List<DriveRemoteBackup>> = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) {
            return@withContext DriveResult.Error(DriveErrorType.EXPIRED_SIGN_IN, "Google Drive account is not signed in.")
        }
        val url = "$DRIVE_FILES_ENDPOINT?spaces=appDataFolder&fields=files(id,name,size,createdTime,description)&orderBy=createdTime%20desc"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val body = response.body?.string().orEmpty()
                        val root = JSONObject(body)
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
                                        appVersion = dj.optString("appVersion", "7.0"),
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
                    }
                    response.code == 401 -> DriveResult.Error(
                        DriveErrorType.EXPIRED_SIGN_IN,
                        "Google Drive authorization has expired. Please sign in again."
                    )
                    response.code in listOf(403, 507) -> DriveResult.Error(
                        DriveErrorType.QUOTA_EXCEEDED,
                        "Google Drive storage quota exceeded or API limit reached."
                    )
                    else -> DriveResult.Error(
                        DriveErrorType.UNKNOWN,
                        "Google Drive error ${response.code}: ${response.message}"
                    )
                }
            }
        } catch (e: Exception) {
            handleException(e)
        }
    }

    suspend fun uploadBackup(
        accessToken: String,
        fileName: String,
        encryptedBytes: ByteArray,
        preview: BackupPreviewSummary
    ): DriveResult<DriveRemoteBackup> = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) {
            return@withContext DriveResult.Error(DriveErrorType.EXPIRED_SIGN_IN, "Google Drive account is not signed in.")
        }
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
                when {
                    response.isSuccessful -> {
                        val body = response.body?.string().orEmpty()
                        val json = JSONObject(body)
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
                    }
                    response.code == 401 -> DriveResult.Error(
                        DriveErrorType.EXPIRED_SIGN_IN,
                        "Google Drive authorization has expired. Please sign in again."
                    )
                    response.code in listOf(403, 507) -> DriveResult.Error(
                        DriveErrorType.QUOTA_EXCEEDED,
                        "Google Drive storage quota exceeded or API limit reached."
                    )
                    else -> DriveResult.Error(
                        DriveErrorType.UNKNOWN,
                        "Failed to upload backup to Google Drive (HTTP ${response.code}): ${response.message}"
                    )
                }
            }
        } catch (e: Exception) {
            handleException(e)
        }
    }

    suspend fun downloadBackup(accessToken: String, fileId: String): DriveResult<ByteArray> = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) {
            return@withContext DriveResult.Error(DriveErrorType.EXPIRED_SIGN_IN, "Google Drive account is not signed in.")
        }
        val url = "$DRIVE_FILES_ENDPOINT/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val bytes = response.body?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            DriveResult.Success(bytes)
                        } else {
                            DriveResult.Error(DriveErrorType.UNKNOWN, "Downloaded empty backup file.")
                        }
                    }
                    response.code == 401 -> DriveResult.Error(
                        DriveErrorType.EXPIRED_SIGN_IN,
                        "Google Drive authorization has expired. Please sign in again."
                    )
                    response.code == 404 -> DriveResult.Error(
                        DriveErrorType.NOT_FOUND,
                        "The requested backup file was not found on Google Drive."
                    )
                    else -> DriveResult.Error(
                        DriveErrorType.UNKNOWN,
                        "Failed to download backup (HTTP ${response.code}): ${response.message}"
                    )
                }
            }
        } catch (e: Exception) {
            handleException(e)
        }
    }

    suspend fun deleteBackup(accessToken: String, fileId: String): DriveResult<Unit> = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) {
            return@withContext DriveResult.Error(DriveErrorType.EXPIRED_SIGN_IN, "Google Drive account is not signed in.")
        }
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
                } else if (response.code == 401) {
                    DriveResult.Error(DriveErrorType.EXPIRED_SIGN_IN, "Google Drive authorization has expired.")
                } else {
                    DriveResult.Error(DriveErrorType.UNKNOWN, "Failed to delete backup (HTTP ${response.code}).")
                }
            }
        } catch (e: Exception) {
            handleException(e)
        }
    }

    private fun <T> handleException(e: Exception): DriveResult<T> {
        return when (e) {
            is UnknownHostException, is SocketTimeoutException, is ConnectException -> {
                DriveResult.Error(
                    DriveErrorType.NO_NETWORK,
                    "No internet connection. Please verify your Wi-Fi or mobile data and try again.",
                    e
                )
            }
            is IOException -> {
                DriveResult.Error(DriveErrorType.UNKNOWN, "Network I/O error: ${e.localizedMessage}", e)
            }
            else -> {
                DriveResult.Error(DriveErrorType.UNKNOWN, "Unexpected error: ${e.localizedMessage}", e)
            }
        }
    }
}
