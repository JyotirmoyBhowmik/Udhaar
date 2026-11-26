package com.jyotirmoyb.udhaar.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.ui.icons.UdhaarIcons
import com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.UpiQrGenerator
import java.io.File

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
                            text = stringResource(
                                R.string.ui_fmt_symbol_a32425,
                                transaction.partyName,
                                InterestCalculator.formatPaise(transaction.principalAmountPaise, transaction.currency)
                            ),
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
                                imageVector = UdhaarIcons.VerifiedUser,
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
                                val mirroredRoleLabel = if (transaction.transactionType == TransactionType.GIVEN) {
                                    stringResource(R.string.ui_taken_borrowed)
                                } else {
                                    stringResource(R.string.ui_given_lent)
                                }
                                Text(
                                    text = stringResource(
                                        R.string.ui_fmt_when_confirms_they_receive_a_53500c,
                                        transaction.partyName,
                                        mirroredRoleLabel
                                    ),
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
                                contentDescription = stringResource(R.string.cd_verification_qr),
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
                                fontSize = 12.sp,
                                fontFamily = IbmPlexMonoFamily,
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
                            "*$myName* lent *${InterestCalculator.formatPaise(transaction.principalAmountPaise, transaction.currency)}* to *${transaction.partyName}*"
                        } else {
                            "*$myName* borrowed *${InterestCalculator.formatPaise(transaction.principalAmountPaise, transaction.currency)}* from *${transaction.partyName}*"
                        }
                        buildString {
                            append("*Udhaar Record Verification*\n")
                            append("Namaste ${transaction.partyName},\n\n")
                            append("• $roleDesc\n")
                            append("• Net Balance: *${InterestCalculator.formatCurrency(calc.netBalanceDue, transaction.currency)}*\n")
                            append("• ID: `${transaction.contentHash.take(10)}`\n")
                            if (shareAttachmentsWithPerson && attachments.isNotEmpty()) {
                                append("• Proofs: ${attachments.size} attached\n")
                            }
                            append("\n👇 *Tap link to Confirm or Dispute (App or Web):*\n")
                            append(shareLink)
                        }
                    }

                    Button(
                        onClick = {
                            val attachmentUris = resolveShareableAttachmentUris(
                                context = context,
                                includeAttachments = shareAttachmentsWithPerson,
                                attachments = attachments
                            )
                            if (attachmentUris.isNotEmpty()) {
                                val sendIntent = buildAttachmentShareIntent(attachmentUris, shareMessage, null)
                                context.startActivity(
                                    Intent.createChooser(sendIntent, context.getString(R.string.chooser_share_verification_attachments))
                                )
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
                                    context.startActivity(
                                        Intent.createChooser(sendIntent, context.getString(R.string.chooser_share_verification_link))
                                    )
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
                            if (shareAttachmentsWithPerson && attachments.isNotEmpty()) {
                                stringResource(R.string.ui_str_share_verification_with_attachments_f6177e)
                            } else {
                                stringResource(R.string.ui_str_send_verification_on_whatsapp_a246d1)
                            },
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
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(context.getString(R.string.share_clip_verification_link), shareLink)
                                )
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_verification_link_copied),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("copy_verification_link_btn"),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(UdhaarIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.ui_str_copy_link_1ccac4), style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = {
                                val attachmentUris = resolveShareableAttachmentUris(
                                    context = context,
                                    includeAttachments = shareAttachmentsWithPerson,
                                    attachments = attachments
                                )
                                val subject = context.getString(R.string.share_subject_verify_entry)
                                val sendIntent = if (attachmentUris.isNotEmpty()) {
                                    buildAttachmentShareIntent(attachmentUris, shareMessage, subject)
                                } else {
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, subject)
                                        putExtra(Intent.EXTRA_TEXT, shareMessage)
                                    }
                                }
                                context.startActivity(
                                    Intent.createChooser(sendIntent, context.getString(R.string.chooser_share_sms_or_app))
                                )
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
                        Icon(UdhaarIcons.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (showPasteInput) {
                                stringResource(R.string.ui_str_hide_received_link_box_ebe20c)
                            } else {
                                stringResource(R.string.ui_str_have_a_verification_link_from_3fe096)
                            },
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

private fun resolveShareableAttachmentUris(
    context: Context,
    includeAttachments: Boolean,
    attachments: List<Attachment>
): List<Uri> {
    if (!includeAttachments || attachments.isEmpty()) return emptyList()
    return attachments.mapNotNull { att ->
        try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(att.localPath))
        } catch (_: Exception) {
            null
        }
    }
}

private fun buildAttachmentShareIntent(
    attachmentUris: List<Uri>,
    shareMessage: String,
    subject: String?
): Intent {
    val action = if (attachmentUris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND
    return Intent(action).apply {
        type = "*/*"
        if (attachmentUris.size > 1) {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(attachmentUris))
        } else {
            putExtra(Intent.EXTRA_STREAM, attachmentUris.first())
        }
        if (subject != null) {
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        putExtra(Intent.EXTRA_TEXT, shareMessage)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

@Composable
internal fun VerificationDetailRow(label: String, value: String) {
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
