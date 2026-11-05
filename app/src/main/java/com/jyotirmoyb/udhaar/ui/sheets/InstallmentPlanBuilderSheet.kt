package com.jyotirmoyb.udhaar.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.InstallmentFrequency
import com.jyotirmoyb.udhaar.data.InstallmentInterestMode
import com.jyotirmoyb.udhaar.data.InstallmentPlan
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.util.InstallmentCalculator
import com.jyotirmoyb.udhaar.util.InstallmentDraft
import com.jyotirmoyb.udhaar.util.InterestCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallmentPlanBuilderSheet(
    tx: UdhaarTransaction,
    existingPlan: InstallmentPlan? = null,
    existingInstallments: List<com.jyotirmoyb.udhaar.data.Installment> = emptyList(),
    isProposalMode: Boolean = false,
    onProposeNewDates: ((List<InstallmentDraft>) -> Unit)? = null,
    onSavePlan: (plan: InstallmentPlan, drafts: List<InstallmentDraft>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var count by remember { mutableIntStateOf(existingPlan?.installmentCount ?: 3) }
    var frequency by remember { mutableStateOf(existingPlan?.frequency ?: InstallmentFrequency.MONTHLY) }
    var interestMode by remember { mutableStateOf(existingPlan?.interestMode ?: InstallmentInterestMode.NONE) }
    var flatRateText by remember { mutableStateOf(if ((existingPlan?.flatInterestRate ?: 0.0) > 0.0) existingPlan!!.flatInterestRate.toString() else "0") }
    var startDateMillis by remember { mutableStateOf(existingPlan?.startDate ?: (tx.startDateMillis.takeIf { it > 0 } ?: System.currentTimeMillis())) }

    val principalPaise = remember(tx.principalAmountPaise) { tx.principalAmountPaise }
    val flatRate = flatRateText.toDoubleOrNull() ?: 0.0

    val totalExpectedPaise = remember(principalPaise, interestMode, flatRate) {
        InstallmentCalculator.calculateTotalAmountWithInterest(principalPaise, interestMode, flatRate)
    }

    val drafts = remember { mutableStateListOf<InstallmentDraft>() }
    var initializedFromExisting by remember { mutableStateOf(false) }

    fun recalculateEqualSplit() {
        val generated = InstallmentCalculator.generateEqualSplit(
            totalPaise = totalExpectedPaise,
            count = count,
            startDateMillis = startDateMillis,
            frequency = frequency
        )
        drafts.clear()
        drafts.addAll(generated)
    }

    LaunchedEffect(count, frequency, totalExpectedPaise, startDateMillis) {
        if (!initializedFromExisting && existingPlan != null && existingInstallments.isNotEmpty() &&
            count == existingPlan.installmentCount &&
            frequency == existingPlan.frequency &&
            totalExpectedPaise == existingPlan.totalAmountDue &&
            startDateMillis == existingPlan.startDate
        ) {
            drafts.clear()
            drafts.addAll(
                existingInstallments.sortedBy { it.sequence }.map {
                    InstallmentDraft(
                        sequence = it.sequence,
                        dueDateMillis = it.dueDate,
                        amountDuePaise = it.amountDue
                    )
                }
            )
            initializedFromExisting = true
        } else {
            initializedFromExisting = true
            recalculateEqualSplit()
        }
    }

    var showDatePickerForIndex by remember { mutableStateOf<Int?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        if (isProposalMode) R.string.propose_schedule_title else R.string.split_into_installments
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // AutoPay Help Disclaimer
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.no_fake_autopay_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    // Installment Count & Frequency row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = count.toString(),
                            onValueChange = { str ->
                                val parsed = str.filter { it.isDigit() }.toIntOrNull()
                                if (parsed != null && parsed in 1..48) {
                                    count = parsed
                                }
                            },
                            label = { Text(stringResource(R.string.installment_count_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )

                        // Start date button
                        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                        OutlinedButton(
                            onClick = { showDatePickerForIndex = -1 },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(sdf.format(Date(startDateMillis)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                item {
                    // Frequency chips
                    Text(
                        text = stringResource(R.string.installment_frequency_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        InstallmentFrequency.values().forEach { freq ->
                            val freqLabelRes = when (freq) {
                                InstallmentFrequency.WEEKLY -> R.string.frequency_weekly
                                InstallmentFrequency.EVERY_2_WEEKS -> R.string.frequency_every_2_weeks
                                InstallmentFrequency.MONTHLY -> R.string.frequency_monthly
                                InstallmentFrequency.CUSTOM -> R.string.frequency_custom
                            }
                            FilterChip(
                                selected = frequency == freq,
                                onClick = { frequency = freq },
                                label = { Text(stringResource(freqLabelRes), style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors()
                            )
                        }
                    }
                }

                item {
                    // Interest mode toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.interest_mode_label),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = interestMode == InstallmentInterestMode.NONE,
                                onClick = { interestMode = InstallmentInterestMode.NONE },
                                label = { Text(stringResource(R.string.interest_none)) }
                            )
                            FilterChip(
                                selected = interestMode == InstallmentInterestMode.FLAT,
                                onClick = { interestMode = InstallmentInterestMode.FLAT },
                                label = { Text(stringResource(R.string.interest_flat)) }
                            )
                        }
                    }
                    if (interestMode == InstallmentInterestMode.FLAT) {
                        OutlinedTextField(
                            value = flatRateText,
                            onValueChange = { flatRateText = it },
                            label = { Text(stringResource(R.string.flat_interest_rate_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    val totalFormatted = "${tx.currency.symbol}${InterestCalculator.formatAmount(InstallmentCalculator.paiseToDouble(totalExpectedPaise))}"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.installment_total_label), fontWeight = FontWeight.Bold)
                        Text(totalFormatted, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        stringResource(R.string.equal_split_info),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Text(
                        stringResource(R.string.manual_edit_preview),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Table of drafts
                items(drafts.toList(), key = { it.sequence }) { draft ->
                    val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    val idx = draft.sequence - 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(R.string.installment_seq_fmt, draft.sequence), fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))

                        // Clickable date
                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .clickable { showDatePickerForIndex = idx }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(sdf.format(Date(draft.dueDateMillis)), style = MaterialTheme.typography.bodySmall)
                        }

                        // Amount field
                        var amountInput by remember(draft.amountDuePaise) {
                            mutableStateOf(InterestCalculator.formatPlainAmount(InstallmentCalculator.paiseToDouble(draft.amountDuePaise)))
                        }
                        OutlinedTextField(
                            value = amountInput,
                            onValueChange = { newVal ->
                                amountInput = newVal
                                val parsed = newVal.toDoubleOrNull()
                                if (parsed != null && parsed >= 0) {
                                    draft.amountDuePaise = InstallmentCalculator.toPaise(parsed)
                                }
                            },
                            prefix = { Text(tx.currency.symbol) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }

                // Mismatch warning
                val sumPaise = drafts.sumOf { it.amountDuePaise }
                val isMismatch = sumPaise != totalExpectedPaise
                if (isMismatch) {
                    item {
                        val sumFormatted = "${tx.currency.symbol}${InterestCalculator.formatAmount(InstallmentCalculator.paiseToDouble(sumPaise))}"
                        val expectedFormatted = "${tx.currency.symbol}${InterestCalculator.formatAmount(InstallmentCalculator.paiseToDouble(totalExpectedPaise))}"
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Text(
                                    text = stringResource(R.string.installment_sum_mismatch_warning, sumFormatted, expectedFormatted),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cancel_installment_plan))
                }

                val sumPaise = drafts.sumOf { it.amountDuePaise }
                val isValid = drafts.isNotEmpty() && sumPaise == totalExpectedPaise

                Button(
                    onClick = {
                        if (isProposalMode && onProposeNewDates != null) {
                            onProposeNewDates(drafts.toList())
                            onDismiss()
                        } else {
                            val plan = InstallmentPlan(
                                transactionId = tx.id,
                                installmentCount = drafts.size,
                                frequency = frequency,
                                startDate = startDateMillis,
                                interestMode = interestMode,
                                flatInterestRate = flatRate,
                                totalAmountDue = totalExpectedPaise
                            )
                            onSavePlan(plan, drafts.toList())
                        }
                    },
                    enabled = isValid,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_installment_plan_button")
                ) {
                    Text(
                        stringResource(
                            if (isProposalMode) R.string.propose_schedule_btn else R.string.save_installment_plan
                        )
                    )
                }
            }
        }
    }

    // Date picker dialog
    showDatePickerForIndex?.let { targetIdx ->
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (targetIdx == -1) startDateMillis else drafts.getOrNull(targetIdx)?.dueDateMillis ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerForIndex = null },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selected ->
                        if (targetIdx == -1) {
                            startDateMillis = selected
                        } else if (targetIdx in drafts.indices) {
                            drafts[targetIdx].dueDateMillis = selected
                        }
                    }
                    showDatePickerForIndex = null
                }) {
                    Text(stringResource(R.string.ui_str_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerForIndex = null }) {
                    Text(stringResource(R.string.ui_str_cancel_1507c4))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
