package com.jyotirmoyb.udhaar.ui.sheets

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.data.cloud.GuestWebLinkCreateResult
import com.jyotirmoyb.udhaar.ui.components.AnimatedSheetContent
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.UpiQrGenerator

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WebShareSheet(
    transaction: UdhaarTransaction,
    settings: UserSettings,
    existingResult: GuestWebLinkCreateResult? = null,
    isGenerating: Boolean = false,
    onGenerateLink: (
        expiryDays: Int,
        trustLevel: String,
        allowNote: Boolean,
        allowAttachments: Boolean,
        showUpi: Boolean,
        allowedActions: List<String>
    ) -> Unit,
    onRevokeLink: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var expiryDays by rememberSaveable { mutableIntStateOf(if (settings.defaultWebLinkExpiryDays in listOf(7, 30, 90)) settings.defaultWebLinkExpiryDays else 30) }
    var trustLevel by rememberSaveable { mutableStateOf(settings.defaultWebTrustLevel.ifBlank { "WHATSAPP_OTP" }) }
    var allowNote by rememberSaveable { mutableStateOf(true) }
    var allowAttachments by rememberSaveable { mutableStateOf(false) }
    var showUpi by rememberSaveable { mutableStateOf(settings.defaultWebShowUpi) }

    var actionConfirm by rememberSaveable { mutableStateOf(true) }
    var actionDispute by rememberSaveable { mutableStateOf(true) }
    var actionPayClaim by rememberSaveable { mutableStateOf(true) }
    var actionProposeDate by rememberSaveable { mutableStateOf(true) }

    val linkResult = existingResult

    val qrBitmap = remember(linkResult?.url) {
        val u = linkResult?.url.orEmpty()
        if (u.isNotBlank()) UpiQrGenerator.generateQrBitmap(u, sizePx = 520) else null
    }

    val draftWhatsAppMessage = remember(transaction, linkResult, settings) {
        val recipientName = transaction.partyName.ifBlank { "there" }
        val myName = settings.payeeName.ifBlank { "Udhaar" }
        val formattedAmount = InterestCalculator.formatCurrency(transaction.principalAmount, transaction.currency)
        val roleDesc = if (transaction.transactionType == TransactionType.GIVEN) "lent" else "borrowed"
        val otpLine = if (!linkResult?.whatsappOtp.isNullOrBlank()) {
            "\nYour WhatsApp verification code is: ${linkResult?.whatsappOtp}"
        } else ""

        "Hi $recipientName, please review and verify our Udhaar record ($formattedAmount $roleDesc by $myName):\n" +
                "${linkResult?.url.orEmpty()}$otpLine"
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        contentWindowInsets = { WindowInsets.navigationBars.union(WindowInsets.ime) },
        modifier = Modifier.testTag("web_share_bottom_sheet")
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
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.web_share_sheet_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val partySummary = "${transaction.partyName} • ${InterestCalculator.formatCurrency(transaction.principalAmount, transaction.currency)}"
                        Text(
                            text = partySummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Recipient Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                val sendLinkTitle = "Send private web link to ${transaction.partyName}"
                                Text(
                                    text = sendLinkTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (transaction.phoneNumber.isNotBlank()) "Phone: ${transaction.phoneNumber}" else "No sign-in required for counterparty",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Configuration Options (if link not generated yet or creating new)
                    if (linkResult == null || linkResult.url.isBlank()) {
                        Text(
                            text = stringResource(R.string.web_share_link_expiry),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7, 30, 90).forEach { days ->
                                FilterChip(
                                    selected = expiryDays == days,
                                    onClick = { expiryDays = days },
                                    label = { Text(stringResource(R.string.days_count, days)) },
                                    modifier = Modifier.testTag("expiry_chip_$days")
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.web_share_verification_method),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = trustLevel == "WHATSAPP_OTP",
                                onClick = { trustLevel = "WHATSAPP_OTP" },
                                label = { Text(stringResource(R.string.trust_level_whatsapp_otp)) },
                                leadingIcon = {
                                    Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.testTag("trust_level_whatsapp_otp")
                            )
                            FilterChip(
                                selected = trustLevel == "LINK_ONLY",
                                onClick = { trustLevel = "LINK_ONLY" },
                                label = { Text(stringResource(R.string.trust_level_link_only)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.testTag("trust_level_link_only")
                            )
                        }

                        // Permissions & Toggles
                        Text(
                            text = stringResource(R.string.web_share_allowed_details_actions),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(R.string.web_share_show_note), style = MaterialTheme.typography.bodySmall)
                                    Switch(checked = allowNote, onCheckedChange = { allowNote = it })
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(R.string.web_share_show_attachments), style = MaterialTheme.typography.bodySmall)
                                    Switch(checked = allowAttachments, onCheckedChange = { allowAttachments = it })
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(stringResource(R.string.web_share_show_upi), style = MaterialTheme.typography.bodySmall)
                                    Switch(checked = showUpi, onCheckedChange = { showUpi = it })
                                }
                            }
                        }

                        // Action Checkboxes
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = actionConfirm, onCheckedChange = { actionConfirm = it })
                                Text(stringResource(R.string.web_share_action_confirm), style = MaterialTheme.typography.labelSmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = actionDispute, onCheckedChange = { actionDispute = it })
                                Text(stringResource(R.string.web_share_action_dispute), style = MaterialTheme.typography.labelSmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = actionPayClaim, onCheckedChange = { actionPayClaim = it })
                                Text(stringResource(R.string.web_share_action_pay_claim), style = MaterialTheme.typography.labelSmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = actionProposeDate, onCheckedChange = { actionProposeDate = it })
                                Text(stringResource(R.string.web_share_action_propose_date), style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Button(
                            onClick = {
                                val allowed = mutableListOf<String>("view")
                                if (actionConfirm) allowed.add("confirm")
                                if (actionDispute) allowed.add("dispute")
                                if (actionPayClaim) allowed.add("pay_claim")
                                if (actionProposeDate) allowed.add("propose_date")
                                allowed.add("set_upi")
                                if (showUpi) allowed.add("show_pay_qr")

                                onGenerateLink(expiryDays, trustLevel, allowNote, allowAttachments, showUpi, allowed)
                            },
                            enabled = !isGenerating,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_generate_web_link"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.web_share_generating))
                            } else {
                                Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.web_share_create_btn))
                            }
                        }
                    } else {
                        // Generated Result View
                        if (qrBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .size(200.dp)
                                    .border(2.dp, PureBlack, RoundedCornerShape(12.dp))
                                    .background(PureWhite, RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "Guest Web Link QR Code",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        if (linkResult.whatsappOtp.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = stringResource(R.string.web_share_otp_code_title),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = linkResult.whatsappOtp,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 4.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = stringResource(R.string.web_share_otp_code_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val clipMsg = context.getString(R.string.share_code_separate_text, linkResult.whatsappOtp)
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText(context.getString(R.string.share_clip_web_link), clipMsg).apply {
                                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                                        description.extras = android.os.PersistableBundle().apply {
                                                            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                                                        }
                                                    }
                                                }
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, context.getString(R.string.toast_code_copied), Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f).height(38.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(stringResource(R.string.btn_share_code_separately), style = MaterialTheme.typography.labelSmall)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                Toast.makeText(context, context.getString(R.string.toast_read_code_in_person, linkResult.whatsappOtp), Toast.LENGTH_LONG).show()
                                            },
                                            modifier = Modifier.weight(1f).height(38.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(stringResource(R.string.btn_read_code_in_person), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        // WhatsApp Action Button
                        Button(
                            onClick = {
                                val sanitizedPhone = InterestCalculator.sanitizePhoneForWhatsApp(
                                    transaction.phoneNumber,
                                    transaction.currency
                                )
                                val encoded = Uri.encode(draftWhatsAppMessage)
                                val uri = if (sanitizedPhone.isNotBlank()) {
                                    Uri.parse("https://wa.me/$sanitizedPhone?text=$encoded")
                                } else {
                                    Uri.parse("https://api.whatsapp.com/send?text=$encoded")
                                }
                                val waIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                runCatching { context.startActivity(waIntent) }.onFailure {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, draftWhatsAppMessage)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Web Link"))
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_share_whatsapp"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.web_share_whatsapp_btn))
                        }

                        // Copy Link & OTP
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText(context.getString(R.string.share_clip_web_link), draftWhatsAppMessage).apply {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        description.extras = android.os.PersistableBundle().apply {
                                            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                                        }
                                    }
                                }
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, context.getString(R.string.toast_link_otp_copied), Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("btn_copy_web_link"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.web_share_copy_btn))
                        }

                        // Revoke Action
                        OutlinedButton(
                            onClick = {
                                onRevokeLink()
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("btn_revoke_web_link"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.web_share_revoke_btn))
                        }
                    }
                }
            }
        }
    }
}
