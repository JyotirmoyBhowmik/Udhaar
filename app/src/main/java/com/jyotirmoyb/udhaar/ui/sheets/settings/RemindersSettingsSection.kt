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
                    onReminderL1DaysChange(it.coerceIn(1, 30))
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
                    onReminderL2DaysChange(it.coerceIn(1, 60))
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
                    onReminderL3DaysChange(it.coerceIn(1, 180))
                }
            },
            label = { Text(stringResource(R.string.escalation_l3_days)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("escalation_l3_input")
        )
    }
}
