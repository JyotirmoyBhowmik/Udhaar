package com.jyotirmoyb.udhaar.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.runtime.getValue
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.Installment
import com.jyotirmoyb.udhaar.data.LedgerFilter
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.reminder.ReminderEngine
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.CardSurfaceWhite
import com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily
import com.jyotirmoyb.udhaar.ui.theme.MutedChipGrey
import com.jyotirmoyb.udhaar.ui.theme.PayableCardInk
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.ReceivableCardInk
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.ui.theme.SubtleSurfaceGrey
import com.jyotirmoyb.udhaar.ui.theme.UdhaarShapeTokens
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.WhatsAppReminderLauncher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class LedgerGroupSection(
    val key: String,
    val titleRes: Int = 0,
    val isOverdueGroup: Boolean = false,
    val items: List<CalculatedUdhaarSummary>,
    val titleText: String = ""
)

internal fun buildGroupedFilteredItems(
    filteredItems: List<CalculatedUdhaarSummary>,
    selectedFilter: LedgerFilter,
    endOfWeekMillis: Long,
    groupByPerson: Boolean = false
): List<LedgerGroupSection> {
    if (groupByPerson) {
        val grouped = filteredItems.groupBy { it.transaction.partyName.trim() }
        return grouped.map { (personName, items) ->
            val isOverdue = items.any { it.isOverdue && !it.transaction.isSettled }
            LedgerGroupSection(
                key = "person_${personName.lowercase()}",
                titleRes = 0,
                isOverdueGroup = isOverdue,
                items = items,
                titleText = personName
            )
        }
    }
    if (selectedFilter == LedgerFilter.SETTLED) {
        return listOf(
            LedgerGroupSection(
                key = "settled",
                titleRes = R.string.group_header_settled,
                isOverdueGroup = false,
                items = filteredItems
            )
        )
    }
    val overdue = mutableListOf<CalculatedUdhaarSummary>()
    val dueThisWeek = mutableListOf<CalculatedUdhaarSummary>()
    val later = mutableListOf<CalculatedUdhaarSummary>()
    val noDueDate = mutableListOf<CalculatedUdhaarSummary>()
    val settled = mutableListOf<CalculatedUdhaarSummary>()

    for (item in filteredItems) {
        val tx = item.transaction
        when {
            tx.isSettled -> settled.add(item)
            item.isOverdue -> overdue.add(item)
            tx.expiryDateMillis != null && tx.expiryDateMillis <= endOfWeekMillis -> dueThisWeek.add(item)
            tx.expiryDateMillis != null -> later.add(item)
            else -> noDueDate.add(item)
        }
    }
    return buildList {
        if (overdue.isNotEmpty()) add(LedgerGroupSection("overdue", R.string.group_header_overdue, true, overdue))
        if (dueThisWeek.isNotEmpty()) add(LedgerGroupSection("due_this_week", R.string.group_header_due_this_week, false, dueThisWeek))
        if (later.isNotEmpty()) add(LedgerGroupSection("later", R.string.group_header_later, false, later))
        if (noDueDate.isNotEmpty()) add(LedgerGroupSection("no_due_date", R.string.group_header_no_due_date, false, noDueDate))
        if (settled.isNotEmpty()) add(LedgerGroupSection("settled", R.string.group_header_settled, false, settled))
    }
}

