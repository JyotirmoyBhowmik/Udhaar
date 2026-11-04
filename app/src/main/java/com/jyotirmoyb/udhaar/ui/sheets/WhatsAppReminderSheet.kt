package com.jyotirmoyb.udhaar.ui.sheets

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Message
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.ReminderChannel
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.reminder.ReminderEngine
import com.jyotirmoyb.udhaar.reminder.ReminderLevel
import com.jyotirmoyb.udhaar.reminder.ReminderPolicy
import com.jyotirmoyb.udhaar.ui.components.AnimatedSheetContent
import com.jyotirmoyb.udhaar.ui.components.UdhaarAnimatedSwitch
import com.jyotirmoyb.udhaar.ui.components.pressScaleFeedback
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.InterestCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WhatsAppReminderModal(
    summary: CalculatedUdhaarSummary,
    userSettings: UserSettings,
    party: PartyEntity? = null,
    fallbackPhoneNumber: String = "",
    installmentPlan: com.jyotirmoyb.udhaar.data.InstallmentPlan? = null,
    installments: List<com.jyotirmoyb.udhaar.data.Installment> = emptyList(),
    onRecordReminderEvent: ((level: Int, channel: ReminderChannel) -> Unit)? = null,
    onSendInAppChat: ((message: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tx = summary.transaction
    val effectivePhone = tx.phoneNumber.ifBlank { fallbackPhoneNumber }

    val policy = remember(userSettings) {
        ReminderPolicy(
            level1DaysBefore = userSettings.reminderLevel1DaysBefore,
            level2MaxOverdueDays = userSettings.reminderLevel2MaxOverdueDays,
            level3MaxOverdueDays = userSettings.reminderLevel3MaxOverdueDays,
            quietHoursEnabled = userSettings.quietHoursEnabled,
            quietHoursStartHour = userSettings.quietHoursStartHour,
            quietHoursEndHour = userSettings.quietHoursEndHour
        )
    }

    val nextUnpaidInstallment = remember(installments) {
        installments
            .sortedBy { it.sequence }
            .firstOrNull { it.amountPaid < it.amountDue && it.status != com.jyotirmoyb.udhaar.data.InstallmentStatus.PAID }
    }

    val computedDefaultLevel = remember(summary, policy, nextUnpaidInstallment) {
        if (nextUnpaidInstallment != null) {
            ReminderEngine.computeInstallmentEscalationLevel(nextUnpaidInstallment, policy)
                ?: ReminderLevel.LEVEL_1_GENTLE
        } else {
            ReminderEngine.computeEscalationLevel(tx, policy) ?: ReminderLevel.LEVEL_1_GENTLE
        }
    }

    var selectedLevel by rememberSaveable(tx.id) {
        mutableStateOf(computedDefaultLevel)
    }

    val defaultLang = remember(party) {
        party?.language?.ifBlank { "en" } ?: "en"
    }
    var selectedLanguage by rememberSaveable(tx.id) {
        mutableStateOf(defaultLang)
    }

    var hideExactAmount by rememberSaveable(tx.id) { mutableStateOf(false) }
    var includeUpiId by rememberSaveable(tx.id) { mutableStateOf(true) }

    val effectiveDueMillis = nextUnpaidInstallment?.dueDate ?: tx.expiryDateMillis
    val dueDateStr = remember(effectiveDueMillis) {
        effectiveDueMillis?.let {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(it))
        }.orEmpty()
    }
    val overdueDays = remember(effectiveDueMillis) {
        ReminderEngine.calculateOverdueDays(effectiveDueMillis) ?: 0
    }

    val principalStr = remember(summary.principal, tx.currency) {
        InterestCalculator.formatCurrency(summary.principal, tx.currency).removePrefix("₹").removePrefix("रू").trim()
    }
    val netDueStr = remember(summary.netBalanceDue, tx.currency) {
        InterestCalculator.formatCurrency(summary.netBalanceDue, tx.currency).removePrefix("₹").removePrefix("रू").trim()
    }
    val paidStr = remember(summary.amountPaid, tx.currency) {
        InterestCalculator.formatCurrency(summary.amountPaid, tx.currency).removePrefix("₹").removePrefix("रू").trim()
    }

    fun generateMessage(level: ReminderLevel, lang: String, hideAmount: Boolean, upi: Boolean): String {
        if (installmentPlan != null && nextUnpaidInstallment != null && !hideAmount) {
            val instAmountDouble = com.jyotirmoyb.udhaar.util.InstallmentCalculator.paiseToDouble(
                nextUnpaidInstallment.amountDue - nextUnpaidInstallment.amountPaid
            )
            val instAmountFormatted = InterestCalculator.formatCurrency(instAmountDouble, tx.currency)
            val totalRemainingFormatted = InterestCalculator.formatCurrency(summary.netBalanceDue, tx.currency)
            val instTemplate = ReminderEngine.getLocalizedInstallmentTemplate(context, lang)
            val baseDraft = ReminderEngine.renderInstallmentDraftMessage(
                template = instTemplate,
                name = tx.partyName,
                sequence = nextUnpaidInstallment.sequence,
                totalCount = installmentPlan.installmentCount,
                installmentAmount = instAmountFormatted,
                dueDate = dueDateStr,
                totalRemaining = totalRemainingFormatted
            )
            return if (upi && userSettings.upiId.isNotBlank()) {
                "$baseDraft UPI: ${userSettings.upiId}"
            } else {
                baseDraft
            }
        }
        val template = ReminderEngine.getLocalizedTemplate(context, level, lang, hideAmount)
        return ReminderEngine.renderDraftMessage(
            template = template,
            name = tx.partyName,
            myName = userSettings.payeeName.ifBlank { "Me" },
            amount = principalStr,
            remaining = netDueStr,
            paidSoFar = paidStr,
            dueDate = dueDateStr,
            daysOverdue = overdueDays,
            upiId = if (upi) userSettings.upiId else ""
        )
    }

    var editableMessage by rememberSaveable(tx.id) {
        mutableStateOf(generateMessage(selectedLevel, selectedLanguage, hideExactAmount, includeUpiId))
    }

    fun regenerate() {
        editableMessage = generateMessage(selectedLevel, selectedLanguage, hideExactAmount, includeUpiId)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = { WindowInsets.navigationBars.union(WindowInsets.ime) }
    ) {
        AnimatedSheetContent(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .align(Alignment.CenterHorizontally)
                .imePadding()
                .navigationBarsPadding()
                .testTag("reminder_editor_modal")
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.reminder_editor_title_fmt, tx.partyName),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val maskedPhoneSuffix = if (effectivePhone.isNotBlank()) "(${InterestCalculator.maskPhoneNumber(effectivePhone)})" else ""
                        Text(
                            text = stringResource(R.string.ui_fmt_to_40e0b0, tx.partyName.uppercase(), maskedPhoneSuffix),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .pressScaleFeedback()
                            .testTag("close_reminder_modal_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_dismiss_needs_attention), tint = MaterialTheme.colorScheme.onSurface)
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
                    // Level Selector (Gentle, Friendly, Firm, Final)
                    Text(
                        text = stringResource(R.string.reminder_editor_level_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReminderLevel.entries.forEach { lvl ->
                            FilterChip(
                                selected = selectedLevel == lvl,
                                onClick = {
                                    selectedLevel = lvl
                                    editableMessage = generateMessage(lvl, selectedLanguage, hideExactAmount, includeUpiId)
                                },
                                label = { Text(stringResource(lvl.titleResId)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("reminder_level_chip_${lvl.levelNumber}")
                            )
                        }
                    }

                    // Language Selector
                    Text(
                        text = stringResource(R.string.preferred_language_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val languages = listOf(
                            "en" to "English",
                            "hi" to "हिन्दी",
                            "bn" to "বাংলা",
                            "as" to "অসমীয়া",
                            "ne" to "नेपाली"
                        )
                        languages.forEach { (code, label) ->
                            FilterChip(
                                selected = selectedLanguage == code,
                                onClick = {
                                    selectedLanguage = code
                                    editableMessage = generateMessage(selectedLevel, code, hideExactAmount, includeUpiId)
                                },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier.testTag("reminder_lang_chip_$code")
                            )
                        }
                    }

                    // Switches: Hide Exact Amount & Include UPI ID
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
                                text = stringResource(R.string.hide_exact_amount_switch),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.ui_str_send_polite_reminder_without_numeri_6457c4),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        UdhaarAnimatedSwitch(
                            checked = hideExactAmount,
                            onCheckedChange = { checked ->
                                hideExactAmount = checked
                                editableMessage = generateMessage(selectedLevel, selectedLanguage, checked, includeUpiId)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("hide_exact_amount_switch")
                        )
                    }

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
                                text = stringResource(R.string.add_upi_id_switch),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = userSettings.upiId.ifBlank { stringResource(R.string.ui_str_upi_id_not_set_in_c3a0bf) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        UdhaarAnimatedSwitch(
                            checked = includeUpiId,
                            onCheckedChange = { checked ->
                                includeUpiId = checked
                                editableMessage = generateMessage(selectedLevel, selectedLanguage, hideExactAmount, checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("include_upi_id_switch")
                        )
                    }

                    // Available Placeholders Row
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.available_placeholders_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val placeholders = listOf(
                            "{name}", "{my_name}", "{amount}", "{remaining}",
                            "{paid_so_far}", "{due_date}", "{days_overdue}", "{upi_id}", "{new_date}"
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            placeholders.forEach { tag ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .clickable {
                                            editableMessage = "$editableMessage $tag"
                                        }
                                        .testTag("placeholder_chip_$tag")
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Message Preview & Editor
                    OutlinedTextField(
                        value = editableMessage,
                        onValueChange = { editableMessage = it },
                        label = { Text(stringResource(R.string.reminder_editor_preview_label)) },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reminder_message_input")
                    )

                    // Sending Actions: WhatsApp, SMS, Udhaar Chat, Copy
                    Button(
                        onClick = {
                            onRecordReminderEvent?.invoke(selectedLevel.levelNumber, ReminderChannel.WHATSAPP)
                            com.jyotirmoyb.udhaar.util.WhatsAppReminderLauncher.launchReminder(
                                context = context,
                                rawPhoneNumber = effectivePhone,
                                currency = tx.currency,
                                message = editableMessage
                            )
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .pressScaleFeedback()
                            .testTag("launch_whatsapp_send_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.reminder_channel_whatsapp), style = MaterialTheme.typography.labelLarge)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onRecordReminderEvent?.invoke(selectedLevel.levelNumber, ReminderChannel.SMS)
                                try {
                                    val clean = effectivePhone.replace(Regex("[^0-9+]"), "")
                                    val uri = if (clean.isNotBlank()) Uri.parse("smsto:$clean") else Uri.parse("smsto:")
                                    val smsIntent = Intent(Intent.ACTION_SENDTO, uri).apply {
                                        putExtra("sms_body", editableMessage)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(smsIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.toast_could_not_open_sms), Toast.LENGTH_SHORT).show()
                                }
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("send_sms_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Message, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.reminder_channel_sms), style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = {
                                onRecordReminderEvent?.invoke(selectedLevel.levelNumber, ReminderChannel.CLIPBOARD)
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.share_clip_reminder), editableMessage))
                                Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("copy_reminder_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.reminder_channel_copy), style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    // In-app Udhaar Chat option if party has linked Udhaar
                    if (party?.linkedUserId?.isNotBlank() == true || onSendInAppChat != null) {
                        Button(
                            onClick = {
                                onRecordReminderEvent?.invoke(selectedLevel.levelNumber, ReminderChannel.IN_APP_CHAT)
                                onSendInAppChat?.invoke(editableMessage)
                                Toast.makeText(context, context.getString(R.string.reminder_channel_chat), Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .pressScaleFeedback()
                                .testTag("send_udhaar_chat_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            )
                        ) {
                            Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.reminder_channel_chat), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
