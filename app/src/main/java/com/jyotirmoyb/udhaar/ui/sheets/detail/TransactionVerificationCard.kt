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
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.VerificationStatus
import com.jyotirmoyb.udhaar.ui.components.StatusAtAGlanceBadge
import com.jyotirmoyb.udhaar.ui.components.pressScaleFeedback
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary

@Composable
fun TransactionVerificationCard(
    summary: CalculatedUdhaarSummary,
    tx: UdhaarTransaction,
    onRequestVerification: () -> Unit,
    onConfirmOrDisputeRecord: (confirm: Boolean, disputeReason: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDisputeInput by rememberSaveable { mutableStateOf(false) }
    var disputeReasonInput by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("detail_verification_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.ui_str_verification_status_760fa1),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            StatusAtAGlanceBadge(
                summary = summary,
                modifier = Modifier.testTag("detail_status_at_a_glance_badge")
            )
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

        OutlinedButton(
            onClick = onRequestVerification,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .pressScaleFeedback()
                .testTag("request_verification_button"),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (tx.verificationStatus == VerificationStatus.LOCAL_ONLY) {
                    stringResource(R.string.ui_str_request_verification_link_qr_83e400)
                } else {
                    stringResource(R.string.ui_str_share_verification_link_qr_edc3d4)
                },
                style = MaterialTheme.typography.labelMedium
            )
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showDisputeInput = false },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .pressScaleFeedback(),
                        shape = RoundedCornerShape(12.dp)
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
                            .height(44.dp)
                            .pressScaleFeedback()
                            .testTag("detail_submit_dispute_button"),
                        shape = RoundedCornerShape(12.dp),
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onConfirmOrDisputeRecord(true, "") },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .pressScaleFeedback()
                            .testTag("detail_confirm_verification_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.ui_str_confirm_entry_be92ce), style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { showDisputeInput = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .pressScaleFeedback()
                            .testTag("detail_dispute_verification_button"),
                        shape = RoundedCornerShape(12.dp),
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
