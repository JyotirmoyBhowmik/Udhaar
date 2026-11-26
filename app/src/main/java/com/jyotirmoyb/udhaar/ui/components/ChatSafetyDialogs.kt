@file:Suppress("MatchingDeclarationName")

package com.jyotirmoyb.udhaar.ui.components

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.ui.icons.UdhaarIcons
import java.util.Calendar

internal enum class ChatReportTarget {
    THREAD,
    USER
}

@Composable
internal fun ChatSafetyOverflowMenu(
    expanded: Boolean,
    effectivePartyName: String,
    isThreadMuted: Boolean,
    isThreadBlocked: Boolean,
    isUserMuted: Boolean,
    isUserBlocked: Boolean,
    payAccent: Color,
    onDismiss: () -> Unit,
    onToggleThreadMute: () -> Unit,
    onToggleThreadBlock: () -> Unit,
    onOpenReportThread: () -> Unit,
    onToggleUserMute: () -> Unit,
    onToggleUserBlock: () -> Unit,
    onOpenReportUser: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    if (isThreadMuted) {
                        stringResource(R.string.ui_str_unmute_notifications_1c25a8)
                    } else {
                        stringResource(R.string.ui_str_mute_thread_notifications_50c04b)
                    }
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (isThreadMuted) UdhaarIcons.NotificationsActive else UdhaarIcons.NotificationsOff,
                    contentDescription = null
                )
            },
            onClick = {
                onDismiss()
                onToggleThreadMute()
            },
            modifier = Modifier.testTag("chat_menu_mute_thread_item")
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = if (isThreadBlocked) {
                        stringResource(R.string.chat_safety_unblock_thread)
                    } else {
                        stringResource(R.string.chat_safety_block_thread)
                    },
                    color = if (isThreadBlocked) MaterialTheme.colorScheme.onSurface else payAccent
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = UdhaarIcons.Block,
                    contentDescription = null,
                    tint = if (isThreadBlocked) MaterialTheme.colorScheme.onSurface else payAccent
                )
            },
            onClick = {
                onDismiss()
                onToggleThreadBlock()
            },
            modifier = Modifier.testTag("chat_menu_block_item")
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.chat_safety_report_thread),
                    color = payAccent
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = UdhaarIcons.Flag,
                    contentDescription = null,
                    tint = payAccent
                )
            },
            onClick = {
                onDismiss()
                onOpenReportThread()
            },
            modifier = Modifier.testTag("chat_menu_report_thread_item")
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = {
                Text(
                    if (isUserMuted) {
                        stringResource(R.string.chat_safety_unmute_user_fmt, effectivePartyName)
                    } else {
                        stringResource(R.string.chat_safety_mute_user_fmt, effectivePartyName)
                    }
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (isUserMuted) UdhaarIcons.NotificationsActive else UdhaarIcons.NotificationsOff,
                    contentDescription = null
                )
            },
            onClick = {
                onDismiss()
                onToggleUserMute()
            },
            modifier = Modifier.testTag("chat_menu_mute_user_item")
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = if (isUserBlocked) {
                        stringResource(R.string.ui_fmt_unblock_79891e, effectivePartyName)
                    } else {
                        stringResource(R.string.ui_fmt_block_0b0d58, effectivePartyName)
                    },
                    color = if (isUserBlocked) MaterialTheme.colorScheme.onSurface else payAccent
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = UdhaarIcons.Block,
                    contentDescription = null,
                    tint = if (isUserBlocked) MaterialTheme.colorScheme.onSurface else payAccent
                )
            },
            onClick = {
                onDismiss()
                onToggleUserBlock()
            },
            modifier = Modifier.testTag("chat_menu_block_user_item")
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.ui_str_report_abusive_user_292645),
                    color = payAccent
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = UdhaarIcons.Flag,
                    contentDescription = null,
                    tint = payAccent
                )
            },
            onClick = {
                onDismiss()
                onOpenReportUser()
            },
            modifier = Modifier.testTag("chat_menu_report_item")
        )
    }
}

