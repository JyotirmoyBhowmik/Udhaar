package com.jyotirmoyb.udhaar.ui.components

import android.app.DatePickerDialog
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jyotirmoyb.udhaar.data.AuditTrailEntry
import com.jyotirmoyb.udhaar.data.ChatMessageEntity
import com.jyotirmoyb.udhaar.data.ChatMessageType
import com.jyotirmoyb.udhaar.data.ChatThreadMetadata
import com.jyotirmoyb.udhaar.data.ChatThreadScope
import com.jyotirmoyb.udhaar.data.ExtensionRequestStatus
import com.jyotirmoyb.udhaar.data.MessageDeliveryStatus
import com.jyotirmoyb.udhaar.data.isCounterpartyTyping
import com.jyotirmoyb.udhaar.data.isOutgoing
import com.jyotirmoyb.udhaar.data.textContent
import android.net.Uri
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.InterestCalculator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class ChatFinancePalette(
    val receiveAccent: Color,
    val receiveContainer: Color,
    val payAccent: Color,
    val payContainer: Color
)

private sealed class TimelineDisplayItem(val timestampMillis: Long) {
    data class AuditSystemItem(val entry: AuditTrailEntry) : TimelineDisplayItem(entry.eventTimeMillis)
    data class UserChatMessageItem(val message: ChatMessageEntity) : TimelineDisplayItem(message.sentAtMillis)
}

