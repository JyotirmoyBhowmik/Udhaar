package com.jyotirmoyb.udhaar.data.repository

import android.content.Context
import android.util.Base64
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.UdhaarDao
import com.jyotirmoyb.udhaar.util.AttachmentManager
import com.jyotirmoyb.udhaar.util.SafeLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

open class AttachmentRepository(
    private val dao: UdhaarDao,
    @ApplicationContext private val context: Context? = null
) {
    val allAttachments: Flow<List<Attachment>> = dao.getAllAttachmentsFlow().catch { e ->
        SafeLogger.e("AttachmentRepository", "Failed reading attachments flow", e)
        emit(emptyList())
    }

    fun getAttachmentsForTransaction(transactionId: Long): Flow<List<Attachment>> =
        dao.getAttachmentsForTransaction(transactionId)

    fun getAttachmentsForPayment(paymentId: Long): Flow<List<Attachment>> =
        dao.getAttachmentsForPayment(paymentId)

    suspend fun insertAttachment(attachment: Attachment): Long =
        dao.insertAttachment(attachment)

    suspend fun deleteAttachment(attachment: Attachment) {
        dao.deleteAttachment(attachment)
        context?.let { ctx ->
            AttachmentManager.deleteAttachmentFile(ctx, attachment.localPath)
        }
    }

    suspend fun deleteAttachmentById(attachmentId: Long) {
        val all = dao.getAllAttachmentsSnapshot()
        val match = all.firstOrNull { it.id == attachmentId }
        if (match != null) {
            deleteAttachment(match)
        } else {
            dao.deleteAttachmentById(attachmentId)
        }
    }

    open fun getLocalAttachmentsMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val filesDir = context?.filesDir ?: return map
        val attachDir = File(filesDir, "attachments")
        if (attachDir.exists() && attachDir.isDirectory) {
            attachDir.listFiles()?.forEach { file ->
                if (file.isFile && file.length() in 1..(5 * 1024 * 1024)) {
                    val b64 = runCatching {
                        Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                    }.getOrNull()
                    if (b64 != null) {
                        map[file.name] = b64
                    }
                }
            }
        }
        return map
    }
}
