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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ReceiptLong
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
        // Row 1: WhatsApp + Receipt + UPI QR / Pay Party UPI
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
                contentPadding = PaddingValues(horizontal = 8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringResource(R.string.ui_str_whatsapp_5c39d5),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = onShareReceipt,
                modifier = Modifier
                    .weight(1f)
                    .height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight)
                    .pressScaleFeedback()
                    .testTag("detail_thermal_receipt_button"),
                shape = com.jyotirmoyb.udhaar.ui.theme.UdhaarShapeTokens.Corner12,
                contentPadding = PaddingValues(horizontal = 8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    stringResource(R.string.ui_str_receipt_slip_1ccbe9),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (tx.currency == CurrencyType.INR && !tx.isSettled) {
                if (tx.transactionType == TransactionType.GIVEN) {
                    OutlinedButton(
                        onClick = onOpenQr,
                        modifier = Modifier
                            .weight(1.1f)
                            .height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight)
                            .pressScaleFeedback()
                            .testTag("detail_upi_qr_button"),
                        shape = com.jyotirmoyb.udhaar.ui.theme.UdhaarShapeTokens.Corner12,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.ui_str_collect_via_my_upi_qr_c6b866),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (tx.transactionType == TransactionType.TAKEN) {
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
                            .weight(1.1f)
                            .height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight)
                            .pressScaleFeedback()
                            .testTag("detail_pay_party_upi_button"),
                        shape = com.jyotirmoyb.udhaar.ui.theme.UdhaarShapeTokens.Corner12,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (partyUpi.isNotBlank()) {
                                stringResource(R.string.ui_fmt_pay_via_upi_7718ac, tx.partyName.uppercase())
                            } else {
                                stringResource(R.string.ui_str_open_upi_qr_tool_2e9aa8)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Row 2: In-App Transaction Chat & Mark Settled / Re-open
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
                contentPadding = PaddingValues(horizontal = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (unreadChatCount > 0) {
                        stringResource(R.string.ui_fmt_open_transaction_chat_new_91bcd1, unreadChatCount)
                    } else {
                        stringResource(R.string.ui_str_open_transaction_chat_extension_41a166)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
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
                contentPadding = PaddingValues(horizontal = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tx.isSettled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (tx.isSettled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSecondaryContainer
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (tx.isSettled) {
                        stringResource(R.string.ui_str_re_open_ledger_entry_bd882c)
                    } else {
                        stringResource(R.string.ui_str_mark_as_fully_settled_c262a2)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Row 3: Edit & Delete Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.Small)
        ) {
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier
                    .weight(1f)
                    .height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight)
                    .pressScaleFeedback()
                    .testTag("edit_transaction_button"),
                shape = com.jyotirmoyb.udhaar.ui.theme.UdhaarShapeTokens.Corner12,
                contentPadding = PaddingValues(horizontal = 8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.ui_str_edit_17865b), style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = onDeleteClicked,
                modifier = Modifier
                    .weight(1f)
                    .height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight)
                    .pressScaleFeedback()
                    .testTag("delete_transaction_button"),
                shape = com.jyotirmoyb.udhaar.ui.theme.UdhaarShapeTokens.Corner12,
                contentPadding = PaddingValues(horizontal = 8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (confirmDelete) {
                        stringResource(R.string.ui_str_tap_to_confirm_0fe819)
                    } else {
                        stringResource(R.string.ui_str_delete_d6f563)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