private val PRESET_QUICK_REPLIES = listOf(
    "I will pay on Friday",
    "Please send proof",
    "Payment sent via UPI",
    "Received, thank you"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatThreadBottomSheet(
    threadKey: String,
    scope: ChatThreadScope,
    partyName: String,
    partyId: Long?,
    partyPhone: String,
    transactionSummary: CalculatedUdhaarSummary?,
    threadMetadata: ChatThreadMetadata?,
    messages: List<ChatMessageEntity>,
    auditEntries: List<AuditTrailEntry>,
    myProfileName: String,
    myUpiId: String,
    onDismiss: () -> Unit,
    onSendMessage: (
        messageType: ChatMessageType,
        textContent: String,
        attachmentUri: String,
        attachmentMimeType: String,
        fromCounterparty: Boolean
    ) -> Unit,
    onRequestExtension: (
        proposedDueDateMillis: Long,
        note: String,
        requestedByCounterparty: Boolean
    ) -> Unit,
    onRespondToExtension: (message: ChatMessageEntity, accept: Boolean) -> Unit,
    onSetTypingIndicator: (isCounterpartyTyping: Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onToggleBlock: () -> Unit,
    onReportAbusiveUser: (reason: String) -> Unit,
    onOpenWhatsAppFallback: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val financeColors = ChatFinancePalette(
        receiveAccent = MaterialTheme.colorScheme.primary,
        receiveContainer = MaterialTheme.colorScheme.secondaryContainer,
        payAccent = MaterialTheme.colorScheme.error,
        payContainer = MaterialTheme.colorScheme.errorContainer
    )
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val tx: UdhaarTransaction? = transactionSummary?.transaction
    val effectivePartyName = partyName.ifBlank { tx?.partyName ?: "Party" }
    val isMuted = threadMetadata?.isMuted == true
    val isBlocked = threadMetadata?.isBlocked == true
    val isReported = threadMetadata?.isReported == true
    val isCounterpartyTyping = threadMetadata?.isCounterpartyTyping == true

    var messageDraft by remember { mutableStateOf("") }
    var simulateCounterpartyMode by remember { mutableStateOf(false) }
    var showExtensionBar by remember { mutableStateOf(false) }
    var extensionNoteDraft by remember { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var customReportReason by remember { mutableStateOf("") }

    // Photo Picker for payment proof image
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onSendMessage(
                ChatMessageType.PAYMENT_PROOF,
                messageDraft.ifBlank { "Payment proof image attached" },
                uri.toString(),
                "image/jpeg",
                simulateCounterpartyMode
            )
            messageDraft = ""
        }
    }

    // Document picker for payment proof PDF
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onSendMessage(
                ChatMessageType.PAYMENT_PROOF,
                messageDraft.ifBlank { "Payment proof PDF statement attached" },
                uri.toString(),
                "application/pdf",
                simulateCounterpartyMode
            )
            messageDraft = ""
        }
    }

    // Merge audit trail system events and chat messages into a unified chronological stream
    val mergedTimeline = remember(messages, auditEntries) {
        val auditItems = auditEntries.map { TimelineDisplayItem.AuditSystemItem(it) }
        val msgItems = messages.map { TimelineDisplayItem.UserChatMessageItem(it) }
        (auditItems + msgItems).sortedBy { it.timestampMillis }
    }

    LaunchedEffect(threadKey, messages.size) {
        // Ensure incoming messages are marked read while sheet is open
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = navBarBottom + 16.dp)
                .testTag("chat_thread_bottom_sheet")
        ) {
            // --- Top Header ---
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
                            text = if (scope == ChatThreadScope.TRANSACTION && tx != null) {
                                "${tx.partyName} • ${tx.currency.symbol}${InterestCalculator.formatAmount(tx.principalAmount)}"
                            } else {
                                "$effectivePartyName • General Thread"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isMuted) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "MUTED",
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (isCounterpartyTyping) {
                        Text(
                            text = "$effectivePartyName is typing…",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = financeColors.receiveAccent,
                            modifier = Modifier.testTag("chat_typing_indicator")
                        )
                    } else {
                        Text(
                            text = if (scope == ChatThreadScope.TRANSACTION && tx != null) {
                                val dueLabel = tx.expiryDateMillis?.let {
                                    "Due ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it))}"
                                } ?: "No due date set"
                                "${tx.transactionType.label} • $dueLabel • Audit & Chat"
                            } else {
                                "Party-level communication & payment proofs"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Mute / Unmute quick button
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_mute_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                            contentDescription = if (isMuted) "Unmute thread" else "Mute thread",
                            tint = if (isMuted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // More menu (WhatsApp fallback, Simulate typing, Block, Report)
                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_overflow_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Thread moderation & options"
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (isMuted) "Unmute Notifications" else "Mute Thread Notifications")
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onToggleMute()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isCounterpartyTyping) "Stop '$effectivePartyName is typing…'"
                                        else "Preview '$effectivePartyName is typing…'"
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.History, contentDescription = null)
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onSetTypingIndicator(!isCounterpartyTyping)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isBlocked) "Unblock $effectivePartyName" else "Block $effectivePartyName",
                                        color = if (isBlocked) MaterialTheme.colorScheme.onSurface else financeColors.payAccent
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Block,
                                        contentDescription = null,
                                        tint = if (isBlocked) MaterialTheme.colorScheme.onSurface else financeColors.payAccent
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onToggleBlock()
                                },
                                modifier = Modifier.testTag("chat_menu_block_item")
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Report Abusive User",
                                        color = financeColors.payAccent
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Flag,
                                        contentDescription = null,
                                        tint = financeColors.payAccent
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showReportDialog = true
                                },
                                modifier = Modifier.testTag("chat_menu_report_item")
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_chat_sheet_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close chat")
                    }
                }
            }

            // --- Action Ribbon: Request Extension (for Transaction Thread) + WhatsApp Fallback ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (scope == ChatThreadScope.TRANSACTION && tx != null && !tx.isSettled) {
                    OutlinedButton(
                        onClick = { showExtensionBar = !showExtensionBar },
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .testTag("toggle_request_extension_button"),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "REQUEST EXTENSION",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black)
                        )
                    }
                }

                // WhatsApp Fallback button for people who do not have the app
                OutlinedButton(
                    onClick = {
                        if (tx != null && onOpenWhatsAppFallback != null) {
                            onOpenWhatsAppFallback(tx.id)
                        } else if (transactionSummary != null) {
                            val msg = InterestCalculator.buildWhatsAppReminderMessage(
                                summary = transactionSummary,
                                hideExactAmount = false,
                                upiId = myUpiId
                            )
                            val sanitized = InterestCalculator.sanitizePhoneForWhatsApp(
                                partyPhone.ifBlank { tx?.phoneNumber.orEmpty() },
                                transactionSummary.transaction.currency
                            )
                            val url = if (sanitized.isNotBlank()) {
                                "https://wa.me/$sanitized?text=${Uri.encode(msg)}"
                            } else {
                                "https://wa.me/?text=${Uri.encode(msg)}"
                            }
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        } else {
                            val msg = "Namaste $effectivePartyName, messaging you from Udhaar Ledger."
                            val sanitized = partyPhone.filter { it.isDigit() }
                            val url = if (sanitized.isNotBlank()) {
                                "https://wa.me/$sanitized?text=${Uri.encode(msg)}"
                            } else {
                                "https://wa.me/?text=${Uri.encode(msg)}"
                            }
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("chat_whatsapp_fallback_button"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "WHATSAPP FALLBACK",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black)
                    )
                }
            }

            // --- One-Tap Request Extension Drawer ---
            if (showExtensionBar && tx != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("extension_request_drawer"),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "PROPOSE NEW DUE DATE (ONE-TAP EXTENSION)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val baseDue = tx.expiryDateMillis?.takeIf { it > System.currentTimeMillis() }
                            ?: System.currentTimeMillis()
                        val dayMs = 24L * 60L * 60L * 1000L

                        OutlinedTextField(
                            value = extensionNoteDraft,
                            onValueChange = { extensionNoteDraft = it },
                            label = { Text("Optional reason (e.g. Salary credited next week)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("extension_reason_input")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(7 to "+7 Days", 15 to "+15 Days", 30 to "+30 Days").forEach { (days, label) ->
                                Button(
                                    onClick = {
                                        val targetDate = baseDue + (days * dayMs)
                                        onRequestExtension(
                                            targetDate,
                                            extensionNoteDraft,
                                            simulateCounterpartyMode
                                        )
                                        extensionNoteDraft = ""
                                        showExtensionBar = false
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("request_ext_${days}d_button"),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    val cal = Calendar.getInstance().apply { timeInMillis = baseDue + (7 * dayMs) }
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val pickedCal = Calendar.getInstance().apply {
                                                set(y, m, d, 23, 59, 59)
                                            }
                                            onRequestExtension(
                                                pickedCal.timeInMillis,
                                                extensionNoteDraft,
                                                simulateCounterpartyMode
                                            )
                                            extensionNoteDraft = ""
                                            showExtensionBar = false
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                                modifier = Modifier
                                    .height(40.dp)
                                    .testTag("request_ext_custom_date_button"),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = "Pick custom extension date",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // --- Report Abusive User Inline Sheet ---
            if (showReportDialog) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("report_abusive_user_card"),
                    shape = RoundedCornerShape(8.dp),
                    color = financeColors.payContainer,
                    border = BorderStroke(1.dp, financeColors.payAccent.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "REPORT & BLOCK ${effectivePartyName.uppercase()}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                            color = financeColors.payAccent
                        )
                        Text(
                            text = "Reporting this thread blocks further messages and records your abuse report.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val presetReasons = listOf(
                            "Harassment or abusive language",
                            "Fake or tampered payment proof",
                            "Spam or unwanted messages"
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presetReasons.forEach { preset ->
                                FilterChip(
                                    selected = customReportReason == preset,
                                    onClick = { customReportReason = preset },
                                    label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = customReportReason,
                            onValueChange = { customReportReason = it },
                            label = { Text("Reason for report") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("report_reason_input")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showReportDialog = false }) {
                                Text("CANCEL")
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = {
                                    onReportAbusiveUser(
                                        customReportReason.ifBlank { "Harassment or abusive language" }
                                    )
                                    showReportDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = financeColors.payAccent,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.testTag("confirm_report_block_button")
                            ) {
                                Text("REPORT & BLOCK", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }

            // --- Blocked / Reported Banner ---
            if (isBlocked) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("thread_blocked_banner"),
                    shape = RoundedCornerShape(6.dp),
                    color = financeColors.payContainer,
                    border = BorderStroke(1.dp, financeColors.payAccent.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isReported) {
                                    "USER REPORTED & BLOCKED (${threadMetadata?.reportReason.orEmpty()})"
                                } else {
                                    "THREAD BLOCKED — MESSAGES PAUSED"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                color = financeColors.payAccent
                            )
                            Text(
                                text = "Unblock to resume sending or receiving messages in this thread.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = onToggleBlock,
                            modifier = Modifier.testTag("unblock_thread_button")
                        ) {
                            Text("UNBLOCK", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // --- Timeline (Messages + Audit Events) ---
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(min = 200.dp, max = 380.dp)
                    .padding(vertical = 8.dp)
                    .testTag("chat_messages_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                if (mergedTimeline.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "No messages yet with $effectivePartyName",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Tap a quick reply below, attach a payment proof image/PDF, or propose a due-date extension.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = mergedTimeline,
                        key = { item ->
                            when (item) {
                                is TimelineDisplayItem.AuditSystemItem -> "audit_${item.entry.id}"
                                is TimelineDisplayItem.UserChatMessageItem -> "msg_${item.message.id}"
                            }
                        }
                    ) { item ->
                        when (item) {
                            is TimelineDisplayItem.AuditSystemItem -> {
                                SystemAuditPillItem(entry = item.entry)
                            }
                            is TimelineDisplayItem.UserChatMessageItem -> {
                                ChatMessageBubbleItem(
                                    message = item.message,
                                    onAcceptExtension = { onRespondToExtension(item.message, true) },
                                    onDeclineExtension = { onRespondToExtension(item.message, false) }
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(6.dp))

            // --- Quick Reply Chips Row ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PRESET_QUICK_REPLIES.forEachIndexed { idx, replyText ->
                    FilterChip(
                        selected = false,
                        enabled = !isBlocked,
                        onClick = {
                            onSendMessage(
                                ChatMessageType.QUICK_REPLY,
                                replyText,
                                "",
                                "",
                                simulateCounterpartyMode
                            )
                        },
                        label = {
                            Text(
                                text = replyText,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        modifier = Modifier.testTag("quick_reply_chip_$idx")
                    )
                }
            }

            // --- Sender Mode Toggle (You vs Simulate Counterparty Reply for single-phone testing) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (simulateCounterpartyMode) {
                        "Mode: Simulating incoming message from $effectivePartyName"
                    } else {
                        "Sending as: ${myProfileName.ifBlank { "You" }}"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (simulateCounterpartyMode) financeColors.receiveAccent else MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilterChip(
                    selected = simulateCounterpartyMode,
                    onClick = { simulateCounterpartyMode = !simulateCounterpartyMode },
                    label = {
                        Text(
                            text = if (simulateCounterpartyMode) "Switch to You" else "Simulate $effectivePartyName Reply",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = financeColors.receiveContainer,
                        selectedLabelColor = financeColors.receiveAccent
                    ),
                    modifier = Modifier.testTag("toggle_simulate_counterparty_chip")
                )
            }

            // --- Composer Input + Image/PDF Attachment Pickers + Send Button ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isBlocked,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("attach_payment_proof_image_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Attach payment proof image",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        pdfPickerLauncher.launch(arrayOf("application/pdf"))
                    },
                    enabled = !isBlocked,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("attach_payment_proof_pdf_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach payment proof PDF",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = messageDraft,
                    onValueChange = { next ->
                        messageDraft = next
                        if (simulateCounterpartyMode) {
                            onSetTypingIndicator(next.isNotBlank())
                        }
                    },
                    placeholder = {
                        Text(
                            text = if (isBlocked) "Thread is blocked" else "Write a message or note…",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    enabled = !isBlocked,
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input")
                )

                Button(
                    onClick = {
                        val trimmed = messageDraft.trim()
                        if (trimmed.isNotEmpty()) {
                            onSendMessage(
                                ChatMessageType.TEXT,
                                trimmed,
                                "",
                                "",
                                simulateCounterpartyMode
                            )
                            messageDraft = ""
                            if (simulateCounterpartyMode) {
                                onSetTypingIndicator(false)
                            }
                        }
                    },
                    enabled = !isBlocked && messageDraft.isNotBlank(),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("chat_send_message_button"),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send message",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemAuditPillItem(entry: AuditTrailEntry) {
    val timeFmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${entry.eventType.label}: ${entry.summaryText} (${timeFmt.format(Date(entry.eventTimeMillis))})",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubbleItem(
    message: ChatMessageEntity,
    onAcceptExtension: () -> Unit,
    onDeclineExtension: () -> Unit
) {
    val financeColors = ChatFinancePalette(
        receiveAccent = MaterialTheme.colorScheme.primary,
        receiveContainer = MaterialTheme.colorScheme.secondaryContainer,
        payAccent = MaterialTheme.colorScheme.error,
        payContainer = MaterialTheme.colorScheme.errorContainer
    )
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    val isOutgoing = message.isOutgoing
    val bubbleBg = if (isOutgoing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val bubbleText = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 310.dp),
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isOutgoing) 12.dp else 2.dp,
                bottomEnd = if (isOutgoing) 2.dp else 12.dp
            ),
            color = bubbleBg,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Sender label + message type tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isOutgoing) "You (${message.senderName})" else message.senderName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        ),
                        color = bubbleText.copy(alpha = 0.75f)
                    )
                    if (message.messageType == ChatMessageType.QUICK_REPLY) {
                        Text(
                            text = "QUICK REPLY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = bubbleText.copy(alpha = 0.6f)
                        )
                    }
                }

                // Payment proof card if attachment present
                if (message.messageType == ChatMessageType.PAYMENT_PROOF || message.attachmentUri.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (message.attachmentMimeType.contains("pdf", ignoreCase = true)) {
                                    Icons.Default.Description
                                } else {
                                    Icons.Default.Image
                                },
                                contentDescription = "Payment Proof Attachment",
                                tint = financeColors.receiveAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (message.attachmentMimeType.contains("pdf", ignoreCase = true)) {
                                        "PAYMENT PROOF (PDF DOCUMENT)"
                                    } else {
                                        "PAYMENT PROOF (IMAGE RECEIPT)"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black
                                    ),
                                    color = financeColors.receiveAccent
                                )
                                Text(
                                    text = message.attachmentUri.takeLast(36).ifBlank { "Verified local attachment" },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Main text content
                Text(
                    text = message.textContent,
                    style = MaterialTheme.typography.bodySmall,
                    color = bubbleText
                )

                // Extension Request interactive card
                if (message.messageType == ChatMessageType.EXTENSION_REQUEST) {
                    val proposedDateText = message.proposedDueDateMillis?.let { dateFmt.format(Date(it)) } ?: "New Date"
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "PROPOSED DUE: $proposedDateText",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = when (message.extensionStatus) {
                                        ExtensionRequestStatus.ACCEPTED -> financeColors.receiveContainer
                                        ExtensionRequestStatus.DECLINED -> financeColors.payContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ) {
                                    Text(
                                        text = message.extensionStatus.name,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black
                                        ),
                                        color = when (message.extensionStatus) {
                                            ExtensionRequestStatus.ACCEPTED -> financeColors.receiveAccent
                                            ExtensionRequestStatus.DECLINED -> financeColors.payAccent
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }

                            if (message.extensionStatus == ExtensionRequestStatus.PENDING) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = onAcceptExtension,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .testTag("accept_extension_button_${message.id}"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = financeColors.receiveAccent,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = "ACCEPT",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black)
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = onDeclineExtension,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .testTag("decline_extension_button_${message.id}"),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = "DECLINE",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Timestamp + Delivery/Read ticks (`✓`, `✓✓`, `✓✓ Read`)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeFmt.format(Date(message.sentAtMillis)),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = bubbleText.copy(alpha = 0.65f)
                    )
                    if (isOutgoing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        val tickLabel = when (message.deliveryStatus) {
                            MessageDeliveryStatus.SENT -> "✓ Sent"
                            MessageDeliveryStatus.DELIVERED -> "✓✓ Delivered"
                            MessageDeliveryStatus.READ -> "✓✓ Read"
                        }
                        Icon(
                            imageVector = if (message.deliveryStatus == MessageDeliveryStatus.SENT) {
                                Icons.Default.Check
                            } else {
                                Icons.Default.DoneAll
                            },
                            contentDescription = tickLabel,
                            modifier = Modifier.size(13.dp),
                            tint = if (message.deliveryStatus == MessageDeliveryStatus.READ) {
                                financeColors.receiveAccent
                            } else {
                                bubbleText.copy(alpha = 0.75f)
                            }
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = when (message.deliveryStatus) {
                                MessageDeliveryStatus.SENT -> "Sent"
                                MessageDeliveryStatus.DELIVERED -> "Delivered"
                                MessageDeliveryStatus.READ -> "Read"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (message.deliveryStatus == MessageDeliveryStatus.READ) {
                                financeColors.receiveAccent
                            } else {
                                bubbleText.copy(alpha = 0.75f)
                            }
                        )
                    }
                }
            }
        }
    }
}
