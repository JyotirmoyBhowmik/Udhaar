package com.jyotirmoyb.udhaar.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.CompoundFrequency
import com.jyotirmoyb.udhaar.data.InterestRatePeriod
import com.jyotirmoyb.udhaar.data.InterestType
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.ui.theme.SubtleSurfaceGrey
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.InterestCalculator

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditTermsAndInterestSection(
    context: Context,
    reducedMotion: Boolean,
    plainAmountLabel: String,
    paymentMode: PaymentMode,
    onPaymentModeChange: (PaymentMode) -> Unit,
    isInterestEnabled: Boolean,
    onInterestEnabledChange: (Boolean) -> Unit,
    interestType: InterestType,
    onInterestTypeChange: (InterestType) -> Unit,
    interestRateText: String,
    onInterestRateTextChange: (String) -> Unit,
    interestRatePeriod: InterestRatePeriod,
    onInterestRatePeriodChange: (InterestRatePeriod) -> Unit,
    compoundFrequency: CompoundFrequency,
    onCompoundFrequencyChange: (CompoundFrequency) -> Unit,
    startDateMillis: Long,
    onStartDateMillisChange: (Long) -> Unit,
    expiryDateMillis: Long?,
    onExpiryDateMillisChange: (Long?) -> Unit,
    reminderDateMillis: Long?,
    onReminderDateMillisChange: (Long?) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    showMoreDetails: Boolean,
    onToggleMoreDetails: () -> Unit,
    showStartDatePicker: Boolean,
    onShowStartDatePickerChange: (Boolean) -> Unit,
    showExpiryDatePicker: Boolean,
    onShowExpiryDatePickerChange: (Boolean) -> Unit,
    showReminderDatePicker: Boolean,
    onShowReminderDatePickerChange: (Boolean) -> Unit,
    previewSummary: CalculatedUdhaarSummary,
    allCurrentAttachments: List<Attachment>,
    onCameraClick: () -> Unit,
    galleryLauncher: ActivityResultLauncher<PickVisualMediaRequest>,
    documentLauncher: ActivityResultLauncher<Array<String>>,
    notificationPermissionLauncher: ActivityResultLauncher<String>,
    onOpenAttachmentViewer: (Attachment) -> Unit,
    onRemoveDraftAttachment: (Attachment) -> Unit,
    onDeleteExistingAttachment: (Attachment) -> Unit,
    textFieldColors: TextFieldColors
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AddEditSectionHeader("STEP 3 · WHEN IS IT DUE? (OPTIONAL)")
        val nowMs = remember { System.currentTimeMillis() }
        val oneWeekMs = remember(nowMs) { nowMs + 7L * 86_400_000L }
        val oneMonthMs = remember(nowMs) { nowMs + 30L * 86_400_000L }
        val expDaysFromNow = expiryDateMillis?.let { ((it - nowMs) / 86_400_000L).toInt() }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MonochromeTogglePill(
                text = "In 1 week",
                selected = expDaysFromNow in 6..8,
                onClick = { onExpiryDateMillisChange(oneWeekMs) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("due_chip_1_week")
            )
            MonochromeTogglePill(
                text = "In 1 month",
                selected = expDaysFromNow in 28..31,
                onClick = { onExpiryDateMillisChange(oneMonthMs) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("due_chip_1_month")
            )
            MonochromeTogglePill(
                text = "Custom",
                selected = expiryDateMillis != null && expDaysFromNow !in 6..8 && expDaysFromNow !in 28..31,
                onClick = { onShowExpiryDatePickerChange(true) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("due_chip_custom")
            )
        }

        DateSelectorRow(
            label = "Due date",
            valueText = expiryDateMillis?.let { InterestCalculator.formatDate(it) } ?: "No due date (Open-ended)",
            onSelectClick = { onShowExpiryDatePickerChange(true) },
            onClearClick = expiryDateMillis?.let { { onExpiryDateMillisChange(null) } },
            testTag = "expiry_date_selector"
        )

        HorizontalDivider(color = BorderGrey, thickness = 1.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .heightIn(min = 48.dp)
                .background(SubtleSurfaceGrey, RoundedCornerShape(12.dp))
                .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                .clickable(onClick = onToggleMoreDetails)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("toggle_more_details_button"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ui_str_more_details_optional_ae1cdb),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PureBlack
                )
                Text(
                    text = stringResource(R.string.ui_str_payment_mode_interest_reminder_note_2fcbd3),
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryInkGrey
                )
            }
            Icon(
                imageVector = if (showMoreDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (showMoreDetails) stringResource(R.string.cd_collapse_details) else stringResource(R.string.cd_expand_details),
                tint = PureBlack
            )
        }

        AnimatedVisibility(visible = showMoreDetails) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.ui_str_payment_mode_8fbf2e),
                    style = MaterialTheme.typography.labelMedium,
                    color = SecondaryInkGrey
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaymentMode.entries.forEach { mode ->
                        val selected = paymentMode == mode
                        MonochromeTogglePill(
                            text = mode.label,
                            selected = selected,
                            onClick = { onPaymentModeChange(mode) },
                            modifier = Modifier.testTag("payment_mode_${mode.name.lowercase()}")
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .heightIn(min = 48.dp)
                        .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                        .background(SubtleSurfaceGrey, RoundedCornerShape(12.dp))
                        .clickable { onInterestEnabledChange(!isInterestEnabled) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.ui_str_charge_or_pay_interest_2e2435),
                            style = MaterialTheme.typography.titleSmall,
                            color = PureBlack
                        )
                        Text(
                            text = if (isInterestEnabled) {
                                stringResource(R.string.ui_str_enabled_accrues_from_start_date_d8af11)
                            } else {
                                stringResource(R.string.ui_fmt_interest_free_only_the_original_05c577, plainAmountLabel.lowercase())
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryInkGrey
                        )
                    }
                    UdhaarAnimatedSwitch(
                        checked = isInterestEnabled,
                        onCheckedChange = onInterestEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PureWhite,
                            checkedTrackColor = PureBlack,
                            uncheckedThumbColor = PureBlack,
                            uncheckedTrackColor = PureWhite,
                            uncheckedBorderColor = PureBlack
                        ),
                        modifier = Modifier.testTag("interest_applicable_switch")
                    )
                }

                AnimatedVisibility(visible = isInterestEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, PureBlack, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ui_str_interest_calculation_type_0d020c),
                            style = MaterialTheme.typography.labelMedium,
                            color = SecondaryInkGrey
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            InterestType.entries.forEach { type ->
                                MonochromeTogglePill(
                                    text = type.label,
                                    selected = interestType == type,
                                    onClick = { onInterestTypeChange(type) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("interest_type_${type.name.lowercase()}")
                                )
                            }
                        }
                        Text(
                            text = if (interestType == InterestType.SIMPLE) {
                                stringResource(R.string.ui_fmt_helper_simple_interest_is_calculate_fe6c26, plainAmountLabel.lowercase())
                            } else {
                                stringResource(R.string.ui_str_helper_compound_interest_adds_unpai_c21a66)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryInkGrey,
                            modifier = Modifier.testTag("interest_type_helper_text")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = interestRateText,
                                onValueChange = { input ->
                                    if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) {
                                        onInterestRateTextChange(input)
                                    }
                                },
                                label = { Text(stringResource(R.string.ui_str_rate_e25a28)) },
                                suffix = { Text(stringResource(R.string.ui_str_symbol_4345cb)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                colors = textFieldColors,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 56.dp)
                                    .testTag("interest_rate_input")
                            )

                            Column(
                                modifier = Modifier.weight(1.3f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                InterestRatePeriod.entries.forEach { period ->
                                    MonochromeTogglePill(
                                        text = period.label,
                                        selected = interestRatePeriod == period,
                                        onClick = { onInterestRatePeriodChange(period) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("rate_period_${period.name.lowercase()}")
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = interestType == InterestType.COMPOUND) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = stringResource(R.string.ui_str_compounding_frequency_bd416d),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SecondaryInkGrey
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CompoundFrequency.entries.forEach { freq ->
                                        MonochromeTogglePill(
                                            text = freq.label,
                                            selected = compoundFrequency == freq,
                                            onClick = { onCompoundFrequencyChange(freq) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("compound_freq_${freq.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                        }

                        DateSelectorRow(
                            label = "Start date (when interest begins)",
                            valueText = InterestCalculator.formatDate(startDateMillis),
                            onSelectClick = { onShowStartDatePickerChange(true) },
                            onClearClick = null,
                            testTag = "start_date_selector"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SubtleSurfaceGrey, RoundedCornerShape(12.dp))
                                .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(R.string.ui_str_live_summary_preview_e49ab4),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondaryInkGrey
                                )
                                Text(
                                    text = previewSummary.subtitleBreakdown,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PureBlack
                                )
                            }
                        }
                    }
                }

                DateSelectorRow(
                    label = "Scheduled reminder (Optional)",
                    valueText = reminderDateMillis?.let { InterestCalculator.formatDateTime(it) } ?: "No reminder scheduled",
                    icon = Icons.Default.NotificationsActive,
                    onSelectClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onShowReminderDatePickerChange(true)
                        }
                    },
                    onClearClick = reminderDateMillis?.let { { onReminderDateMillisChange(null) } },
                    testTag = "reminder_date_selector"
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    label = { Text(stringResource(R.string.ui_str_note_or_reason_optional_4213c0)) },
                    placeholder = { Text(stringResource(R.string.ui_str_e_g_shop_supplies_or_79c2a4)) },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag("notes_input")
                )

                AddEditAttachmentsSection(
                    reducedMotion = reducedMotion,
                    allCurrentAttachments = allCurrentAttachments,
                    onCameraClick = onCameraClick,
                    galleryLauncher = galleryLauncher,
                    documentLauncher = documentLauncher,
                    onOpenAttachmentViewer = onOpenAttachmentViewer,
                    onRemoveDraftAttachment = onRemoveDraftAttachment,
                    onDeleteExistingAttachment = onDeleteExistingAttachment
                )
            }
        }
    }

    AddEditDatePickerDialogs(
        context = context,
        startDateMillis = startDateMillis,
        onStartDateMillisChange = onStartDateMillisChange,
        expiryDateMillis = expiryDateMillis,
        onExpiryDateMillisChange = onExpiryDateMillisChange,
        reminderDateMillis = reminderDateMillis,
        onReminderDateMillisChange = onReminderDateMillisChange,
        showStartDatePicker = showStartDatePicker,
        onShowStartDatePickerChange = onShowStartDatePickerChange,
        showExpiryDatePicker = showExpiryDatePicker,
        onShowExpiryDatePickerChange = onShowExpiryDatePickerChange,
        showReminderDatePicker = showReminderDatePicker,
        onShowReminderDatePickerChange = onShowReminderDatePickerChange
    )
}
