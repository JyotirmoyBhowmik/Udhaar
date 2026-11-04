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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.ui.common.BreakdownLine
import com.jyotirmoyb.udhaar.ui.components.udhaarSharedBounds
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.InterestCalculator

@Composable
fun TransactionBalanceCard(
    summary: CalculatedUdhaarSummary,
    tx: UdhaarTransaction,
    animatedNetBalanceText: String,
    modifier: Modifier = Modifier
) {
    var showFullBreakdown by rememberSaveable { mutableStateOf(false) }
    val principalFormatted = InterestCalculator.formatCurrency(tx.principalAmount, tx.currency)
    val interestFormatted = InterestCalculator.formatCurrency(summary.accruedInterest, tx.currency)
    val paidFormatted = InterestCalculator.formatCurrency(tx.amountPaid, tx.currency)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .clickable { showFullBreakdown = !showFullBreakdown }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (tx.isSettled) {
                    stringResource(R.string.ui_str_settled_record_2977ef)
                } else {
                    stringResource(R.string.ui_str_net_remaining_due_3993cb)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = animatedNetBalanceText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.udhaarSharedBounds("tx_amount_${tx.id}")
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("toggle_balance_breakdown_button"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    R.string.detail_compact_summary_fmt,
                    principalFormatted,
                    interestFormatted,
                    paidFormatted
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(start = 6.dp)
            ) {
                Text(
                    text = stringResource(
                        if (showFullBreakdown) R.string.detail_hide_breakdown else R.string.detail_show_breakdown
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (showFullBreakdown) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        AnimatedVisibility(visible = showFullBreakdown) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
                BreakdownLine(
                    label = if (tx.transactionType == TransactionType.GIVEN) "Amount lent" else "Amount borrowed",
                    value = principalFormatted
                )
                BreakdownLine(
                    label = if (tx.isInterestEnabled) {
                        "${tx.interestType.label} (${tx.interestRate}${tx.interestRatePeriod.shortSuffix} • ${summary.elapsedDays}d)"
                    } else {
                        "Interest"
                    },
                    value = interestFormatted
                )
                BreakdownLine("Total with interest", InterestCalculator.formatCurrency(summary.grossTotal, tx.currency))
                BreakdownLine("Total paid / received", "- $paidFormatted")
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
        }
    }
}
