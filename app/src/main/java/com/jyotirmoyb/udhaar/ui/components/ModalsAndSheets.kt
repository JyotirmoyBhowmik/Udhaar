package com.jyotirmoyb.udhaar.ui.components
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.BuildConfig

import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import coil.compose.AsyncImage
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.util.AttachmentManager
import java.io.File
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.data.AuditTrailEntry
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.data.VerificationStatus
import com.jyotirmoyb.udhaar.security.SecureScreenEffect
import com.jyotirmoyb.udhaar.ui.QrModalState
import com.jyotirmoyb.udhaar.ui.theme.AppFontScale
import com.jyotirmoyb.udhaar.ui.theme.AppThemeMode
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.ui.theme.SubtleSurfaceGrey
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.ThermalReceiptGenerator
import com.jyotirmoyb.udhaar.util.UpiQrGenerator

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailModal(
    summary: CalculatedUdhaarSummary,
    partialPayments: List<PartialPayment>,
    auditTrail: List<AuditTrailEntry> = emptyList(),
    linkedParty: PartyEntity? = null,
    attachments: List<Attachment> = emptyList(),
    attachmentsByPayment: Map<Long, List<Attachment>> = emptyMap(),
    onDismiss: () -> Unit,
    onRecordPartialPayment: (amount: Double, paymentMode: PaymentMode, note: String) -> Unit,
    onRecordCounterpartyPayment: (amount: Double, paymentMode: PaymentMode, note: String) -> Unit = { _, _, _ -> },
    onRecordPartialPaymentWithProof: (amount: Double, paymentMode: PaymentMode, note: String, proof: Attachment?) -> Unit = { _, _, _, _ -> },
    onAcknowledgePartialPayment: (PartialPayment) -> Unit = {},
    onDeletePartialPayment: (PartialPayment) -> Unit,
    onAddAttachment: (Attachment) -> Unit = {},
    onDeleteAttachment: (Attachment) -> Unit = {},
    onOpenAttachmentViewer: (Attachment) -> Unit = {},
    onRequestVerification: () -> Unit = {},
    onConfirmOrDisputeRecord: (confirm: Boolean, disputeReason: String) -> Unit = { _, _ -> },
    onToggleSettled: () -> Unit,
    onEdit: () -> Unit,
    onDeleteTransaction: () -> Unit,
    onOpenQr: () -> Unit,
    onPayPartyViaUpi: (partyName: String, partyUpiId: String, amountInr: Double) -> Unit = { _, _, _ -> },
    onShareReceipt: () -> Unit = {},
    onOpenWhatsApp: () -> Unit,
    unreadChatCount: Int = 0,
    onOpenTransactionChat: () -> Unit = {}
) {
    val tx = summary.transaction
    val context = LocalContext.current
    var partialAmountText by rememberSaveable { mutableStateOf("") }
    var partialPaymentMode by rememberSaveable { mutableStateOf(tx.paymentMode) }
    var partialNoteText by rememberSaveable { mutableStateOf("") }
    var reportedByCounterparty by rememberSaveable { mutableStateOf(false) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var showDisputeInput by rememberSaveable { mutableStateOf(false) }
    var disputeReasonInput by rememberSaveable { mutableStateOf("") }
    var showVerificationDetails by rememberSaveable { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            modifier = Modifier.testTag("delete_transaction_confirm_dialog"),
            title = {
                Text(
                    text = stringResource(R.string.ui_str_delete_transaction_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.ui_fmt_delete_transaction_confirm_body,
                        tx.partyName,
                        InterestCalculator.formatCurrency(tx.principalAmount, tx.currency)
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        onDeleteTransaction()
                    },
                    modifier = Modifier.testTag("confirm_delete_transaction_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.ui_str_delete_d6f563))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmDelete = false },
                    modifier = Modifier.testTag("cancel_delete_transaction_button")
                ) {
                    Text(stringResource(R.string.ui_str_cancel_1507c4))
                }
            }
        )
    }

    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }
    var pendingProofAttachment by remember { mutableStateOf<Attachment?>(null) }
    var isAttachingToPayment by remember { mutableStateOf(false) }
    var showProofPickerMenu by remember { mutableStateOf(false) }

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
                if (isAttachingToPayment) {
                    pendingProofAttachment = processed
                } else {
                    onAddAttachment(processed)
                }
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
                if (isAttachingToPayment) {
                    pendingProofAttachment = processed
                } else {
                    onAddAttachment(processed)
                }
            }
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val processed = AttachmentManager.copyDocumentToStorage(
                context = context,
                sourceUri = uri,
                transactionId = tx.id,
                addedBy = "You"
            )
            if (processed != null) {
                if (isAttachingToPayment) {
                    pendingProofAttachment = processed
                } else {
                    onAddAttachment(processed)
                }
            }
        }
    }

    val reducedMotion = rememberReducedMotionEnabled()
    val animatedNetBalanceText = rememberAnimatedCurrencyText(
        targetAmount = summary.netBalanceDue,
        currency = tx.currency,
        hideAmount = false,
        reducedMotion = reducedMotion
    )

    BackHandler(onBack = onDismiss)

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .udhaarSharedBounds("tx_card_${tx.id}")
            .testTag("transaction_detail_screen"),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
        ) {
            // Sticky Header with shared avatar and name
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PersonAvatar(
                        name = tx.partyName,
                        photoUri = linkedParty?.photoUri.orEmpty(),
                        size = 40.dp,
                        modifier = Modifier.udhaarSharedElement("tx_avatar_${tx.id}")
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tx.partyName.uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.udhaarSharedBounds("tx_name_${tx.id}")
                        )
                        Text(
                            text = stringResource(R.string.ui_fmt_symbol_41590b, tx.transactionType.badgeLabel, tx.currency.code, tx.paymentMode.label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .pressScaleFeedback()
                        .testTag("close_detail_modal_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

            // Scrollable Body with >= 32dp extra bottom padding
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Net Balance & Breakdown Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (tx.isSettled) stringResource(R.string.ui_str_settled_record_2977ef) else stringResource(R.string.ui_str_net_remaining_due_3993cb),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = animatedNetBalanceText,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.udhaarSharedBounds("tx_amount_${tx.id}")
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                    BreakdownLine(
                        label = if (tx.transactionType == TransactionType.GIVEN) "Amount lent" else "Amount borrowed",
                        value = InterestCalculator.formatCurrency(tx.principalAmount, tx.currency)
                    )
                    BreakdownLine(
                        label = if (tx.isInterestEnabled) {
                            "${tx.interestType.label} (${tx.interestRate}${tx.interestRatePeriod.shortSuffix} • ${summary.elapsedDays}d)"
                        } else {
                            "Interest"
                        },
                        value = InterestCalculator.formatCurrency(summary.accruedInterest, tx.currency)
                    )
                    BreakdownLine("Total with interest", InterestCalculator.formatCurrency(summary.grossTotal, tx.currency))
                    BreakdownLine("Total paid / received", "- ${InterestCalculator.formatCurrency(tx.amountPaid, tx.currency)}")
                    BreakdownLine("Start date", InterestCalculator.formatDate(tx.startDateMillis))
                    tx.expiryDateMillis?.let {
                        BreakdownLine(
                            label = if (summary.isOverdue) "Due date (Overdue)" else "Due date",
                            value = InterestCalculator.formatDate(it),
                            bold = summary.isOverdue
                        )
                    }
                    if (tx.notes.isNotBlank()) {
                        BreakdownLine("Notes", tx.notes)
                    }
                }

                // Phase 3: Two-Party Verification Card with collapsed technical details
                Column(
                    modifier = Modifier
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
                                    modifier = Modifier.weight(1f).height(44.dp).pressScaleFeedback(),
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

                // Phase 6: Transaction Documents & Attachments Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .testTag("detail_attachments_section"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.ui_fmt_attached_documents_7a618e, attachments.size),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Quick Attach Action Buttons (Camera / Gallery / File Picker)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val (uri, _) = AttachmentManager.createCameraTempFileUri(context)
                                cameraTempUri = uri
                                isAttachingToPayment = false
                                cameraLauncher.launch(uri)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("attach_camera_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.ui_str_camera_7d2039), style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = {
                                isAttachingToPayment = false
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("attach_gallery_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.ui_str_gallery_73dcec), style = MaterialTheme.typography.labelSmall)
                        }

                        OutlinedButton(
                            onClick = {
                                isAttachingToPayment = false
                                documentLauncher.launch(arrayOf("application/pdf", "image/*", "text/*", "*/*"))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("attach_doc_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.ui_str_doc_pdf_c5e19f), style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (attachments.isEmpty()) {
                        Text(
                            text = stringResource(R.string.ui_str_no_documents_attached_attach_promis_e527cb),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("detail_attachments_lazy_row"),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(attachments, key = { it.id }) { att ->
                                val isImg = att.mimeType.startsWith("image/")
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = udhaarAnimateItem(reducedMotion)
                                        .size(width = 110.dp, height = 110.dp)
                                        .pressScaleFeedback()
                                        .clickable { onOpenAttachmentViewer(att) }
                                        .testTag("attachment_thumb_${att.id}")
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        if (isImg) {
                                            AsyncImage(
                                                model = File(att.localPath),
                                                contentDescription = att.fileName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Description,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(32.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = att.fileName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = AttachmentManager.formatFileSize(att.sizeBytes),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .background(PureBlack.copy(alpha = 0.75f))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = att.fileName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PureWhite,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Partial Payment Entry Section (Only when not settled)
                if (!tx.isSettled) {
                    Column(
                        modifier = Modifier
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
                                                String.format(java.util.Locale.US, "%.2f", computed)
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
                                        isAttachingToPayment = true
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
                                        isAttachingToPayment = true
                                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
                                            text = stringResource(R.string.ui_fmt_proof_6a6b8f, currentProof.fileName, AttachmentManager.formatFileSize(currentProof.sizeBytes)),
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
                            Text(stringResource(R.string.ui_fmt_add_installment_061836, partialPaymentMode.label.uppercase()), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                // Installment History List
                if (partialPayments.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ui_fmt_payment_history_534f10, partialPayments.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        partialPayments.forEach { payment ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = InterestCalculator.formatCurrency(payment.amount, tx.currency),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = payment.paymentMode.label.uppercase(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = if (payment.isAcknowledged) stringResource(R.string.ui_str_acknowledged_f4e3eb) else stringResource(R.string.ui_str_unconfirmed_9c4ae7),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (payment.isAcknowledged) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.error
                                                }
                                            )
                                        }
                                        val recorder = payment.recordedByName.ifBlank { "You" }
                                        val sub = buildString {
                                            append("Recorded by $recorder • ")
                                            append(InterestCalculator.fmtDate(payment.paidAtMillis))
                                            if (payment.note.isNotBlank()) {
                                                append(" • ")
                                                append(payment.note)
                                            }
                                        }
                                        Text(
                                            text = sub,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        val paymentProofs = attachmentsByPayment[payment.id].orEmpty()
                                        if (paymentProofs.isNotEmpty()) {
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                            ) {
                                                items(paymentProofs, key = { it.id }) { proof ->
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = MaterialTheme.colorScheme.surface,
                                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                                        modifier = Modifier
                                                            .clickable { onOpenAttachmentViewer(proof) }
                                                            .testTag("payment_proof_badge_${payment.id}_${proof.id}")
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.AttachFile,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = stringResource(R.string.ui_fmt_proof_08b803, proof.fileName),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    IconButton(
                                        onClick = { onDeletePartialPayment(payment) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Delete installment",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                if (!payment.isAcknowledged || payment.needsMyAcknowledgement) {
                                    val displayActor = payment.recordedByName.ifBlank { tx.partyName }
                                    val formattedAmt = InterestCalculator.formatCurrency(payment.amount, tx.currency)
                                    OutlinedButton(
                                        onClick = { onAcknowledgePartialPayment(payment) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .testTag("acknowledge_payment_${payment.id}"),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.ui_fmt_marked_received_confirm_59385a, displayActor, formattedAmt),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Collapsible "Verification details" section (SHA-256 hash, cloud IDs, and Audit Trail)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .testTag("detail_audit_trail_section"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showVerificationDetails = !showVerificationDetails }
                            .padding(vertical = 4.dp)
                            .testTag("toggle_verification_details_button"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.ui_str_verification_details_533cd5),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (showVerificationDetails) {
                                    stringResource(R.string.ui_str_hide_technical_sha_256_hash_b7df6b)
                                } else {
                                    stringResource(R.string.ui_fmt_tap_to_view_sha_256_e79662, auditTrail.size)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (showVerificationDetails) stringResource(R.string.ui_str_hide_34d8b6) else stringResource(R.string.ui_str_show_d97d1e),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    AnimatedVisibility(visible = showVerificationDetails) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.8.dp)
                        if (tx.contentHash.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.ui_fmt_sha_256_4f25db, tx.contentHash),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (tx.remoteId.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.ui_fmt_cloud_id_b63185, tx.remoteId),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (auditTrail.isEmpty()) {
                            Text(
                                text = stringResource(R.string.ui_str_no_audit_events_recorded_yet_68b5a2),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            auditTrail.forEach { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.ui_fmt_symbol_969885, entry.eventType.label, entry.actorName),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (entry.summaryText.isNotBlank()) {
                                            Text(
                                                text = entry.summaryText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (entry.contentHashSnapshot.isNotBlank()) {
                                            Text(
                                                text = stringResource(R.string.ui_fmt_hash_708ee0, entry.contentHashSnapshot.take(16)),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = InterestCalculator.fmtDate(entry.eventTimeMillis),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        }
                    }
                }

                // Phase 4: In-App Transaction Chat & One-Tap Extension Button
                Button(
                    onClick = onOpenTransactionChat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .pressScaleFeedback()
                        .testTag("detail_open_chat_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (unreadChatCount > 0) {
                            stringResource(R.string.ui_fmt_open_transaction_chat_new_91bcd1, unreadChatCount)
                        } else {
                            stringResource(R.string.ui_str_open_transaction_chat_extension_41a166)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Quick Actions Row (WhatsApp + Thermal Receipt + UPI QR / Pay Party UPI)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenWhatsApp,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .pressScaleFeedback()
                                .testTag("detail_whatsapp_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.ui_str_whatsapp_5c39d5), style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = onShareReceipt,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .pressScaleFeedback()
                                .testTag("detail_thermal_receipt_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.ui_str_receipt_slip_1ccbe9), style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (tx.currency == CurrencyType.INR && !tx.isSettled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (tx.transactionType == TransactionType.GIVEN) {
                                OutlinedButton(
                                    onClick = onOpenQr,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .pressScaleFeedback()
                                        .testTag("detail_upi_qr_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.ui_str_collect_via_my_upi_qr_c6b866), style = MaterialTheme.typography.labelMedium)
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
                                        .weight(1f)
                                        .height(48.dp)
                                        .pressScaleFeedback()
                                        .testTag("detail_pay_party_upi_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (partyUpi.isNotBlank()) stringResource(R.string.ui_fmt_pay_via_upi_7718ac, tx.partyName.uppercase()) else stringResource(R.string.ui_str_open_upi_qr_tool_2e9aa8),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Mark Settled / Re-open Button
                Button(
                    onClick = onToggleSettled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .pressScaleFeedback()
                        .testTag("toggle_settled_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (tx.isSettled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                        contentColor = if (tx.isSettled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                    ),
                    border = if (tx.isSettled) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (tx.isSettled) stringResource(R.string.ui_str_re_open_ledger_entry_bd882c) else stringResource(R.string.ui_str_mark_as_fully_settled_c262a2),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Edit & Delete Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .pressScaleFeedback()
                            .testTag("edit_transaction_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.ui_str_edit_17865b), style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = {
                            if (confirmDelete) {
                                onDeleteTransaction()
                            } else {
                                confirmDelete = true
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .pressScaleFeedback()
                            .testTag("delete_transaction_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (confirmDelete) stringResource(R.string.ui_str_tap_to_confirm_0fe819) else stringResource(R.string.ui_str_delete_d6f563),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        }
    }
}

/**
 * Full-Screen Settings Screen with Sticky Top Bar, Sticky Bottom Save Bar, and 5 Clear Sections:
 * 1. Profile and UPI
 * 2. Security
 * 3. Currency and Theme
 * 4. Backup and Sync
 * 5. About
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileSettingsModal(
    currentSettings: UserSettings,
    onSave: (
        upiId: String,
        payeeName: String,
        defaultCurrency: CurrencyType,
        inrEnabled: Boolean,
        nprEnabled: Boolean,
        themeMode: AppThemeMode,
        fontScale: AppFontScale,
        biometricLockEnabled: Boolean,
        securityPin: String,
        closeModal: Boolean
    ) -> Unit,
    onSaveExtendedProfile: (phoneNumber: String, profilePhotoUri: String, cloudVerificationConsent: Boolean) -> Unit = { _, _, _ -> },
    onShareMyPaymentDetails: () -> Unit = {},
    onOpenReceivedVerificationLink: (String) -> Unit = {},
    onSignInWithGoogleForVerification: () -> Unit = {},
    onDeleteMyCloudData: () -> Unit = {},
    onLockAppNow: () -> Unit = {},
    onOpenGlobalQrModal: () -> Unit = {},
    onOpenShareAppModal: () -> Unit = {},
    onExportFullLedgerSaf: () -> Unit,
    onShareFullLedgerFile: () -> Unit,
    onImportLedgerSaf: () -> Unit,
    driveRemoteBackups: List<com.jyotirmoyb.udhaar.backup.DriveRemoteBackup> = emptyList(),
    isDriveOperationInProgress: Boolean = false,
    driveOperationStatusMessage: String? = null,
    driveOperationError: String? = null,
    hasPendingDriveResolution: Boolean = false,
    driveConnectionTestReport: com.jyotirmoyb.udhaar.backup.DriveConnectionTestReport? = null,
    appSigningInfo: com.jyotirmoyb.udhaar.util.AppSigningInfo? = null,
    onSignInWithGoogleForDrive: () -> Unit = {},
    onReconnectGoogleDrive: () -> Unit = onSignInWithGoogleForDrive,
    onRunDriveConnectionTest: () -> Unit = {},
    onClearDriveConnectionTestReport: () -> Unit = {},
    onSignOutFromDrive: () -> Unit = {},
    onBackupNowToDrive: (passphrase: String, savePassphraseForAuto: Boolean) -> Unit = { _, _ -> },
    onRestoreDriveBackup: (com.jyotirmoyb.udhaar.backup.DriveRemoteBackup, passphrase: String) -> Unit = { _, _ -> },
    onDeleteDriveBackup: (com.jyotirmoyb.udhaar.backup.DriveRemoteBackup) -> Unit = {},
    onDeleteAllDriveBackups: () -> Unit = {},
    onUpdateAutoBackupSettings: (com.jyotirmoyb.udhaar.data.AutoBackupFrequency, Boolean, String?) -> Unit = { _, _, _ -> },
    onRefreshDriveBackups: () -> Unit = {},
    onClearDriveOperationError: () -> Unit = {},
    dbEncryptionState: com.jyotirmoyb.udhaar.security.EncryptedStorageManager.DbEncryptionState =
        com.jyotirmoyb.udhaar.security.EncryptedStorageManager.DbEncryptionState.ENCRYPTED,
    onRetryDatabaseEncryption: () -> Unit = {},
    onExportLocalUdhaarSaf: (passphrase: String) -> Unit = {},
    onRetryCloudBackup: () -> Unit = {},
    onOpenContactPicker: () -> Unit = {},
    onSaveReminderSettings: ((Boolean, Int, Int, Int, Int, Int) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    // Ensure FLAG_SECURE is cleared on the Settings screen so it renders visibly on all displays & screenshots
    SecureScreenEffect(enabled = false)

    BackHandler {
        onDismiss()
    }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    var payeeName by rememberSaveable(currentSettings.payeeName) { mutableStateOf(currentSettings.payeeName) }
    var upiId by rememberSaveable(currentSettings.upiId) { mutableStateOf(currentSettings.upiId) }
    var phoneNumber by rememberSaveable(currentSettings.phoneNumber) { mutableStateOf(currentSettings.phoneNumber) }
    var showFullUpiId by rememberSaveable { mutableStateOf(false) }
    var showFullPhoneNumber by rememberSaveable { mutableStateOf(false) }
    var profilePhotoUri by rememberSaveable(currentSettings.profilePhotoUri) { mutableStateOf(currentSettings.profilePhotoUri) }
    var cloudVerificationConsent by rememberSaveable(currentSettings.cloudVerificationConsent) {
        mutableStateOf(currentSettings.cloudVerificationConsent)
    }
    var incomingLinkInput by rememberSaveable { mutableStateOf("") }
    var confirmCloudDelete by rememberSaveable { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            profilePhotoUri = uri.toString()
            onSaveExtendedProfile(phoneNumber.trim(), profilePhotoUri, cloudVerificationConsent)
        }
    }

    var inrEnabled by rememberSaveable(currentSettings.inrEnabled) { mutableStateOf(currentSettings.inrEnabled) }
    var nprEnabled by rememberSaveable(currentSettings.nprEnabled) { mutableStateOf(currentSettings.nprEnabled) }
    var defaultCurrency by rememberSaveable(currentSettings.defaultCurrency) { mutableStateOf(currentSettings.defaultCurrency) }
    var themeMode by rememberSaveable(currentSettings.themeMode) { mutableStateOf(currentSettings.themeMode) }
    var fontScale by rememberSaveable(currentSettings.fontScale) { mutableStateOf(currentSettings.fontScale) }
    var biometricLockEnabled by rememberSaveable(currentSettings.biometricLockEnabled) {
        mutableStateOf(currentSettings.biometricLockEnabled)
    }
    var securityPin by rememberSaveable {
        mutableStateOf("")
    }
    var showSecurityPin by rememberSaveable { mutableStateOf(false) }
    var pinValidationError by rememberSaveable { mutableStateOf<String?>(null) }

    var showBackupDialog by rememberSaveable { mutableStateOf(false) }
    var isLocalUdhaarExportMode by rememberSaveable { mutableStateOf(false) }
    var backupPassphrase by rememberSaveable { mutableStateOf("") }
    var confirmBackupPassphrase by rememberSaveable { mutableStateOf("") }
    var savePassphraseForAuto by rememberSaveable { mutableStateOf(currentSettings.driveAutoBackupPassphraseSaved) }
    var backupPassphraseError by rememberSaveable { mutableStateOf<String?>(null) }
    var backupPassphraseVisible by rememberSaveable { mutableStateOf(false) }

    var showRestoreDialog by rememberSaveable { mutableStateOf(false) }
    var selectedRestoreBackup by remember { mutableStateOf<com.jyotirmoyb.udhaar.backup.DriveRemoteBackup?>(null) }
    var restorePassphrase by rememberSaveable { mutableStateOf("") }
    var restorePassphraseVisible by rememberSaveable { mutableStateOf(false) }
    var showDeleteAllBackupsDialog by rememberSaveable { mutableStateOf(false) }
    var showTechnicalDetails by rememberSaveable { mutableStateOf(false) }

    var quietHoursEnabled by rememberSaveable(currentSettings.quietHoursEnabled) { mutableStateOf(currentSettings.quietHoursEnabled) }
    var quietHoursStartHour by rememberSaveable(currentSettings.quietHoursStartHour) { mutableStateOf(currentSettings.quietHoursStartHour) }
    var quietHoursEndHour by rememberSaveable(currentSettings.quietHoursEndHour) { mutableStateOf(currentSettings.quietHoursEndHour) }
    var reminderL1Days by rememberSaveable(currentSettings.reminderLevel1DaysBefore) { mutableStateOf(currentSettings.reminderLevel1DaysBefore) }
    var reminderL2Days by rememberSaveable(currentSettings.reminderLevel2MaxOverdueDays) { mutableStateOf(currentSettings.reminderLevel2MaxOverdueDays) }
    var reminderL3Days by rememberSaveable(currentSettings.reminderLevel3MaxOverdueDays) { mutableStateOf(currentSettings.reminderLevel3MaxOverdueDays) }

    fun persistReminderSettings() {
        onSaveReminderSettings?.invoke(
            quietHoursEnabled,
            quietHoursStartHour,
            quietHoursEndHour,
            reminderL1Days,
            reminderL2Days,
            reminderL3Days
        )
    }

    val validateAndSave: (Boolean) -> Unit = { closeAfterSave ->
        val trimmedPin = securityPin.trim()
        val hasValidNewPin = trimmedPin.length in 4..8 && trimmedPin.all { it.isDigit() }
        if (biometricLockEnabled && !currentSettings.hasPinConfigured && !hasValidNewPin) {
            pinValidationError = "Please set a 4 to 8 digit numeric PIN before enabling App Lock."
            Toast.makeText(
                context,
                "Please set a 4 to 8 digit PIN to enable App Lock.",
                Toast.LENGTH_SHORT
            ).show()
        } else if (trimmedPin.isNotEmpty() && !hasValidNewPin) {
            pinValidationError = "Security PIN must be 4 to 8 numeric digits."
            Toast.makeText(
                context,
                "Security PIN must be 4 to 8 numeric digits.",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            pinValidationError = null
            onSaveExtendedProfile(phoneNumber.trim(), profilePhotoUri, cloudVerificationConsent)
            onSave(
                upiId.trim(),
                payeeName.trim(),
                defaultCurrency,
                inrEnabled,
                nprEnabled,
                themeMode,
                fontScale,
                biometricLockEnabled,
                trimmedPin,
                closeAfterSave
            )
            if (closeAfterSave) {
                Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val persistImmediately = {
        onSaveExtendedProfile(phoneNumber.trim(), profilePhotoUri, cloudVerificationConsent)
        onSave(
            upiId.trim(),
            payeeName.trim(),
            defaultCurrency,
            inrEnabled,
            nprEnabled,
            themeMode,
            fontScale,
            biometricLockEnabled,
            securityPin.trim(),
            false
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.ui_str_settings_preferences_1f2ee4),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.ui_str_profile_security_currency_backup_ab_1c71e9),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .imePadding()
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .pressScaleFeedback()
                            .testTag("cancel_settings_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(stringResource(R.string.ui_str_cancel_1507c4), style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = { validateAndSave(true) },
                        modifier = Modifier
                            .weight(1.6f)
                            .height(48.dp)
                            .pressScaleFeedback()
                            .testTag("save_settings_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.ui_str_save_settings_d0f818), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .testTag("settings_lazy_column"),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 44.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // =========================================================================
                // 1. PROFILE AND UPI
                // =========================================================================
                item(key = "section_profile_and_upi") {
                    SettingsSectionCard(
                        sectionNumber = "1",
                        title = "PROFILE AND UPI",
                        subtitle = "Business name & UPI ID for QR collection and WhatsApp reminders",
                        testTag = "section_profile_and_upi"
                    ) {
                        OutlinedTextField(
                            value = payeeName,
                            onValueChange = { payeeName = it },
                            label = { Text(stringResource(R.string.ui_str_your_business_payee_name_2ece56)) },
                            placeholder = { Text(stringResource(R.string.ui_str_e_g_sharma_general_store_99d829)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_payee_name_input")
                        )

                        OutlinedTextField(
                            value = upiId,
                            onValueChange = {
                                showFullUpiId = true
                                upiId = it
                            },
                            label = { Text(stringResource(R.string.ui_str_your_default_upi_id_for_72f5b9)) },
                            placeholder = { Text(stringResource(R.string.ui_str_e_g_merchant_okicici_a9d993)) },
                            singleLine = true,
                            visualTransformation = if (showFullUpiId || upiId.isBlank()) {
                                VisualTransformation.None
                            } else {
                                UpiMaskVisualTransformation
                            },
                            trailingIcon = {
                                if (upiId.isNotBlank()) {
                                    IconButton(
                                        onClick = { showFullUpiId = !showFullUpiId },
                                        modifier = Modifier.testTag("toggle_settings_upi_visibility")
                                    ) {
                                        Icon(
                                            imageVector = if (showFullUpiId) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (showFullUpiId) "Hide UPI ID" else "Show UPI ID"
                                        )
                                    }
                                }
                            },
                            supportingText = {
                                if (upiId.isNotBlank()) {
                                    Text(
                                        text = stringResource(R.string.ui_fmt_displayed_publicly_as_0e3b13, InterestCalculator.maskUpiId(upiId)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_upi_id_input")
                        )

                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = {
                                showFullPhoneNumber = true
                                phoneNumber = it
                            },
                            label = { Text(stringResource(R.string.ui_str_your_phone_number_for_verification_8fdaa6)) },
                            placeholder = { Text(stringResource(R.string.ui_str_e_g_91_9876543210_46e6ed)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            visualTransformation = if (showFullPhoneNumber || phoneNumber.isBlank()) {
                                VisualTransformation.None
                            } else {
                                PhoneMaskVisualTransformation
                            },
                            trailingIcon = {
                                if (phoneNumber.isNotBlank()) {
                                    IconButton(
                                        onClick = { showFullPhoneNumber = !showFullPhoneNumber },
                                        modifier = Modifier.testTag("toggle_settings_phone_visibility")
                                    ) {
                                        Icon(
                                            imageVector = if (showFullPhoneNumber) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (showFullPhoneNumber) "Hide Phone Number" else "Show Phone Number"
                                        )
                                    }
                                }
                            },
                            supportingText = {
                                if (phoneNumber.isNotBlank()) {
                                    Text(
                                        text = stringResource(R.string.ui_fmt_displayed_publicly_as_21e544, InterestCalculator.maskPhoneNumber(phoneNumber)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_phone_number_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("settings_pick_profile_photo_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text(
                                    text = if (profilePhotoUri.isNotBlank()) stringResource(R.string.ui_str_change_photo_c7c5db) else stringResource(R.string.ui_str_optional_photo_89a9b1),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    persistImmediately()
                                    onShareMyPaymentDetails()
                                },
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(48.dp)
                                    .testTag("settings_share_payment_profile_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.ui_str_share_my_details_439f39), style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                persistImmediately()
                                onOpenGlobalQrModal()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_open_upi_qr_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.ui_str_open_my_upi_qr_code_0821c9), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                // =========================================================================
                // 2. SECURITY
                // =========================================================================
                item(key = "section_security") {
                    SettingsSectionCard(
                        sectionNumber = "2",
                        title = "SECURITY",
                        subtitle = "Biometric fingerprint, device credential & salted PBKDF2 PIN lock",
                        testTag = "section_security"
                    ) {
                        val isEncryptionFailed = dbEncryptionState ==
                            com.jyotirmoyb.udhaar.security.EncryptedStorageManager.DbEncryptionState.FAILED ||
                            dbEncryptionState ==
                            com.jyotirmoyb.udhaar.security.EncryptedStorageManager.DbEncryptionState.NONE
                        val isEncryptionMigrating = dbEncryptionState ==
                            com.jyotirmoyb.udhaar.security.EncryptedStorageManager.DbEncryptionState.MIGRATING

                        val statusTitle = when {
                            dbEncryptionState == com.jyotirmoyb.udhaar.security.EncryptedStorageManager.DbEncryptionState.ENCRYPTED ->
                                stringResource(R.string.db_encryption_status_yes)
                            isEncryptionMigrating ->
                                stringResource(R.string.db_encryption_status_migrating)
                            else ->
                                stringResource(R.string.db_encryption_status_failed_retry)
                        }
                        val statusSubtitle = when {
                            dbEncryptionState == com.jyotirmoyb.udhaar.security.EncryptedStorageManager.DbEncryptionState.ENCRYPTED ->
                                stringResource(R.string.db_encryption_subtitle_yes)
                            isEncryptionMigrating ->
                                stringResource(R.string.db_encryption_subtitle_migrating)
                            else ->
                                stringResource(R.string.db_encryption_subtitle_failed)
                        }
                        val borderColor = when {
                            isEncryptionFailed -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                .background(
                                    if (isEncryptionFailed) {
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable(enabled = isEncryptionFailed) {
                                    onRetryDatabaseEncryption()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("database_encryption_status_row"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isEncryptionFailed) Icons.Default.Warning else Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = borderColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = statusTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isEncryptionFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.testTag("database_encryption_status_text")
                                    )
                                    Text(
                                        text = statusSubtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isEncryptionFailed) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = stringResource(R.string.ui_str_biometric_pin_app_lock_1d820f),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.ui_str_require_fingerprint_phone_pin_or_ea5359),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            UdhaarAnimatedSwitch(
                                checked = biometricLockEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        val candidate = securityPin.trim()
                                        val hasValidNewPin = candidate.length in 4..8 && candidate.all { it.isDigit() }
                                        if (!currentSettings.hasPinConfigured && !hasValidNewPin) {
                                            pinValidationError = "Set a 4 to 8 digit numeric PIN below before turning on App Lock."
                                            Toast.makeText(
                                                context,
                                                "Enter a 4 to 8 digit PIN below first to enable App Lock.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@UdhaarAnimatedSwitch
                                        }
                                        pinValidationError = null
                                        biometricLockEnabled = true
                                        persistImmediately()
                                    } else {
                                        pinValidationError = null
                                        biometricLockEnabled = false
                                        persistImmediately()
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("biometric_lock_switch")
                            )
                        }

                        OutlinedTextField(
                            value = securityPin,
                            onValueChange = { input ->
                                if (input.length <= 8 && input.all { it.isDigit() }) {
                                    securityPin = input
                                    pinValidationError = null
                                }
                            },
                            label = {
                                Text(
                                    if (currentSettings.hasPinConfigured) {
                                        stringResource(R.string.ui_str_change_4_8_digit_security_795f1a)
                                    } else {
                                        stringResource(R.string.ui_str_set_4_8_digit_security_400df1)
                                    }
                                )
                            },
                            placeholder = {
                                Text(
                                    if (currentSettings.hasPinConfigured) stringResource(R.string.ui_str_leave_blank_to_keep_current_b44066) else stringResource(R.string.ui_str_enter_4_to_8_digits_e796a0)
                                )
                            },
                            singleLine = true,
                            visualTransformation = if (showSecurityPin) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { showSecurityPin = !showSecurityPin },
                                    modifier = Modifier.testTag("toggle_settings_pin_visibility")
                                ) {
                                    Icon(
                                        imageVector = if (showSecurityPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showSecurityPin) "Hide PIN" else "Show PIN",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            isError = pinValidationError != null,
                            supportingText = {
                                val currentPinError = pinValidationError
                                if (currentPinError != null) {
                                    Text(
                                        text = currentPinError,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                } else {
                                    Text(
                                        text = if (currentSettings.hasPinConfigured) {
                                            stringResource(R.string.ui_str_pin_is_stored_as_a_a204da)
                                        } else {
                                            stringResource(R.string.ui_str_no_default_pin_exists_you_7470c3)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_security_pin_input")
                        )

                        if (currentSettings.hasPinConfigured || (securityPin.trim().length in 4..8)) {
                            OutlinedButton(
                                onClick = {
                                    persistImmediately()
                                    onLockAppNow()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("lock_app_now_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.ui_str_lock_app_now_5b5570), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                // =========================================================================
                // 3. CURRENCY AND THEME
                // =========================================================================
                item(key = "section_currency_and_theme") {
                    SettingsSectionCard(
                        sectionNumber = "3",
                        title = "CURRENCY AND THEME",
                        subtitle = "INR (₹) / NPR (रू) ledgers, 4 themes, font scale & phone settings",
                        testTag = "section_currency_and_theme"
                    ) {
                        Text(
                            text = stringResource(R.string.ui_str_enabled_ledger_currencies_f084db),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ui_str_enable_indian_rupee_inr_nepalese_109ff1),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.ui_str_indian_rupee_inr_e33b10),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.ui_str_supports_offline_upi_qr_inr_73075a),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            UdhaarAnimatedSwitch(
                                checked = inrEnabled,
                                onCheckedChange = { checked ->
                                    if (!checked && !nprEnabled) {
                                        nprEnabled = true
                                        defaultCurrency = CurrencyType.NPR
                                    }
                                    inrEnabled = checked
                                    if (!inrEnabled && defaultCurrency == CurrencyType.INR) {
                                        defaultCurrency = CurrencyType.NPR
                                    }
                                    persistImmediately()
                                },
                                modifier = Modifier.testTag("settings_enable_inr_switch")
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.ui_str_nepalese_rupee_npr_8944f0),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.ui_str_enable_npr_ledger_tabs_entries_fcb848),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            UdhaarAnimatedSwitch(
                                checked = nprEnabled,
                                onCheckedChange = { checked ->
                                    if (!checked && !inrEnabled) {
                                        inrEnabled = true
                                        defaultCurrency = CurrencyType.INR
                                    }
                                    nprEnabled = checked
                                    if (!nprEnabled && defaultCurrency == CurrencyType.NPR) {
                                        defaultCurrency = CurrencyType.INR
                                    }
                                    persistImmediately()
                                },
                                modifier = Modifier.testTag("settings_enable_npr_switch")
                            )
                        }

                        if (inrEnabled && nprEnabled) {
                            Text(
                                text = stringResource(R.string.ui_str_default_startup_currency_47f398),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CurrencyType.entries.forEach { curr ->
                                    MonochromeTogglePill(
                                        text = curr.displayName,
                                        selected = defaultCurrency == curr,
                                        onClick = {
                                            defaultCurrency = curr
                                            persistImmediately()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

                        // Font Scale
                        Text(
                            text = stringResource(R.string.ui_str_text_ui_size_mobile_fit_c8a35b),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AppFontScale.entries.forEach { scaleOption ->
                                val isSelected = fontScale == scaleOption
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .background(
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            fontScale = scaleOption
                                            persistImmediately()
                                        }
                                        .padding(vertical = 8.dp, horizontal = 4.dp)
                                        .testTag("font_scale_${scaleOption.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = scaleOption.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

                        // App Theme Selector
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.ui_str_color_theme_5e8956),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppThemeMode.entries.forEach { mode ->
                                val isSelected = themeMode == mode
                                Box(
                                    modifier = Modifier
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .background(
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            themeMode = mode
                                            persistImmediately()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .testTag("theme_option_${mode.name.lowercase()}")
                                ) {
                                    Column {
                                        Text(
                                            text = mode.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = mode.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

                        // Phone System Settings Shortcuts
                        Text(
                            text = stringResource(R.string.ui_str_phone_system_settings_integration_405856),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                                    }.onFailure {
                                        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("open_phone_display_settings_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Text(
                                    text = stringResource(R.string.ui_str_display_font_311f8f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                                    }.onFailure {
                                        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("open_phone_security_settings_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Text(
                                    text = stringResource(R.string.ui_str_phone_pin_bio_4ca34f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                }.onFailure {
                                    runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("open_app_system_settings_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = stringResource(R.string.ui_str_app_permissions_notification_settin_8e5f3d),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // =========================================================================
                // 4. REMINDERS AND QUIET HOURS
                // =========================================================================
                item(key = "section_reminders_and_quiet_hours") {
                    SettingsSectionCard(
                        sectionNumber = "4",
                        title = stringResource(R.string.quiet_hours_settings_title),
                        subtitle = stringResource(R.string.quiet_hours_settings_subtitle),
                        testTag = "section_reminders_and_quiet_hours"
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.quiet_hours_settings_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.quiet_hours_settings_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            UdhaarAnimatedSwitch(
                                checked = quietHoursEnabled,
                                onCheckedChange = { checked ->
                                    quietHoursEnabled = checked
                                    persistReminderSettings()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("quiet_hours_toggle_switch")
                            )
                        }

                        if (quietHoursEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = quietHoursStartHour.toString(),
                                    onValueChange = { str ->
                                        str.filter { it.isDigit() }.toIntOrNull()?.let {
                                            quietHoursStartHour = it.coerceIn(0, 23)
                                            persistReminderSettings()
                                        }
                                    },
                                    label = { Text(stringResource(R.string.quiet_hours_start_label)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("quiet_hours_start_input")
                                )
                                OutlinedTextField(
                                    value = quietHoursEndHour.toString(),
                                    onValueChange = { str ->
                                        str.filter { it.isDigit() }.toIntOrNull()?.let {
                                            quietHoursEndHour = it.coerceIn(0, 23)
                                            persistReminderSettings()
                                        }
                                    },
                                    label = { Text(stringResource(R.string.quiet_hours_end_label)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("quiet_hours_end_input")
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

                        Text(
                            text = stringResource(R.string.escalation_ranges_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.escalation_ranges_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = reminderL1Days.toString(),
                            onValueChange = { str ->
                                str.filter { it.isDigit() }.toIntOrNull()?.let {
                                    reminderL1Days = it.coerceIn(1, 30)
                                    persistReminderSettings()
                                }
                            },
                            label = { Text(stringResource(R.string.escalation_l1_days)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("escalation_l1_input")
                        )

                        OutlinedTextField(
                            value = reminderL2Days.toString(),
                            onValueChange = { str ->
                                str.filter { it.isDigit() }.toIntOrNull()?.let {
                                    reminderL2Days = it.coerceIn(1, 60)
                                    persistReminderSettings()
                                }
                            },
                            label = { Text(stringResource(R.string.escalation_l2_days)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("escalation_l2_input")
                        )

                        OutlinedTextField(
                            value = reminderL3Days.toString(),
                            onValueChange = { str ->
                                str.filter { it.isDigit() }.toIntOrNull()?.let {
                                    reminderL3Days = it.coerceIn(1, 180)
                                    persistReminderSettings()
                                }
                            },
                            label = { Text(stringResource(R.string.escalation_l3_days)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("escalation_l3_input")
                        )
                    }
                }

                // =========================================================================
                // =========================================================================
                // 5. BACKUP AND SYNC
                // =========================================================================
                item(key = "section_backup_and_sync") {
                    SettingsSectionCard(
                        sectionNumber = "5",
                        title = "BACKUP AND SYNC",
                        subtitle = "Google Drive AppData cloud backup, encrypted restore & offline JSON export",
                        testTag = "section_backup_and_sync"
                    ) {
                        // Operation In Progress Indicator
                        if (isDriveOperationInProgress) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                                    .testTag("drive_operation_progress_card"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = driveOperationStatusMessage ?: stringResource(R.string.ui_str_google_drive_operation_in_progress_97811d),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Reconnect Google Drive Prompt Banner (when authorize() returns hasResolution or 401 retry fails)
                        val showReconnectPrompt = hasPendingDriveResolution ||
                            currentSettings.driveNeedsReconnect ||
                            currentSettings.driveLastErrorCode == com.jyotirmoyb.udhaar.backup.DriveBackupErrorCode.AUTH_EXPIRED
                        if (showReconnectPrompt) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                                    .testTag("drive_reconnect_prompt_card"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.drive_reconnect_google_drive_prompt_title),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.drive_reconnect_google_drive_prompt_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Button(
                                    onClick = onReconnectGoogleDrive,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp)
                                        .testTag("drive_reconnect_prompt_button"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.drive_reconnect_google_drive_prompt_title),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }

                        // Persisted Last Error in Plain Words (from DriveBackupWorker or last backup attempt)
                        val persistedErrorCode = currentSettings.driveLastErrorCode
                        val plainPersistedError = remember(persistedErrorCode, currentSettings.driveLastErrorMessage) {
                            if (persistedErrorCode != com.jyotirmoyb.udhaar.backup.DriveBackupErrorCode.NONE) {
                                currentSettings.driveLastErrorMessage.ifBlank {
                                    com.jyotirmoyb.udhaar.backup.DriveBackupWorker.plainErrorDescription(persistedErrorCode)
                                }
                            } else {
                                ""
                            }
                        }
                        val effectiveErrorText = driveOperationError ?: plainPersistedError.takeIf { it.isNotBlank() }

                        if (effectiveErrorText != null) {
                            val errorCodeTag = if (persistedErrorCode != com.jyotirmoyb.udhaar.backup.DriveBackupErrorCode.NONE) {
                                persistedErrorCode.name
                            } else {
                                "DRIVE_ERROR"
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                                    .testTag("drive_error_banner"),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.ui_str_google_drive_notice_fb05e0),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.drive_last_error_label_fmt, errorCodeTag, effectiveErrorText),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("drive_last_error_plain_text")
                                )
                                if (driveOperationError != null) {
                                    OutlinedButton(
                                        onClick = onClearDriveOperationError,
                                        modifier = Modifier
                                            .align(Alignment.End)
                                            .height(34.dp)
                                            .testTag("dismiss_drive_error_button"),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                    ) {
                                        Text(stringResource(R.string.ui_str_dismiss_d2fd9a), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        // Google Drive AppData Section
                        Text(
                            text = stringResource(R.string.ui_str_google_drive_app_data_cloud_f609d0),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ui_str_backups_are_client_side_encrypted_450b36),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (currentSettings.driveAccountEmail.isBlank()) {
                            Button(
                                onClick = onSignInWithGoogleForDrive,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("google_drive_sign_in_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.ui_str_sign_in_with_google_drive_f8dca0), style = MaterialTheme.typography.labelLarge)
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                                    .testTag("drive_account_connected_card"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.ui_str_connected_google_account_4aaee2),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = currentSettings.driveAccountEmail,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.testTag("drive_connected_email_text")
                                        )
                                        val lastBackupStr = if (currentSettings.driveLastBackupMillis > 0) {
                                            val dateStr = InterestCalculator.fmtDate(currentSettings.driveLastBackupMillis)
                                            val sizeKb = currentSettings.driveLastBackupSizeBytes / 1024
                                            "$dateStr (${sizeKb} KB)"
                                        } else {
                                            "No backup yet"
                                        }
                                        Text(
                                            text = stringResource(R.string.ui_fmt_last_backup_a7f073, lastBackupStr),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.testTag("drive_last_backup_text")
                                        )
                                    }

                                    IconButton(
                                        onClick = onRefreshDriveBackups,
                                        modifier = Modifier.testTag("refresh_drive_backups_button")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Backups", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            isLocalUdhaarExportMode = false
                                            backupPassphrase = ""
                                            confirmBackupPassphrase = ""
                                            backupPassphraseError = null
                                            showBackupDialog = true
                                        },
                                        enabled = !isDriveOperationInProgress,
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .height(44.dp)
                                            .testTag("drive_backup_now_button"),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        if (isDriveOperationInProgress) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.drive_backing_up_progress), style = MaterialTheme.typography.labelMedium)
                                        } else {
                                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.ui_str_back_up_now_e11395), style = MaterialTheme.typography.labelMedium)
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = onReconnectGoogleDrive,
                                        enabled = !isDriveOperationInProgress,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("drive_reconnect_button"),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.drive_reconnect_button), style = MaterialTheme.typography.labelMedium)
                                    }
                                }

                                OutlinedButton(
                                    onClick = onSignOutFromDrive,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .testTag("drive_sign_out_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Text(stringResource(R.string.ui_str_sign_out_c790dd), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        // App Signing Credentials (Package name, SHA-1, SHA-256 with Copy buttons)
                        val appPkg = appSigningInfo?.packageName ?: BuildConfig.APPLICATION_ID
                        val sha1Val = appSigningInfo?.sha1Fingerprint ?: ""
                        val sha256Val = appSigningInfo?.sha256Fingerprint ?: ""

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                                .testTag("app_signing_credentials_card"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.drive_signing_credentials_title),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = stringResource(R.string.drive_signing_credentials_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            SigningCredentialRow(
                                label = stringResource(R.string.drive_package_name_label),
                                value = appPkg,
                                testTag = "package_name_row",
                                copyTag = "copy_package_name_button",
                                onCopy = {
                                    clipboardManager.setText(AnnotatedString(appPkg))
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.drive_copied_toast_fmt, context.getString(R.string.drive_package_name_label)),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )

                            if (sha1Val.isNotBlank()) {
                                SigningCredentialRow(
                                    label = stringResource(R.string.drive_sha1_label),
                                    value = sha1Val,
                                    testTag = "sha1_credential_row",
                                    copyTag = "copy_sha1_button",
                                    onCopy = {
                                        clipboardManager.setText(AnnotatedString(sha1Val))
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.drive_copied_toast_fmt, "SHA-1"),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }

                            if (sha256Val.isNotBlank()) {
                                SigningCredentialRow(
                                    label = stringResource(R.string.drive_sha256_label),
                                    value = sha256Val,
                                    testTag = "sha256_credential_row",
                                    copyTag = "copy_sha256_button",
                                    onCopy = {
                                        clipboardManager.setText(AnnotatedString(sha256Val))
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.drive_copied_toast_fmt, "SHA-256"),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            }
                        }

                        // "Run connection test" Button & Diagnostic Result Card
                        OutlinedButton(
                            onClick = onRunDriveConnectionTest,
                            enabled = !isDriveOperationInProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("drive_connection_test_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.drive_run_connection_test_button), style = MaterialTheme.typography.labelMedium)
                        }

                        if (driveConnectionTestReport != null) {
                            val testBorderColor = if (driveConnectionTestReport.passed) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, testBorderColor, RoundedCornerShape(12.dp))
                                    .background(testBorderColor.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                                    .testTag("drive_connection_test_result_card"),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (driveConnectionTestReport.passed) Icons.Default.Check else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = testBorderColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (driveConnectionTestReport.passed) {
                                            stringResource(R.string.drive_connection_test_passed_title)
                                        } else {
                                            stringResource(R.string.drive_connection_test_failed_title)
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = testBorderColor
                                    )
                                }
                                if (!driveConnectionTestReport.failedStep.isNullOrBlank()) {
                                    Text(
                                        text = stringResource(R.string.drive_connection_test_step_fmt, driveConnectionTestReport.failedStep),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                val plainFix = driveConnectionTestReport.plainLanguageFix
                                if (!plainFix.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, testBorderColor.copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth().testTag("drive_plain_fix_box")
                                    ) {
                                        Text(
                                            text = plainFix,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = driveConnectionTestReport.errorMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                val techDetails = driveConnectionTestReport.technicalDetails
                                    ?: listOfNotNull(
                                        driveConnectionTestReport.errorCode.takeIf { it.isNotBlank() }?.let { "Code: $it" },
                                        driveConnectionTestReport.errorMessage.takeIf { it.isNotBlank() }
                                    ).joinToString("\n")

                                if (techDetails.isNotBlank()) {
                                    TextButton(
                                        onClick = { showTechnicalDetails = !showTechnicalDetails },
                                        modifier = Modifier.testTag("toggle_technical_details_button")
                                    ) {
                                        Text(
                                            text = if (showTechnicalDetails) {
                                                stringResource(R.string.drive_hide_technical_details)
                                            } else {
                                                stringResource(R.string.drive_show_technical_details)
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (showTechnicalDetails) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.fillMaxWidth().testTag("drive_technical_details_panel")
                                        ) {
                                            Text(
                                                text = techDetails,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = onClearDriveConnectionTestReport,
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .height(34.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, testBorderColor)
                                ) {
                                    Text(stringResource(R.string.ui_str_dismiss_d2fd9a), style = MaterialTheme.typography.labelSmall, color = testBorderColor)
                                }
                            }
                        }

                        // Help text for common Google Drive setup causes
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                                .testTag("drive_common_causes_help_card"),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.drive_common_causes_title),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.drive_common_causes_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Privacy Policy & Data Safety link
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.drive_privacy_policy_button),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.drive_privacy_policy_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = {
                                        uriHandler.openUri("https://udhaar.jyotirmoyb.com/privacy.html")
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .testTag("drive_privacy_policy_button"),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.drive_privacy_policy_button),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }

                        // "Delete my Drive backups" button
                        if (currentSettings.driveAccountEmail.isNotBlank() || driveRemoteBackups.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showDeleteAllBackupsDialog = true },
                                enabled = !isDriveOperationInProgress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .testTag("drive_delete_all_backups_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.drive_delete_backups_button),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        if (currentSettings.driveAccountEmail.isNotBlank()) {

                            // Automatic Backup with WorkManager
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                                    .testTag("drive_auto_backup_config_card"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.ui_str_automatic_backup_workmanager_961df8),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.ui_str_udhaar_schedules_periodic_backgroun_accdee),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    com.jyotirmoyb.udhaar.data.AutoBackupFrequency.entries.forEach { freq ->
                                        val isSelected = currentSettings.driveAutoBackupFrequency == freq
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .border(
                                                    width = if (isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .background(
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    onUpdateAutoBackupSettings(
                                                        freq,
                                                        currentSettings.driveAutoBackupWifiOnly,
                                                        null
                                                    )
                                                }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = freq.label.uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.ui_str_wi_fi_only_6ce953),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.ui_str_only_upload_auto_backups_over_1776af),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    UdhaarAnimatedSwitch(
                                        checked = currentSettings.driveAutoBackupWifiOnly,
                                        onCheckedChange = { wifiOnly ->
                                            onUpdateAutoBackupSettings(
                                                currentSettings.driveAutoBackupFrequency,
                                                wifiOnly,
                                                null
                                            )
                                        },
                                        modifier = Modifier.testTag("drive_auto_backup_wifi_switch")
                                    )
                                }

                                if (currentSettings.driveAutoBackupFrequency != com.jyotirmoyb.udhaar.data.AutoBackupFrequency.DISABLED && !currentSettings.driveAutoBackupPassphraseSaved) {
                                    Text(
                                        text = stringResource(R.string.ui_str_auto_backup_is_scheduled_but_40f61e),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            // List of Earlier Backups in Drive AppData
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                                    .testTag("drive_earlier_backups_card"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.ui_fmt_earlier_cloud_backups_02efea, driveRemoteBackups.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (driveRemoteBackups.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.ui_str_no_backups_found_in_google_dbb9f5),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    driveRemoteBackups.forEach { backup ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = backup.fileName,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val sizeKb = backup.sizeBytes / 1024
                                                Text(
                                                    text = stringResource(R.string.ui_fmt_kb_bb7db8, sizeKb),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            val dateStr = if (backup.createdTimeMillis > 0) {
                                                InterestCalculator.fmtDate(backup.createdTimeMillis)
                                            } else {
                                                "Unknown date"
                                            }
                                            Text(
                                                text = stringResource(R.string.ui_fmt_created_aes_256_gcm_encrypted_b1c8fd, dateStr),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        selectedRestoreBackup = backup
                                                        restorePassphrase = ""
                                                        showRestoreDialog = true
                                                    },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(38.dp)
                                                        .testTag("restore_backup_${backup.fileId}_button"),
                                                    shape = RoundedCornerShape(8.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                ) {
                                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(stringResource(R.string.ui_str_restore_6a8135), style = MaterialTheme.typography.labelSmall)
                                                }

                                                OutlinedButton(
                                                    onClick = { onDeleteDriveBackup(backup) },
                                                    modifier = Modifier
                                                        .weight(0.8f)
                                                        .height(38.dp)
                                                        .testTag("delete_backup_${backup.fileId}_button"),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                                ) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(stringResource(R.string.ui_str_delete_d6f563), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

                        // Local Offline JSON Backup & Restore
                        Text(
                            text = stringResource(R.string.ui_str_local_offline_json_backup_restore_01188e),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ui_str_export_your_entire_ledger_all_3a1431),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onExportFullLedgerSaf,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("export_ledger_json_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.ui_str_save_backup_9dd257), style = MaterialTheme.typography.labelMedium)
                            }

                            OutlinedButton(
                                onClick = onShareFullLedgerFile,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("share_ledger_json_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.ui_str_share_file_39064b), style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                isLocalUdhaarExportMode = true
                                backupPassphrase = ""
                                confirmBackupPassphrase = ""
                                backupPassphraseError = null
                                showBackupDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("export_local_udhaar_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.export_local_udhaar_backup), style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = onImportLedgerSaf,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("import_ledger_json_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.ui_str_import_restore_json_backup_61b7ef), style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = onOpenContactPicker,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_import_contacts_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.ui_str_import_from_contacts_8cdd06), style = MaterialTheme.typography.labelMedium)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

                        // Phase 3: Two-Party Cloud Verification, Privacy Consent & Delete My Cloud Data
                        Text(
                            text = stringResource(R.string.ui_str_two_party_cloud_verification_privac_8c2a13),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.ui_str_udhaar_works_100_offline_by_5362e5),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.ui_str_explicit_cloud_verification_consent_255a87),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.ui_str_allow_shared_records_e2ee_notes_2689cf),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            UdhaarAnimatedSwitch(
                                checked = cloudVerificationConsent,
                                onCheckedChange = { granted ->
                                    cloudVerificationConsent = granted
                                    persistImmediately()
                                },
                                modifier = Modifier.testTag("cloud_verification_consent_switch")
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                persistImmediately()
                                onSignInWithGoogleForVerification()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("google_sign_in_verification_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            val cloudAccountLabel = currentSettings.cloudUserEmail.ifBlank { stringResource(R.string.ui_verified_account_caps) }
                            Text(
                                text = if (currentSettings.cloudUserId.isNotBlank()) {
                                    stringResource(R.string.ui_fmt_signed_in_06c074, cloudAccountLabel)
                                } else {
                                    stringResource(R.string.ui_str_sign_in_with_google_for_7f6a17)
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        if (currentSettings.cloudUserId.isNotBlank() ||
                            currentSettings.cloudBackupState != com.jyotirmoyb.udhaar.data.CloudBackupState.IDLE ||
                            currentSettings.cloudLastBackupTimeMillis > 0L
                        ) {
                            val backupState = currentSettings.cloudBackupState
                            val lastBackupMillis = currentSettings.cloudLastBackupTimeMillis
                            val isJustNow = lastBackupMillis > 0L &&
                                kotlin.math.abs(System.currentTimeMillis() - lastBackupMillis) < 90_000L
                            val formattedTimestamp = remember(lastBackupMillis) {
                                if (lastBackupMillis > 0L) {
                                    java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
                                        .format(java.util.Date(lastBackupMillis))
                                } else {
                                    ""
                                }
                            }
                            val statusText = when {
                                backupState == com.jyotirmoyb.udhaar.data.CloudBackupState.BACKING_UP ->
                                    stringResource(R.string.cloud_backup_status_backing_up)
                                backupState == com.jyotirmoyb.udhaar.data.CloudBackupState.FAILED ->
                                    stringResource(R.string.cloud_backup_status_failed_retry)
                                lastBackupMillis > 0L && isJustNow ->
                                    stringResource(R.string.cloud_backup_status_just_now)
                                lastBackupMillis > 0L ->
                                    stringResource(
                                        R.string.cloud_backup_status_timestamp,
                                        formattedTimestamp
                                    )
                                else ->
                                    stringResource(R.string.cloud_backup_status_backing_up)
                            }
                            val statusColor = when (backupState) {
                                com.jyotirmoyb.udhaar.data.CloudBackupState.FAILED -> MaterialTheme.colorScheme.error
                                com.jyotirmoyb.udhaar.data.CloudBackupState.BACKING_UP -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.primary
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = statusColor.copy(alpha = 0.08f),
                                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("cloud_backup_status_row")
                                    .then(
                                        if (backupState != com.jyotirmoyb.udhaar.data.CloudBackupState.BACKING_UP) {
                                            Modifier.clickable { onRetryCloudBackup() }
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = when (backupState) {
                                            com.jyotirmoyb.udhaar.data.CloudBackupState.FAILED -> Icons.Default.Warning
                                            com.jyotirmoyb.udhaar.data.CloudBackupState.BACKING_UP -> Icons.Default.Sync
                                            else -> Icons.Default.Check
                                        },
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor,
                                            modifier = Modifier.testTag("cloud_backup_status_text")
                                        )
                                        Text(
                                            text = when {
                                                backupState == com.jyotirmoyb.udhaar.data.CloudBackupState.FAILED &&
                                                    currentSettings.cloudBackupError.isNotBlank() ->
                                                    currentSettings.cloudBackupError
                                                backupState == com.jyotirmoyb.udhaar.data.CloudBackupState.BACKING_UP ->
                                                    stringResource(R.string.cloud_backup_subtitle_syncing)
                                                else ->
                                                    stringResource(R.string.cloud_backup_subtitle_synced)
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Open / Verify a received link directly
                        OutlinedTextField(
                            value = incomingLinkInput,
                            onValueChange = { incomingLinkInput = it },
                            label = { Text(stringResource(R.string.ui_str_open_received_verification_or_payme_e37547)) },
                            placeholder = { Text(stringResource(R.string.ui_str_paste_https_jyotirmoyb_com_u_6b00ac)) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_open_verification_link_input")
                        )

                        if (incomingLinkInput.isNotBlank()) {
                            Button(
                                onClick = {
                                    onOpenReceivedVerificationLink(incomingLinkInput.trim())
                                    incomingLinkInput = ""
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("settings_open_verification_link_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.ui_str_inspect_shared_link_022c24), style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                if (confirmCloudDelete) {
                                    confirmCloudDelete = false
                                    cloudVerificationConsent = false
                                    onDeleteMyCloudData()
                                } else {
                                    confirmCloudDelete = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("delete_my_cloud_data_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (confirmCloudDelete) stringResource(R.string.ui_str_tap_again_to_permanently_delete_1bbe18) else stringResource(R.string.ui_str_delete_my_cloud_data_c570cb),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                // =========================================================================
                // 5. ABOUT
                // =========================================================================
                item(key = "section_about") {
                    SettingsSectionCard(
                        sectionNumber = "5",
                        title = "ABOUT",
                        subtitle = "Share Udhaar app, version info & direct developer support",
                        testTag = "section_about"
                    ) {
                        // Share & Give Access Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                                .testTag("share_give_access_card"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.ui_str_share_udhaar_give_access_0b30b9),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.ui_str_share_the_udhaar_app_via_d0183f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = onOpenShareAppModal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("open_share_app_modal_from_settings"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.ui_str_share_udhaar_app_qr_invite_9e0ec0), style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        // About & Developer Support Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                                .testTag("about_and_support_card"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SupportAgent,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = stringResource(
                                            R.string.settings_about_version_format,
                                            com.jyotirmoyb.udhaar.BuildConfig.VERSION_NAME,
                                            com.jyotirmoyb.udhaar.BuildConfig.VERSION_CODE
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.testTag("settings_about_version_text")
                                    )
                                    Text(
                                        text = stringResource(R.string.ui_str_developer_admin_jyotirmoy_bhowmik_436c7f),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.ui_str_support_email_jyotirmoybhowmik_gmai_803452),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            OutlinedButton(
                                onClick = {
                                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:admin@jyotirmoyb.com")
                                        putExtra(Intent.EXTRA_SUBJECT, "Udhaar App Support / Feedback")
                                    }
                                    runCatching {
                                        context.startActivity(emailIntent)
                                    }.onFailure {
                                        Toast.makeText(context, "Admin Email: admin@jyotirmoyb.com", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("contact_admin_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.ui_str_contact_admin_email_support_ff5066), style = MaterialTheme.typography.labelMedium)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse("https://udhaar.jyotirmoyb.com/privacy.html"))
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("open_privacy_policy_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val privacyPolicyLabel = remember { "Privacy Policy" }
                                    Text(text = privacyPolicyLabel, style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.jyotirmoyb.udhaar"))
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("check_play_update_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    val checkUpdateLabel = remember { "Check Update" }
                                    Text(text = checkUpdateLabel, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.ui_str_encrypt_back_up_to_google_a82102),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ui_str_set_a_passphrase_to_encrypt_4bdc01),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = backupPassphrase,
                        onValueChange = {
                            backupPassphrase = it
                            backupPassphraseError = null
                        },
                        label = { Text(stringResource(R.string.ui_str_backup_passphrase_2a9696)) },
                        placeholder = { Text(stringResource(R.string.ui_str_enter_strong_passphrase_2db9a2)) },
                        singleLine = true,
                        visualTransformation = if (backupPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { backupPassphraseVisible = !backupPassphraseVisible }) {
                                Icon(
                                    imageVector = if (backupPassphraseVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup_passphrase_input")
                    )

                    OutlinedTextField(
                        value = confirmBackupPassphrase,
                        onValueChange = {
                            confirmBackupPassphrase = it
                            backupPassphraseError = null
                        },
                        label = { Text(stringResource(R.string.ui_str_confirm_passphrase_81f868)) },
                        placeholder = { Text(stringResource(R.string.ui_str_re_enter_passphrase_87fa77)) },
                        singleLine = true,
                        visualTransformation = if (backupPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup_confirm_passphrase_input")
                    )

                    val currentBackupPassError = backupPassphraseError
                    if (currentBackupPassError != null) {
                        Text(
                            text = currentBackupPassError,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = savePassphraseForAuto,
                            onCheckedChange = { savePassphraseForAuto = it },
                            modifier = Modifier.testTag("save_passphrase_for_auto_checkbox")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.ui_str_save_securely_in_phone_keystore_216a55),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pass = backupPassphrase.trim()
                        val confirm = confirmBackupPassphrase.trim()
                        if (pass.length < 4) {
                            backupPassphraseError = "Passphrase must be at least 4 characters."
                        } else if (pass != confirm) {
                            backupPassphraseError = "Passphrases do not match."
                        } else {
                            showBackupDialog = false
                            if (isLocalUdhaarExportMode) {
                                onExportLocalUdhaarSaf(pass)
                            } else {
                                onBackupNowToDrive(pass, savePassphraseForAuto)
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm_encrypt_and_backup_button")
                ) {
                    Text(
                        if (isLocalUdhaarExportMode) {
                            stringResource(R.string.export_local_udhaar_backup)
                        } else {
                            stringResource(R.string.ui_str_encrypt_upload_623890)
                        }
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showBackupDialog = false },
                    modifier = Modifier.testTag("cancel_backup_dialog_button")
                ) {
                    Text(stringResource(R.string.ui_str_cancel_1507c4))
                }
            }
        )
    }

    val currentRestoreBackup = selectedRestoreBackup
    if (showRestoreDialog && currentRestoreBackup != null) {
        val backup = currentRestoreBackup
        AlertDialog(
            onDismissRequest = {
                showRestoreDialog = false
                selectedRestoreBackup = null
            },
            title = {
                Text(
                    text = stringResource(R.string.ui_str_decrypt_preview_restore_e4b787),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ui_fmt_enter_the_passphrase_used_when_0b3c36, backup.fileName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = restorePassphrase,
                        onValueChange = { restorePassphrase = it },
                        label = { Text(stringResource(R.string.ui_str_backup_passphrase_2a9696)) },
                        placeholder = { Text(stringResource(R.string.ui_str_passphrase_89950c)) },
                        singleLine = true,
                        visualTransformation = if (restorePassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { restorePassphraseVisible = !restorePassphraseVisible }) {
                                Icon(
                                    imageVector = if (restorePassphraseVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_passphrase_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pass = restorePassphrase.trim()
                        if (pass.isNotEmpty()) {
                            showRestoreDialog = false
                            onRestoreDriveBackup(backup, pass)
                        }
                    },
                    modifier = Modifier.testTag("confirm_decrypt_and_preview_button")
                ) {
                    Text(stringResource(R.string.ui_str_decrypt_preview_10fcf4))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showRestoreDialog = false
                        selectedRestoreBackup = null
                    },
                    modifier = Modifier.testTag("cancel_restore_dialog_button")
                ) {
                    Text(stringResource(R.string.ui_str_cancel_1507c4))
                }
            }
        )
    }

    if (showDeleteAllBackupsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllBackupsDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.drive_delete_backups_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.drive_delete_backups_dialog_msg),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllBackupsDialog = false
                        onDeleteAllDriveBackups()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_all_drive_backups_button")
                ) {
                    Text(stringResource(R.string.drive_delete_backups_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteAllBackupsDialog = false },
                    modifier = Modifier.testTag("cancel_delete_all_drive_backups_button")
                ) {
                    Text(stringResource(R.string.ui_str_cancel_1507c4))
                }
            }
        )
    }
}

@Composable
private fun SigningCredentialRow(
    label: String,
    value: String,
    testTag: String,
    copyTag: String,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onCopy,
                modifier = Modifier
                    .size(36.dp)
                    .testTag(copyTag)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.drive_copy_button_content_desc, label),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
internal fun SettingsSectionCard(
    sectionNumber: String,
    title: String,
    subtitle: String,
    testTag: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = sectionNumber,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), thickness = 1.dp)
        content()
    }
}

@Composable
internal fun BreakdownLine(
    label: String,
    value: String,
    bold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End
        )
    }
}

private fun proportionalOffsetMapping(originalLen: Int, transformedLen: Int): OffsetMapping =
    object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            if (originalLen <= 0) return 0
            val clamped = offset.coerceIn(0, originalLen)
            return ((clamped.toLong() * transformedLen) / originalLen).toInt().coerceIn(0, transformedLen)
        }

        override fun transformedToOriginal(offset: Int): Int {
            if (transformedLen <= 0) return 0
            val clamped = offset.coerceIn(0, transformedLen)
            return ((clamped.toLong() * originalLen) / transformedLen).toInt().coerceIn(0, originalLen)
        }
    }

val PhoneMaskVisualTransformation = VisualTransformation { text ->
    val masked = InterestCalculator.maskPhoneNumber(text.text)
    TransformedText(
        text = AnnotatedString(masked),
        offsetMapping = proportionalOffsetMapping(text.text.length, masked.length)
    )
}

val UpiMaskVisualTransformation = VisualTransformation { text ->
    val masked = InterestCalculator.maskUpiId(text.text)
    TransformedText(
        text = AnnotatedString(masked),
        offsetMapping = proportionalOffsetMapping(text.text.length, masked.length)
    )
}

@Composable
fun SqlCipherBlockingErrorScreen(
    errorMessage: String,
    onExportBackup: () -> Unit,
    onTryAgain: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("sqlcipher_blocking_error_screen"),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = stringResource(R.string.sqlcipher_error_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.sqlcipher_error_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (errorMessage.isNotBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportBackup,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .pressScaleFeedback()
                            .testTag("sqlcipher_error_export_backup_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.sqlcipher_error_export_backup),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Button(
                        onClick = onTryAgain,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .pressScaleFeedback()
                            .testTag("sqlcipher_error_try_again_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.sqlcipher_error_try_again),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

