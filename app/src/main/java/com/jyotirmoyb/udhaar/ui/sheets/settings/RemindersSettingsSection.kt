package com.jyotirmoyb.udhaar.ui.sheets.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.ui.common.SettingsSectionCard
import com.jyotirmoyb.udhaar.ui.components.UdhaarAnimatedSwitch

@Composable
fun RemindersSettingsSection(
    quietHoursEnabled: Boolean,
    onQuietHoursEnabledChange: (Boolean) -> Unit,
    quietHoursStartHour: Int,
    onQuietHoursStartHourChange: (Int) -> Unit,
    quietHoursEndHour: Int,
    onQuietHoursEndHourChange: (Int) -> Unit,
    reminderL1Days: Int,
    onReminderL1DaysChange: (Int) -> Unit,
    reminderL2Days: Int,
    onReminderL2DaysChange: (Int) -> Unit,
    reminderL3Days: Int,
    onReminderL3DaysChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSectionCard(
        sectionNumber = "",
        title = stringResource(R.string.quiet_hours_settings_title),
        subtitle = stringResource(R.string.quiet_hours_settings_subtitle),
        testTag = "section_reminders_and_quiet_hours",
        initiallyExpanded = false
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.quiet_hours_settings_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            UdhaarAnimatedSwitch(
                checked = quietHoursEnabled,
                onCheckedChange = onQuietHoursEnabledChange,
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
                            onQuietHoursStartHourChange(it.coerceIn(0, 23))
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
                            onQuietHoursEndHourChange(it.coerceIn(0, 23))
                        }
                    },
                    label = { Text(stringResource(R.string.quiet_hours_end_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("quiet_hours_end_input")
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

        Text(
            text = stringResource(R.string.escalation_ranges_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = stringResource(R.string.escalation_ranges_subtitle),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = reminderL1Days.toString(),
                onValueChange = { str ->
                    str.filter { it.isDigit() }.toIntOrNull()?.let {
                        onReminderL1DaysChange(it.coerceIn(1, 30))
                    }
                },
                label = { Text(stringResource(R.string.escalation_l1_days), maxLines = 1) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("escalation_l1_input")
            )

            OutlinedTextField(
                value = reminderL2Days.toString(),
                onValueChange = { str ->
                    str.filter { it.isDigit() }.toIntOrNull()?.let {
                        onReminderL2DaysChange(it.coerceIn(1, 60))
                    }
                },
                label = { Text(stringResource(R.string.escalation_l2_days), maxLines = 1) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("escalation_l2_input")
            )

            OutlinedTextField(
                value = reminderL3Days.toString(),
                onValueChange = { str ->
                    str.filter { it.isDigit() }.toIntOrNull()?.let {
                        onReminderL3DaysChange(it.coerceIn(1, 180))
                    }
                },
                label = { Text(stringResource(R.string.escalation_l3_days), maxLines = 1) },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("escalation_l3_input")
            )
        }
    }
}
