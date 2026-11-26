package com.jyotirmoyb.udhaar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.ui.icons.UdhaarIcons
import com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.VerificationCryptoHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomingVerificationReviewSheet(
    payload: VerificationCryptoHelper.SharedVerificationPayload,
    onDismiss: () -> Unit,
    onConfirmRecord: (counterpartyNameOverride: String) -> Unit,
    onDisputeRecord: (reason: String, counterpartyNameOverride: String) -> Unit
) {
    var showDisputeReason by remember { mutableStateOf(false) }
    var disputeReason by remember { mutableStateOf("") }

    val rawSenderName = if (payload.creatorRole == TransactionType.GIVEN) payload.lenderName else payload.borrowerName
    val needsCounterpartyNameConfirm = VerificationCryptoHelper.isPlaceholderSelfName(rawSenderName) ||
        payload.lenderName.trim().equals("You", ignoreCase = true) ||
        payload.borrowerName.trim().equals("You", ignoreCase = true)
    var editableCounterpartyName by remember(payload) {
        mutableStateOf(
            if (VerificationCryptoHelper.isPlaceholderSelfName(rawSenderName)) "" else rawSenderName.trim()
        )
    }
    var counterpartyNameError by remember(payload) { mutableStateOf(false) }

    val decryptedNote = remember(payload) {
        if (payload.encryptedNote.isNotBlank() && payload.shareToken.isNotBlank()) {
            VerificationCryptoHelper.decryptE2eeField(
                cipherText = payload.encryptedNote,
                remoteId = payload.remoteId,
                shareToken = payload.shareToken
            )
        } else {
            ""
        }
    }

    val senderName = editableCounterpartyName.trim().ifBlank { rawSenderName }
    val mirroredRoleText = if (payload.creatorRole == TransactionType.GIVEN) {
        stringResource(R.string.verification_mirrored_borrowed_fmt, senderName)
    } else {
        stringResource(R.string.verification_mirrored_lent_fmt, senderName)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        contentWindowInsets = { WindowInsets.navigationBars.union(WindowInsets.ime) }
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.ui_str_verify_shared_udhaar_entry_510f4b),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ui_fmt_shared_by_a300b1, senderName),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_close),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 14.dp, bottom = 36.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = mirroredRoleText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = InterestCalculator.formatCurrency(
                                    payload.principalAmount,
                                    if (payload.currencyCode == stringResource(R.string.ui_str_npr_e09e1a)) {
                                        CurrencyType.NPR
                                    } else {
                                        CurrencyType.INR
                                    }
                                ),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                fontFamily = IbmPlexMonoFamily,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(10.dp))

                            VerificationDetailRow(stringResource(R.string.verification_label_lender), payload.lenderName)
                            VerificationDetailRow(stringResource(R.string.verification_label_borrower), payload.borrowerName)
                            VerificationDetailRow(
                                stringResource(R.string.verification_label_start_date),
                                InterestCalculator.fmtDate(payload.startDateMillis)
                            )
                            VerificationDetailRow(
                                stringResource(R.string.verification_label_due_date),
                                payload.expiryDateMillis?.let { InterestCalculator.fmtDate(it) }
                                    ?: stringResource(R.string.verification_value_no_due_date)
                            )
                            VerificationDetailRow(
                                stringResource(R.string.verification_label_interest_terms),
                                if (!payload.isInterestEnabled || payload.interestRate <= 0.0) {
                                    stringResource(R.string.verification_value_interest_free)
                                } else {
                                    stringResource(
                                        R.string.verification_value_interest_fmt,
                                        payload.interestRate.toString(),
                                        payload.interestType.label,
                                        payload.interestRatePeriod.shortSuffix
                                    )
                                }
                            )
                            if (decryptedNote.isNotBlank()) {
                                VerificationDetailRow(stringResource(R.string.verification_label_decrypted_note), decryptedNote)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.ui_str_sha_256_content_hash_2ab195),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = payload.contentHash,
                                fontSize = 12.sp,
                                fontFamily = IbmPlexMonoFamily,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.ui_str_confirming_saves_a_mirrored_copy_4c195e),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (needsCounterpartyNameConfirm) {
                        Text(
                            text = stringResource(R.string.incoming_counterparty_name_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = editableCounterpartyName,
                            onValueChange = {
                                editableCounterpartyName = it
                                if (it.trim().isNotBlank() && !it.trim().equals("You", ignoreCase = true)) {
                                    counterpartyNameError = false
                                }
                            },
                            label = { Text(stringResource(R.string.incoming_counterparty_name_label)) },
                            placeholder = { Text(stringResource(R.string.incoming_counterparty_name_placeholder)) },
                            isError = counterpartyNameError,
                            supportingText = if (counterpartyNameError) {
                                { Text(stringResource(R.string.incoming_counterparty_name_required)) }
                            } else {
                                null
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("incoming_counterparty_name_input")
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (showDisputeReason) {
                        OutlinedTextField(
                            value = disputeReason,
                            onValueChange = { disputeReason = it },
                            label = { Text(stringResource(R.string.ui_str_reason_for_disputing_e_g_79ca89)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dispute_reason_input")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showDisputeReason = false },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text(stringResource(R.string.ui_str_cancel_1507c4))
                            }
                            val defaultDisputeReason = stringResource(R.string.verification_default_dispute_reason)
                            Button(
                                onClick = {
                                    val resolvedName = editableCounterpartyName.trim()
                                    if (VerificationCryptoHelper.isPlaceholderSelfName(rawSenderName) &&
                                        VerificationCryptoHelper.isPlaceholderSelfName(resolvedName)
                                    ) {
                                        counterpartyNameError = true
                                        return@Button
                                    }
                                    onDisputeRecord(
                                        disputeReason.ifBlank { defaultDisputeReason },
                                        resolvedName
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("submit_dispute_btn"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Text(stringResource(R.string.ui_str_submit_dispute_4c1abb), fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                val resolvedName = editableCounterpartyName.trim()
                                if (VerificationCryptoHelper.isPlaceholderSelfName(rawSenderName) &&
                                    VerificationCryptoHelper.isPlaceholderSelfName(resolvedName)
                                ) {
                                    counterpartyNameError = true
                                    return@Button
                                }
                                onConfirmRecord(resolvedName)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .pressScaleFeedback()
                                .testTag("confirm_incoming_verification_btn"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.ui_str_confirm_save_mirrored_entry_ab4d5c), fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = { showDisputeReason = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .pressScaleFeedback()
                                .testTag("dispute_incoming_verification_btn"),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(UdhaarIcons.ReportProblem, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.ui_str_dispute_terms_57b8e1), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomingPaymentProfileAcceptSheet(
    payload: VerificationCryptoHelper.SharedPaymentProfilePayload,
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        contentWindowInsets = { WindowInsets.navigationBars.union(WindowInsets.ime) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .align(Alignment.CenterHorizontally)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.ui_str_save_shared_payment_details_25d811),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.ui_fmt_shared_their_payment_profile_accept_891c41, payload.displayName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    VerificationDetailRow(stringResource(R.string.verification_label_party_name), payload.displayName)
                    if (payload.phoneNumber.isNotBlank()) {
                        VerificationDetailRow(
                            stringResource(R.string.verification_label_phone_number),
                            InterestCalculator.maskPhoneNumber(payload.phoneNumber)
                        )
                    }
                    VerificationDetailRow(
                        stringResource(R.string.verification_label_upi_id),
                        if (payload.upiId.isNotBlank()) {
                            InterestCalculator.maskUpiId(payload.upiId)
                        } else {
                            stringResource(R.string.verification_value_none)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.ui_str_decline_2adbe2))
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("accept_shared_payment_profile_btn"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.ui_str_accept_save_upi_257fc1), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
