package com.jyotirmoyb.udhaar.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.jyotirmoyb.udhaar.data.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

data class ProcessedAttachment(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localPath: String,
    val sha256: String
)

sealed class AttachmentProcessResult {
    data class Success(val attachment: ProcessedAttachment) : AttachmentProcessResult()
    data class Error(val message: String) : AttachmentProcessResult()
}

object AttachmentManager {

    const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024L // 10 MB limit
    private const val MAX_IMAGE_DIMENSION = 2048
    private const val JPEG_COMPRESSION_QUALITY = 85

    fun getAttachmentsDir(context: Context): File {
        val dir = File(context.filesDir, "attachments")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun createCameraTempFileUri(context: Context): Pair<Uri, File> = getCameraTempFile(context)

    fun getCameraTempFile(context: Context): Pair<Uri, File> {
        val cacheDir = File(context.cacheDir, "attachments")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val tempFile = File(cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
        return Pair(uri, tempFile)
    }

    fun processCapturedImage(
        context: Context,
        sourceUri: Uri,
        transactionId: Long,
        paymentId: Long? = null,
        addedBy: String = "You"
    ): Attachment? {
        return try {
            val contentResolver = context.contentResolver
            val originalName = queryDisplayName(context, sourceUri) ?: "photo_${System.currentTimeMillis()}.jpg"
            val inputStream = contentResolver.openInputStream(sourceUri) ?: return null
            val bytes = inputStream.use { it.readBytes() }
            if (bytes.isEmpty()) return null

            val bitmap = decodeSampledBitmapFromBytes(bytes, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION) ?: return null
            val rotated = fixOrientationIfPossible(bytes, bitmap)
            val outStream = ByteArrayOutputStream()
            rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_COMPRESSION_QUALITY, outStream)
            val compressedBytes = outStream.toByteArray()
            if (compressedBytes.size > MAX_FILE_SIZE_BYTES) return null

            val sha256 = calculateSha256(compressedBytes)
            val safeBaseName = originalName.substringBeforeLast(".").replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
            val destFile = File(getAttachmentsDir(context), "img_${System.currentTimeMillis()}_${safeBaseName}.jpg")
            destFile.writeBytes(compressedBytes)

            Attachment(
                id = 0L,
                transactionId = transactionId,
                paymentId = paymentId,
                fileName = originalName,
                mimeType = "image/jpeg",
                sizeBytes = destFile.length(),
                localPath = destFile.absolutePath,
                remoteUrl = null,
                addedAtMillis = System.currentTimeMillis(),
                addedBy = addedBy,
                sha256 = sha256
            )
        } catch (_: Exception) {
            null
        }
    }

    fun copyDocumentToStorage(
        context: Context,
        sourceUri: Uri,
        transactionId: Long,
        paymentId: Long? = null,
        addedBy: String = "You"
    ): Attachment? {
        return try {
            val contentResolver = context.contentResolver
            val originalName = queryDisplayName(context, sourceUri) ?: "doc_${System.currentTimeMillis()}"
            val mimeType = contentResolver.getType(sourceUri) ?: inferMimeType(originalName)

            if (mimeType.startsWith("image/")) {
                return processCapturedImage(context, sourceUri, transactionId, paymentId, addedBy)
            }

            val inputStream = contentResolver.openInputStream(sourceUri) ?: return null
            val bytes = inputStream.use { it.readBytes() }
            if (bytes.size > MAX_FILE_SIZE_BYTES) return null

            val sha256 = calculateSha256(bytes)
            val safeName = originalName.replace(Regex("[^a-zA-Z0-9_.-]"), "_").take(40)
            val destFile = File(getAttachmentsDir(context), "doc_${System.currentTimeMillis()}_${safeName}")
            destFile.writeBytes(bytes)

            Attachment(
                id = 0L,
                transactionId = transactionId,
                paymentId = paymentId,
                fileName = originalName,
                mimeType = mimeType,
                sizeBytes = destFile.length(),
                localPath = destFile.absolutePath,
                remoteUrl = null,
                addedAtMillis = System.currentTimeMillis(),
                addedBy = addedBy,
                sha256 = sha256
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun processAndSaveUri(
        context: Context,
        sourceUri: Uri,
        customName: String? = null
    ): AttachmentProcessResult = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val originalName = customName ?: queryDisplayName(context, sourceUri) ?: "document_${System.currentTimeMillis()}"
            val detectedMime = contentResolver.getType(sourceUri) ?: inferMimeType(originalName)

            val isImage = detectedMime.startsWith("image/")

            if (isImage) {
                // Compress and auto-crop/scale image
                val inputStream = contentResolver.openInputStream(sourceUri)
                    ?: return@withContext AttachmentProcessResult.Error("Could not open image file")

                val tempBytes = inputStream.use { it.readBytes() }
                if (tempBytes.size > MAX_FILE_SIZE_BYTES) {
                    // Try decoding and compressing down
                }

                val bitmap = decodeSampledBitmapFromBytes(tempBytes, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
                    ?: return@withContext AttachmentProcessResult.Error("Failed to decode image")

                val rotatedBitmap = fixOrientationIfPossible(tempBytes, bitmap)

                val outStream = ByteArrayOutputStream()
                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_COMPRESSION_QUALITY, outStream)
                val compressedBytes = outStream.toByteArray()

                if (compressedBytes.size > MAX_FILE_SIZE_BYTES) {
                    return@withContext AttachmentProcessResult.Error("Image exceeds 10 MB limit even after compression")
                }

                val sha256 = calculateSha256(compressedBytes)
                val cleanExt = if (originalName.contains(".")) {
                    originalName.substringAfterLast(".").lowercase()
                } else {
                    "jpg"
                }
                val safeBaseName = originalName.substringBeforeLast(".").replace(Regex("[^a-zA-Z0-9_-]"), "_").take(40)
                val destFileName = "img_${System.currentTimeMillis()}_${safeBaseName}.${cleanExt.ifBlank { "jpg" }}"
                val destFile = File(getAttachmentsDir(context), destFileName)
                destFile.writeBytes(compressedBytes)

                AttachmentProcessResult.Success(
                    ProcessedAttachment(
                        fileName = originalName,
                        mimeType = "image/jpeg",
                        sizeBytes = destFile.length(),
                        localPath = destFileName,
                        sha256 = sha256
                    )
                )
            } else {
                // Non-image document (PDF, TXT, DOC, etc.)
                val inputStream = contentResolver.openInputStream(sourceUri)
                    ?: return@withContext AttachmentProcessResult.Error("Could not open file")

                val bytes = inputStream.use { it.readBytes() }
                if (bytes.size > MAX_FILE_SIZE_BYTES) {
                    return@withContext AttachmentProcessResult.Error("File size exceeds 10 MB limit (${bytes.size / (1024 * 1024)} MB)")
                }

                val sha256 = calculateSha256(bytes)
                val safeName = originalName.replace(Regex("[^a-zA-Z0-9_.-]"), "_").take(50)
                val destFileName = "doc_${System.currentTimeMillis()}_$safeName"
                val destFile = File(getAttachmentsDir(context), destFileName)
                destFile.writeBytes(bytes)

                AttachmentProcessResult.Success(
                    ProcessedAttachment(
                        fileName = originalName,
                        mimeType = detectedMime,
                        sizeBytes = destFile.length(),
                        localPath = destFileName,
                        sha256 = sha256
                    )
                )
            }
        } catch (e: Exception) {
            AttachmentProcessResult.Error("Error processing attachment: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    suspend fun processCameraFile(
        context: Context,
        tempFile: File,
        customName: String? = null
    ): AttachmentProcessResult = withContext(Dispatchers.IO) {
        try {
            if (!tempFile.exists() || tempFile.length() == 0L) {
                return@withContext AttachmentProcessResult.Error("No photo was captured")
            }
            val bytes = tempFile.readBytes()
            val bitmap = decodeSampledBitmapFromBytes(bytes, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
                ?: return@withContext AttachmentProcessResult.Error("Could not decode captured photo")

            val rotated = fixOrientationIfPossible(bytes, bitmap)
            val outStream = ByteArrayOutputStream()
            rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_COMPRESSION_QUALITY, outStream)
            val compressedBytes = outStream.toByteArray()

            val sha256 = calculateSha256(compressedBytes)
            val baseName = customName ?: "camera_photo_${System.currentTimeMillis()}.jpg"
            val destFileName = "att_cam_${System.currentTimeMillis()}.jpg"
            val destFile = File(getAttachmentsDir(context), destFileName)
            destFile.writeBytes(compressedBytes)

            // Cleanup temp file
            runCatching { tempFile.delete() }

            AttachmentProcessResult.Success(
                ProcessedAttachment(
                    fileName = baseName,
                    mimeType = "image/jpeg",
                    sizeBytes = destFile.length(),
                    localPath = destFileName,
                    sha256 = sha256
                )
            )
        } catch (e: Exception) {
            AttachmentProcessResult.Error("Error saving camera photo: ${e.localizedMessage ?: "Unknown"}")
        }
    }

    fun getFileForAttachment(context: Context, localPath: String): File {
        val f = File(localPath)
        if (f.isAbsolute && f.exists()) return f
        return File(getAttachmentsDir(context), f.name)
    }

    fun getShareableUri(context: Context, localPath: String): Uri? {
        val file = getFileForAttachment(context, localPath)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun shareAttachment(context: Context, localPath: String, mimeType: String) {
        val uri = getShareableUri(context, localPath) ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share Attachment")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun openAttachmentWithIntent(context: Context, localPath: String, mimeType: String) {
        val uri = getShareableUri(context, localPath) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            val chooser = Intent.createChooser(intent, "Open with...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { context.startActivity(chooser) }
        }
    }

    fun deleteAttachmentFile(context: Context, localPath: String): Boolean {
        val file = getFileForAttachment(context, localPath)
        return if (file.exists()) file.delete() else true
    }

    fun calculateSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> "${bytes / 1024} KB"
            else -> "$bytes B"
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            }
        }.getOrNull()
    }

    private fun inferMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast(".", "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> "application/octet-stream"
        }
    }

    private fun decodeSampledBitmapFromBytes(bytes: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        var inSampleSize = 1
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            val halfHeight: Int = options.outHeight / 2
            val halfWidth: Int = options.outWidth / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }

    private fun fixOrientationIfPossible(bytes: ByteArray, bitmap: Bitmap): Bitmap {
        return try {
            val tempExifFile = File.createTempFile("exif_probe", ".jpg").apply {
                writeBytes(bytes)
                deleteOnExit()
            }
            val exif = ExifInterface(tempExifFile.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED
            )
            runCatching { tempExifFile.delete() }

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                else -> bitmap
            }
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
