package com.jyotirmoyb.udhaar.ui.sheets.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.AuditTrailEntry
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily
import com.jyotirmoyb.udhaar.util.InterestCalculator

@Composable
fun VerificationAuditTrailSection(
    tx: UdhaarTransaction,
    auditTrail: List<AuditTrailEntry>,
    modifier: Modifier = Modifier
) {
    var showVerificationDetails by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
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
                text = if (showVerificationDetails) {
                    stringResource(R.string.ui_str_hide_34d8b6)
                } else {
                    stringResource(R.string.ui_str_show_d97d1e)
                },
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
                        fontFamily = IbmPlexMonoFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (tx.remoteId.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.ui_fmt_cloud_id_b63185, tx.remoteId),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = IbmPlexMonoFamily,
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
                                        fontFamily = IbmPlexMonoFamily,
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
}
