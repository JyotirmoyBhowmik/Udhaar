package com.jyotirmoyb.udhaar.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.UdhaarDatabase
import com.jyotirmoyb.udhaar.di.FirebaseModule
import com.jyotirmoyb.udhaar.util.AttachmentManager
import com.jyotirmoyb.udhaar.util.SafeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PaymentProofDialog(
    txId: Long,
    linkId: String,
    onDismiss: () -> Unit,
    onProofSavedToLedger: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var proofBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var proofMime by remember { mutableStateOf("image/webp") }
    var proofRawBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isSaved by remember { mutableStateOf(false) }

    LaunchedEffect(linkId) {
        if (linkId.isBlank()) {
            errorMessage = context.getString(R.string.proof_not_found)
            isLoading = false
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                val firestore = FirebaseModule.getFirestore(context)
                if (firestore == null) {
                    errorMessage = "Database unavailable"
                    isLoading = false
                    return@withContext
                }

                val doc = firestore.collection("guestLinks").document(linkId)
                    .collection("actions").document("CLAIM_PAYMENT")
                    .collection("proof").document("image")
                    .get()
                    .await()

                if (!doc.exists()) {
                    errorMessage = context.getString(R.string.proof_not_found)
                    isLoading = false
                    return@withContext
                }

                val dataB64 = doc.getString("dataB64")
                val mime = doc.getString("mime") ?: "image/webp"

                if (dataB64.isNullOrBlank()) {
                    errorMessage = context.getString(R.string.proof_not_found)
                    isLoading = false
                    return@withContext
                }

                val cleanB64 = if (dataB64.contains(",")) dataB64.substringAfter(",") else dataB64
                val bytes = Base64.decode(cleanB64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                if (bmp == null) {
                    errorMessage = "Unable to decode image"
                    isLoading = false
                    return@withContext
                }

                proofRawBytes = bytes
                proofMime = mime
                proofBitmap = bmp
                isLoading = false
            } catch (e: Exception) {
                SafeLogger.e("PaymentProofDialog", "Failed to load proof: ${e.javaClass.simpleName}")
                errorMessage = context.getString(R.string.proof_not_found)
                isLoading = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .testTag("payment_proof_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.proof_dialog_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                    }
                }

                // Content
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Text(
                                    text = stringResource(R.string.proof_loading),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    errorMessage != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = errorMessage ?: stringResource(R.string.proof_not_found),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    proofBitmap != null -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = proofBitmap!!.asImageBitmap(),
                                contentDescription = stringResource(R.string.proof_dialog_title),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 380.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }

                        // Encryption & privacy badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.proof_encryption_notice),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Save to ledger action
                        Button(
                            onClick = {
                                if (isSaving || isSaved || proofRawBytes == null) return@Button
                                scope.launch {
                                    isSaving = true
                                    val bytes = proofRawBytes ?: return@launch
                                    val success = withContext(Dispatchers.IO) {
                                        try {
                                            val ext = if (proofMime.contains("webp", ignoreCase = true)) "webp" else "jpg"
                                            val fileName = "proof_claim_${System.currentTimeMillis()}.$ext"
                                            val destFile = File(AttachmentManager.getAttachmentsDir(context), fileName)
                                            destFile.writeBytes(bytes)

                                            val sha256 = AttachmentManager.calculateSha256(bytes)
                                            val attachment = Attachment(
                                                id = 0L,
                                                transactionId = txId,
                                                paymentId = null,
                                                fileName = fileName,
                                                mimeType = proofMime,
                                                sizeBytes = destFile.length(),
                                                localPath = destFile.absolutePath,
                                                remoteUrl = null,
                                                addedAtMillis = System.currentTimeMillis(),
                                                addedBy = "Guest (Web Proof)",
                                                sha256 = sha256
                                            )
                                            val dao = UdhaarDatabase.getInstance(context).udhaarDao()
                                            dao.insertAttachment(attachment)

                                            // Delete Firestore proof document to free quota
                                            val firestore = FirebaseModule.getFirestore(context)
                                            if (firestore != null && linkId.isNotBlank()) {
                                                runCatching {
                                                    firestore.collection("guestLinks").document(linkId)
                                                        .collection("actions").document("CLAIM_PAYMENT")
                                                        .collection("proof").document("image")
                                                        .delete()
                                                        .await()
                                                }
                                            }
                                            true
                                        } catch (e: Exception) {
                                            SafeLogger.e("PaymentProofDialog", "Failed to save attachment: ${e.javaClass.simpleName}")
                                            false
                                        }
                                    }
                                    isSaving = false
                                    if (success) {
                                        isSaved = true
                                        Toast.makeText(context, context.getString(R.string.proof_saved_to_ledger), Toast.LENGTH_SHORT).show()
                                        onProofSavedToLedger()
                                    }
                                }
                            },
                            enabled = !isSaving && !isSaved,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("btn_save_proof_to_ledger"),
                            shape = RoundedCornerShape(10.dp),
                            colors = if (isSaved) ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) else ButtonDefaults.buttonColors()
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.proof_saving_encrypted), style = MaterialTheme.typography.labelMedium)
                            } else if (isSaved) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.proof_saved_status), style = MaterialTheme.typography.labelMedium)
                            } else {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.btn_save_to_ledger), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                // Bottom actions
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_close_proof_dialog"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.btn_close), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
