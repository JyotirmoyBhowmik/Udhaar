package com.jyotirmoyb.udhaar.ui.components

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }
    var pendingProofAttachment by remember { mutableStateOf<Attachment?>(null) }
    var isAttachingToPayment by remember { mutableStateOf(false) }
    var showProofPickerMenu by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraTempUri != null) {
            val processed = AttachmentManager.processCapturedImage(
                context = context,
                sourceUri = cameraTempUri!!,
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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
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
            // Sticky Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tx.partyName.uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${tx.transactionType.badgeLabel} • ${tx.currency.code} • ${tx.paymentMode.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_detail_modal_button")
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
                            text = if (tx.isSettled) "SETTLED RECORD" else "NET REMAINING DUE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = InterestCalculator.formatCurrency(summary.netBalanceDue, tx.currency),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                    BreakdownLine("Principal Amount", InterestCalculator.formatCurrency(tx.principalAmount, tx.currency))
                    BreakdownLine(
                        label = if (tx.isInterestEnabled) {
                            "${tx.interestType.label} (${tx.interestRate}${tx.interestRatePeriod.shortSuffix} • ${summary.elapsedDays}d)"
                        } else {
                            "Interest"
                        },
                        value = InterestCalculator.formatCurrency(summary.accruedInterest, tx.currency)
                    )
                    BreakdownLine("Gross Total", InterestCalculator.formatCurrency(summary.grossTotal, tx.currency))
                    BreakdownLine("Total Paid / Received", "- ${InterestCalculator.formatCurrency(tx.amountPaid, tx.currency)}")
                    BreakdownLine("Start Date", InterestCalculator.formatDate(tx.startDateMillis))
                    tx.expiryDateMillis?.let {
                        BreakdownLine(
                            label = if (summary.isOverdue) "Due Date (OVERDUE)" else "Due Date",
                            value = InterestCalculator.formatDate(it),
                            bold = summary.isOverdue
                        )
                    }
                    if (tx.notes.isNotBlank()) {
                        BreakdownLine("Notes", tx.notes)
                    }
                }

                // Phase 3: Two-Party Verification & Content Hash Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
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
                            text = "TWO-PARTY VERIFICATION",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box(
                            modifier = Modifier
                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = tx.verificationStatus.shortBadge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (tx.contentHash.isNotBlank()) {
                        Text(
                            text = "SHA-256: ${tx.contentHash.take(24)}...",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (tx.disputeReason.isNotBlank() && tx.verificationStatus == VerificationStatus.DISPUTED) {
                        Text(
                            text = "Dispute Note: ${tx.disputeReason}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (tx.verificationStatus == VerificationStatus.EDITED_AFTER_CONFIRM) {
                        Text(
                            text = "Terms were edited after confirmation. Ask ${tx.partyName} to re-confirm or confirm the updated hash below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    OutlinedButton(
                        onClick = onRequestVerification,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("request_verification_button"),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (tx.verificationStatus == VerificationStatus.LOCAL_ONLY) {
                                "REQUEST VERIFICATION (LINK & QR)"
                            } else {
                                "SHARE VERIFICATION LINK & QR"
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
                                label = { Text("Reason for Dispute") },
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
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("CANCEL", style = MaterialTheme.typography.labelSmall)
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
                                        .testTag("detail_submit_dispute_button"),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    Text("SUBMIT DISPUTE", style = MaterialTheme.typography.labelSmall)
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
                                        .testTag("detail_confirm_verification_button"),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("CONFIRM ENTRY", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = { showDisputeInput = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("detail_dispute_verification_button"),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                ) {
                                    Text(
                                        text = "DISPUTE",
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
                                text = "ATTACHED DOCUMENTS (${attachments.size})",
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
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CAMERA", style = MaterialTheme.typography.labelSmall)
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
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("GALLERY", style = MaterialTheme.typography.labelSmall)
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
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("DOC/PDF", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (attachments.isEmpty()) {
                        Text(
                            text = "No documents attached. Attach promissory notes, bills, or photo receipts.",
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
                                    modifier = Modifier
                                        .size(width = 110.dp, height = 110.dp)
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
                            text = "RECORD PARTIAL / INSTALLMENT PAYMENT",
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
                                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
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
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .background(
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(4.dp)
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
                                label = { Text("Amount (${tx.currency.symbol})") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("partial_payment_amount_input")
                            )
                            OutlinedTextField(
                                value = partialNoteText,
                                onValueChange = { partialNoteText = it },
                                label = { Text("Note (Optional)") },
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
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PROOF CAM", style = MaterialTheme.typography.labelSmall)
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
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PROOF PIC", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
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
                                        text = "Proof: ${pendingProofAttachment!!.fileName} (${AttachmentManager.formatFileSize(pendingProofAttachment!!.sizeBytes)})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        AttachmentManager.deleteAttachmentFile(context, pendingProofAttachment!!.localPath)
                                        pendingProofAttachment = null
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove proof", modifier = Modifier.size(14.dp))
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
                                text = "Reported by ${tx.partyName} (Requires my confirmation)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = reportedByCounterparty,
                                onCheckedChange = { reportedByCounterparty = it },
                                modifier = Modifier.testTag("reported_by_counterparty_switch")
                            )
                        }
                        if (errorText != null) {
                            Text(
                                text = errorText!!,
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
                                .testTag("submit_partial_payment_button"),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ADD INSTALLMENT (${partialPaymentMode.label.uppercase()})", style = MaterialTheme.typography.labelLarge)
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
                            text = "PAYMENT HISTORY (${partialPayments.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        partialPayments.forEach { payment ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
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
                                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(3.dp))
                                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = payment.paymentMode.label.uppercase(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = if (payment.isAcknowledged) "✓ Acknowledged" else "⏳ Unconfirmed",
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
                                            append(InterestCalculator.formatDate(payment.paidAtMillis))
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
                                                        shape = RoundedCornerShape(4.dp),
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
                                                                text = "Proof: ${proof.fileName}",
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
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "$displayActor marked $formattedAmt received — Confirm?",
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

                // Phase 3: Immutable Audit Trail Timeline
                if (auditTrail.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                            .testTag("detail_audit_trail_section"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "IMMUTABLE AUDIT TRAIL (${auditTrail.size})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        auditTrail.forEach { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${entry.eventType.label} • ${entry.actorName}",
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
                                            text = "Hash: ${entry.contentHashSnapshot.take(16)}...",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = InterestCalculator.formatDate(entry.eventTimeMillis),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                        .testTag("detail_open_chat_button"),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (unreadChatCount > 0) {
                            "OPEN TRANSACTION CHAT ($unreadChatCount NEW)"
                        } else {
                            "OPEN TRANSACTION CHAT & EXTENSION"
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
                                .testTag("detail_whatsapp_button"),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WHATSAPP", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = onShareReceipt,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("detail_thermal_receipt_button"),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("RECEIPT SLIP", style = MaterialTheme.typography.labelMedium)
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
                                        .testTag("detail_upi_qr_button"),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("COLLECT VIA MY UPI QR", style = MaterialTheme.typography.labelMedium)
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
                                        .testTag("detail_pay_party_upi_button"),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (partyUpi.isNotBlank()) "PAY ${tx.partyName.uppercase()} VIA UPI" else "OPEN UPI QR TOOL",
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
                        .testTag("toggle_settled_button"),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (tx.isSettled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                        contentColor = if (tx.isSettled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary
                    ),
                    border = if (tx.isSettled) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (tx.isSettled) "RE-OPEN LEDGER ENTRY" else "MARK AS FULLY SETTLED",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Edit & Delete Row (Last buttons — guaranteed reachable via ModalBottomSheet + 40dp bottom padding)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("edit_transaction_button"),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("EDIT", style = MaterialTheme.typography.labelMedium)
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
                            .testTag("delete_transaction_button"),
                        shape = RoundedCornerShape(6.dp),
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
                            text = if (confirmDelete) "TAP TO CONFIRM" else "DELETE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

/**
 * Full-screen attachment viewer with pinch-to-zoom, share, and delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentViewerModal(
    attachment: Attachment,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isImage = attachment.mimeType.startsWith("image/")

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Attachment?") },
            text = { Text("Permanently delete '${attachment.fileName}' from this device?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("DELETE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("CANCEL")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = attachment.fileName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${AttachmentManager.formatFileSize(attachment.sizeBytes)} • ${attachment.addedBy}",
                            style = MaterialTheme.typography.labelSmall,
                            color = BorderGrey
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_attachment_viewer_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PureWhite
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier.testTag("share_attachment_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = PureWhite
                        )
                    }
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("delete_attachment_viewer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = Color(0xFFFF5252)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PureBlack,
                    titleContentColor = PureWhite,
                    navigationIconContentColor = PureWhite,
                    actionIconContentColor = PureWhite
                )
            )
        },
        containerColor = PureBlack
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            if (isImage) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offset += pan
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = File(attachment.localPath),
                        contentDescription = attachment.fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .background(PureWhite, RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = PureBlack
                    )
                    Text(
                        text = attachment.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PureBlack,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "${attachment.mimeType} • ${AttachmentManager.formatFileSize(attachment.sizeBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryInkGrey
                    )
                    if (attachment.sha256.isNotBlank()) {
                        Text(
                            text = "SHA-256: ${attachment.sha256.take(24)}...",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = SecondaryInkGrey
                        )
                    }
                    Button(
                        onClick = {
                            AttachmentManager.openAttachmentWithIntent(context, attachment.localPath, attachment.mimeType)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("open_external_document_button"),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PureBlack,
                            contentColor = PureWhite
                        )
                    ) {
                        Text("OPEN IN DOCUMENT VIEWER")
                    }
                }
            }
        }
    }
}

/**
 * Thermal Receipt Statement Preview & PNG Share ModalBottomSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThermalReceiptModal(
    title: String,
    subtitle: String,
    receiptBitmap: Bitmap,
    fileNamePrefix: String,
    shareCaption: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
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
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_receipt_modal_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, PureBlack, RoundedCornerShape(6.dp))
                        .background(PureWhite, RoundedCornerShape(6.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = receiptBitmap.asImageBitmap(),
                        contentDescription = "Thermal Statement Receipt Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("thermal_receipt_image_preview"),
                        contentScale = ContentScale.FillWidth
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            ThermalReceiptGenerator.shareReceiptBitmapAsPng(
                                context = context,
                                bitmap = receiptBitmap,
                                fileNamePrefix = fileNamePrefix,
                                captionText = shareCaption
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_thermal_receipt_image_button"),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SHARE PNG", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = {
                            ThermalReceiptGenerator.shareReceiptBitmapAsPdf(
                                context = context,
                                bitmap = receiptBitmap,
                                fileNamePrefix = fileNamePrefix,
                                captionText = shareCaption
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_thermal_receipt_pdf_button"),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SHARE PDF", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpiQrCodeModal(
    qrState: QrModalState,
    userSettings: UserSettings,
    onSaveUpiSettings: (upiId: String, payeeName: String, defaultCurrency: CurrencyType) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isPayToParty = qrState.isPayToPartyMode && !qrState.customUpiId.isNullOrBlank()

    var upiIdInput by rememberSaveable(qrState) {
        mutableStateOf(
            if (isPayToParty) qrState.customUpiId.orEmpty() else userSettings.upiId
        )
    }
    var payeeNameInput by rememberSaveable(qrState) {
        mutableStateOf(
            if (isPayToParty) qrState.partyName.orEmpty() else userSettings.payeeName
        )
    }
    var amountText by rememberSaveable(qrState) {
        mutableStateOf(
            qrState.initialAmountInr?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else String.format(java.util.Locale.US, "%.2f", it)
            } ?: ""
        )
    }

    val parsedAmount = amountText.trim().toDoubleOrNull()
    val effectiveUpiId = upiIdInput.trim()
    val effectivePayeeName = payeeNameInput.trim().ifBlank { "Udhaar Payee" }

    val upiUri = remember(effectiveUpiId, effectivePayeeName, parsedAmount, qrState.partyName, isPayToParty) {
        if (effectiveUpiId.isBlank()) {
            ""
        } else {
            val note = if (isPayToParty) {
                "Udhaar repayment to ${qrState.partyName ?: effectivePayeeName}"
            } else {
                qrState.partyName?.let { "Udhaar settlement - $it" } ?: "Udhaar Payment"
            }
            UpiQrGenerator.buildUpiUri(
                upiId = effectiveUpiId,
                payeeName = effectivePayeeName,
                amountInr = parsedAmount,
                transactionNote = note
            )
        }
    }

    val qrBitmap = remember(upiUri) {
        if (upiUri.isBlank()) null else UpiQrGenerator.generateQrBitmap(upiUri, sizePx = 560)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
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
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isPayToParty) "PAY PARTY VIA UPI" else "RECEIVE VIA UPI QR",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isPayToParty) {
                            "SETTLE ${qrState.partyName?.uppercase() ?: "PARTY"} DIRECTLY"
                        } else {
                            qrState.partyName?.let { "FOR ${it.uppercase()} • INR (₹)" } ?: "GLOBAL MERCHANT / PERSONAL QR"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_qr_modal_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = upiIdInput,
                    onValueChange = { upiIdInput = it },
                    label = { Text(if (isPayToParty) "Party UPI ID (VPA) *" else "Your UPI ID (VPA) *") },
                    placeholder = { Text("e.g. yourname@okaxis") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("qr_upi_id_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = payeeNameInput,
                        onValueChange = { payeeNameInput = it },
                        label = { Text("Payee Name") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("qr_payee_name_input")
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = {
                            if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) {
                                amountText = it
                            }
                        },
                        label = { Text("Amount (₹)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("qr_amount_input")
                    )
                }

                if (!isPayToParty && (upiIdInput.trim() != userSettings.upiId || payeeNameInput.trim() != userSettings.payeeName)) {
                    OutlinedButton(
                        onClick = {
                            onSaveUpiSettings(
                                upiIdInput.trim(),
                                payeeNameInput.trim(),
                                userSettings.defaultCurrency
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("save_upi_from_qr_button"),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Text("SAVE AS DEFAULT UPI PROFILE", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(210.dp)
                            .border(2.dp, PureBlack, RoundedCornerShape(8.dp))
                            .background(PureWhite, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Generated UPI QR Code",
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("upi_qr_bitmap_image")
                        )
                    }
                    Text(
                        text = upiUri,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("UPI Link", upiUri))
                                Toast.makeText(context, "UPI Payment Link copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("copy_upi_uri_button"),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("COPY LINK", style = MaterialTheme.typography.labelMedium)
                        }

                        Button(
                            onClick = {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
                                    context.startActivity(intent)
                                }.onFailure {
                                    Toast.makeText(context, "No UPI app installed on this device.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp)
                                .testTag("launch_upi_app_button"),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("OPEN UPI APP", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Enter a valid UPI ID above (e.g. merchant@upi) to generate an offline scan-ready QR code.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * Share Udhaar App ModalBottomSheet — Lets users share Play Store link, scan QR, or invite via WhatsApp.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareUdhaarAppModal(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appShareUrl = "https://play.google.com/store/apps/details?id=${context.packageName}"
    val shareMessage = buildString {
        appendLine("📒 *Udhaar — Smart Interest & Khata Ledger (INR ₹ & NPR रू)*")
        appendLine("Track Given/Taken loans, automatic Simple & Compound interest, offline UPI QR codes, thermal receipts, and WhatsApp reminders.")
        appendLine()
        append("Download / Open Udhaar App: $appShareUrl")
    }

    val appQrBitmap = remember(appShareUrl) {
        UpiQrGenerator.generateQrBitmap(appShareUrl, sizePx = 520)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
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
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SHARE UDHAAR APP",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "INVITE FRIENDS, CUSTOMERS OR PARTNERS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_share_app_modal_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (appQrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .border(2.dp, PureBlack, RoundedCornerShape(8.dp))
                            .background(PureWhite, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = appQrBitmap.asImageBitmap(),
                            contentDescription = "Udhaar App Download QR Code",
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("share_app_qr_image")
                        )
                    }
                }

                Text(
                    text = "Scan this QR code with any phone camera to open Udhaar on Google Play, or share the invite link below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Udhaar — Interest & Khata Ledger App")
                            putExtra(Intent.EXTRA_TEXT, shareMessage)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Udhaar App via"))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("share_app_native_button"),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SHARE APP INVITE LINK", style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Udhaar App Link", shareMessage))
                        Toast.makeText(context, "App invite message copied!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("copy_app_link_button"),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("COPY INVITE MESSAGE", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppReminderModal(
    summary: CalculatedUdhaarSummary,
    userSettings: UserSettings,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tx = summary.transaction

    var hideExactAmount by rememberSaveable { mutableStateOf(false) }

    val generatedMessage = remember(summary, userSettings.upiId, hideExactAmount) {
        InterestCalculator.buildWhatsAppReminderMessage(
            summary = summary,
            hideExactAmount = hideExactAmount,
            upiId = userSettings.upiId,
            includeUpiId = true
        )
    }

    var editableMessage by rememberSaveable(generatedMessage) {
        mutableStateOf(generatedMessage)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
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
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "WHATSAPP REMINDER",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "TO: ${tx.partyName.uppercase()} ${if (tx.phoneNumber.isNotBlank()) "(${tx.phoneNumber})" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_whatsapp_modal_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Hide Exact Amount Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "HIDE EXACT AMOUNT IN MESSAGE",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Send polite reminder without numeric figures",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = hideExactAmount,
                        onCheckedChange = { hideExactAmount = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("hide_exact_amount_switch")
                    )
                }

                OutlinedTextField(
                    value = editableMessage,
                    onValueChange = { editableMessage = it },
                    label = { Text("Preview & Customize Message") },
                    minLines = 5,
                    maxLines = 9,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("whatsapp_message_input")
                )

                Button(
                    onClick = {
                        val sanitizedPhone = InterestCalculator.sanitizePhoneForWhatsApp(
                            tx.phoneNumber,
                            tx.currency
                        )
                        val encodedMessage = Uri.encode(editableMessage)
                        val waUrl = if (sanitizedPhone.isNotBlank()) {
                            "https://wa.me/$sanitizedPhone?text=$encodedMessage"
                        } else {
                            "https://wa.me/?text=$encodedMessage"
                        }
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
                            context.startActivity(intent)
                        }.onFailure {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, editableMessage)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Send Reminder"))
                        }
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("launch_whatsapp_send_button"),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SEND VIA WHATSAPP", style = MaterialTheme.typography.labelLarge)
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
    onSignInWithGoogleForDrive: () -> Unit = {},
    onSignOutFromDrive: () -> Unit = {},
    onBackupNowToDrive: (passphrase: String, savePassphraseForAuto: Boolean) -> Unit = { _, _ -> },
    onRestoreDriveBackup: (com.jyotirmoyb.udhaar.backup.DriveRemoteBackup, passphrase: String) -> Unit = { _, _ -> },
    onDeleteDriveBackup: (com.jyotirmoyb.udhaar.backup.DriveRemoteBackup) -> Unit = {},
    onUpdateAutoBackupSettings: (com.jyotirmoyb.udhaar.data.AutoBackupFrequency, Boolean, String?) -> Unit = { _, _, _ -> },
    onRefreshDriveBackups: () -> Unit = {},
    onClearDriveOperationError: () -> Unit = {},
    onDismiss: () -> Unit
) {
    // Block screenshots on the Settings screen where PIN is configured
    SecureScreenEffect(enabled = true)

    BackHandler {
        onDismiss()
    }

    val context = LocalContext.current
    var payeeName by rememberSaveable(currentSettings.payeeName) { mutableStateOf(currentSettings.payeeName) }
    var upiId by rememberSaveable(currentSettings.upiId) { mutableStateOf(currentSettings.upiId) }
    var phoneNumber by rememberSaveable(currentSettings.phoneNumber) { mutableStateOf(currentSettings.phoneNumber) }
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
    var backupPassphrase by rememberSaveable { mutableStateOf("") }
    var confirmBackupPassphrase by rememberSaveable { mutableStateOf("") }
    var savePassphraseForAuto by rememberSaveable { mutableStateOf(currentSettings.driveAutoBackupPassphraseSaved) }
    var backupPassphraseError by rememberSaveable { mutableStateOf<String?>(null) }
    var backupPassphraseVisible by rememberSaveable { mutableStateOf(false) }

    var showRestoreDialog by rememberSaveable { mutableStateOf(false) }
    var selectedRestoreBackup by remember { mutableStateOf<com.jyotirmoyb.udhaar.backup.DriveRemoteBackup?>(null) }
    var restorePassphrase by rememberSaveable { mutableStateOf("") }
    var restorePassphraseVisible by rememberSaveable { mutableStateOf(false) }

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

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
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
                                text = "SETTINGS & PREFERENCES",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "PROFILE • SECURITY • CURRENCY • BACKUP • ABOUT",
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
                    .navigationBarsPadding()
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
                            .testTag("cancel_settings_button"),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text("CANCEL", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = { validateAndSave(true) },
                        modifier = Modifier
                            .weight(1.6f)
                            .height(48.dp)
                            .testTag("save_settings_button"),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SAVE SETTINGS", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                            label = { Text("Your Business / Payee Name") },
                            placeholder = { Text("e.g. Sharma General Store") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_payee_name_input")
                        )

                        OutlinedTextField(
                            value = upiId,
                            onValueChange = { upiId = it },
                            label = { Text("Your Default UPI ID (for INR QR)") },
                            placeholder = { Text("e.g. merchant@okicici") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_upi_id_input")
                        )

                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("Your Phone Number (for Verification & Profile Share)") },
                            placeholder = { Text("e.g. +91 9876543210") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Text(
                                    text = if (profilePhotoUri.isNotBlank()) "CHANGE PHOTO ✓" else "OPTIONAL PHOTO",
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
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SHARE MY DETAILS", style = MaterialTheme.typography.labelMedium)
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
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("OPEN MY UPI QR CODE", style = MaterialTheme.typography.labelLarge)
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
                                        text = "BIOMETRIC & PIN APP LOCK",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Require Fingerprint, Phone PIN, or App PIN on launch",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
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
                                            return@Switch
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
                                        "Change 4–8 Digit Security PIN (Salted Hash Active)"
                                    } else {
                                        "Set 4–8 Digit Security PIN (Required for App Lock)"
                                    }
                                )
                            },
                            placeholder = {
                                Text(
                                    if (currentSettings.hasPinConfigured) "Leave blank to keep current PIN" else "Enter 4 to 8 digits"
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
                                if (pinValidationError != null) {
                                    Text(
                                        text = pinValidationError!!,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                } else {
                                    Text(
                                        text = if (currentSettings.hasPinConfigured) {
                                            "PIN is stored as a PBKDF2-HMAC-SHA256 salted hash in Android Keystore."
                                        } else {
                                            "No default PIN exists. You must set a 4–8 digit PIN to enable App Lock."
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
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("LOCK APP NOW", style = MaterialTheme.typography.labelMedium)
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
                            text = "ENABLED LEDGER CURRENCIES",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Enable Indian Rupee (INR ₹), Nepalese Rupee (NPR रू), or both. At least one currency stays active.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Indian Rupee — INR (₹)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Supports offline UPI QR & INR khata",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
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
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Nepalese Rupee — NPR (रू)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Enable NPR ledger tabs & entries",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
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
                                text = "DEFAULT STARTUP CURRENCY",
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
                            text = "TEXT & UI SIZE (MOBILE FIT)",
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
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .background(
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(6.dp)
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
                                text = "COLOR THEME",
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
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .background(
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(6.dp)
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
                            text = "PHONE SYSTEM SETTINGS INTEGRATION",
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
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Text(
                                    text = "DISPLAY & FONT",
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
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Text(
                                    text = "PHONE PIN / BIO",
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
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = "APP PERMISSIONS & NOTIFICATION SETTINGS",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // =========================================================================
                // =========================================================================
                // 4. BACKUP AND SYNC
                // =========================================================================
                item(key = "section_backup_and_sync") {
                    SettingsSectionCard(
                        sectionNumber = "4",
                        title = "BACKUP AND SYNC",
                        subtitle = "Google Drive AppData cloud backup, encrypted restore & offline JSON export",
                        testTag = "section_backup_and_sync"
                    ) {
                        // Operation In Progress Indicator
                        if (isDriveOperationInProgress) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
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
                                        text = driveOperationStatusMessage ?: "Google Drive operation in progress...",
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

                        // Drive Operation Error Banner
                        if (driveOperationError != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
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
                                        text = "GOOGLE DRIVE NOTICE",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Text(
                                    text = driveOperationError,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                OutlinedButton(
                                    onClick = onClearDriveOperationError,
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .height(34.dp)
                                        .testTag("dismiss_drive_error_button"),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                ) {
                                    Text("DISMISS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        // Google Drive AppData Section
                        Text(
                            text = "GOOGLE DRIVE APP DATA CLOUD BACKUP",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Backups are client-side encrypted with AES-256-GCM before upload and saved strictly in your hidden Google Drive AppData folder (Udhaar cannot access any of your other files).",
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
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SIGN IN WITH GOOGLE DRIVE", style = MaterialTheme.typography.labelLarge)
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
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
                                            text = "CONNECTED GOOGLE ACCOUNT",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = currentSettings.driveAccountEmail,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        val lastBackupStr = if (currentSettings.driveLastBackupMillis > 0) {
                                            val dateStr = InterestCalculator.formatDate(currentSettings.driveLastBackupMillis)
                                            val sizeKb = currentSettings.driveLastBackupSizeBytes / 1024
                                            "$dateStr (${sizeKb} KB)"
                                        } else {
                                            "No backup yet"
                                        }
                                        Text(
                                            text = "Last Backup: $lastBackupStr",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                            backupPassphrase = ""
                                            confirmBackupPassphrase = ""
                                            backupPassphraseError = null
                                            showBackupDialog = true
                                        },
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .height(44.dp)
                                            .testTag("drive_backup_now_button"),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("BACK UP NOW", style = MaterialTheme.typography.labelMedium)
                                    }

                                    OutlinedButton(
                                        onClick = onSignOutFromDrive,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .testTag("drive_sign_out_button"),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        Text("SIGN OUT", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }

                            // Automatic Backup with WorkManager
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                    .padding(12.dp)
                                    .testTag("drive_auto_backup_config_card"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "AUTOMATIC BACKUP (WORKMANAGER)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Udhaar schedules periodic background backups using WorkManager. Passphrase must be saved in Android Keystore.",
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
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .background(
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                                    shape = RoundedCornerShape(6.dp)
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
                                            text = "Wi-Fi Only",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Only upload auto-backups over unmetered Wi-Fi connections",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
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
                                        text = "⚠️ Auto-backup is scheduled, but no backup passphrase is saved in Keystore yet. Tap 'Back Up Now' and check 'Save passphrase' to enable unattended background uploads.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            // List of Earlier Backups in Drive AppData
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                    .padding(12.dp)
                                    .testTag("drive_earlier_backups_card"),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "EARLIER CLOUD BACKUPS (${driveRemoteBackups.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (driveRemoteBackups.isEmpty()) {
                                    Text(
                                        text = "No backups found in Google Drive AppData folder yet. Tap 'Back Up Now' above to create your first encrypted backup.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    driveRemoteBackups.forEach { backup ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
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
                                                    text = "${sizeKb} KB",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            val dateStr = if (backup.createdTimeMillis > 0) {
                                                InterestCalculator.formatDate(backup.createdTimeMillis)
                                            } else {
                                                "Unknown date"
                                            }
                                            Text(
                                                text = "Created: $dateStr • AES-256-GCM Encrypted",
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
                                                    shape = RoundedCornerShape(4.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                ) {
                                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("RESTORE", style = MaterialTheme.typography.labelSmall)
                                                }

                                                OutlinedButton(
                                                    onClick = { onDeleteDriveBackup(backup) },
                                                    modifier = Modifier
                                                        .weight(0.8f)
                                                        .height(38.dp)
                                                        .testTag("delete_backup_${backup.fileId}_button"),
                                                    shape = RoundedCornerShape(4.dp),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                                                ) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("DELETE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
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
                            text = "LOCAL OFFLINE JSON BACKUP & RESTORE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Export your entire ledger (all parties, INR & NPR transactions, partial payment history, and settings) as a portable unencrypted JSON file or restore from local storage.",
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
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SAVE BACKUP", style = MaterialTheme.typography.labelMedium)
                            }

                            OutlinedButton(
                                onClick = onShareFullLedgerFile,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("share_ledger_json_button"),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SHARE FILE", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        OutlinedButton(
                            onClick = onImportLedgerSaf,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("import_ledger_json_button"),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("IMPORT / RESTORE JSON BACKUP", style = MaterialTheme.typography.labelMedium)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

                        // Phase 3: Two-Party Cloud Verification, Privacy Consent & Delete My Cloud Data
                        Text(
                            text = "TWO-PARTY CLOUD VERIFICATION & PRIVACY",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Udhaar works 100% offline by default. Nothing leaves your device unless you explicitly consent below or share a verification link.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Explicit Cloud Verification Consent",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Allow shared records & E2EE notes to sync with Cloud Firestore",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
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
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = if (currentSettings.cloudUserId.isNotBlank()) {
                                    "SIGNED IN (${currentSettings.cloudUserEmail.ifBlank { "VERIFIED ACCOUNT" }})"
                                } else {
                                    "SIGN IN WITH GOOGLE FOR CLOUD VERIFICATION"
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        // Open / Verify a received link directly
                        OutlinedTextField(
                            value = incomingLinkInput,
                            onValueChange = { incomingLinkInput = it },
                            label = { Text("Open Received Verification or Payment Link") },
                            placeholder = { Text("Paste https://jyotirmoyb.com/u/... link") },
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
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("INSPECT SHARED LINK", style = MaterialTheme.typography.labelMedium)
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
                            shape = RoundedCornerShape(6.dp),
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
                                text = if (confirmCloudDelete) "TAP AGAIN TO PERMANENTLY DELETE CLOUD DATA" else "DELETE MY CLOUD DATA",
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
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                .padding(12.dp)
                                .testTag("share_give_access_card"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "SHARE UDHAAR & GIVE ACCESS",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Share the Udhaar app via QR or WhatsApp, or send your ledger statement file to a partner device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = onOpenShareAppModal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("open_share_app_modal_from_settings"),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SHARE UDHAAR APP (QR & INVITE LINK)", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        // About & Developer Support Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
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
                                        text = "UDHAAR LEDGER v7.0",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Developer & Admin: Jyotirmoy Bhowmik",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = "Support Email: jyotirmoybhowmik@gmail.com",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            OutlinedButton(
                                onClick = {
                                    val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:jyotirmoybhowmik@gmail.com")
                                        putExtra(Intent.EXTRA_SUBJECT, "Udhaar App Support / Feedback")
                                    }
                                    runCatching {
                                        context.startActivity(emailIntent)
                                    }.onFailure {
                                        Toast.makeText(context, "Admin Email: jyotirmoybhowmik@gmail.com", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("contact_admin_button"),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("CONTACT ADMIN (EMAIL SUPPORT)", style = MaterialTheme.typography.labelMedium)
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
                    text = "ENCRYPT & BACK UP TO GOOGLE DRIVE",
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
                        text = "Set a passphrase to encrypt your backup on-device before uploading to Google Drive AppData.\n\n⚠️ IMPORTANT: If you forget or lose this passphrase, your encrypted backup CANNOT be recovered.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = backupPassphrase,
                        onValueChange = {
                            backupPassphrase = it
                            backupPassphraseError = null
                        },
                        label = { Text("Backup Passphrase") },
                        placeholder = { Text("Enter strong passphrase") },
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
                        label = { Text("Confirm Passphrase") },
                        placeholder = { Text("Re-enter passphrase") },
                        singleLine = true,
                        visualTransformation = if (backupPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("backup_confirm_passphrase_input")
                    )

                    if (backupPassphraseError != null) {
                        Text(
                            text = backupPassphraseError!!,
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
                            text = "Save securely in phone Keystore for automatic daily/weekly backups",
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
                            onBackupNowToDrive(pass, savePassphraseForAuto)
                        }
                    },
                    modifier = Modifier.testTag("confirm_encrypt_and_backup_button")
                ) {
                    Text("ENCRYPT & UPLOAD")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showBackupDialog = false },
                    modifier = Modifier.testTag("cancel_backup_dialog_button")
                ) {
                    Text("CANCEL")
                }
            }
        )
    }

    if (showRestoreDialog && selectedRestoreBackup != null) {
        val backup = selectedRestoreBackup!!
        AlertDialog(
            onDismissRequest = {
                showRestoreDialog = false
                selectedRestoreBackup = null
            },
            title = {
                Text(
                    text = "DECRYPT & PREVIEW RESTORE",
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
                        text = "Enter the passphrase used when creating '${backup.fileName}'. The file will be decrypted and previewed before any changes are applied.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = restorePassphrase,
                        onValueChange = { restorePassphrase = it },
                        label = { Text("Backup Passphrase") },
                        placeholder = { Text("Passphrase") },
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
                    Text("DECRYPT & PREVIEW")
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
                    Text("CANCEL")
                }
            }
        )
    }
}

/**
 * Restore Preview Dialog: Previews record counts from parsed decrypted backup
 * and allows the user to choose Merge with existing or Replace all local data.
 */
@Composable
fun RestorePreviewDialog(
    backup: com.jyotirmoyb.udhaar.backup.DriveRemoteBackup,
    parsed: com.jyotirmoyb.udhaar.util.ParsedLedgerBackup,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "RESTORE BACKUP PREVIEW",
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
                    text = "Backup successfully decrypted from '${backup.fileName}'. Preview of contents:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    BreakdownLine("Schema Version", "v${parsed.schemaVersion}")
                    BreakdownLine("Transactions", "${parsed.transactions.size}")
                    BreakdownLine("Parties", "${parsed.parties.size}")
                    BreakdownLine("Partial Payments", "${parsed.partialPayments.size}")
                    BreakdownLine("Chat Messages", "${parsed.chatMessages.size}")
                    BreakdownLine("Attachments", "${parsed.attachments.size}")
                    val defaultCurr = parsed.settings["defaultCurrency"] as? String
                    if (!defaultCurr.isNullOrBlank()) {
                        BreakdownLine("Currency", defaultCurr)
                    }
                }

                Text(
                    text = "Choose how to apply this backup:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "• MERGE: Adds all remote records into your ledger, keeping local records.\n• REPLACE: Completely wipes your local ledger and replaces it with this backup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onMerge,
                    modifier = Modifier.testTag("restore_merge_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("MERGE")
                }
                Button(
                    onClick = onReplace,
                    modifier = Modifier.testTag("restore_replace_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("REPLACE ALL")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("restore_cancel_button")
            ) {
                Text("CANCEL")
            }
        }
    )
}

@Composable
private fun SettingsSectionCard(
    sectionNumber: String,
    title: String,
    subtitle: String,
    testTag: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
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
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
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
private fun BreakdownLine(
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