internal fun dispatchBorrowerAction(
    context: Context,
    userSettings: UserSettings,
    summary: CalculatedUdhaarSummary,
    actionType: String,
    onRequestExtension: (CalculatedUdhaarSummary) -> Unit
) {
    val netDue = InterestCalculator.formatCurrency(summary.netBalanceDue, summary.transaction.currency)
        .removePrefix("₹").removePrefix("रू")
    when (actionType) {
        "i_paid" -> {
            val msg = ReminderEngine.renderDraftMessage(
                template = context.getString(R.string.borrower_msg_i_paid),
                name = summary.transaction.partyName,
                myName = userSettings.payeeName.ifBlank { "Me" },
                amount = netDue,
                remaining = netDue,
                paidSoFar = "",
                dueDate = "",
                daysOverdue = 0,
                upiId = userSettings.upiId
            )
            WhatsAppReminderLauncher.launchReminder(context, summary.transaction.phoneNumber, summary.transaction.currency, msg)
        }
        "need_more_time" -> onRequestExtension(summary)
        "ask_details" -> {
            val msg = ReminderEngine.renderDraftMessage(
                template = context.getString(R.string.borrower_msg_ask_details),
                name = summary.transaction.partyName,
                myName = userSettings.payeeName.ifBlank { "Me" },
                amount = netDue,
                remaining = netDue,
                paidSoFar = "",
                dueDate = "",
                daysOverdue = 0,
                upiId = userSettings.upiId
            )
            WhatsAppReminderLauncher.launchReminder(context, summary.transaction.phoneNumber, summary.transaction.currency, msg)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BorrowerExtensionDatePickerDialog(
    summary: CalculatedUdhaarSummary?,
    userSettings: UserSettings,
    onDismiss: () -> Unit
) {
    if (summary == null) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis() + 7L * 86_400_000L
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis ?: (System.currentTimeMillis() + 7L * 86_400_000L)
                    val dateFormatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(selectedMillis))
                    val netDue = InterestCalculator.formatCurrency(summary.netBalanceDue, summary.transaction.currency)
                        .removePrefix("₹").removePrefix("रू").trim()
                    val msg = ReminderEngine.renderDraftMessage(
                        template = context.getString(R.string.borrower_msg_need_more_time),
                        name = summary.transaction.partyName,
                        myName = userSettings.payeeName.ifBlank { "Me" },
                        amount = netDue,
                        remaining = netDue,
                        paidSoFar = "",
                        dueDate = "",
                        daysOverdue = 0,
                        upiId = userSettings.upiId,
                        newDate = dateFormatted
                    )
                    WhatsAppReminderLauncher.launchReminder(context, summary.transaction.phoneNumber, summary.transaction.currency, msg)
                    onDismiss()
                },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.ui_str_confirm_f6fda1))
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.ui_str_cancel_1507c4))
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
internal fun DashboardAdaptiveDetailPane(
    selectedSummary: CalculatedUdhaarSummary?,
    partyPhotoUri: String = "",
    partialPayments: List<PartialPayment> = emptyList(),
    installments: List<Installment> = emptyList(),
    isAmountsHidden: Boolean = false,
    onOpenFullDetailSheet: (CalculatedUdhaarSummary) -> Unit,
    onWhatsAppClick: (CalculatedUdhaarSummary) -> Unit,
    onQrClick: (CalculatedUdhaarSummary) -> Unit,
    onChatClick: (CalculatedUdhaarSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("adaptive_detail_pane"),
        shape = UdhaarShapeTokens.Corner16,
        color = CardSurfaceWhite,
        border = BorderStroke(1.dp, BorderGrey)
    ) {
        if (selectedSummary == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.adaptive_select_record_prompt),
                    style = MaterialTheme.typography.bodyLarge,
                    color = SecondaryInkGrey,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val tx = selectedSummary.transaction
            val isGiven = tx.transactionType == TransactionType.GIVEN
            val formattedNet = InterestCalculator.formatCurrencyPrivacy(
                selectedSummary.netBalanceDue,
                tx.currency,
                isAmountsHidden
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        PersonAvatar(
                            name = tx.partyName,
                            photoUri = partyPhotoUri,
                            size = 46.dp
                        )
                        Column {
                            Text(
                                text = tx.partyName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = PureBlack
                            )
                            Text(
                                text = selectedSummary.dueStatusText,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = IbmPlexMonoFamily),
                                color = if (selectedSummary.isOverdue) PayableCardInk else SecondaryInkGrey
                            )
                        }
                    }

                    Text(
                        text = formattedNet,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = IbmPlexMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = if (isGiven) ReceivableCardInk else PayableCardInk,
                        modifier = Modifier.testTag("adaptive_detail_amount")
                    )
                }

                HorizontalDivider(color = BorderGrey)

                Text(
                    text = InterestCalculator.formatSubtitleBreakdownPrivacy(selectedSummary, isAmountsHidden),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = IbmPlexMonoFamily),
                    color = PureBlack
                )

                if (tx.notes.isNotBlank()) {
                    Surface(
                        color = SubtleSurfaceGrey,
                        shape = UdhaarShapeTokens.Corner8,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = tx.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryInkGrey,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (partialPayments.isNotEmpty()) {
                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.ui_str_repayment_history_e95aa2),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = PureBlack
                        )
                        partialPayments.take(5).forEach { payment ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = sdf.format(Date(payment.paidAtMillis)),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = IbmPlexMonoFamily),
                                    color = SecondaryInkGrey
                                )
                                Text(
                                    text = InterestCalculator.formatCurrencyPaisePrivacy(
                                        payment.amountPaise,
                                        tx.currency,
                                        isAmountsHidden
                                    ),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = IbmPlexMonoFamily,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = ReceivableCardInk
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onOpenFullDetailSheet(selectedSummary) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("adaptive_detail_open_record_btn"),
                        shape = UdhaarShapeTokens.Corner12,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PureBlack,
                            contentColor = CardSurfaceWhite
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.ui_str_record_payment_86e563),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = { onWhatsAppClick(selectedSummary) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("adaptive_detail_reminder_btn"),
                        shape = UdhaarShapeTokens.Corner12,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.ui_str_send_reminder_1e475a),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onQrClick(selectedSummary) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("adaptive_detail_qr_btn"),
                        shape = UdhaarShapeTokens.Corner12,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MutedChipGrey,
                            contentColor = PureBlack
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.ui_str_get_qr_receipt_2aa81f),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    OutlinedButton(
                        onClick = { onChatClick(selectedSummary) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("adaptive_detail_chat_btn"),
                        shape = UdhaarShapeTokens.Corner12,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.cd_open_chat_for_fmt, tx.partyName),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun EmptyLedgerState(
    hasAnyTransactions: Boolean,
    onAddClick: () -> Unit,
    onInviteClick: () -> Unit = {},
    onResetFilter: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_ledger_box")
    ) {
        if (hasAnyTransactions) {
            FriendlyEmptyStateCard(
                sentence = stringResource(R.string.empty_filtered_ledger_sentence),
                primaryButtonLabel = stringResource(R.string.empty_filtered_clear_filters),
                onPrimaryClick = onResetFilter
            )
        } else {
            FriendlyEmptyStateCard(
                sentence = stringResource(R.string.empty_ledger_welcome_sentence),
                primaryButtonLabel = "",
                onPrimaryClick = onAddClick,
                secondaryButtonLabel = stringResource(R.string.empty_ledger_invite_friend),
                onSecondaryClick = onInviteClick,
                modifier = Modifier.testTag("empty_state_add_button")
            )
        }
    }
}

