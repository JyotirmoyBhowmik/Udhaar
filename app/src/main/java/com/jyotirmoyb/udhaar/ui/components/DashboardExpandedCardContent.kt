package com.jyotirmoyb.udhaar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.reminder.ReminderEngine
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.CardSurfaceWhite
import com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily
import com.jyotirmoyb.udhaar.ui.theme.MutedChipGrey
import com.jyotirmoyb.udhaar.ui.theme.PayableCardBg
import com.jyotirmoyb.udhaar.ui.theme.PayableCardInk
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.ui.theme.SubtleSurfaceGrey
import com.jyotirmoyb.udhaar.ui.theme.UdhaarShapeTokens
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.InstallmentProgress
import com.jyotirmoyb.udhaar.util.InterestCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ExpandedTransactionCardContent(
    summary: CalculatedUdhaarSummary,
    partyPhotoUri: String,
    isAmountsHidden: Boolean,
    unreadCount: Int,
    attachmentsCount: Int,
    installmentProgress: InstallmentProgress?,
    formattedCardAmount: String,
    cardAmountFontSize: TextUnit,
    onCollapse: () -> Unit,
    onChatClick: () -> Unit,
    onWhatsAppClick: () -> Unit,
    onQrClick: () -> Unit,
    onSnoozeClick: (recordId: Long, days: Int) -> Unit,
    onStopRemindersClick: (recordId: Long) -> Unit,
    onEnableRemindersClick: (recordId: Long) -> Unit,
    onBorrowerAction: ((summary: CalculatedUdhaarSummary, actionType: String) -> Unit)?
) {
    val tx = summary.transaction
    val isGiven = tx.transactionType == TransactionType.GIVEN

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = if (isGiven) PureBlack else PayableCardBg,
                        shape = UdhaarShapeTokens.Corner8
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isGiven) stringResource(R.string.ui_str_given_567b49) else stringResource(R.string.ui_str_taken_7abbf5),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    color = if (isGiven) CardSurfaceWhite else PayableCardInk,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Box(
                modifier = Modifier
                    .background(MutedChipGrey, UdhaarShapeTokens.Corner8)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = tx.paymentMode.shortBadge,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    color = PureBlack,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Box(
                modifier = Modifier
                    .background(MutedChipGrey, UdhaarShapeTokens.Corner8)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = tx.currency.code,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    color = SecondaryInkGrey,
                    maxLines = 1,
                    softWrap = false
                )
            }
            StatusAtAGlanceBadge(
                summary = summary,
                modifier = Modifier.testTag("tx_verification_badge_${tx.id}")
            )
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .background(PureBlack, UdhaarShapeTokens.Corner8)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("tx_unread_badge_${tx.id}")
                ) {
                    Text(
                        text = stringResource(R.string.ui_fmt_symbol_301919, unreadCount),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = CardSurfaceWhite,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            if (attachmentsCount > 0) {
                Box(
                    modifier = Modifier
                        .background(SubtleSurfaceGrey, UdhaarShapeTokens.Corner8)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("tx_attachments_badge_${tx.id}")
                ) {
                    Text(
                        text = stringResource(R.string.ui_fmt_symbol_e20cea, attachmentsCount),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = PureBlack,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            if (installmentProgress?.hasOverdue == true) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.errorContainer, UdhaarShapeTokens.Corner8)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("tx_installment_late_badge_${tx.id}")
                ) {
                    Text(
                        text = stringResource(R.string.badge_late),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = summary.dueStatusText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = IbmPlexMonoFamily,
                    fontSize = 12.sp,
                    fontWeight = if (summary.isOverdue || tx.isSettled) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = if (summary.isOverdue) PayableCardInk else SecondaryInkGrey,
                maxLines = 1,
                softWrap = false
            )

            var showReminderMenu by remember { mutableStateOf(false) }
            Box {
                AppIconButton(
                    onClick = { showReminderMenu = true },
                    modifier = Modifier.testTag("card_reminder_menu_button_${tx.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.cd_more_options),
                        tint = SecondaryInkGrey,
                        modifier = Modifier.size(16.dp)
                    )
                }
                DropdownMenu(
                    expanded = showReminderMenu,
                    onDismissRequest = { showReminderMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.snooze_1_day)) },
                        onClick = {
                            showReminderMenu = false
                            onSnoozeClick(tx.id, 1)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.snooze_3_days)) },
                        onClick = {
                            showReminderMenu = false
                            onSnoozeClick(tx.id, 3)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.snooze_1_week)) },
                        onClick = {
                            showReminderMenu = false
                            onSnoozeClick(tx.id, 7)
                        }
                    )
                    if (tx.remindersDisabled) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.resume_reminders_for_record)) },
                            onClick = {
                                showReminderMenu = false
                                onEnableRemindersClick(tx.id)
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.stop_reminders_for_record)) },
                            onClick = {
                                showReminderMenu = false
                                onStopRemindersClick(tx.id)
                            }
                        )
                    }
                }
            }
            AppIconButton(
                onClick = onCollapse,
                modifier = Modifier.testTag("tx_card_expand_toggle_${tx.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.card_collapse_details),
                    tint = SecondaryInkGrey,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PersonAvatar(
                name = tx.partyName,
                photoUri = partyPhotoUri,
                size = 40.dp,
                modifier = Modifier.udhaarSharedElement("tx_avatar_${tx.id}")
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = tx.partyName,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = PureBlack,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (tx.isSettled) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.udhaarSharedBounds("tx_name_${tx.id}")
                )
                if (tx.phoneNumber.isNotBlank()) {
                    Text(
                        text = InterestCalculator.maskPhoneNumber(tx.phoneNumber),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = IbmPlexMonoFamily,
                            fontSize = 12.sp
                        ),
                        color = SecondaryInkGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Text(
            text = formattedCardAmount,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontFamily = IbmPlexMonoFamily,
                fontWeight = FontWeight.Bold,
                fontSize = cardAmountFontSize
            ),
            color = PureBlack,
            maxLines = 1,
            textDecoration = if (tx.isSettled) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier
                .udhaarSharedBounds("tx_amount_${tx.id}")
                .testTag("tx_card_amount_${tx.id}")
        )
    }

    Text(
        text = InterestCalculator.formatSubtitleBreakdownPrivacy(summary, isAmountsHidden),
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = IbmPlexMonoFamily,
            fontSize = 12.sp
        ),
        color = SecondaryInkGrey,
        modifier = Modifier.testTag("tx_card_breakdown_${tx.id}")
    )

    if (tx.notes.isNotBlank()) {
        Text(
            text = stringResource(R.string.ui_fmt_symbol_c8354b, tx.notes),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontStyle = FontStyle.Italic,
                fontSize = 14.sp
            ),
            color = SecondaryInkGrey,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }

    if (installmentProgress != null) {
        val sdf = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .testTag("tx_card_installment_progress_${tx.id}"),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.installments_progress_fmt,
                        installmentProgress.paidCount,
                        installmentProgress.totalCount
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = IbmPlexMonoFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    ),
                    color = PureBlack
                )
                installmentProgress.nextDueInstallment?.let { nextDue ->
                    val nextDueFormatted =
                        "${tx.currency.symbol}${InterestCalculator.formatAmountPaise(nextDue.amountDue - nextDue.amountPaid)}"
                    val nextDueDateStr = sdf.format(Date(nextDue.dueDate))
                    Text(
                        text = stringResource(
                            R.string.next_installment_due_fmt,
                            nextDueFormatted,
                            nextDueDateStr
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = IbmPlexMonoFamily,
                            fontSize = 12.sp
                        ),
                        color = if (nextDue.dueDate < System.currentTimeMillis() && nextDue.amountPaid < nextDue.amountDue)
                            MaterialTheme.colorScheme.error
                        else
                            SecondaryInkGrey
                    )
                }
            }
            LinearProgressIndicator(
                progress = { installmentProgress.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(UdhaarShapeTokens.Corner8),
                color = MaterialTheme.colorScheme.primary,
                trackColor = BorderGrey.copy(alpha = 0.5f)
            )
        }
    }

    HorizontalDivider(color = BorderGrey.copy(alpha = 0.7f), thickness = 1.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (tx.isSettled) stringResource(R.string.ui_str_tap_for_receipt_693fae) else stringResource(R.string.ui_str_tap_for_repayment_receipt_bc12be),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            color = PureBlack,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .pressScaleFeedback()
                .clip(UdhaarShapeTokens.Corner8)
                .clickable(onClick = onChatClick)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("card_chat_button_${tx.id}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = stringResource(R.string.cd_open_chat_for_fmt, tx.partyName),
                tint = SecondaryInkGrey,
                modifier = Modifier.size(16.dp)
            )
            if (unreadCount > 0) {
                Text(
                    text = stringResource(R.string.ui_fmt_chat_40e9c8, unreadCount),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = PureBlack
                )
            }
        }
    }

    if (!isGiven && !tx.isSettled) {
        val now = System.currentTimeMillis()
        val overdueDays = ReminderEngine.calculateOverdueDays(tx.expiryDateMillis, now)
        val dueDateFormatted = tx.expiryDateMillis?.let {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it))
        }.orEmpty()
        val selfReminderText = if (overdueDays != null && overdueDays > 0) {
            stringResource(
                R.string.borrower_self_reminder_overdue_fmt,
                InterestCalculator.formatCurrencyPrivacy(summary.netBalanceDue, tx.currency, isAmountsHidden),
                tx.partyName,
                overdueDays
            )
        } else if (overdueDays != null && overdueDays >= -3) {
            stringResource(
                R.string.borrower_self_reminder_fmt,
                InterestCalculator.formatCurrencyPrivacy(summary.netBalanceDue, tx.currency, isAmountsHidden),
                tx.partyName,
                dueDateFormatted
            )
        } else null

        if (selfReminderText != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = UdhaarShapeTokens.Corner8,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("borrower_self_reminder_${tx.id}")
            ) {
                Text(
                    text = selfReminderText,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = { onBorrowerAction?.invoke(summary, "i_paid") },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("borrower_btn_paid_${tx.id}"),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                shape = UdhaarShapeTokens.Corner8
            ) {
                Text(stringResource(R.string.borrower_btn_i_paid), style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = { onBorrowerAction?.invoke(summary, "need_more_time") },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("borrower_btn_need_time_${tx.id}"),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                shape = UdhaarShapeTokens.Corner8
            ) {
                Text(stringResource(R.string.borrower_btn_need_more_time), style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = { onBorrowerAction?.invoke(summary, "ask_details") },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("borrower_btn_details_${tx.id}"),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                shape = UdhaarShapeTokens.Corner8
            ) {
                Text(stringResource(R.string.borrower_btn_ask_details), style = MaterialTheme.typography.labelSmall)
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onQrClick,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .pressScaleFeedback()
                .testTag("card_qr_button_${tx.id}"),
            shape = UdhaarShapeTokens.Corner12,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MutedChipGrey,
                contentColor = PureBlack
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Icon(
                imageVector = Icons.Default.QrCode2,
                contentDescription = stringResource(R.string.cd_get_qr_or_receipt_for_fmt, tx.partyName),
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.ui_str_get_qr_receipt_2aa81f),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Button(
            onClick = onWhatsAppClick,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .pressScaleFeedback()
                .testTag("card_whatsapp_button_${tx.id}"),
            shape = UdhaarShapeTokens.Corner12,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PureBlack,
                contentColor = CardSurfaceWhite
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = stringResource(R.string.cd_send_reminder_to_fmt, tx.partyName),
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.ui_str_send_reminder_1e475a),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = CardSurfaceWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
