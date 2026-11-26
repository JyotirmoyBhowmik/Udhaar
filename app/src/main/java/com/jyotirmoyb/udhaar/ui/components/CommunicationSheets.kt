package com.jyotirmoyb.udhaar.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.AuditTrailEntry
import com.jyotirmoyb.udhaar.data.ChatMessageEntity
import com.jyotirmoyb.udhaar.data.ChatMessageType
import com.jyotirmoyb.udhaar.data.ChatThreadMetadata
import com.jyotirmoyb.udhaar.data.ChatThreadScope
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.UserChatSafetyEntity
import com.jyotirmoyb.udhaar.data.isCounterpartyTyping
import com.jyotirmoyb.udhaar.ui.icons.UdhaarIcons
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.WhatsAppReminderLauncher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    myUpiId: String,
    isCloudLinked: Boolean = true,
    userSafety: UserChatSafetyEntity? = null,
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
    onToggleMute: () -> Unit,
    onToggleBlock: () -> Unit,
    onReportAbusiveUser: (reason: String) -> Unit,
    onReportThread: (reason: String) -> Unit = onReportAbusiveUser,
    onToggleUserMute: () -> Unit = {},
    onToggleUserBlock: () -> Unit = {},
    onReportUser: (reason: String) -> Unit = onReportAbusiveUser,
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
    val effectivePartyName = partyName.ifBlank { tx?.partyName ?: stringResource(R.string.chat_default_party_name) }
    val isThreadMuted = threadMetadata?.isMuted == true
    val isThreadBlocked = threadMetadata?.isBlocked == true
    val isThreadReported = threadMetadata?.isReported == true
    val isUserMuted = userSafety?.isMuted == true
    val isUserBlocked = userSafety?.isBlocked == true
    val isUserReported = userSafety?.isReported == true
    val isEffectivelyMuted = isThreadMuted || isUserMuted
    val isEffectivelyBlocked = isThreadBlocked || isUserBlocked
    val effectiveReportReason = userSafety?.reportReason?.ifBlank { null }
        ?: threadMetadata?.reportReason.orEmpty()
    val isCounterpartyTyping = threadMetadata?.isCounterpartyTyping == true

    var messageDraft by remember { mutableStateOf("") }
    var showExtensionBar by remember { mutableStateOf(false) }
    var extensionNoteDraft by remember { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var activeReportTarget by remember { mutableStateOf<ChatReportTarget?>(null) }
    var customReportReason by remember { mutableStateOf("") }

    val quickReplies = listOf(
        stringResource(R.string.chat_quick_reply_pay_friday),
        stringResource(R.string.chat_quick_reply_send_proof),
        stringResource(R.string.chat_quick_reply_paid_upi),
        stringResource(R.string.chat_quick_reply_received)
    )

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onSendMessage(
                ChatMessageType.PAYMENT_PROOF,
                messageDraft.ifBlank { context.getString(R.string.chat_proof_image_attached) },
                uri.toString(),
                "image/jpeg",
                false
            )
            messageDraft = ""
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            onSendMessage(
                ChatMessageType.PAYMENT_PROOF,
                messageDraft.ifBlank { context.getString(R.string.chat_proof_pdf_attached) },
                uri.toString(),
                "application/pdf",
                false
            )
            messageDraft = ""
        }
    }

    val mergedTimeline = remember(messages, auditEntries) {
        val auditItems = auditEntries.map { TimelineDisplayItem.AuditSystemItem(it) }
        val msgItems = messages.map { TimelineDisplayItem.UserChatMessageItem(it) }
        (auditItems + msgItems).sortedBy { it.timestampMillis }
    }

    LaunchedEffect(threadKey, messages.size) {
        // Keep incoming messages synced while sheet is visible
    }

    val reducedMotion = rememberReducedMotionEnabled()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        AnimatedSheetContent(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = navBarBottom + 16.dp)
                .testTag("chat_thread_bottom_sheet")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PersonAvatar(
                            name = effectivePartyName,
                            photoUri = "",
                            size = 38.dp,
                            modifier = Modifier.testTag("chat_header_avatar")
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (scope == ChatThreadScope.TRANSACTION && tx != null) {
                                        stringResource(
                                            R.string.ui_fmt_symbol_099c95,
                                            tx.partyName,
                                            tx.currency.symbol,
                                            InterestCalculator.formatAmount(InterestCalculator.paiseToRupees(tx.principalAmountPaise))
                                        )
                                    } else {
                                        stringResource(R.string.ui_fmt_general_thread_6c2c74, effectivePartyName)
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isEffectivelyMuted) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.ui_str_muted_a0f807),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (isCounterpartyTyping) {
                                Text(
                                    text = stringResource(R.string.ui_fmt_is_typing_958d10, effectivePartyName),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = financeColors.receiveAccent,
                                    modifier = Modifier.testTag("chat_typing_indicator")
                                )
                            } else {
                                val dueLabel = tx?.expiryDateMillis?.let {
                                    val formattedDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it))
                                    stringResource(R.string.ui_fmt_due_date_short, formattedDate)
                                } ?: stringResource(R.string.ui_no_due_date_set)
                                Text(
                                    text = if (scope == ChatThreadScope.TRANSACTION && tx != null) {
                                        stringResource(R.string.ui_fmt_audit_chat_c0cee8, tx.transactionType.label, dueLabel)
                                    } else {
                                        stringResource(R.string.ui_str_party_level_communication_payment_p_ce4f34)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onToggleMute,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("chat_mute_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (isEffectivelyMuted) UdhaarIcons.NotificationsOff else UdhaarIcons.NotificationsActive,
                                contentDescription = if (isEffectivelyMuted) {
                                    stringResource(R.string.cd_unmute_thread)
                                } else {
                                    stringResource(R.string.cd_mute_thread)
                                },
                                tint = if (isEffectivelyMuted) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("chat_overflow_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.cd_thread_options)
                                )
                            }
                            ChatSafetyOverflowMenu(
                                expanded = showOverflowMenu,
                                effectivePartyName = effectivePartyName,
                                isThreadMuted = isThreadMuted,
                                isThreadBlocked = isThreadBlocked,
                                isUserMuted = isUserMuted,
                                isUserBlocked = isUserBlocked,
                                payAccent = financeColors.payAccent,
                                onDismiss = { showOverflowMenu = false },
                                onToggleThreadMute = onToggleMute,
                                onToggleThreadBlock = onToggleBlock,
                                onOpenReportThread = {
                                    customReportReason = ""
                                    activeReportTarget = ChatReportTarget.THREAD
                                },
                                onToggleUserMute = onToggleUserMute,
                                onToggleUserBlock = onToggleUserBlock,
                                onOpenReportUser = {
                                    customReportReason = ""
                                    activeReportTarget = ChatReportTarget.USER
                                }
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("close_chat_sheet_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_close_chat)
                            )
                        }
                    }
                }

                ChatActionButtonsRow(
                    showRequestExtension = scope == ChatThreadScope.TRANSACTION && tx != null && !tx.isSettled,
                    onToggleRequestExtension = { showExtensionBar = !showExtensionBar },
                    onWhatsAppFallbackClick = {
                        if (tx != null && onOpenWhatsAppFallback != null) {
                            onOpenWhatsAppFallback(tx.id)
                        } else if (transactionSummary != null) {
                            val msg = InterestCalculator.buildWhatsAppReminderMessage(
                                summary = transactionSummary,
                                hideExactAmount = false,
                                upiId = myUpiId
                            )
                            WhatsAppReminderLauncher.launchReminder(
                                context = context,
                                rawPhoneNumber = partyPhone.ifBlank { tx?.phoneNumber.orEmpty() },
                                currency = transactionSummary.transaction.currency,
                                message = msg
                            )
                        } else {
                            val msg = context.getString(R.string.chat_whatsapp_fallback_greeting_fmt, effectivePartyName)
                            WhatsAppReminderLauncher.launchReminder(
                                context = context,
                                rawPhoneNumber = partyPhone,
                                currency = CurrencyType.INR,
                                message = msg
                            )
                        }
                    }
                )

                if (showExtensionBar && tx != null) {
                    ChatExtensionRequestDrawer(
                        context = context,
                        tx = tx,
                        extensionNoteDraft = extensionNoteDraft,
                        onNoteChange = { extensionNoteDraft = it },
                        onRequestDate = { targetDate ->
                            onRequestExtension(targetDate, extensionNoteDraft, false)
                            extensionNoteDraft = ""
                            showExtensionBar = false
                        }
                    )
                }

                activeReportTarget?.let { target ->
                    ChatReportSafetyCard(
                        reportTarget = target,
                        effectivePartyName = effectivePartyName,
                        customReportReason = customReportReason,
                        financeColors = financeColors,
                        onReasonChange = { customReportReason = it },
                        onCancel = { activeReportTarget = null },
                        onConfirm = { reason ->
                            if (target == ChatReportTarget.THREAD) {
                                onReportThread(reason)
                            } else {
                                onReportUser(reason)
                            }
                            activeReportTarget = null
                        }
                    )
                }

                if (isEffectivelyBlocked) {
                    ChatBlockedStatusBanner(
                        isThreadBlocked = isThreadBlocked,
                        isUserBlocked = isUserBlocked,
                        isThreadReported = isThreadReported,
                        isUserReported = isUserReported,
                        reportReason = effectiveReportReason,
                        partyName = effectivePartyName,
                        financeColors = financeColors,
                        onUnblockThread = onToggleBlock,
                        onUnblockUser = onToggleUserBlock
                    )
                }

                if (!isCloudLinked) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("chat_local_only_notice_banner"),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = if (scope == ChatThreadScope.TRANSACTION) {
                                stringResource(R.string.chat_local_only_notice_tx)
                            } else {
                                stringResource(R.string.chat_local_only_notice_party)
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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
                        item(key = "empty_chat_placeholder") {
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
                                        text = stringResource(R.string.ui_fmt_no_messages_yet_with_2caa5a, effectivePartyName),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.ui_str_tap_a_quick_reply_below_e95e6d),
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
                            Box(modifier = udhaarAnimateItem(reducedMotion)) {
                                when (item) {
                                    is TimelineDisplayItem.AuditSystemItem -> {
                                        SystemAuditPillItem(entry = item.entry)
                                    }
                                    is TimelineDisplayItem.UserChatMessageItem -> {
                                        ChatMessageBubbleItem(
                                            message = item.message,
                                            isCloudLinked = isCloudLinked,
                                            onAcceptExtension = { onRespondToExtension(item.message, true) },
                                            onDeclineExtension = { onRespondToExtension(item.message, false) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(6.dp))

                ChatComposerSection(
                    quickReplies = quickReplies,
                    isEffectivelyBlocked = isEffectivelyBlocked,
                    messageDraft = messageDraft,
                    onQuickReplyClick = { replyText ->
                        onSendMessage(ChatMessageType.QUICK_REPLY, replyText, "", "", false)
                    },
                    onPickImage = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onPickPdf = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                    onMessageDraftChange = { next ->
                        messageDraft = next
                    },
                    onSendMessageClick = {
                        val trimmed = messageDraft.trim()
                        if (trimmed.isNotEmpty()) {
                            onSendMessage(ChatMessageType.TEXT, trimmed, "", "", false)
                            messageDraft = ""
                        }
                    }
                )
            }
        }
    }
}
