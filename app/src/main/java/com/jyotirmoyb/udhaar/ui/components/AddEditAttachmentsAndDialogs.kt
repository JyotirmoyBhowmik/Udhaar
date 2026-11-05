package com.jyotirmoyb.udhaar.ui.components

import android.app.TimePickerDialog
import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.util.InterestCalculator
import java.io.File
import java.util.Calendar

@Composable
internal fun DeleteTransactionConfirmDialog(
    initialTransaction: UdhaarTransaction,
    onDismiss: () -> Unit,
    onConfirmDelete: (UdhaarTransaction) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("delete_transaction_confirm_dialog"),
        title = {
            Text(
                text = stringResource(R.string.ui_str_delete_transaction_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = stringResource(
                    R.string.ui_fmt_delete_transaction_confirm_body,
                    initialTransaction.partyName,
                    InterestCalculator.formatCurrencyPaise(initialTransaction.principalAmountPaise, initialTransaction.currency)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirmDelete(initialTransaction) },
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_delete_transaction_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(stringResource(R.string.ui_str_delete_d6f563))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("cancel_delete_transaction_button")
            ) {
                Text(stringResource(R.string.ui_str_cancel_1507c4))
            }
        }
    )
}

@Composable
internal fun AddEditAttachmentsSection(
    reducedMotion: Boolean,
    allCurrentAttachments: List<Attachment>,
    onCameraClick: () -> Unit,
    galleryLauncher: ActivityResultLauncher<PickVisualMediaRequest>,
    documentLauncher: ActivityResultLauncher<Array<String>>,
    onOpenAttachmentViewer: (Attachment) -> Unit,
    onRemoveDraftAttachment: (Attachment) -> Unit,
    onDeleteExistingAttachment: (Attachment) -> Unit
) {
    Text(
        text = stringResource(R.string.ui_fmt_attachments_bills_0773eb, allCurrentAttachments.size),
        style = MaterialTheme.typography.labelMedium,
        color = SecondaryInkGrey
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onCameraClick,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("add_attachment_camera_button"),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.ui_str_camera_4da9c9), style = MaterialTheme.typography.labelSmall)
        }
        OutlinedButton(
            onClick = {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("add_attachment_gallery_button"),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.ui_str_gallery_9c30a3), style = MaterialTheme.typography.labelSmall)
        }
        OutlinedButton(
            onClick = {
                documentLauncher.launch(arrayOf("application/pdf", "image/*", "text/*", "*/*"))
            },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("add_attachment_doc_button"),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.ui_str_doc_pdf_7135c0), style = MaterialTheme.typography.labelSmall)
        }
    }

    if (allCurrentAttachments.isNotEmpty()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_edit_attachments_row"),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(allCurrentAttachments, key = { "${it.id}_${it.localPath}" }) { att ->
                val isImg = att.mimeType.startsWith("image/")
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, BorderGrey),
                    modifier = udhaarAnimateItem(reducedMotion)
                        .size(96.dp)
                        .pressScaleFeedback()
                        .clickable { onOpenAttachmentViewer(att) }
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
                                    .padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = att.fileName,
                                    modifier = Modifier.size(24.dp),
                                    tint = PureBlack
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    att.fileName,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        AppIconButton(
                            onClick = {
                                if (att.id > 0L) onDeleteExistingAttachment(att)
                                else onRemoveDraftAttachment(att)
                            },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(
                                        PureBlack.copy(alpha = 0.7f),
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.cd_close),
                                    tint = PureWhite,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddEditDatePickerDialogs(
    context: Context,
    startDateMillis: Long,
    onStartDateMillisChange: (Long) -> Unit,
    expiryDateMillis: Long?,
    onExpiryDateMillisChange: (Long?) -> Unit,
    reminderDateMillis: Long?,
    onReminderDateMillisChange: (Long?) -> Unit,
    showStartDatePicker: Boolean,
    onShowStartDatePickerChange: (Boolean) -> Unit,
    showExpiryDatePicker: Boolean,
    onShowExpiryDatePickerChange: (Boolean) -> Unit,
    showReminderDatePicker: Boolean,
    onShowReminderDatePickerChange: (Boolean) -> Unit
) {
    if (showStartDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = startDateMillis)
        DatePickerDialog(
            onDismissRequest = { onShowStartDatePickerChange(false) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onStartDateMillisChange(it) }
                        onShowStartDatePickerChange(false)
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.ui_str_confirm_f6fda1), color = PureBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onShowStartDatePickerChange(false) },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.ui_str_cancel_1507c4), color = SecondaryInkGrey)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showExpiryDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = expiryDateMillis ?: (System.currentTimeMillis() + 30L * 86_400_000L)
        )
        DatePickerDialog(
            onDismissRequest = { onShowExpiryDatePickerChange(false) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onExpiryDateMillisChange(it) }
                        onShowExpiryDatePickerChange(false)
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.ui_str_confirm_f6fda1), color = PureBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onShowExpiryDatePickerChange(false) },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.ui_str_cancel_1507c4), color = SecondaryInkGrey)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showReminderDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = reminderDateMillis ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { onShowReminderDatePickerChange(false) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedDate = pickerState.selectedDateMillis ?: System.currentTimeMillis()
                        onShowReminderDatePickerChange(false)
                        val cal = Calendar.getInstance().apply { timeInMillis = selectedDate }
                        val nowCal = Calendar.getInstance()
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                cal.set(Calendar.MINUTE, minute)
                                cal.set(Calendar.SECOND, 0)
                                onReminderDateMillisChange(cal.timeInMillis)
                            },
                            nowCal.get(Calendar.HOUR_OF_DAY),
                            (nowCal.get(Calendar.MINUTE) + 5) % 60,
                            false
                        ).show()
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.ui_str_next_pick_time_d07be0), color = PureBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onShowReminderDatePickerChange(false) },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.ui_str_cancel_1507c4), color = SecondaryInkGrey)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
