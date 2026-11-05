package com.jyotirmoyb.udhaar.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.ui.PartyKhataSummary
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.ui.theme.SubtleSurfaceGrey
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.InterestCalculator

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PartyDetailSummaryCard(
    khata: PartyKhataSummary,
    enabledCurrencies: List<CurrencyType>,
    unreadCount: Int,
    reducedMotion: Boolean,
    onEditParty: () -> Unit,
    onWhatsAppClick: (CalculatedUdhaarSummary) -> Unit,
    onPayPartyViaUpi: (Double?) -> Unit,
    onShareThermalReceipt: () -> Unit,
    onExportPartyStatement: () -> Unit,
    onSharePartyStatement: () -> Unit,
    onExportPartyPdfStatement: () -> Unit,
    onShareMyPaymentDetails: () -> Unit,
    onRequestPartyPaymentDetails: () -> Unit,
    onRequestVerification: () -> Unit,
    onOpenPartyGeneralChat: () -> Unit
) {
    val context = LocalContext.current
    val party = khata.party
    val inrEnabled = CurrencyType.INR in enabledCurrencies
    val nprEnabled = CurrencyType.NPR in enabledCurrencies

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .udhaarSharedBounds("party_card_${party.partyId}_${party.name}")
            .border(1.5.dp, PureBlack, RoundedCornerShape(12.dp))
            .background(PureWhite, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val meta = buildString {
            if (party.tagOrNotes.isNotBlank()) append("Tag: ${party.tagOrNotes}")
            if (party.phoneNumber.isNotBlank()) {
                if (isNotEmpty()) append("  •  ")
                append("Phone: ${InterestCalculator.maskPhoneNumber(party.phoneNumber)}")
            }
            if (party.upiId.isNotBlank()) {
                if (isNotEmpty()) append("  •  ")
                append("UPI VPA: ${InterestCalculator.maskUpiId(party.upiId)}")
            }
            if (party.email.isNotBlank()) {
                if (isNotEmpty()) append("  •  ")
                append("Email: ${party.email}")
            }
        }
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryInkGrey
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PureBlack, RoundedCornerShape(12.dp))
                .background(SubtleSurfaceGrey, RoundedCornerShape(12.dp))
                .padding(12.dp)
                .testTag("party_trust_profile_card"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.ui_str_repayment_trust_summary_015512),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = PureBlack
                )
                Box(
                    modifier = Modifier
                        .background(PureBlack, RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ui_fmt_symbol_e770b0, khata.trustBadge, khata.trustScorePercent),
                        style = MaterialTheme.typography.labelSmall,
                        color = PureWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_str_oldest_overdue_14e0f2), style = MaterialTheme.typography.labelSmall, color = SecondaryInkGrey)
                    Text(
                        text = if (khata.oldestOverdueDays > 0) stringResource(R.string.ui_fmt_days_3773dd, khata.oldestOverdueDays) else stringResource(R.string.ui_str_none_on_time_b96c7f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = PureBlack
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.ui_str_avg_repayment_time_433a7d), style = MaterialTheme.typography.labelSmall, color = SecondaryInkGrey)
                    Text(
                        text = if (khata.averageRepaymentDays > 0) stringResource(R.string.ui_fmt_days_75db18, khata.averageRepaymentDays.toInt()) else stringResource(R.string.ui_str_no_settlements_0c38ad),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = PureBlack
                    )
                }
            }
            Text(
                text = stringResource(R.string.ui_str_trust_rating_visible_only_to_32c545),
                style = MaterialTheme.typography.labelSmall,
                color = SecondaryInkGrey
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PureBlack, RoundedCornerShape(12.dp))
        ) {
            if (inrEnabled) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(PureBlack)
                        .padding(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ui_str_inr_net_position_02ba82),
                        style = MaterialTheme.typography.labelSmall,
                        color = BorderGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val inrLabel = when {
                        khata.inrNetPaise > 0L -> "RECEIVE ${InterestCalculator.formatCurrencyPaise(khata.inrNetPaise, CurrencyType.INR)}"
                        khata.inrNetPaise < 0L -> "PAY ${InterestCalculator.formatCurrencyPaise(-khata.inrNetPaise, CurrencyType.INR)}"
                        else -> "₹0 (SETTLED)"
                    }
                    Text(
                        text = inrLabel,
                        style = MaterialTheme.typography.titleLarge,
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.ui_fmt_given_taken_5521ec,
                            InterestCalculator.formatCurrencyPaise(khata.inrToReceivePaise, CurrencyType.INR),
                            InterestCalculator.formatCurrencyPaise(khata.inrToPayPaise, CurrencyType.INR)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = BorderGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (nprEnabled) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (!inrEnabled) PureBlack else PureWhite)
                        .padding(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ui_str_npr_net_position_d959f5),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (!inrEnabled) BorderGrey else SecondaryInkGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val nprLabel = when {
                        khata.nprNetPaise > 0L -> "RECEIVE ${InterestCalculator.formatCurrencyPaise(khata.nprNetPaise, CurrencyType.NPR)}"
                        khata.nprNetPaise < 0L -> "PAY ${InterestCalculator.formatCurrencyPaise(-khata.nprNetPaise, CurrencyType.NPR)}"
                        else -> "रू0 (SETTLED)"
                    }
                    Text(
                        text = nprLabel,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (!inrEnabled) PureWhite else PureBlack,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.ui_fmt_given_taken_962d59,
                            InterestCalculator.formatCurrencyPaise(khata.nprToReceivePaise, CurrencyType.NPR),
                            InterestCalculator.formatCurrencyPaise(khata.nprToPayPaise, CurrencyType.NPR)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (!inrEnabled) BorderGrey else SecondaryInkGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (party.phoneNumber.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        val dialIntent = Intent(
                            Intent.ACTION_DIAL,
                            Uri.parse("tel:${party.phoneNumber.filter { it.isDigit() || it == '+' }}")
                        )
                        runCatching { context.startActivity(dialIntent) }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PureBlack),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.ui_str_call_1b3cb4), style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = {
                        val firstUnsettled = khata.transactions.firstOrNull { !it.transaction.isSettled }
                            ?: khata.transactions.firstOrNull()
                        if (firstUnsettled != null) {
                            onWhatsAppClick(firstUnsettled)
                        } else {
                            val msg = "Hello ${party.name}, sharing our current Udhaar ledger summary."
                            com.jyotirmoyb.udhaar.util.WhatsAppReminderLauncher.launchReminder(
                                context = context,
                                rawPhoneNumber = party.phoneNumber,
                                currency = party.preferredCurrency,
                                message = msg
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PureBlack),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("party_whatsapp_quick_action_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.ui_str_whatsapp_5c39d5), style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = {
                        val smsIntent = Intent(
                            Intent.ACTION_SENDTO,
                            Uri.parse("smsto:${party.phoneNumber.filter { it.isDigit() || it == '+' }}")
                        )
                        runCatching { context.startActivity(smsIntent) }
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PureBlack),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("party_sms_quick_action_button")
                ) {
                    Icon(Icons.Default.Message, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.ui_str_sms_9e10d4), style = MaterialTheme.typography.labelMedium)
                }
            }

            if (party.email.isNotBlank()) {
                OutlinedButton(
                    onClick = {
                        val mailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:${party.email.trim()}")
                            putExtra(Intent.EXTRA_SUBJECT, "Udhaar Ledger Statement — ${party.name}")
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(mailIntent, "Email ${party.name}"))
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PureBlack),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                ) {
                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.ui_str_email_564269), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        OutlinedButton(
            onClick = onRequestVerification,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("party_request_verification_button"),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.5.dp, PureBlack),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
        ) {
            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (party.linkedUserId.isNotBlank()) stringResource(R.string.ui_fmt_verified_contact_833521, party.linkedUserId) else stringResource(R.string.ui_str_request_verification_link_id_40daa2),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Button(
            onClick = onShareThermalReceipt,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("party_share_thermal_receipt_button"),
            shape = RoundedCornerShape(12.dp),
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
                text = stringResource(R.string.ui_str_share_receipt_statement_image_pdf_f07d4e),
                style = MaterialTheme.typography.labelLarge
            )
        }

        OutlinedButton(
            onClick = onExportPartyPdfStatement,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("party_export_pdf_statement_button"),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.5.dp, PureBlack),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.ui_str_export_pdf_statement_audit_trail_c9613d),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }

        val amountWeOweInr = khata.inrToPay.takeIf { it > 0.0 }
        if (inrEnabled) {
            OutlinedButton(
                onClick = {
                    if (party.upiId.isNotBlank()) {
                        onPayPartyViaUpi(amountWeOweInr)
                    } else {
                        Toast.makeText(
                            context,
                            "Add ${party.name}'s UPI ID to generate their Payback QR.",
                            Toast.LENGTH_SHORT
                        ).show()
                        onEditParty()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("pay_party_via_upi_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, PureBlack),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
            ) {
                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (party.upiId.isNotBlank()) {
                        stringResource(R.string.ui_fmt_pay_party_via_upi_27379b, InterestCalculator.maskUpiId(party.upiId))
                    } else {
                        stringResource(R.string.ui_str_set_party_upi_id_to_58d127)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onShareMyPaymentDetails,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("party_share_my_payment_details_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, PureBlack),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
            ) {
                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.ui_str_share_my_payment_details_c9cc51),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedButton(
                onClick = onRequestPartyPaymentDetails,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("party_request_payment_details_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, PureBlack),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.ui_str_request_their_details_03e3b9),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Button(
            onClick = onOpenPartyGeneralChat,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("party_open_general_chat_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PureBlack,
                contentColor = PureWhite
            )
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (unreadCount > 0) {
                    stringResource(R.string.ui_fmt_open_party_chat_thread_unread_55743b, unreadCount)
                } else {
                    stringResource(R.string.ui_str_open_party_chat_thread_b19bc9)
                },
                style = MaterialTheme.typography.labelLarge
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onExportPartyStatement,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("export_party_statement_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, PureBlack),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
            ) {
                Text(stringResource(R.string.ui_str_save_statement_json_d2f264), style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = onSharePartyStatement,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("share_party_statement_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, PureBlack),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.ui_str_share_backup_json_525a50), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
