package com.jyotirmoyb.udhaar.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.getParsedDateProposal
import com.jyotirmoyb.udhaar.data.getParsedPaymentClaim
import com.jyotirmoyb.udhaar.ui.components.AnimatedSheetContent
import com.jyotirmoyb.udhaar.util.InterestCalculator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    transactionsWithUpdates: List<UdhaarTransaction>,
    onConfirmPaymentClaim: (transactionId: Long) -> Unit,
    onRejectPaymentClaim: (transactionId: Long, reason: String) -> Unit,
    onAcceptDateProposal: (transactionId: Long) -> Unit,
    onDeclineDateProposal: (transactionId: Long, reason: String) -> Unit,
    onSuggestCounterDate: (transactionId: Long, newDateMillis: Long) -> Unit,
    onOpenTransactionDetail: (transactionId: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var suggestingCounterDateTxId by rememberSaveable { mutableStateOf<Long?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        contentWindowInsets = { WindowInsets.navigationBars.union(WindowInsets.ime) },
        modifier = Modifier.testTag("updates_inbox_sheet")
    ) {
        AnimatedSheetContent {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .align(Alignment.CenterHorizontally)
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.inbox_updates_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.inbox_updates_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (transactionsWithUpdates.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp)
                            .testTag("inbox_empty_state"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.inbox_all_caught_up),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.inbox_no_pending_updates),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(transactionsWithUpdates, key = { it.id }) { tx ->
                            val claim = tx.getParsedPaymentClaim()
                            val dateProposal = tx.getParsedDateProposal()

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("inbox_item_${tx.id}"),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Row 1: Contact Name and Amount
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = tx.partyName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = InterestCalculator.formatPaise(tx.principalAmountPaise, tx.currency),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    // Action 1: Pending Payment Claim
                                    if (claim != null) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.Payments,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    val claimTitle = "${tx.partyName} says they paid ${InterestCalculator.formatPaise(claim.amountPaise, tx.currency)}"
                                                    Text(
                                                        text = claimTitle,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                val dateStr = InterestCalculator.formatDate(claim.dateMillis)
                                                val claimSub = "Date: $dateStr • Mode: ${claim.paymentMode.label}${if (claim.reference.isNotBlank()) " (Ref: ${claim.reference})" else ""}"
                                                Text(
                                                    text = claimSub,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                                if (claim.proofUrl.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(stringResource(R.string.inbox_proof_image_attached), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(10.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = { onConfirmPaymentClaim(tx.id) },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(40.dp)
                                                            .testTag("btn_confirm_claim_${tx.id}"),
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = MaterialTheme.colorScheme.primary,
                                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                                        )
                                                    ) {
                                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(stringResource(R.string.btn_confirm_payment), style = MaterialTheme.typography.labelSmall)
                                                    }
                                                    OutlinedButton(
                                                        onClick = { onRejectPaymentClaim(tx.id, "Not received") },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(40.dp)
                                                            .testTag("btn_reject_claim_${tx.id}"),
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = ButtonDefaults.outlinedButtonColors(
                                                            contentColor = MaterialTheme.colorScheme.error
                                                        )
                                                    ) {
                                                        Text(stringResource(R.string.btn_not_received), style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Action 2: Pending Date Proposal
                                    if (dateProposal != null && dateProposal.proposedDateMillis > 0L) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.DateRange,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    val proposedDateStr = InterestCalculator.formatDate(dateProposal.proposedDateMillis)
                                                    val propTitle = "${tx.partyName} proposes to return on $proposedDateStr"
                                                    Text(
                                                        text = propTitle,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                if (dateProposal.message.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    val propMsg = "“${dateProposal.message}”"
                                                    Text(
                                                        text = propMsg,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(10.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Button(
                                                        onClick = { onAcceptDateProposal(tx.id) },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(40.dp)
                                                            .testTag("btn_accept_date_${tx.id}"),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text(stringResource(R.string.btn_accept), style = MaterialTheme.typography.labelSmall)
                                                    }
                                                    OutlinedButton(
                                                        onClick = { onDeclineDateProposal(tx.id, "Declined") },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(40.dp)
                                                            .testTag("btn_decline_date_${tx.id}"),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text(stringResource(R.string.btn_decline), style = MaterialTheme.typography.labelSmall)
                                                    }
                                                    OutlinedButton(
                                                        onClick = { suggestingCounterDateTxId = tx.id },
                                                        modifier = Modifier
                                                            .weight(1.2f)
                                                            .height(40.dp)
                                                            .testTag("btn_suggest_date_${tx.id}"),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Text(stringResource(R.string.btn_suggest_date), style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Status Summary line
                                    if (tx.lastGuestAction.isNotBlank() && claim == null && dateProposal == null) {
                                        Text(
                                            text = when (tx.lastGuestAction) {
                                                "CONFIRMED" -> "✓ Confirmed by ${tx.partyName}"
                                                "DISPUTED" -> "! Disputed by ${tx.partyName}: ${tx.disputeReason.ifBlank { "Terms disputed" }}"
                                                "PAYMENT_CONFIRMED" -> "✓ Payment claim accepted"
                                                "PAYMENT_REJECTED" -> "✕ Payment claim marked not received"
                                                "DATE_ACCEPTED" -> "✓ Proposed due date accepted"
                                                "DATE_DECLINED" -> "✕ Proposed due date declined"
                                                else -> "Last action: ${tx.lastGuestAction}"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = if (tx.lastGuestAction.contains("REJECT") || tx.lastGuestAction == "DISPUTED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    TextButton(
                                        onClick = {
                                            onOpenTransactionDetail(tx.id)
                                            onDismiss()
                                        },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text(stringResource(R.string.inbox_view_record_detail), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Counter Date Picker Dialog
    val counterTxId = suggestingCounterDateTxId
    if (counterTxId != null) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis() + (7L * 24 * 60 * 60 * 1000)
        )
        DatePickerDialog(
            onDismissRequest = { suggestingCounterDateTxId = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = datePickerState.selectedDateMillis
                        if (selected != null && selected > 0L) {
                            onSuggestCounterDate(counterTxId, selected)
                        }
                        suggestingCounterDateTxId = null
                    }
                ) {
                    Text(stringResource(R.string.btn_suggest))
                }
            },
            dismissButton = {
                TextButton(onClick = { suggestingCounterDateTxId = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
