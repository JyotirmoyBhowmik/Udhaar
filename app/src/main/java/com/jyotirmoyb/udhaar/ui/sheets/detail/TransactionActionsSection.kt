package com.jyotirmoyb.udhaar.ui.sheets.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.ui.components.pressScaleFeedback
import com.jyotirmoyb.udhaar.ui.icons.UdhaarIcons
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary

@Composable
fun TransactionActionsSection(
    summary: CalculatedUdhaarSummary,
    tx: UdhaarTransaction,
    linkedParty: PartyEntity?,
    unreadChatCount: Int,
    confirmDelete: Boolean,
    onOpenTransactionChat: () -> Unit,
    onOpenWhatsApp: () -> Unit,
    onShareReceipt: () -> Unit,
    onOpenQr: () -> Unit,
    onPayPartyViaUpi: (partyName: String, partyUpiId: String, amountInr: Double) -> Unit,
    onToggleSettled: () -> Unit,
    onEdit: () -> Unit,
    onDeleteClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: Primary Actions (Chat & Mark Paid / Re-open)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.Small)
        ) {
            Button(
                onClick = onOpenTransactionChat,
                modifier = Modifier
                    .weight(1f)
                    .height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight)
                    .pressScaleFeedback()
                    .testTag("detail_open_chat_button"),
                shape = com.jyotirmoyb.udhaar.ui.theme.UdhaarShapeTokens.Corner12,
                contentPadding = PaddingValues(horizontal = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(UdhaarIcons.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (unreadChatCount > 0) "Chat ($unreadChatCount)" else "Chat",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = onToggleSettled,
                modifier = Modifier
                    .weight(1f)
                    .height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight)
                    .pressScaleFeedback()
                    .testTag("toggle_settled_button"),
                shape = com.jyotirmoyb.udhaar.ui.theme.UdhaarShapeTokens.Corner12,
                contentPadding = PaddingValues(horizontal = 10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tx.isSettled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (tx.isSettled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSecondaryContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (tx.isSettled) "Re-open" else "Mark Paid",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Row 2: Share & Collect (WhatsApp + Collect QR / Pay UPI / Receipt)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.Small)
        ) {
            OutlinedButton(
                onClick = onOpenWhatsApp,
                modifier = Modifier
                    .weight(1f)
                    .height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight)
                    .pressScaleFeedback()
                    .testTag("detail_whatsapp_button"),
                shape = com.jyotirmoyb.udhaar.ui.theme.UdhaarShapeTokens.Corner12,
                contentPadding = PaddingValues(horizontal = 10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.ui_str_whatsapp_5c39d5),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (tx.currency == CurrencyType.INR && !tx.isSettled) {
                if (tx.transactionType == TransactionType.GIVEN) {
                    OutlinedButton(
                        onClick = onOpenQr,
                        modifier = Modifier
                            .weight(1f)
                            .height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight)
                            .pressScaleFeedback()
                            .testTag("detail_upi_qr_button"),
                        shape = com.jyotirmoyb.udhaar.ui.theme.UdhaarShapeTokens.Corner12,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(UdhaarIcons.QrCode2, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Collect QR",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    val partyUpi = linkedParty?.upiId.orEmpty()
                    OutlinedButton(
                        onClick = {
                            if (partyUpi.isNotBlank()) {
                                onPayPartyViaUpi(tx.partyName, partyUpi, summary.netBalanceDue)
                            } else {
                                onOpenQr()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight)
                            .pressScaleFeedback()
                            .testTag("detail_pay_party_upi_button"),
                        shape = com.jyotirmoyb.udhaar.ui.theme.UdhaarShapeTokens.Corner12,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(UdhaarIcons.QrCode2, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (partyUpi.isNotBlank()) "Pay UPI" else "UPI QR",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onShareReceipt,
                    modifier = Modifier
                        .weight(1f)
                        .height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight)
                        .pressScaleFeedback()
                        .testTag("detail_thermal_receipt_button"),
                    shape = com.jyotirmoyb.udhaar.ui.theme.UdhaarShapeTokens.Corner12,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(UdhaarIcons.ReceiptLong, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.ui_str_receipt_slip_1ccbe9),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
