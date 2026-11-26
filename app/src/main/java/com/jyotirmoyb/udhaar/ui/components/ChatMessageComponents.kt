package com.jyotirmoyb.udhaar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.AuditTrailEntry
import com.jyotirmoyb.udhaar.data.ChatMessageEntity
import com.jyotirmoyb.udhaar.data.ChatMessageType
import com.jyotirmoyb.udhaar.data.ExtensionRequestStatus
import com.jyotirmoyb.udhaar.data.MessageDeliveryStatus
import com.jyotirmoyb.udhaar.data.isOutgoing
import com.jyotirmoyb.udhaar.data.textContent
import com.jyotirmoyb.udhaar.ui.icons.UdhaarIcons
import com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class ChatFinancePalette(
    val receiveAccent: Color,
    val receiveContainer: Color,
    val payAccent: Color,
    val payContainer: Color
)

internal sealed class TimelineDisplayItem(val timestampMillis: Long) {
    data class AuditSystemItem(val entry: AuditTrailEntry) : TimelineDisplayItem(entry.eventTimeMillis)
    data class UserChatMessageItem(val message: ChatMessageEntity) : TimelineDisplayItem(message.sentAtMillis)
}

@Composable
internal fun SystemAuditPillItem(entry: AuditTrailEntry) {
    val timeFmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = UdhaarIcons.Verified,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(
                        R.string.ui_fmt_symbol_9c4f32,
                        entry.eventType.label,
                        entry.summaryText,
                        timeFmt.format(Date(entry.eventTimeMillis))
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun ChatMessageBubbleItem(
    message: ChatMessageEntity,
    isCloudLinked: Boolean = true,
    onAcceptExtension: () -> Unit,
    onDeclineExtension: () -> Unit
) {
    val financeColors = ChatFinancePalette(
        receiveAccent = MaterialTheme.colorScheme.primary,
        receiveContainer = MaterialTheme.colorScheme.secondaryContainer,
        payAccent = MaterialTheme.colorScheme.error,
        payContainer = MaterialTheme.colorScheme.errorContainer
    )
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    val isOutgoing = message.isOutgoing
    val bubbleBg = if (isOutgoing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val bubbleText = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 310.dp),
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isOutgoing) 12.dp else 2.dp,
                bottomEnd = if (isOutgoing) 2.dp else 12.dp
            ),
            color = bubbleBg,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isOutgoing) {
                            stringResource(R.string.ui_fmt_you_382cbf, message.senderName)
                        } else {
                            message.senderName
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = bubbleText.copy(alpha = 0.75f)
                    )
                    if (message.messageType == ChatMessageType.QUICK_REPLY) {
                        Text(
                            text = stringResource(R.string.ui_str_quick_reply_ce8251),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = bubbleText.copy(alpha = 0.6f)
                        )
                    }
                }

                if (message.messageType == ChatMessageType.PAYMENT_PROOF || message.attachmentUri.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (message.attachmentMimeType.contains("pdf", ignoreCase = true)) {
                                    UdhaarIcons.Description
                                } else {
                                    UdhaarIcons.Image
                                },
                                contentDescription = stringResource(R.string.cd_payment_proof_attachment),
                                tint = financeColors.receiveAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (message.attachmentMimeType.contains("pdf", ignoreCase = true)) {
                                        stringResource(R.string.ui_str_payment_proof_pdf_document_28f436)
                                    } else {
                                        stringResource(R.string.ui_str_payment_proof_image_receipt_5f4055)
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black
                                    ),
                                    color = financeColors.receiveAccent
                                )
                                Text(
                                    text = message.attachmentUri.takeLast(36).ifBlank {
                                        stringResource(R.string.ui_str_verified_local_attachment_57cd60)
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 12.sp,
                                        fontFamily = IbmPlexMonoFamily
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Text(
                    text = message.textContent,
                    style = MaterialTheme.typography.bodySmall,
                    color = bubbleText
                )

                if (message.messageType == ChatMessageType.EXTENSION_REQUEST) {
                    val proposedDateText = message.proposedDueDateMillis?.let { dateFmt.format(Date(it)) }
                        ?: stringResource(R.string.chat_new_date_fallback)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.ui_fmt_proposed_due_de12ce, proposedDateText),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when (message.extensionStatus) {
                                        ExtensionRequestStatus.ACCEPTED -> financeColors.receiveContainer
                                        ExtensionRequestStatus.DECLINED -> financeColors.payContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ) {
                                    Text(
                                        text = message.extensionStatus.name,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black
                                        ),
                                        color = when (message.extensionStatus) {
                                            ExtensionRequestStatus.ACCEPTED -> financeColors.receiveAccent
                                            ExtensionRequestStatus.DECLINED -> financeColors.payAccent
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }

                            if (message.extensionStatus == ExtensionRequestStatus.PENDING) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = onAcceptExtension,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("accept_extension_button_${message.id}"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = financeColors.receiveAccent,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.ui_str_accept_514158),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black)
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = onDeclineExtension,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("decline_extension_button_${message.id}"),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.ui_str_decline_2adbe2),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeFmt.format(Date(message.sentAtMillis)),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = bubbleText.copy(alpha = 0.65f)
                    )
                    if (isOutgoing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        val isLocalOnlyStatus = !isCloudLinked && message.deliveryStatus != MessageDeliveryStatus.READ
                        val statusText = when {
                            isLocalOnlyStatus -> stringResource(R.string.chat_status_local_only)
                            message.deliveryStatus == MessageDeliveryStatus.SENT -> stringResource(R.string.ui_str_sent_35f49d)
                            message.deliveryStatus == MessageDeliveryStatus.DELIVERED -> stringResource(R.string.ui_str_delivered_eea956)
                            else -> stringResource(R.string.ui_str_read_852b43)
                        }
                        Icon(
                            imageVector = if (isLocalOnlyStatus || message.deliveryStatus == MessageDeliveryStatus.SENT) {
                                Icons.Default.Check
                            } else {
                                UdhaarIcons.DoneAll
                            },
                            contentDescription = statusText,
                            modifier = Modifier.size(13.dp),
                            tint = if (!isLocalOnlyStatus && message.deliveryStatus == MessageDeliveryStatus.READ) {
                                financeColors.receiveAccent
                            } else {
                                bubbleText.copy(alpha = 0.75f)
                            }
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (!isLocalOnlyStatus && message.deliveryStatus == MessageDeliveryStatus.READ) {
                                financeColors.receiveAccent
                            } else {
                                bubbleText.copy(alpha = 0.75f)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChatComposerSection(
    quickReplies: List<String>,
    isEffectivelyBlocked: Boolean,
    messageDraft: String,
    onQuickReplyClick: (String) -> Unit,
    onPickImage: () -> Unit,
    onPickPdf: () -> Unit,
    onMessageDraftChange: (String) -> Unit,
    onSendMessageClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        quickReplies.forEachIndexed { idx, replyText ->
            FilterChip(
                selected = false,
                enabled = !isEffectivelyBlocked,
                onClick = { onQuickReplyClick(replyText) },
                label = {
                    Text(
                        text = replyText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                },
                modifier = Modifier.testTag("quick_reply_chip_$idx")
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = onPickImage,
            enabled = !isEffectivelyBlocked,
            modifier = Modifier
                .size(48.dp)
                .testTag("attach_payment_proof_image_button")
        ) {
            Icon(
                imageVector = UdhaarIcons.Image,
                contentDescription = stringResource(R.string.cd_attach_payment_proof_image),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(
            onClick = onPickPdf,
            enabled = !isEffectivelyBlocked,
            modifier = Modifier
                .size(48.dp)
                .testTag("attach_payment_proof_pdf_button")
        ) {
            Icon(
                imageVector = UdhaarIcons.AttachFile,
                contentDescription = stringResource(R.string.cd_attach_payment_proof_pdf),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedTextField(
            value = messageDraft,
            onValueChange = onMessageDraftChange,
            placeholder = {
                Text(
                    text = if (isEffectivelyBlocked) {
                        stringResource(R.string.ui_str_thread_is_blocked_31e13b)
                    } else {
                        stringResource(R.string.ui_str_write_a_message_or_note_8070bf)
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            },
            enabled = !isEffectivelyBlocked,
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .testTag("chat_message_input")
        )

        Button(
            onClick = onSendMessageClick,
            enabled = !isEffectivelyBlocked && messageDraft.isNotBlank(),
            modifier = Modifier
                .height(48.dp)
                .pressScaleFeedback()
                .testTag("chat_send_message_button"),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.cd_send_message),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
