package com.jyotirmoyb.udhaar.ui.components
import com.jyotirmoyb.udhaar.R

import androidx.compose.ui.res.stringResource

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.content.FileProvider
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.UpiQrGenerator
import com.jyotirmoyb.udhaar.util.VerificationCryptoHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationShareSheet(
    transaction: UdhaarTransaction,
    shareLink: String,
    settings: UserSettings,
    attachments: List<Attachment> = emptyList(),
    onDismiss: () -> Unit,
    onOpenVerificationLink: (String) -> Unit
) {
    val context = LocalContext.current
    val qrBitmap = remember(shareLink) {
        UpiQrGenerator.generateQrBitmap(shareLink, sizePx = 560)
    }
    var pastedLink by remember { mutableStateOf("") }
    var showPasteInput by remember { mutableStateOf(false) }
    var shareAttachmentsWithPerson by rememberSaveable { mutableStateOf(false) }

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
                        text = stringResource(R.string.ui_str_two_party_verification_6d55b1),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.ui_fmt_symbol_a32425, transaction.partyName, InterestCalculator.formatCurrency(transaction.principalAmount, transaction.currency)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 14.dp, bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.ui_fmt_status_ae447c, transaction.verificationStatus.label),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val mirroredRoleLabel = if (transaction.transactionType == TransactionType.GIVEN) stringResource(R.string.ui_taken_borrowed) else stringResource(R.string.ui_given_lent)
                            Text(
                                text = stringResource(R.string.ui_fmt_when_confirms_they_receive_a_53500c, transaction.partyName, mirroredRoleLabel),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(210.dp)
                            .border(2.dp, PureBlack, RoundedCornerShape(12.dp))
                            .background(PureWhite, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Verification Link QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ui_str_scan_inside_udhaar_or_any_27d0ff),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.ui_str_sha_256_content_hash_tamper_1faaf4),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = transaction.contentHash.ifBlank { stringResource(R.string.ui_str_generating_813622) },
                            fontSize = 11.sp,
                            fontFamily = com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.ui_str_e2ee_protection_notes_attachments_a_7b2e75),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (attachments.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = shareAttachmentsWithPerson,
                                onCheckedChange = { shareAttachmentsWithPerson = it },
                                modifier = Modifier.testTag("share_attachments_checkbox")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.ui_str_share_attachments_with_this_person_d72168),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.ui_fmt_proof_document_s_bill_s_25c1eb, attachments.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                val shareMessage = remember(transaction, shareLink, settings, shareAttachmentsWithPerson, attachments) {
                    val calc = InterestCalculator.calculate(transaction)
                    val myName = settings.payeeName.ifBlank { "Udhaar User" }
                    val roleDesc = if (transaction.transactionType == TransactionType.GIVEN) {
                        "$myName lent ${InterestCalculator.formatCurrency(transaction.principalAmount, transaction.currency)} to ${transaction.partyName}"
                    } else {
                        "$myName borrowed ${InterestCalculator.formatCurrency(transaction.principalAmount, transaction.currency)} from ${transaction.partyName}"
                    }
                    buildString {
                        append("Namaste ${transaction.partyName},\n\n")
                        append("Please verify and confirm our Udhaar ledger entry:\n")
                        append("• Summary: $roleDesc\n")
                        append("• Net Balance: ${InterestCalculator.formatCurrency(calc.netBalanceDue, transaction.currency)}\n")
                        append("• Content Hash: ${transaction.contentHash.take(16)}...\n")
                        if (shareAttachmentsWithPerson && attachments.isNotEmpty()) {
                            append("• Proofs Attached: ${attachments.size} document(s)\n")
                        }
                        append("\nConfirm or Dispute here (opens in Udhaar or Web):\n")
                        append(shareLink)
                    }
                }

                Button(
                    onClick = {
                        val attachmentUris = if (shareAttachmentsWithPerson && attachments.isNotEmpty()) {
                            ArrayList(attachments.mapNotNull { att ->
                                try {
                                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(att.localPath))
                                } catch (_: Exception) {
                                    null
                                }
                            })
                        } else {
                            emptyList<Uri>()
                        }

                        if (attachmentUris.isNotEmpty()) {
                            val sendIntent = Intent(if (attachmentUris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
                                type = "*/*"
                                if (attachmentUris.size > 1) {
                                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(attachmentUris))
                                } else {
                                    putExtra(Intent.EXTRA_STREAM, attachmentUris.first())
                                }
                                putExtra(Intent.EXTRA_TEXT, shareMessage)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Verification & Attachments"))
                        } else {
                            val sanitizedPhone = InterestCalculator.sanitizePhoneForWhatsApp(
                                transaction.phoneNumber,
                                transaction.currency
                            )
                            val encoded = Uri.encode(shareMessage)
                            val waUrl = if (sanitizedPhone.isNotBlank()) {
                                "https://wa.me/$sanitizedPhone?text=$encoded"
                            } else {
                                "https://wa.me/?text=$encoded"
                            }
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)))
                            }.onFailure {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareMessage)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Verification Link"))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("share_verification_whatsapp_btn"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (shareAttachmentsWithPerson && attachments.isNotEmpty()) stringResource(R.string.ui_str_share_verification_with_attachments_f6177e)
                        else stringResource(R.string.ui_str_send_verification_on_whatsapp_a246d1),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Udhaar Verification Link", shareLink))
                            Toast.makeText(context, "Verification link copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("copy_verification_link_btn"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.ui_str_copy_link_1ccac4), style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            val attachmentUris = if (shareAttachmentsWithPerson && attachments.isNotEmpty()) {
                                ArrayList(attachments.mapNotNull { att ->
                                    try {
                                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(att.localPath))
                                    } catch (_: Exception) {
                                        null
                                    }
                                })
                            } else {
                                emptyList<Uri>()
                            }

                            val sendIntent = if (attachmentUris.isNotEmpty()) {
                                Intent(if (attachmentUris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
                                    type = "*/*"
                                    if (attachmentUris.size > 1) {
                                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(attachmentUris))
                                    } else {
                                        putExtra(Intent.EXTRA_STREAM, attachmentUris.first())
                                    }
                                    putExtra(Intent.EXTRA_SUBJECT, "Verify Udhaar Entry")
                                    putExtra(Intent.EXTRA_TEXT, shareMessage)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            } else {
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Verify Udhaar Entry")
                                    putExtra(Intent.EXTRA_TEXT, shareMessage)
                                }
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share via SMS or App"))
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_verification_other_btn"),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.ui_str_sms_other_63952d), style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    onClick = { showPasteInput = !showPasteInput },
                    modifier = Modifier.testTag("toggle_open_received_link_btn")
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showPasteInput) stringResource(R.string.ui_str_hide_received_link_box_ebe20c) else stringResource(R.string.ui_str_have_a_verification_link_from_3fe096),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                if (showPasteInput) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = pastedLink,
                        onValueChange = { pastedLink = it },
                        label = { Text(stringResource(R.string.ui_str_paste_https_jyotirmoyb_com_u_6b00ac)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (pastedLink.isNotBlank()) {
                                onOpenVerificationLink(pastedLink.trim())
                            }
                        },
                        enabled = pastedLink.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().pressScaleFeedback()
                    ) {
                        Text(stringResource(R.string.ui_str_inspect_confirm_mirrored_record_ab0e67))
                    }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomingVerificationReviewSheet(
    payload: VerificationCryptoHelper.SharedVerificationPayload,
    onDismiss: () -> Unit,
    onConfirmRecord: () -> Unit,
    onDisputeRecord: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    var showDisputeReason by remember { mutableStateOf(false) }
    var disputeReason by remember { mutableStateOf("") }

    val decryptedNote = remember(payload) {
        if (payload.encryptedNote.isNotBlank() && payload.shareToken.isNotBlank()) {
            VerificationCryptoHelper.decryptE2eeField(
                cipherText = payload.encryptedNote,
                remoteId = payload.remoteId,
                shareToken = payload.shareToken
            )
        } else ""
    }

    val senderName = if (payload.creatorRole == TransactionType.GIVEN) payload.lenderName else payload.borrowerName
    val mirroredRoleText = if (payload.creatorRole == TransactionType.GIVEN) {
        "YOU TOOK / BORROWED (Mirrored from ${payload.lenderName}'s 'Given' entry)"
    } else {
        "YOU GAVE / LENT (Mirrored from ${payload.borrowerName}'s 'Taken' entry)"
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
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
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
                                if (payload.currencyCode == stringResource(R.string.ui_str_npr_e09e1a)) CurrencyType.NPR else CurrencyType.INR
                            ),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(10.dp))

                        VerificationDetailRow("Lender (Gave)", payload.lenderName)
                        VerificationDetailRow("Borrower (Took)", payload.borrowerName)
                        VerificationDetailRow("Start Date", InterestCalculator.fmtDate(payload.startDateMillis))
                        VerificationDetailRow(
                            "Due Date",
                            payload.expiryDateMillis?.let { InterestCalculator.fmtDate(it) } ?: "Open / No Due Date"
                        )
                        VerificationDetailRow(
                            "Interest Terms",
                            if (!payload.isInterestEnabled || payload.interestRate <= 0.0) {
                                "0% (Interest-Free)"
                            } else {
                                "${payload.interestRate}% (${payload.interestType.label}, ${payload.interestRatePeriod.shortSuffix})"
                            }
                        )
                        if (decryptedNote.isNotBlank()) {
                            VerificationDetailRow("Decrypted Note (E2EE)", decryptedNote)
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
                            fontSize = 11.sp,
                            fontFamily = com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily,
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
                        Button(
                            onClick = {
                                onDisputeRecord(disputeReason.ifBlank { "Disputed by counterparty" })
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
                        onClick = onConfirmRecord,
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
                        Icon(Icons.Default.ReportProblem, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.ui_str_dispute_terms_57b8e1), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun VerificationDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareMyPaymentDetailsSheet(
    settings: UserSettings,
    targetParty: PartyEntity? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val myName = settings.payeeName.ifBlank { "Udhaar User" }

    val profileLink = remember(settings) {
        VerificationCryptoHelper.buildPaymentProfileLink(
            VerificationCryptoHelper.SharedPaymentProfilePayload(
                userId = settings.cloudUserId,
                displayName = myName,
                phoneNumber = settings.phoneNumber,
                upiId = settings.upiId,
                photoUrl = settings.profilePhotoUri
            )
        )
    }
    val qrBitmap = remember(profileLink) {
        UpiQrGenerator.generateQrBitmap(profileLink, sizePx = 560)
    }

    val shareText = remember(settings, profileLink, targetParty) {
        buildString {
            if (targetParty != null) {
                append("Namaste ${targetParty.name},\n\n")
            } else {
                append("Namaste,\n\n")
            }
            append("Here are my payment details from Udhaar:\n")
            append("• Name: $myName\n")
            if (settings.phoneNumber.isNotBlank()) {
                append("• Phone: ${InterestCalculator.maskPhoneNumber(settings.phoneNumber)}\n")
            }
            if (settings.upiId.isNotBlank()) {
                append("• UPI ID: ${InterestCalculator.maskUpiId(settings.upiId)}\n")
            }
            append("\nTap to save my UPI ID directly to your Udhaar Party book:\n")
            append(profileLink)
        }
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
                        text = stringResource(R.string.ui_str_share_my_payment_details_c9cc51),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (targetParty != null) stringResource(R.string.ui_fmt_sharing_with_565a7a, targetParty.name) else stringResource(R.string.ui_str_qr_app_link_for_instant_a4b214),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 14.dp, bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = myName.take(2).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = myName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (settings.phoneNumber.isNotBlank()) stringResource(R.string.ui_fmt_phone_812cdd, InterestCalculator.maskPhoneNumber(settings.phoneNumber)) else stringResource(R.string.ui_str_phone_not_set_in_settings_d8a427),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (settings.upiId.isNotBlank()) stringResource(R.string.ui_fmt_upi_2971f0, InterestCalculator.maskUpiId(settings.upiId)) else stringResource(R.string.ui_str_upi_id_not_set_in_c3a0bf),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(205.dp)
                            .border(2.dp, PureBlack, RoundedCornerShape(12.dp))
                            .background(PureWhite, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Payment Profile QR",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val phoneDigits = targetParty?.phoneNumber?.filter { it.isDigit() }.orEmpty()
                        val encoded = Uri.encode(shareText)
                        val waUrl = if (phoneDigits.length >= 10) {
                            val formatted = if (phoneDigits.length == 10) "91$phoneDigits" else phoneDigits
                            "https://wa.me/$formatted?text=$encoded"
                        } else {
                            "https://wa.me/?text=$encoded"
                        }
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)))
                        }.onFailure {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Payment Profile"))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("share_payment_profile_whatsapp_btn"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_str_share_via_whatsapp_b0968d), style = MaterialTheme.typography.labelLarge)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Udhaar Payment Profile", profileLink))
                            Toast.makeText(context, "Payment profile link copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.ui_str_copy_link_1ccac4), style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Payment Details"))
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.ui_str_other_app_f796ab), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestPartyPaymentDetailsSheet(
    party: PartyEntity,
    settings: UserSettings,
    onDismiss: () -> Unit,
    onOpenReceivedProfileLink: (String) -> Unit
) {
    val context = LocalContext.current
    var pastedProfileLink by remember { mutableStateOf("") }
    val myName = settings.payeeName.ifBlank { "Udhaar User" }

    val requestLink = "https://jyotirmoyb.com/u/request-upi?from=${Uri.encode(myName)}&party=${Uri.encode(party.name)}"
    val qrBitmap = remember(requestLink) {
        UpiQrGenerator.generateQrBitmap(requestLink, sizePx = 500)
    }

    val requestMessage = remember(party, myName) {
        buildString {
            append("Namaste ${party.name},\n\n")
            append("$myName is requesting your UPI ID / payment details on Udhaar so payments can be settled quickly.\n\n")
            append("In Udhaar, tap 'Share My Payment Details' and send your link back, or reply with your UPI ID:\n")
            append(requestLink)
        }
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
                        text = stringResource(R.string.ui_str_request_payment_details_d31a36),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.ui_fmt_ask_to_share_their_upi_ee3730, party.name),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 14.dp, bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .border(2.dp, PureBlack, RoundedCornerShape(12.dp))
                            .background(PureWhite, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Request Payment Details QR",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val phoneDigits = party.phoneNumber.filter { it.isDigit() }
                        val encoded = Uri.encode(requestMessage)
                        val waUrl = if (phoneDigits.length >= 10) {
                            val formatted = if (phoneDigits.length == 10) "91$phoneDigits" else phoneDigits
                            "https://wa.me/$formatted?text=$encoded"
                        } else {
                            "https://wa.me/?text=$encoded"
                        }
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)))
                        }.onFailure {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, requestMessage)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Request Payment Details"))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("send_request_payment_whatsapp_btn"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_str_request_via_whatsapp_11cb38), style = MaterialTheme.typography.labelLarge)
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.ui_fmt_already_received_s_payment_link_7c4c6f, party.name),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pastedProfileLink,
                    onValueChange = { pastedProfileLink = it },
                    label = { Text(stringResource(R.string.ui_str_paste_https_jyotirmoyb_com_u_906892)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("paste_party_profile_link_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        if (pastedProfileLink.isNotBlank()) {
                            onOpenReceivedProfileLink(pastedProfileLink.trim())
                        }
                    },
                    enabled = pastedProfileLink.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("preview_shared_profile_btn"),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.ui_str_preview_accept_payment_details_be315a))
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
                    VerificationDetailRow("Party Name", payload.displayName)
                    if (payload.phoneNumber.isNotBlank()) {
                        VerificationDetailRow("Phone Number", InterestCalculator.maskPhoneNumber(payload.phoneNumber))
                    }
                    VerificationDetailRow(
                        "UPI ID",
                        if (payload.upiId.isNotBlank()) InterestCalculator.maskUpiId(payload.upiId) else "None"
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
