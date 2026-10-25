package com.example.ui.components

import android.content.Intent
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.CurrencyType
import com.example.data.PartialPayment
import com.example.data.TransactionType
import com.example.data.UserSettings
import com.example.notification.ReminderReceiver
import com.example.ui.QrModalState
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.MutedChipGrey
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SecondaryInkGrey
import com.example.ui.theme.SubtleSurfaceGrey
import com.example.util.CalculatedUdhaarSummary
import com.example.util.InterestCalculator
import com.example.util.UpiQrGenerator

@Composable
fun TransactionDetailModal(
    summary: CalculatedUdhaarSummary,
    partialPayments: List<PartialPayment>,
    onDismiss: () -> Unit,
    onRecordPartialPayment: (Double, String) -> Unit,
    onDeletePartialPayment: (PartialPayment) -> Unit,
    onToggleSettled: () -> Unit,
    onEdit: () -> Unit,
    onDeleteTransaction: () -> Unit,
    onOpenQr: () -> Unit,
    onOpenWhatsApp: () -> Unit
) {
    val tx = summary.transaction
    val currency = tx.currency
    var partialAmountText by rememberSaveable { mutableStateOf("") }
    var partialNoteText by rememberSaveable { mutableStateOf("") }

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

                // Record Partial Repayment Section
                if (!tx.isSettled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, PureBlack, RoundedCornerShape(4.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "LOG PARTIAL REPAYMENT / INSTALLMENT",
                            style = MaterialTheme.typography.labelMedium,
                            color = PureBlack
                        )
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
                                    onRecordPartialPayment(amt, partialNoteText)
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

                // Partial Payment History
                if (partialPayments.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "REPAYMENT HISTORY (${partialPayments.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = SecondaryInkGrey
                        )
                        partialPayments.forEach { payment ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = InterestCalculator.formatCurrency(payment.amount, currency),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = PureBlack
                                    )
                                    val sub = buildString {
                                        append(InterestCalculator.formatDate(payment.paidAtMillis))
                                        if (payment.note.isNotBlank()) {
                                            append(" • ${payment.note}")
                                        }
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

                // Quick Action Buttons (WhatsApp, UPI QR, Settle)
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

@Composable
fun UpiQrCodeModal(
    qrState: QrModalState,
    userSettings: UserSettings,
    onSaveUpiSettings: (String, String, CurrencyType) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var upiIdInput by rememberSaveable(userSettings.upiId) {
        mutableStateOf(userSettings.upiId)
    }
    var payeeNameInput by rememberSaveable(userSettings.payeeName) {
        mutableStateOf(userSettings.payeeName)
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
                            text = "UPI QR CODE GENERATOR",
                            style = MaterialTheme.typography.titleLarge,
                            color = PureBlack
                        )
                        Text(
                            text = if (qrState.partyName != null) {
                                "FOR ${qrState.partyName.uppercase()} • INR (₹)"
                            } else {
                                "GLOBAL INR (₹) GPAY / PHONEPE / PAYTM QR"
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
                    label = { Text("Your UPI ID (VPA) *") },
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
                        placeholder = { Text("Your Name") },
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
                                text = "Enter your UPI ID (e.g. name@upi) above to generate a scannable monochrome QR code offline.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SecondaryInkGrey,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Save UPI Settings & Share QR Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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

                    Button(
                        onClick = {
                            onSaveUpiSettings(upiIdInput, payeeNameInput, userSettings.defaultCurrency)
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
    onSave: (String, String, CurrencyType) -> Unit,
    onDismiss: () -> Unit
) {
    var upiId by rememberSaveable(currentSettings.upiId) { mutableStateOf(currentSettings.upiId) }
    var payeeName by rememberSaveable(currentSettings.payeeName) { mutableStateOf(currentSettings.payeeName) }
    var defaultCurrency by rememberSaveable(currentSettings.defaultCurrency) {
        mutableStateOf(currentSettings.defaultCurrency)
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
                .fillMaxWidth(0.92f)
                .border(1.5.dp, PureBlack, RoundedCornerShape(4.dp)),
            shape = RoundedCornerShape(4.dp),
            color = PureWhite
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PROFILE & UPI SETTINGS",
                            style = MaterialTheme.typography.titleLarge,
                            color = PureBlack
                        )
                        Text(
                            text = "OFFLINE VPA & CURRENCY CONFIGURATION",
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
                    label = { Text("UPI ID (VPA) for INR Settlements") },
                    placeholder = { Text("e.g. merchant@okicici") },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_upi_id_input")
                )

                Text(
                    text = "DEFAULT LEDGER CURRENCY",
                    style = MaterialTheme.typography.labelMedium,
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

                Button(
                    onClick = { onSave(upiId, payeeName, defaultCurrency) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_settings_button"),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PureBlack,
                        contentColor = PureWhite
                    )
                ) {
                    Text("SAVE CONFIGURATION", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