@Composable
internal fun DashboardPrimaryFab(
    isPeopleTab: Boolean,
    isFabExpanded: Boolean,
    reducedMotion: Boolean,
    onAddPartyClick: () -> Unit,
    onAddUdhaarClick: () -> Unit
) {
    val fabCorner by animateDpAsState(
        targetValue = if (isFabExpanded) 16.dp else 26.dp,
        animationSpec = if (reducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "fabCorner"
    )
    ExtendedFloatingActionButton(
        onClick = if (isPeopleTab) onAddPartyClick else onAddUdhaarClick,
        expanded = isFabExpanded,
        icon = {
            Icon(
                imageVector = if (isPeopleTab) Icons.Default.PersonAdd else Icons.Default.Add,
                contentDescription = stringResource(R.string.add_udhaar_button),
                tint = com.jyotirmoyb.udhaar.ui.theme.AccentFabInk,
                modifier = Modifier.size(22.dp)
            )
        },
        text = {
            Text(
                text = if (isPeopleTab) {
                    stringResource(R.string.ui_str_new_contact_82691e)
                } else {
                    stringResource(R.string.ui_str_new_udhaar_210bee)
                },
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp
                ),
                color = com.jyotirmoyb.udhaar.ui.theme.AccentFabInk,
                maxLines = 1
            )
        },
        containerColor = com.jyotirmoyb.udhaar.ui.theme.AccentFabGreen,
        contentColor = com.jyotirmoyb.udhaar.ui.theme.AccentFabInk,
        shape = RoundedCornerShape(fabCorner),
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 10.dp
        ),
        modifier = Modifier
            .heightIn(min = 52.dp)
            .animateContentSize(
                animationSpec = if (reducedMotion) snap() else spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .pressScaleFeedback()
            .testTag("add_udhaar_fab")
    )
}
