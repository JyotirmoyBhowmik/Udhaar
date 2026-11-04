package com.jyotirmoyb.udhaar.ui.sheets.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.Installment
import com.jyotirmoyb.udhaar.data.InstallmentPlan
import com.jyotirmoyb.udhaar.data.InstallmentStatus
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.util.InstallmentCalculator
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.UpiQrGenerator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InstallmentTimelineCard(
    tx: UdhaarTransaction,
    plan: InstallmentPlan,
    installments: List<Installment>,
    lenderUpiId: String,
    onEditPlan: () -> Unit,
    onRequestNewDates: () -> Unit,
    onConfirmUpiPayment: (installment: Installment, amountDouble: Double) -> Unit,
    onMoveInstallmentPayment: (fromSequence: Int, toSequence: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val progress = remember(installments) {
        InstallmentCalculator.computeProgress(installments)
    }

    var pendingUpiConfirmationInstallment by remember { mutableStateOf<Installment?>(null) }
    var movingPaymentFromInstallment by remember { mutableStateOf<Installment?>(null) }
    val sdf = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val unpaidInstallments = remember(installments) {
        installments.sortedBy { it.sequence }.filter { it.amountPaid < it.amountDue }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header & Progress Summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(R.string.installments_timeline_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.installments_progress_fmt, progress.paidCount, progress.totalCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (progress.hasOverdue) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.badge_late),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Progress Bar
        LinearProgressIndicator(
            progress = { progress.progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        // Next Due Installment Highlight
        progress.nextDueInstallment?.let { nextDue ->
            val dueAmt = tx.currency.symbol + InterestCalculator.formatAmount(InstallmentCalculator.paiseToDouble(nextDue.amountDue - nextDue.amountPaid))
            val dueStr = sdf.format(Date(nextDue.dueDate))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.next_installment_due_fmt, dueAmt, dueStr),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                // UPI Pay Now button (only available for TAKEN or if lender UPI is configured)
                if (tx.transactionType == TransactionType.TAKEN && lenderUpiId.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            val amountDouble = InstallmentCalculator.paiseToDouble(nextDue.amountDue - nextDue.amountPaid)
                            val note = context.getString(R.string.upi_installment_note_fmt, nextDue.sequence, progress.totalCount)
                            val upiUri = UpiQrGenerator.buildUpiUri(
                                upiId = lenderUpiId,
                                payeeName = tx.partyName,
                                amountInr = amountDouble,
                                transactionNote = note
                            )
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
                            context.startActivity(Intent.createChooser(intent, "Pay via UPI"))
                            pendingUpiConfirmationInstallment = nextDue
                        },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("pay_installment_now_button")
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.pay_installment_now), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Timeline List
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            installments.sortedBy { it.sequence }.forEach { inst ->
                val isPaid = inst.status == InstallmentStatus.PAID || inst.amountPaid >= inst.amountDue
                val isLate = inst.status == InstallmentStatus.LATE || (!isPaid && System.currentTimeMillis() > inst.dueDate)
                val isPartial = inst.status == InstallmentStatus.PARTIAL || (inst.amountPaid > 0L && !isPaid)
                val dueAmt = tx.currency.symbol + InterestCalculator.formatAmount(InstallmentCalculator.paiseToDouble(inst.amountDue))
                val canMoveThisPayment = inst.amountPaid > 0L && unpaidInstallments.any { it.sequence != inst.sequence }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isLate) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        // Status Icon / Circle
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    when {
                                        isPaid -> Color(0xFF10B981) // Emerald
                                        isLate -> MaterialTheme.colorScheme.error
                                        isPartial -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.outline
                                    },
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPaid) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            } else if (isLate) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                            }
                        }

                        Column {
                            Text(
                                text = stringResource(R.string.installment_item_header_fmt, inst.sequence, dueAmt),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isPaid) FontWeight.Normal else FontWeight.SemiBold
                            )
                            val dueFormatted = sdf.format(Date(inst.dueDate))
                            val dateText = if (isPaid && inst.paidAtMillis != null) {
                                stringResource(R.string.installment_item_due_paid_fmt, dueFormatted, sdf.format(Date(inst.paidAtMillis)))
                            } else {
                                stringResource(R.string.installment_item_due_fmt, dueFormatted)
                            }
                            Text(
                                text = dateText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (canMoveThisPayment) {
                            TextButton(
                                onClick = { movingPaymentFromInstallment = inst },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.move_installment_payment_btn),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        // Status Badge
                        val (statusLabel, statusColor) = when {
                            isPaid -> stringResource(R.string.badge_paid) to Color(0xFF10B981)
                            isLate -> stringResource(R.string.badge_late) to MaterialTheme.colorScheme.error
                            isPartial -> stringResource(R.string.badge_partial) to MaterialTheme.colorScheme.tertiary
                            else -> stringResource(R.string.badge_pending) to MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }
            }
        }

        // No fake AutoPay help note on the plan screen
        Text(
            text = stringResource(R.string.no_fake_autopay_help),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("no_fake_autopay_help_text")
        )

        // Actions: Edit Plan / Request New Dates
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (tx.transactionType == TransactionType.TAKEN) {
                TextButton(
                    onClick = onRequestNewDates,
                    modifier = Modifier.testTag("request_new_dates_button")
                ) {
                    Text(stringResource(R.string.request_new_dates))
                }
            }
            TextButton(
                onClick = onEditPlan,
                modifier = Modifier.testTag("edit_installment_plan_button")
            ) {
                Text(stringResource(R.string.edit_installment_plan))
            }
        }
    }

    // Dialog to move applied payment from one installment to another unpaid installment
    movingPaymentFromInstallment?.let { sourceInst ->
        val candidateTargets = unpaidInstallments.filter { it.sequence != sourceInst.sequence }
        AlertDialog(
            onDismissRequest = { movingPaymentFromInstallment = null },
            title = {
                Text(stringResource(R.string.move_installment_payment_title, sourceInst.sequence))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.move_installment_payment_subtitle),
                        style = MaterialTheme.typography.bodySmall
                    )
                    candidateTargets.forEach { target ->
                        val remainingDouble = InstallmentCalculator.paiseToDouble(target.amountDue - target.amountPaid)
                        val remainingFormatted = "${tx.currency.symbol}${InterestCalculator.formatAmount(remainingDouble)}"
                        val dueStr = sdf.format(Date(target.dueDate))
                        OutlinedButton(
                            onClick = {
                                onMoveInstallmentPayment(sourceInst.sequence, target.sequence)
                                movingPaymentFromInstallment = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.move_installment_target_fmt,
                                    target.sequence,
                                    dueStr,
                                    remainingFormatted
                                ),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { movingPaymentFromInstallment = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    // Confirmation Dialog after UPI payment app returns
    pendingUpiConfirmationInstallment?.let { targetInst ->
        val amountDouble = InstallmentCalculator.paiseToDouble(targetInst.amountDue - targetInst.amountPaid)
        val formattedAmt = "${tx.currency.symbol}${InterestCalculator.formatAmount(amountDouble)}"
        AlertDialog(
            onDismissRequest = { pendingUpiConfirmationInstallment = null },
            title = { Text(stringResource(R.string.upi_payment_confirmation_title)) },
            text = { Text(stringResource(R.string.upi_payment_confirmation_msg, formattedAmt)) },
            confirmButton = {
                Button(
                    onClick = {
                        onConfirmUpiPayment(targetInst, amountDouble)
                        pendingUpiConfirmationInstallment = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.upi_payment_confirmed_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUpiConfirmationInstallment = null }) {
                    Text(stringResource(R.string.upi_payment_cancelled_btn))
                }
            }
        )
    }
}