@Composable
internal fun ChatExtensionRequestDrawer(
    context: Context,
    tx: UdhaarTransaction,
    extensionNoteDraft: String,
    onNoteChange: (String) -> Unit,
    onRequestDate: (Long) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .testTag("extension_request_drawer"),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.ui_str_propose_new_due_date_one_fbfc83),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            val baseDue = tx.expiryDateMillis?.takeIf { it > System.currentTimeMillis() }
                ?: System.currentTimeMillis()
            val dayMs = 24L * 60L * 60L * 1000L

            OutlinedTextField(
                value = extensionNoteDraft,
                onValueChange = onNoteChange,
                label = { Text(stringResource(R.string.ui_str_optional_reason_e_g_salary_631efd)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("extension_reason_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    7 to stringResource(R.string.chat_ext_plus_7d),
                    15 to stringResource(R.string.chat_ext_plus_15d),
                    30 to stringResource(R.string.chat_ext_plus_30d)
                ).forEach { (days, label) ->
                    Button(
                        onClick = { onRequestDate(baseDue + (days * dayMs)) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("request_ext_${days}d_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = baseDue + (7 * dayMs) }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val pickedCal = Calendar.getInstance().apply {
                                    set(y, m, d, 23, 59, 59)
                                }
                                onRequestDate(pickedCal.timeInMillis)
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("request_ext_custom_date_button"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = UdhaarIcons.CalendarMonth,
                        contentDescription = stringResource(R.string.cd_pick_custom_extension_date),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatReportSafetyCard(
    reportTarget: ChatReportTarget,
    effectivePartyName: String,
    customReportReason: String,
    financeColors: ChatFinancePalette,
    onReasonChange: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val presetReasons = listOf(
        stringResource(R.string.chat_report_reason_harassment),
        stringResource(R.string.chat_report_reason_fake_proof),
        stringResource(R.string.chat_report_reason_spam)
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .testTag("report_abusive_user_card"),
        shape = RoundedCornerShape(8.dp),
        color = financeColors.payContainer,
        border = BorderStroke(1.dp, financeColors.payAccent.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (reportTarget == ChatReportTarget.THREAD) {
                    stringResource(R.string.chat_safety_report_thread_title_fmt, effectivePartyName)
                } else {
                    stringResource(R.string.ui_fmt_report_block_107a91, effectivePartyName)
                },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                color = financeColors.payAccent
            )
            Text(
                text = if (reportTarget == ChatReportTarget.THREAD) {
                    stringResource(R.string.ui_str_reporting_this_thread_blocks_furthe_533f26)
                } else {
                    stringResource(R.string.chat_safety_report_user_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                presetReasons.forEach { preset ->
                    FilterChip(
                        selected = customReportReason == preset,
                        onClick = { onReasonChange(preset) },
                        label = {
                            Text(
                                text = preset,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    )
                }
            }
            OutlinedTextField(
                value = customReportReason,
                onValueChange = onReasonChange,
                label = { Text(stringResource(R.string.ui_str_reason_for_report_c64e37)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("report_reason_input")
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.ui_str_cancel_1507c4))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = {
                        onConfirm(customReportReason.ifBlank { presetReasons.first() })
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = financeColors.payAccent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_report_block_button")
                ) {
                    Text(stringResource(R.string.ui_str_report_block_67fd77), fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
internal fun ChatBlockedStatusBanner(
    isThreadBlocked: Boolean,
    isUserBlocked: Boolean,
    isThreadReported: Boolean,
    isUserReported: Boolean,
    reportReason: String,
    partyName: String,
    financeColors: ChatFinancePalette,
    onUnblockThread: () -> Unit,
    onUnblockUser: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .testTag("thread_blocked_banner"),
        shape = RoundedCornerShape(12.dp),
        color = financeColors.payContainer,
        border = BorderStroke(1.dp, financeColors.payAccent.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isUserBlocked && isUserReported ->
                            stringResource(R.string.ui_fmt_user_reported_blocked_5785b6, reportReason)
                        isUserBlocked ->
                            stringResource(R.string.chat_safety_user_blocked_banner_fmt, partyName)
                        isThreadReported ->
                            stringResource(R.string.ui_fmt_user_reported_blocked_5785b6, reportReason)
                        else ->
                            stringResource(R.string.ui_str_thread_blocked_messages_paused_5309cf)
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                    color = financeColors.payAccent
                )
                Text(
                    text = stringResource(R.string.ui_str_unblock_to_resume_sending_or_92897f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isThreadBlocked) {
                TextButton(
                    onClick = onUnblockThread,
                    modifier = Modifier.testTag("unblock_thread_button")
                ) {
                    Text(stringResource(R.string.ui_str_unblock_ffe934), fontWeight = FontWeight.Black)
                }
            }
            if (isUserBlocked) {
                TextButton(
                    onClick = onUnblockUser,
                    modifier = Modifier.testTag("unblock_user_button")
                ) {
                    Text(stringResource(R.string.chat_safety_unblock_user_short), fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
internal fun ChatActionButtonsRow(
    showRequestExtension: Boolean,
    onToggleRequestExtension: () -> Unit,
    onWhatsAppFallbackClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showRequestExtension) {
            OutlinedButton(
                onClick = onToggleRequestExtension,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("toggle_request_extension_button"),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = UdhaarIcons.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.ui_str_request_extension_6b0c72),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black)
                )
            }
        }

        OutlinedButton(
            onClick = onWhatsAppFallbackClick,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("chat_whatsapp_fallback_button"),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.ui_str_whatsapp_fallback_3df293),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black)
            )
        }
    }
}
