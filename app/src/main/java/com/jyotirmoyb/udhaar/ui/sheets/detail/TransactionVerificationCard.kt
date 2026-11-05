package com.jyotirmoyb.udhaar.ui.sheets.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.VerificationStatus
import com.jyotirmoyb.udhaar.data.getParsedDateProposal
import com.jyotirmoyb.udhaar.data.getParsedPaymentClaim
import com.jyotirmoyb.udhaar.ui.components.StatusAtAGlanceBadge
import com.jyotirmoyb.udhaar.ui.components.pressScaleFeedback
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.InterestCalculator

@Composable
fun TransactionVerificationCard(
    summary: CalculatedUdhaarSummary,
    tx: UdhaarTransaction,
    onRequestVerification: () -> Unit = {},
    onConfirmOrDisputeRecord: (confirm: Boolean, disputeReason: String) -> Unit = { _, _ -> },
    onOpenWebShare: () -> Unit = {},
    onRevokeWebLink: () -> Unit = {},
    onConfirmPaymentClaim: () -> Unit = {},
    onRejectPaymentClaim: () -> Unit = {},
    onAcceptDateProposal: () -> Unit = {},
    onDeclineDateProposal: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDisputeInput by rememberSaveable { mutableStateOf(false) }
    var disputeReasonInput by rememberSaveable { mutableStateOf("") }

    val claim = tx.getParsedPaymentClaim()
    val dateProposal = tx.getParsedDateProposal()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(10.dp)
            .testTag("detail_verification_section"),
        verticalArrangement = Arrangement.spacedBy(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.Small)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.ui_str_verification_status_760fa1),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
                if (tx.trustLevel.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.testTag("trust_level_badge_${tx.id}")
                    ) {
                        Text(
                            text = when (tx.trustLevel) {
                                "WHATSAPP_OTP", "PHONE_VERIFIED" -> "WhatsApp Verified ✓"
                                "LINK_ONLY" -> "Link Only"
                                "APP_VERIFIED" -> "App Verified ✓"
                                else -> tx.trustLevel
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                StatusAtAGlanceBadge(
                    summary = summary,
                    modifier = Modifier.testTag("detail_status_at_a_glance_badge")
                )
            }
        }

        // Web Link Status Line & Actions (Story 2)
        if (tx.webLinkStatus.isNotBlank() && tx.webLinkStatus != "NONE") {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().testTag("web_link_status_card")
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (tx.webLinkStatus) {
                                    "ACTIVE" -> "Web Link: Active"
                                    "REVOKED" -> "Web Link: Revoked"
                                    "EXPIRED" -> "Web Link: Expired"
                                    else -> "Web Link: ${tx.webLinkStatus}"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (tx.lastGuestAction.isNotBlank()) {
                            val actionSummary = "Action: ${tx.lastGuestAction}"
                            Text(
                                text = actionSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.Small)
                    ) {
                        if (tx.webLinkStatus == "ACTIVE") {
                            OutlinedButton(
                                onClick = onOpenWebShare,
                                modifier = Modifier.weight(1f).height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight).testTag("btn_resend_web_link"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(stringResource(R.string.btn_resend), style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = onRevokeWebLink,
                                modifier = Modifier.weight(1f).height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight).testTag("btn_revoke_detail_link"),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(stringResource(R.string.btn_revoke), style = MaterialTheme.typography.labelSmall)
                            }
                        } else {
                            Button(
                                onClick = onOpenWebShare,
                                modifier = Modifier.fillMaxWidth().height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight).testTag("btn_create_new_web_link"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(stringResource(R.string.btn_create_new_web_link), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // Pending Payment Claim Banner
        if (claim != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().testTag("pending_payment_claim_banner")
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Payments, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        val claimSummary = "${tx.partyName} claimed payment of ${InterestCalculator.formatPaise(claim.amountPaise, tx.currency)}"
                        Text(
                            text = claimSummary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    val modeSummary = "Mode: ${claim.paymentMode.label}${if (claim.reference.isNotBlank()) " • Ref: ${claim.reference}" else ""}"
                    Text(
                        text = modeSummary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.Small)) {
                        Button(
                            onClick = onConfirmPaymentClaim,
                            modifier = Modifier.weight(1f).height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight).testTag("btn_detail_confirm_claim"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.btn_confirm_payment), style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = onRejectPaymentClaim,
                            modifier = Modifier.weight(1f).height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight).testTag("btn_detail_reject_claim"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(stringResource(R.string.btn_not_received), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Pending Date Proposal Banner
        if (dateProposal != null && dateProposal.proposedDateMillis > 0L) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth().testTag("pending_date_proposal_banner")
            ) {
                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        val propSummary = "${tx.partyName} proposed new due date: ${InterestCalculator.formatDate(dateProposal.proposedDateMillis)}"
                        Text(
                            text = propSummary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (dateProposal.message.isNotBlank()) {
                        val quotedMsg = "“${dateProposal.message}”"
                        Text(text = quotedMsg, style = MaterialTheme.typography.labelSmall)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.Small)) {
                        Button(
                            onClick = onAcceptDateProposal,
                            modifier = Modifier.weight(1f).height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight).testTag("btn_detail_accept_date"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.btn_accept_date), style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = onDeclineDateProposal,
                            modifier = Modifier.weight(1f).height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight).testTag("btn_detail_decline_date"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(stringResource(R.string.btn_decline), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        if (tx.disputeReason.isNotBlank() && tx.verificationStatus == VerificationStatus.DISPUTED) {
            Text(
                text = stringResource(R.string.ui_fmt_dispute_note_26ea88, tx.disputeReason),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (tx.verificationStatus == VerificationStatus.EDITED_AFTER_CONFIRM) {
            Text(
                text = stringResource(R.string.ui_fmt_terms_were_edited_after_confirmatio_ef3a45, tx.partyName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.Small)
        ) {
            // Send Web Link Button
            Button(
                onClick = onOpenWebShare,
                modifier = Modifier
                    .weight(1f)
                    .height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight)
                    .pressScaleFeedback()
                    .testTag("send_web_link_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.web_share_sheet_title),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // QR & App-to-App Button
            OutlinedButton(
                onClick = onRequestVerification,
                modifier = Modifier
                    .weight(1f)
                    .height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight)
                    .pressScaleFeedback()
                    .testTag("request_verification_button"),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.btn_qr_app_link),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        if (tx.needsMyConfirmation ||
            tx.verificationStatus == VerificationStatus.PENDING ||
            tx.verificationStatus == VerificationStatus.EDITED_AFTER_CONFIRM
        ) {
            if (showDisputeInput) {
                OutlinedTextField(
                    value = disputeReasonInput,
                    onValueChange = { disputeReasonInput = it },
                    label = { Text(stringResource(R.string.ui_str_reason_for_dispute_780e95)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detail_dispute_reason_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.Small)
                ) {
                    OutlinedButton(
                        onClick = { showDisputeInput = false },
                        modifier = Modifier
                            .weight(1f)
                            .height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight)
                            .pressScaleFeedback(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.ui_str_cancel_1507c4), style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = {
                            onConfirmOrDisputeRecord(
                                false,
                                disputeReasonInput.ifBlank { "Disputed by counterparty" }
                            )
                            showDisputeInput = false
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight)
                            .pressScaleFeedback()
                            .testTag("detail_submit_dispute_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(stringResource(R.string.ui_str_submit_dispute_4c1abb), style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.Small)
                ) {
                    Button(
                        onClick = { onConfirmOrDisputeRecord(true, "") },
                        modifier = Modifier
                            .weight(1f)
                            .height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight)
                            .pressScaleFeedback()
                            .testTag("detail_confirm_verification_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.ui_str_confirm_entry_be92ce), style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { showDisputeInput = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing.SecondaryButtonHeight)
                            .pressScaleFeedback()
                            .testTag("detail_dispute_verification_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Text(
                            text = stringResource(R.string.ui_str_dispute_722d50),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
