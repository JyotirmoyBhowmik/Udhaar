package com.jyotirmoyb.udhaar.ui.sheets.detail

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    Column(
        modifier = modifier
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
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.udhaarSharedBounds("tx_amount_${tx.id}")
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
        BreakdownLine(
            label = if (tx.transactionType == TransactionType.GIVEN) "Amount lent" else "Amount borrowed",
            value = InterestCalculator.formatCurrency(tx.principalAmount, tx.currency)
        )
        BreakdownLine(
            label = if (tx.isInterestEnabled) {
                "${tx.interestType.label} (${tx.interestRate}${tx.interestRatePeriod.shortSuffix} • ${summary.elapsedDays}d)"
            } else {
                "Interest"
            },
            value = InterestCalculator.formatCurrency(summary.accruedInterest, tx.currency)
        )
        BreakdownLine("Total with interest", InterestCalculator.formatCurrency(summary.grossTotal, tx.currency))
        BreakdownLine("Total paid / received", "- ${InterestCalculator.formatCurrency(tx.amountPaid, tx.currency)}")
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
