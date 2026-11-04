package com.jyotirmoyb.udhaar.ui.sheets.detail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.ui.components.pressScaleFeedback
import com.jyotirmoyb.udhaar.ui.components.rememberReducedMotionEnabled
import com.jyotirmoyb.udhaar.ui.components.udhaarAnimateItem
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.util.AttachmentManager
import java.io.File

@Composable
fun TransactionAttachmentsSection(
    transactionId: Long,
    attachments: List<Attachment>,
    onAddAttachment: (Attachment) -> Unit,
    onOpenAttachmentViewer: (Attachment) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }
    val reducedMotion = rememberReducedMotionEnabled()

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = cameraTempUri
        if (success && capturedUri != null) {
            val processed = AttachmentManager.processCapturedImage(
                context = context,
                sourceUri = capturedUri,
                transactionId = transactionId,
                addedBy = "You"
            )
            if (processed != null) {
                onAddAttachment(processed)
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val processed = AttachmentManager.processCapturedImage(
                context = context,
                sourceUri = uri,
                transactionId = transactionId,
                addedBy = "You"
            )
            if (processed != null) {
                onAddAttachment(processed)
            }
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val processed = AttachmentManager.copyDocumentToStorage(
                context = context,
                sourceUri = uri,
                transactionId = transactionId,
                addedBy = "You"
            )
            if (processed != null) {
                onAddAttachment(processed)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(12.dp)
            .testTag("detail_attachments_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.ui_fmt_attached_documents_7a618e, attachments.size),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val (uri, _) = AttachmentManager.createCameraTempFileUri(context)
                    cameraTempUri = uri
                    cameraLauncher.launch(uri)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .testTag("attach_camera_button"),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.ui_str_camera_7d2039), style = MaterialTheme.typography.labelSmall)
            }

            OutlinedButton(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .testTag("attach_gallery_button"),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.ui_str_gallery_73dcec), style = MaterialTheme.typography.labelSmall)
            }

            OutlinedButton(
                onClick = {
                    documentLauncher.launch(arrayOf("application/pdf", "image/*", "text/*", "*/*"))
                },
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .testTag("attach_doc_button"),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.ui_str_doc_pdf_c5e19f), style = MaterialTheme.typography.labelSmall)
            }
        }

        if (attachments.isEmpty()) {
            Text(
                text = stringResource(R.string.ui_str_no_documents_attached_attach_promis_e527cb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("detail_attachments_lazy_row"),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(attachments, key = { it.id }) { att ->
                    val isImg = att.mimeType.startsWith("image/")
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = udhaarAnimateItem(reducedMotion)
                            .size(width = 110.dp, height = 110.dp)
                            .pressScaleFeedback()
                            .clickable { onOpenAttachmentViewer(att) }
                            .testTag("attachment_thumb_${att.id}")
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (isImg) {
                                AsyncImage(
                                    model = File(att.localPath),
                                    contentDescription = att.fileName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = att.fileName,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = AttachmentManager.formatFileSize(att.sizeBytes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(PureBlack.copy(alpha = 0.75f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = att.fileName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PureWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
