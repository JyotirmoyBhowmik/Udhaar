package com.jyotirmoyb.udhaar.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jyotirmoyb.udhaar.BuildConfig
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.notification.ReminderReceiver
import com.jyotirmoyb.udhaar.ui.QrModalState
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.MutedChipGrey
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.ui.theme.SubtleSurfaceGrey
import com.jyotirmoyb.udhaar.ui.theme.activePaletteState
import com.jyotirmoyb.udhaar.ui.theme.paletteForTheme
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.ThermalReceiptGenerator
import com.jyotirmoyb.udhaar.util.UpiQrGenerator
import kotlin.math.roundToLong

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailModal(
    summary: CalculatedUdhaarSummary,
    partialPayments: List<PartialPayment>,
    linkedParty: PartyEntity?,
    onDismiss: () -> Unit,
    onRecordPartialPayment: (Double, PaymentMode, String) -> Unit,
    onDeletePartialPayment: (PartialPayment) -> Unit,
    onToggleSettled: () -> Unit,
    onEdit: () -> Unit,
    onDeleteTransaction: () -> Unit,
    onOpenQr: () -> Unit,
    onPayPartyViaUpi: (String, String, Double?) -> Unit,
    onShareReceipt: () -> Unit,
    onOpenWhatsApp: () -> Unit
) {
    val tx = summary.transaction
    val currency = tx.currency
    var partialAmountText by rememberSaveable { mutableStateOf("") }
    var partialNoteText by rememberSaveable { mutableStateOf("") }
    var selectedInstallmentMode by rememberSaveable { mutableStateOf(tx.paymentMode) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PureBlack,
        unfocusedBorderColor = BorderGrey,
        focusedLabelColor = PureBlack,
        unfocusedLabelColor = SecondaryInkGrey,
        cursorColor = PureBlack,
        focusedContainerColor = PureWhite,
        unfocusedContainerColor = SubtleSurfaceGrey
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(1.5.dp, PureBlack, RoundedCornerShape(4.dp)),
            shape = RoundedCornerShape(4.dp),
            color = PureWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tx.transactionType.badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryInkGrey
                        )
                        Text(
                            text = tx.partyName,
                            style = MaterialTheme.typography.headlineMedium,
                            color = PureBlack
                        )
                        if (tx.phoneNumber.isNotBlank()) {
                            Text(
                                text = tx.phoneNumber,
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryInkGrey
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_detail_modal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = PureBlack
                        )
                    }
                }

                // Black Summary Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PureBlack, RoundedCornerShape(4.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (tx.isSettled) "SETTLED RECORD" else "NET OUTSTANDING DUE",
                            style = MaterialTheme.typography.labelSmall,
                            color = BorderGrey
                        )
                        Text(
                            text = "${tx.paymentMode.label.uppercase()} • ${currency.code}",
                            style = MaterialTheme.typography.labelSmall,
                            color = BorderGrey
                        )
                    }
                    Text(
                        text = InterestCalculator.formatCurrency(summary.netBalanceDue, currency),
                        style = MaterialTheme.typography.displayMedium,
                        color = PureWhite
                    )
                    Text(
                        text = summary.subtitleBreakdown,
                        style = MaterialTheme.typography.bodySmall,
                        color = BorderGrey
                    )
                }

                // Detailed Breakdown Rows
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
                        .background(SubtleSurfaceGrey, RoundedCornerShape(4.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BreakdownRow("Principal Amount", InterestCalculator.formatCurrency(summary.principal, currency))
                    BreakdownRow(
                        "Accrued Interest (${summary.elapsedDays}d)",
                        InterestCalculator.formatCurrency(summary.accruedInterest, currency)
                    )
                    BreakdownRow("Gross Total", InterestCalculator.formatCurrency(summary.grossTotal, currency))
                    BreakdownRow("Total Repaid So Far", "- ${InterestCalculator.formatCurrency(summary.amountPaid, currency)}")
                    HorizontalDivider(color = BorderGrey, thickness = 1.dp)
                    BreakdownRow(
                        "Remaining Balance",
                        InterestCalculator.formatCurrency(summary.netBalanceDue, currency),
                        bold = true
                    )
                    BreakdownRow("Start Date", InterestCalculator.formatDate(tx.startDateMillis))
                    if (tx.expiryDateMillis != null) {
                        BreakdownRow("Due / Expiry Date", InterestCalculator.formatDate(tx.expiryDateMillis))
                    }
                    if (tx.notes.isNotBlank()) {
                        BreakdownRow("Notes", tx.notes)
                    }
                }

                // Enhancement C: One-Tap Partial Repayment Quick-Chips & Installment Logger
                if (!tx.isSettled) {
                    val remaining = summary.netBalanceDue
                    val chip25 = ((remaining * 0.25) * 100.0).roundToLong() / 100.0
                    val chip50 = ((remaining * 0.50) * 100.0).roundToLong() / 100.0
                    val chipInterestOnly = ((summary.accruedInterest.coerceAtMost(remaining)) * 100.0).roundToLong() / 100.0
                    val chip100 = ((remaining) * 100.0).roundToLong() / 100.0

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, PureBlack, RoundedCornerShape(4.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "LOG PARTIAL REPAYMENT • ONE-TAP QUICK CHIPS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = PureBlack
                        )

                        // Quick-Chips Row: [ 25% ] [ 50% ] [ Interest Only ] [ 100% Full Settle ]
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            QuickRepaymentChip(
                                label = "25% (${InterestCalculator.formatCurrency(chip25, currency)})",
                                tag = "quick_chip_25",
                                onClick = {
                                    partialAmountText = InterestCalculator.formatPlainAmount(chip25)
                                    partialNoteText = "25% Partial Installment"
                                }
                            )
                            QuickRepaymentChip(
                                label = "50% (${InterestCalculator.formatCurrency(chip50, currency)})",
                                tag = "quick_chip_50",
                                onClick = {
                                    partialAmountText = InterestCalculator.formatPlainAmount(chip50)
                                    partialNoteText = "50% Partial Installment"
                                }
                            )
                            if (chipInterestOnly > 0.0) {
                                QuickRepaymentChip(
                                    label = "Interest Only (${InterestCalculator.formatCurrency(chipInterestOnly, currency)})",
                                    tag = "quick_chip_interest",
                                    onClick = {
                                        partialAmountText = InterestCalculator.formatPlainAmount(chipInterestOnly)
                                        partialNoteText = "Accrued Interest Settlement"
                                    }
                                )
                            }
                            QuickRepaymentChip(
                                label = "100% Full Settle (${InterestCalculator.formatCurrency(chip100, currency)})",
                                tag = "quick_chip_100",
                                onClick = {
                                    partialAmountText = InterestCalculator.formatPlainAmount(chip100)
                                    partialNoteText = "100% Full Balance Settlement"
                                }
                            )
                        }

                        // Payment Mode Selector for the Installment
                        Text(
                            text = "INSTALLMENT PAYMENT MODE",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryInkGrey
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PaymentMode.entries.forEach { mode ->
                                MonochromeTogglePill(
                                    text = mode.shortBadge,
                                    selected = selectedInstallmentMode == mode,
                                    onClick = { selectedInstallmentMode = mode },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("partial_mode_${mode.name.lowercase()}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = partialAmountText,
                                onValueChange = { input ->
                                    if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) {
                                        partialAmountText = input
                                    }
                                },
                                label = { Text("Amount (${currency.symbol})") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = textFieldColors,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("partial_payment_amount_input")
                            )
                            OutlinedTextField(
                                value = partialNoteText,
                                onValueChange = { partialNoteText = it },
                                label = { Text("Note (Optional)") },
                                singleLine = true,
                                colors = textFieldColors,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("partial_payment_note_input")
                            )
                        }
                        Button(
                            onClick = {
                                val amt = partialAmountText.toDoubleOrNull() ?: 0.0
                                if (amt > 0.0) {
                                    onRecordPartialPayment(amt, selectedInstallmentMode, partialNoteText)
                                    partialAmountText = ""
                                    partialNoteText = ""
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("add_partial_payment_button"),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PureBlack,
                                contentColor = PureWhite
                            )
                        ) {
                            Text("RECORD PARTIAL PAYMENT", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                // Enhancement C: Running Chronological Audit Trail of Partial Repayments
                if (partialPayments.isNotEmpty()) {
                    val chronologicalPayments = remember(partialPayments) {
                        partialPayments.sortedBy { it.paidAtMillis }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
                            .padding(12.dp)
                            .testTag("partial_repayment_audit_trail"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "RUNNING REPAYMENT AUDIT TRAIL (${chronologicalPayments.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = PureBlack
                        )
                        var cumulativePaid = 0.0
                        chronologicalPayments.forEachIndexed { index, payment ->
                            cumulativePaid += payment.amount
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "#${index + 1} • ${InterestCalculator.formatCurrency(payment.amount, currency)}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = PureBlack
                                        )
                                        Box(
                                            modifier = Modifier
                                                .background(MutedChipGrey, RoundedCornerShape(2.dp))
                                                .border(0.5.dp, BorderGrey, RoundedCornerShape(2.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = payment.paymentMode.shortBadge,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PureBlack
                                            )
                                        }
                                    }
                                    val sub = buildString {
                                        append(InterestCalculator.formatDateTime(payment.paidAtMillis))
                                        if (payment.note.isNotBlank()) {
                                            append(" • ${payment.note}")
                                        }
                                        append(" (Cumul: ${InterestCalculator.formatCurrency(cumulativePaid, currency)})")
                                    }
                                    Text(
                                        text = sub,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SecondaryInkGrey
                                    )
                                }
                                IconButton(onClick = { onDeletePartialPayment(payment) }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Remove payment",
                                        tint = PureBlack
                                    )
                                }
                            }
                            HorizontalDivider(color = BorderGrey, thickness = 0.5.dp)
                        }
                    }
                }

                // Enhancement B: Share Thermal-Style Monochrome Statement Receipt (Image & PDF)
                Button(
                    onClick = onShareReceipt,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("detail_share_receipt_button"),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PureBlack,
                        contentColor = PureWhite
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SHARE RECEIPT / STATEMENT (IMAGE & PDF)",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Quick Action Buttons (WhatsApp, UPI QR for GIVEN, Pay Party via UPI for TAKEN)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenWhatsApp,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("detail_whatsapp_button"),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, PureBlack),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WHATSAPP", style = MaterialTheme.typography.labelMedium)
                    }

                    if (currency == CurrencyType.INR && tx.transactionType == TransactionType.GIVEN) {
                        OutlinedButton(
                            onClick = onOpenQr,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("detail_upi_qr_button"),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, PureBlack),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("UPI QR", style = MaterialTheme.typography.labelMedium)
                        }
                    } else if (currency == CurrencyType.INR && tx.transactionType == TransactionType.TAKEN) {
                        OutlinedButton(
                            onClick = {
                                onPayPartyViaUpi(
                                    tx.partyName,
                                    linkedParty?.upiId.orEmpty(),
                                    summary.netBalanceDue.takeIf { it > 0.0 }
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("detail_pay_party_upi_button"),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, PureBlack),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PAY PARTY UPI", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Button(
                    onClick = onToggleSettled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("toggle_settled_button"),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (tx.isSettled) MutedChipGrey else PureBlack,
                        contentColor = if (tx.isSettled) PureBlack else PureWhite
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (tx.isSettled) "REOPEN RECORD (MARK UNSETTLED)" else "MARK FULLY SETTLED",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Edit & Delete Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("edit_transaction_button"),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, BorderGrey),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("EDIT", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onDeleteTransaction,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("delete_transaction_button"),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, BorderGrey),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("DELETE", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickRepaymentChip(
    label: String,
    tag: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(3.dp),
        border = BorderStroke(1.dp, PureBlack),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SubtleSurfaceGrey,
            contentColor = PureBlack
        ),
        modifier = Modifier
            .height(34.dp)
            .testTag(tag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = PureBlack
        )
    }
}

@Composable
private fun BreakdownRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (bold) PureBlack else SecondaryInkGrey,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = PureBlack,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/**
 * Enhancement B: Thermal-Style Monochrome Statement Receipt Preview & Share Modal (Image PNG & PDF)
 */
@Composable
fun ThermalReceiptModal(
    title: String,
    subtitle: String,
    receiptBitmap: Bitmap,
    fileNamePrefix: String,
    shareCaption: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(1.5.dp, PureBlack, RoundedCornerShape(4.dp)),
            shape = RoundedCornerShape(4.dp),
            color = PureWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = PureBlack
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryInkGrey
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_receipt_modal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = PureBlack
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, PureBlack, RoundedCornerShape(4.dp))
                        .background(PureWhite, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                        .testTag("thermal_receipt_preview_box"),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = receiptBitmap.asImageBitmap(),
                        contentDescription = "Thermal Monochrome Statement Receipt",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            ThermalReceiptGenerator.shareReceiptBitmapAsPng(
                                context = context,
                                bitmap = receiptBitmap,
                                fileNamePrefix = fileNamePrefix,
                                captionText = shareCaption
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("share_receipt_png_button"),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PureBlack,
                            contentColor = PureWhite
                        )
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SHARE IMAGE (PNG)", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            ThermalReceiptGenerator.shareReceiptBitmapAsPdf(
                                context = context,
                                bitmap = receiptBitmap,
                                fileNamePrefix = fileNamePrefix,
                                captionText = shareCaption
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("share_receipt_pdf_button"),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.5.dp, PureBlack),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SHARE PDF", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun UpiQrCodeModal(
    qrState: QrModalState,
    userSettings: UserSettings,
    onSaveUpiSettings: (String, String, CurrencyType) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isPayToParty = qrState.isPayToPartyMode

    var upiIdInput by rememberSaveable(qrState.customUpiId, userSettings.upiId) {
        mutableStateOf(qrState.customUpiId ?: userSettings.upiId)
    }
    var payeeNameInput by rememberSaveable(qrState.partyName, userSettings.payeeName, isPayToParty) {
        mutableStateOf(
            if (isPayToParty) {
                qrState.partyName ?: "Party"
            } else {
                userSettings.payeeName
            }
        )
    }
    var amountInput by rememberSaveable(qrState.initialAmountInr) {
        mutableStateOf(
            qrState.initialAmountInr?.takeIf { it > 0.0 }?.let {
                InterestCalculator.formatPlainAmount(it)
            } ?: ""
        )
    }

    val amountVal = amountInput.toDoubleOrNull()?.takeIf { it > 0.0 }
    val upiUri = remember(upiIdInput, payeeNameInput, amountVal, qrState.partyName) {
        if (upiIdInput.trim().isEmpty()) {
            ""
        } else {
            UpiQrGenerator.buildUpiUri(
                upiId = upiIdInput,
                payeeName = payeeNameInput,
                amountInr = amountVal,
                transactionNote = qrState.partyName?.let { "Udhaar settlement - $it" }
            )
        }
    }

    val qrBitmap = remember(upiUri) {
        if (upiUri.isBlank()) null else UpiQrGenerator.generateQrBitmap(upiUri, sizePx = 560)
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PureBlack,
        unfocusedBorderColor = BorderGrey,
        focusedLabelColor = PureBlack,
        unfocusedLabelColor = SecondaryInkGrey,
        cursorColor = PureBlack,
        focusedContainerColor = PureWhite,
        unfocusedContainerColor = SubtleSurfaceGrey
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .border(1.5.dp, PureBlack, RoundedCornerShape(4.dp)),
            shape = RoundedCornerShape(4.dp),
            color = PureWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isPayToParty) "PAY PARTY VIA UPI" else "UPI QR CODE GENERATOR",
                            style = MaterialTheme.typography.titleLarge,
                            color = PureBlack
                        )
                        Text(
                            text = when {
                                isPayToParty -> "DIRECT PAYBACK TO ${payeeNameInput.uppercase()} • INR (₹)"
                                qrState.partyName != null -> "FOR ${qrState.partyName.uppercase()} • INR (₹)"
                                else -> "GLOBAL INR (₹) GPAY / PHONEPE / PAYTM QR"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryInkGrey
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_qr_modal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = PureBlack
                        )
                    }
                }

                // UPI ID & Payee Name quick fields
                OutlinedTextField(
                    value = upiIdInput,
                    onValueChange = { upiIdInput = it },
                    label = { Text(if (isPayToParty) "Party's UPI ID (VPA) *" else "Your UPI ID (VPA) *") },
                    placeholder = { Text("e.g. yourname@okaxis") },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("qr_upi_id_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = payeeNameInput,
                        onValueChange = { payeeNameInput = it },
                        label = { Text("Payee Name") },
                        placeholder = { Text("Payee Name") },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("qr_payee_name_input")
                    )
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) {
                                amountInput = input
                            }
                        },
                        label = { Text("Amount (₹)") },
                        placeholder = { Text("Optional") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = textFieldColors,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("qr_amount_input")
                    )
                }

                // Clean White Monochrome QR Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, PureBlack, RoundedCornerShape(4.dp))
                        .background(PureWhite, RoundedCornerShape(4.dp))
                        .padding(16.dp)
                        .testTag("upi_qr_card_box"),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = payeeNameInput.ifBlank { "UDHAAR PAYEE" }.uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = PureBlack
                            )
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "UPI QR Code for $upiIdInput",
                                modifier = Modifier.size(220.dp)
                            )
                            if (amountVal != null) {
                                Text(
                                    text = "₹${InterestCalculator.formatPlainAmount(amountVal)}",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = PureBlack
                                )
                            }
                            Text(
                                text = upiUri,
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryInkGrey,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = null,
                                tint = PureBlack,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Enter a valid UPI ID (e.g. name@upi) above to generate a scannable monochrome QR code offline.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SecondaryInkGrey,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                if (isPayToParty && upiUri.isNotBlank()) {
                    Button(
                        onClick = {
                            val payIntent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching {
                                context.startActivity(payIntent)
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    "No UPI payment app installed on this device. Scan the QR code above.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("launch_upi_app_button"),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PureBlack,
                            contentColor = PureWhite
                        )
                    ) {
                        Text(
                            text = "OPEN GPAY / PHONEPE / PAYTM TO PAY",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // Save UPI Settings & Share QR Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isPayToParty) {
                        OutlinedButton(
                            onClick = {
                                onSaveUpiSettings(upiIdInput, payeeNameInput, userSettings.defaultCurrency)
                                Toast.makeText(context, "UPI Profile saved", Toast.LENGTH_SHORT).show()
                            },
                            enabled = upiIdInput.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("save_qr_profile_button"),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, PureBlack),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                        ) {
                            Text("SAVE UPI ID", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    Button(
                        onClick = {
                            if (!isPayToParty) {
                                onSaveUpiSettings(upiIdInput, payeeNameInput, userSettings.defaultCurrency)
                            }
                            UpiQrGenerator.shareQrCardImage(
                                context = context,
                                upiId = upiIdInput,
                                payeeName = payeeNameInput,
                                amountInr = amountVal,
                                partyName = qrState.partyName
                            )
                        },
                        enabled = upiIdInput.isNotBlank(),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("share_qr_image_button"),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PureBlack,
                            contentColor = PureWhite
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SHARE QR IMAGE", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun ShareUdhaarAppModal(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val playStoreUrl = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"
    val qrBitmap = remember(playStoreUrl) {
        UpiQrGenerator.generateQrBitmap(playStoreUrl, sizePx = 560)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(1.5.dp, PureBlack, RoundedCornerShape(4.dp)),
            shape = RoundedCornerShape(4.dp),
            color = PureWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SHARE / GIVE ACCESS TO UDHAAR",
                            style = MaterialTheme.typography.titleLarge,
                            color = PureBlack
                        )
                        Text(
                            text = "SCAN TO INSTALL FROM GOOGLE PLAY STORE",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryInkGrey
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = PureBlack)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, PureBlack, RoundedCornerShape(4.dp))
                        .background(PureWhite, RoundedCornerShape(4.dp))
                        .padding(16.dp)
                        .testTag("play_store_qr_box"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "UDHAAR • MONOCHROME LEDGER",
                            style = MaterialTheme.typography.titleMedium,
                            color = PureBlack
                        )
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Play Store QR Code for Udhaar",
                                modifier = Modifier.size(210.dp)
                            )
                        }
                        Text(
                            text = playStoreUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryInkGrey,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Text(
                    text = "Anyone nearby can scan this QR code with their phone camera to open Udhaar on Google Play. For Closed/Internal testing tracks, ensure their email is added to your Play Console tester list or share the invite link below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryInkGrey,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = {
                        val inviteMessage = buildString {
                            append("Install *Udhaar* — Minimalist Monochrome Loan & Ledger App (INR ₹ & NPR रू):\n")
                            append(playStoreUrl)
                            append("\n\nIf you need tester access or have questions, contact Admin: admin@jyotirmoyb.com")
                        }
                        val waUri = Uri.parse("https://wa.me/?text=${Uri.encode(inviteMessage)}")
                        val waIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching {
                            context.startActivity(waIntent)
                        }.onFailure {
                            val fallback = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, inviteMessage)
                            }
                            context.startActivity(Intent.createChooser(fallback, "Share Udhaar App"))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("share_app_whatsapp_button"),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PureBlack,
                        contentColor = PureWhite
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SHARE APP VIA WHATSAPP", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun WhatsAppReminderModal(
    summary: CalculatedUdhaarSummary,
    userSettings: UserSettings,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tx = summary.transaction

    var hideExactAmount by rememberSaveable { mutableStateOf(false) }
    var includeUpiInMessage by rememberSaveable { mutableStateOf(true) }
    var editablePhone by rememberSaveable(tx.phoneNumber) { mutableStateOf(tx.phoneNumber) }

    val generatedMessage = remember(summary, hideExactAmount, userSettings.upiId, includeUpiInMessage) {
        InterestCalculator.buildWhatsAppReminderMessage(
            summary = summary,
            hideExactAmount = hideExactAmount,
            upiId = userSettings.upiId,
            includeUpiId = includeUpiInMessage
        )
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PureBlack,
        unfocusedBorderColor = BorderGrey,
        focusedLabelColor = PureBlack,
        unfocusedLabelColor = SecondaryInkGrey,
        cursorColor = PureBlack,
        focusedContainerColor = PureWhite,
        unfocusedContainerColor = SubtleSurfaceGrey
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .border(1.5.dp, PureBlack, RoundedCornerShape(4.dp)),
            shape = RoundedCornerShape(4.dp),
            color = PureWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "WHATSAPP REMINDER ENGINE",
                            style = MaterialTheme.typography.titleLarge,
                            color = PureBlack
                        )
                        Text(
                            text = "TO: ${tx.partyName.uppercase()} • ${InterestCalculator.formatCurrency(summary.netBalanceDue, tx.currency)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryInkGrey
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_whatsapp_modal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = PureBlack
                        )
                    }
                }

                OutlinedTextField(
                    value = editablePhone,
                    onValueChange = { editablePhone = it },
                    label = { Text("Recipient WhatsApp Number") },
                    placeholder = { Text("${tx.currency.defaultCountryCode} 98XXXXXXXX") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("whatsapp_phone_input")
                )

                // Privacy Toggle: [x] Hide Exact Amount in Message
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, PureBlack, RoundedCornerShape(4.dp))
                        .background(
                            if (hideExactAmount) PureBlack else SubtleSurfaceGrey,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable { hideExactAmount = !hideExactAmount }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("hide_exact_amount_checkbox"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = hideExactAmount,
                        onCheckedChange = { hideExactAmount = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = PureWhite,
                            uncheckedColor = PureBlack,
                            checkmarkColor = PureBlack
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Hide Exact Amount in Message",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (hideExactAmount) PureWhite else PureBlack
                        )
                        Text(
                            text = if (hideExactAmount) {
                                "Privacy Mode ON • Masks principal & interest figures"
                            } else {
                                "Standard Mode • Includes principal & interest breakdown"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hideExactAmount) BorderGrey else SecondaryInkGrey
                        )
                    }
                }

                // Optional UPI ID inclusion toggle (when INR & UPI ID is configured & Amount is visible)
                if (!hideExactAmount && tx.currency == CurrencyType.INR && userSettings.upiId.isNotBlank() && tx.transactionType == TransactionType.GIVEN) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { includeUpiInMessage = !includeUpiInMessage }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeUpiInMessage,
                            onCheckedChange = { includeUpiInMessage = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = PureBlack,
                                uncheckedColor = PureBlack,
                                checkmarkColor = PureWhite
                            )
                        )
                        Text(
                            text = "Append UPI ID (${userSettings.upiId}) to message",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PureBlack
                        )
                    }
                }

                // Polite Message Preview Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
                        .background(SubtleSurfaceGrey, RoundedCornerShape(4.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "MESSAGE PREVIEW",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryInkGrey
                    )
                    Text(
                        text = generatedMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PureBlack,
                        modifier = Modifier.testTag("whatsapp_message_preview_text")
                    )
                }

                // Send via WhatsApp Action
                Button(
                    onClick = {
                        val sanitizedPhone = InterestCalculator.sanitizePhoneForWhatsApp(
                            editablePhone,
                            tx.currency
                        )
                        val encodedText = Uri.encode(generatedMessage)
                        val waUrl = if (sanitizedPhone.isNotEmpty()) {
                            "https://wa.me/$sanitizedPhone?text=$encodedText"
                        } else {
                            "https://wa.me/?text=$encodedText"
                        }
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching {
                            context.startActivity(intent)
                        }.onFailure {
                            Toast.makeText(
                                context,
                                "Opening share menu for WhatsApp message",
                                Toast.LENGTH_SHORT
                            ).show()
                            val fallback = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, generatedMessage)
                            }
                            context.startActivity(Intent.createChooser(fallback, "Send Reminder"))
                        }
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("send_whatsapp_intent_button"),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PureBlack,
                        contentColor = PureWhite
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SEND VIA WHATSAPP", style = MaterialTheme.typography.labelLarge)
                }

                // Trigger Local Notification Now (To test Notification -> Quick-Share workflow)
                OutlinedButton(
                    onClick = {
                        ReminderReceiver.showNotification(
                            context = context,
                            transactionId = tx.id,
                            partyName = tx.partyName,
                            formattedAmount = InterestCalculator.formatCurrency(summary.netBalanceDue, tx.currency),
                            txTypeLabel = tx.transactionType.label
                        )
                        Toast.makeText(
                            context,
                            "Reminder notification sent! Tap it in the status bar to launch quick-share.",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("test_notification_button"),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, BorderGrey),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("TEST SCHEDULED NOTIFICATION ALERT", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun ProfileSettingsModal(
    currentSettings: UserSettings,
    onSave: (String, String, CurrencyType, Boolean, Boolean, AppThemeMode, Boolean, String) -> Unit,
    onLockAppNow: () -> Unit,
    onOpenShareAppModal: () -> Unit,
    onExportFullLedgerSaf: () -> Unit,
    onShareFullLedgerFile: () -> Unit,
    onImportLedgerSaf: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var upiId by rememberSaveable(currentSettings.upiId) { mutableStateOf(currentSettings.upiId) }
    var payeeName by rememberSaveable(currentSettings.payeeName) { mutableStateOf(currentSettings.payeeName) }
    var inrEnabled by rememberSaveable(currentSettings.inrEnabled) { mutableStateOf(currentSettings.inrEnabled) }
    var nprEnabled by rememberSaveable(currentSettings.nprEnabled) { mutableStateOf(currentSettings.nprEnabled) }
    var defaultCurrency by rememberSaveable(currentSettings.defaultCurrency) {
        mutableStateOf(currentSettings.defaultCurrency)
    }
    var selectedThemeMode by rememberSaveable(currentSettings.themeMode) {
        mutableStateOf(currentSettings.themeMode)
    }
    var biometricLockEnabled by rememberSaveable(currentSettings.biometricLockEnabled) {
        mutableStateOf(currentSettings.biometricLockEnabled)
    }
    var securityPin by rememberSaveable(currentSettings.securityPin) {
        mutableStateOf(currentSettings.securityPin)
    }

    val playStoreUrl = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"
    val miniPlayQrBitmap = remember(playStoreUrl) {
        UpiQrGenerator.generateQrBitmap(playStoreUrl, sizePx = 320)
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PureBlack,
        unfocusedBorderColor = BorderGrey,
        focusedLabelColor = PureBlack,
        unfocusedLabelColor = SecondaryInkGrey,
        cursorColor = PureBlack,
        focusedContainerColor = PureWhite,
        unfocusedContainerColor = SubtleSurfaceGrey
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .border(1.5.dp, PureBlack, RoundedCornerShape(4.dp)),
            shape = RoundedCornerShape(4.dp),
            color = PureWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SETTINGS, CURRENCY, THEME & SUPPORT",
                            style = MaterialTheme.typography.titleLarge,
                            color = PureBlack
                        )
                        Text(
                            text = "INR / NPR TOGGLES, MULTI-THEME, VPA & ADMIN",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryInkGrey
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_settings_modal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = PureBlack
                        )
                    }
                }

                // 1. Currency Enable / Disable Section (NPR Disabled by Default)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, PureBlack, RoundedCornerShape(4.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "CURRENCY ENABLE / DISABLE (NPR DISABLED BY DEFAULT)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = PureBlack
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Indian Rupee — INR (₹)",
                                style = MaterialTheme.typography.titleSmall,
                                color = PureBlack
                            )
                            Text(
                                text = if (inrEnabled) "Enabled in Ledger & Summary" else "Disabled (Hidden)",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryInkGrey
                            )
                        }
                        Switch(
                            checked = inrEnabled,
                            onCheckedChange = { checked ->
                                inrEnabled = checked
                                if (!checked && !nprEnabled) {
                                    nprEnabled = true
                                    defaultCurrency = CurrencyType.NPR
                                } else if (checked && !nprEnabled) {
                                    defaultCurrency = CurrencyType.INR
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureWhite,
                                checkedTrackColor = PureBlack,
                                uncheckedThumbColor = PureBlack,
                                uncheckedTrackColor = PureWhite,
                                uncheckedBorderColor = PureBlack
                            ),
                            modifier = Modifier.testTag("settings_enable_inr_switch")
                        )
                    }

                    HorizontalDivider(color = BorderGrey, thickness = 1.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Nepalese Rupee — NPR (रू)",
                                style = MaterialTheme.typography.titleSmall,
                                color = PureBlack
                            )
                            Text(
                                text = if (nprEnabled) "Enabled in Ledger & Summary" else "Disabled by default (Toggle ON to use रू)",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryInkGrey
                            )
                        }
                        Switch(
                            checked = nprEnabled,
                            onCheckedChange = { checked ->
                                nprEnabled = checked
                                if (!checked && !inrEnabled) {
                                    inrEnabled = true
                                    defaultCurrency = CurrencyType.INR
                                } else if (!checked && defaultCurrency == CurrencyType.NPR) {
                                    defaultCurrency = CurrencyType.INR
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureWhite,
                                checkedTrackColor = PureBlack,
                                uncheckedThumbColor = PureBlack,
                                uncheckedTrackColor = PureWhite,
                                uncheckedBorderColor = PureBlack
                            ),
                            modifier = Modifier.testTag("settings_enable_npr_switch")
                        )
                    }
                }

                // 2. Multi-Theme Appearance Selector
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "APP THEME & VISUAL APPEARANCE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = PureBlack
                    )
                    AppThemeMode.entries.forEach { mode ->
                        val isSelected = selectedThemeMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) PureBlack else BorderGrey,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .background(
                                    color = if (isSelected) PureBlack else SubtleSurfaceGrey,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    selectedThemeMode = mode
                                    activePaletteState = paletteForTheme(mode)
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("theme_option_${mode.name.lowercase()}"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isSelected) PureWhite else PureBlack
                                )
                                Text(
                                    text = mode.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) BorderGrey else SecondaryInkGrey
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected Theme",
                                    tint = PureWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Profile, UPI & Biometric Configuration (Enhancement A)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "PROFILE, UPI VPA & APP LOCK CONFIGURATION",
                        style = MaterialTheme.typography.labelMedium,
                        color = SecondaryInkGrey
                    )

                    OutlinedTextField(
                        value = payeeName,
                        onValueChange = { payeeName = it },
                        label = { Text("Payee / Business Name") },
                        placeholder = { Text("e.g. Jyoti Enterprises") },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_payee_name_input")
                    )

                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it },
                        label = { Text("Your UPI ID (VPA) for INR Settlements") },
                        placeholder = { Text("e.g. merchant@okicici") },
                        singleLine = true,
                        colors = textFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_upi_id_input")
                    )

                    if (inrEnabled && nprEnabled) {
                        Text(
                            text = "DEFAULT LEDGER CURRENCY",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryInkGrey
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CurrencyType.entries.forEach { curr ->
                                MonochromeTogglePill(
                                    text = curr.displayName,
                                    selected = defaultCurrency == curr,
                                    onClick = { defaultCurrency = curr },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("settings_currency_${curr.name.lowercase()}")
                                )
                            }
                        }
                    }

                    // Enhancement A: Biometric / PIN App Lock Configuration
                    HorizontalDivider(color = BorderGrey, thickness = 1.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "App Lock (Biometric / Device PIN)",
                                style = MaterialTheme.typography.titleSmall,
                                color = PureBlack
                            )
                            Text(
                                text = "Require Fingerprint / Face Unlock or PIN when opening Udhaar",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryInkGrey
                            )
                        }
                        Switch(
                            checked = biometricLockEnabled,
                            onCheckedChange = { biometricLockEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = PureWhite,
                                checkedTrackColor = PureBlack,
                                uncheckedThumbColor = PureBlack,
                                uncheckedTrackColor = PureWhite,
                                uncheckedBorderColor = PureBlack
                            ),
                            modifier = Modifier.testTag("biometric_lock_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = securityPin,
                            onValueChange = { input ->
                                if (input.length <= 8 && input.all { it.isDigit() }) {
                                    securityPin = input
                                }
                            },
                            label = { Text("Fallback Security PIN") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            colors = textFieldColors,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_security_pin_input")
                        )
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onLockAppNow()
                            },
                            modifier = Modifier
                                .height(56.dp)
                                .padding(top = 6.dp)
                                .testTag("lock_app_now_button"),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, PureBlack),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("LOCK NOW", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Button(
                        onClick = {
                            onSave(
                                upiId,
                                payeeName,
                                defaultCurrency,
                                inrEnabled,
                                nprEnabled,
                                selectedThemeMode,
                                biometricLockEnabled,
                                securityPin.ifBlank { "0000" }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_settings_button"),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PureBlack,
                            contentColor = PureWhite
                        )
                    ) {
                        Text("SAVE ALL SETTINGS", style = MaterialTheme.typography.labelLarge)
                    }
                }

                // 4. Enhancement E: Share / Give Access to Udhaar Card (Play Store Link + Scannable App QR)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, PureBlack, RoundedCornerShape(4.dp))
                        .background(SubtleSurfaceGrey, RoundedCornerShape(4.dp))
                        .padding(14.dp)
                        .testTag("share_give_access_card"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "SHARE / GIVE ACCESS TO UDHAAR",
                        style = MaterialTheme.typography.titleSmall,
                        color = PureBlack
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (miniPlayQrBitmap != null) {
                            Image(
                                bitmap = miniPlayQrBitmap.asImageBitmap(),
                                contentDescription = "Google Play QR Code",
                                modifier = Modifier
                                    .size(84.dp)
                                    .border(1.dp, PureBlack, RoundedCornerShape(2.dp))
                                    .background(PureWhite)
                                    .padding(4.dp)
                                    .clickable { onOpenShareAppModal() }
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Scan to install Udhaar from Google Play Store or share the invite link directly via WhatsApp.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryInkGrey
                            )
                            Text(
                                text = playStoreUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = PureBlack
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenShareAppModal,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, PureBlack),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("FULL APP QR", style = MaterialTheme.typography.labelSmall)
                        }
                        Button(
                            onClick = {
                                val inviteMsg = "Install *Udhaar* — Minimalist Loan & Ledger App:\n$playStoreUrl"
                                val waIntent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://wa.me/?text=${Uri.encode(inviteMsg)}")
                                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                runCatching {
                                    context.startActivity(waIntent)
                                }.onFailure {
                                    val share = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, inviteMsg)
                                    }
                                    context.startActivity(Intent.createChooser(share, "Share Udhaar App"))
                                }
                            },
                            modifier = Modifier
                                .weight(1.2f)
                                .height(44.dp)
                                .testTag("settings_share_app_whatsapp_button"),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PureBlack,
                                contentColor = PureWhite
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SHARE ON WHATSAPP", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // 5. Offline Ledger Export / Import (Share Access to Records)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "OFFLINE LEDGER BACKUP & RECORD TRANSFER (JSON)",
                        style = MaterialTheme.typography.labelMedium,
                        color = SecondaryInkGrey
                    )
                    Text(
                        text = "Export your Full Ledger (Parties, Transactions & Partial Payments) to a portable JSON file or import records from another device offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryInkGrey
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onExportFullLedgerSaf,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("export_ledger_json_button"),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, PureBlack),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("EXPORT JSON", style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = onShareFullLedgerFile,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("share_ledger_json_button"),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, PureBlack),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SHARE BACKUP", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    OutlinedButton(
                        onClick = onImportLedgerSaf,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("import_ledger_json_button"),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, PureBlack),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("IMPORT LEDGER BACKUP (JSON)", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // 6. Enhancement E: Admin & Support Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, PureBlack, RoundedCornerShape(4.dp))
                        .background(PureWhite, RoundedCornerShape(4.dp))
                        .padding(14.dp)
                        .testTag("about_and_support_card"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ADMIN & SUPPORT",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = PureBlack
                    )
                    HorizontalDivider(color = BorderGrey, thickness = 1.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Administrator & Developer",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryInkGrey
                        )
                        Text(
                            text = "Jyotirmoy Bhowmik",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = PureBlack
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Admin Email",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryInkGrey
                        )
                        Text(
                            text = "admin@jyotirmoyb.com",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = PureBlack
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Version",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryInkGrey
                        )
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} (Code ${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodySmall,
                            color = PureBlack
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    OutlinedButton(
                        onClick = {
                            val adminEmail = "admin@jyotirmoyb.com"
                            runCatching {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Admin Email", adminEmail))
                            }
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:admin@jyotirmoyb.com")
                                putExtra(
                                    Intent.EXTRA_SUBJECT,
                                    "Udhaar App Support (v${BuildConfig.VERSION_NAME})"
                                )
                            }
                            try {
                                if (intent.resolveActivity(context.packageManager) != null) {
                                    context.startActivity(Intent.createChooser(intent, "Contact Admin"))
                                } else {
                                    context.startActivity(Intent.createChooser(intent, "Contact Admin"))
                                    Toast.makeText(
                                        context,
                                        "Admin email (admin@jyotirmoyb.com) copied to clipboard",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "No email client found. Copied admin@jyotirmoyb.com to clipboard.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("contact_admin_button"),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.5.dp, PureBlack),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Contact Admin (admin@jyotirmoyb.com)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
