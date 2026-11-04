package com.jyotirmoyb.udhaar.ui.sheets.detail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.ui.components.UdhaarAnimatedSwitch
import com.jyotirmoyb.udhaar.ui.components.pressScaleFeedback
import com.jyotirmoyb.udhaar.util.AttachmentManager
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import java.util.Locale

@Composable
fun PartialPaymentSection(
    summary: CalculatedUdhaarSummary,
    tx: UdhaarTransaction,
    onRecordPartialPayment: (amount: Double, paymentMode: PaymentMode, note: String) -> Unit,
    onRecordCounterpartyPayment: (amount: Double, paymentMode: PaymentMode, note: String) -> Unit,
    onRecordPartialPaymentWithProof: (amount: Double, paymentMode: PaymentMode, note: String, proof: Attachment?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var partialAmountText by rememberSaveable { mutableStateOf("") }
    var partialPaymentMode by rememberSaveable { mutableStateOf<PaymentMode>(tx.paymentMode) }
    var partialNoteText by rememberSaveable { mutableStateOf("") }
    var reportedByCounterparty by rememberSaveable { mutableStateOf(false) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }
    var pendingProofAttachment by remember { mutableStateOf<Attachment?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = cameraTempUri
        if (success && capturedUri != null) {
            val processed = AttachmentManager.processCapturedImage(
                context = context,
                sourceUri = capturedUri,
                transactionId = tx.id,
                addedBy = "You"
            )
            if (processed != null) {
                pendingProofAttachment = processed
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val processed = AttachmentManager.processCapturedImage(
                context = context,
                sourceUri = uri,
                transactionId = tx.id,
                addedBy = "You"
            )
            if (processed != null) {
                pendingProofAttachment = processed
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.ui_str_record_partial_installment_payment_a11544),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Quick Percentage Fill Chips (25%, 50%, 75%, 100% Full Remaining)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "25%" to 0.25,
                "50%" to 0.50,
                "75%" to 0.75,
                "100% FULL" to 1.0
            ).forEach { (label, fraction) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .clickable {
                            val computed = kotlin.math.round(summary.netBalanceDue * fraction * 100.0) / 100.0
                            partialAmountText = if (computed % 1.0 == 0.0) {
                                computed.toLong().toString()
                            } else {
                                String.format(Locale.US, "%.2f", computed)
                            }
                            errorText = null
                        }
                        .padding(vertical = 6.dp)
                        .testTag("quick_pay_${label.take(3).lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Payment Mode Selector for Installment (Cash / UPI / Bank / Other)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PaymentMode.entries.forEach { mode ->
                val isSelected = partialPaymentMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { partialPaymentMode = mode }
                        .padding(vertical = 6.dp)
                        .testTag("partial_mode_${mode.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = partialAmountText,
                onValueChange = {
                    if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) {
                        partialAmountText = it
                        errorText = null
                    }
                },
                label = { Text(stringResource(R.string.ui_fmt_amount_c53f8e, tx.currency.symbol)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .weight(1f)
                    .testTag("partial_payment_amount_input")
            )
            OutlinedTextField(
                value = partialNoteText,
                onValueChange = { partialNoteText = it },
                label = { Text(stringResource(R.string.ui_str_note_optional_df9d34)) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("partial_payment_note_input")
            )
        }

        // Optional Payment Proof Attachment
        if (pendingProofAttachment == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val (uri, _) = AttachmentManager.createCameraTempFileUri(context)
                        cameraTempUri = uri
                        cameraLauncher.launch(uri)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag("proof_camera_button"),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.ui_str_proof_cam_cc115b), style = MaterialTheme.typography.labelSmall)
                }

                OutlinedButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag("proof_gallery_button"),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.ui_str_proof_pic_ec0060), style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            val currentProof = pendingProofAttachment
            if (currentProof != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(
                                R.string.ui_fmt_proof_6a6b8f,
                                currentProof.fileName,
                                AttachmentManager.formatFileSize(currentProof.sizeBytes)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            AttachmentManager.deleteAttachmentFile(context, currentProof.localPath)
                            pendingProofAttachment = null
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove proof", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { reportedByCounterparty = !reportedByCounterparty }
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.ui_fmt_reported_by_requires_my_confirmatio_cb9cc6, tx.partyName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            UdhaarAnimatedSwitch(
                checked = reportedByCounterparty,
                onCheckedChange = { reportedByCounterparty = it },
                modifier = Modifier.testTag("reported_by_counterparty_switch")
            )
        }

        val currentErrorText = errorText
        if (currentErrorText != null) {
            Text(
                text = currentErrorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
        }

        Button(
            onClick = {
                val amt = partialAmountText.trim().toDoubleOrNull()
                if (amt == null || amt <= 0.0) {
                    errorText = "Enter a valid installment amount > 0"
                    return@Button
                }
                val proof = pendingProofAttachment
                if (reportedByCounterparty) {
                    onRecordCounterpartyPayment(amt, partialPaymentMode, partialNoteText.trim())
                } else if (proof != null) {
                    onRecordPartialPaymentWithProof(amt, partialPaymentMode, partialNoteText.trim(), proof)
                } else {
                    onRecordPartialPayment(amt, partialPaymentMode, partialNoteText.trim())
                }
                partialAmountText = ""
                partialNoteText = ""
                pendingProofAttachment = null
                reportedByCounterparty = false
                errorText = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .pressScaleFeedback()
                .testTag("submit_partial_payment_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(
                    R.string.ui_fmt_add_installment_061836,
                    partialPaymentMode.label.uppercase()
                ),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
