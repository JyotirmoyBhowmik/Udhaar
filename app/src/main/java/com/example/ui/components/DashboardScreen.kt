package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CurrencyType
import com.example.data.LedgerFilter
import com.example.data.TransactionType
import com.example.ui.CurrencyLedgerTotals
import com.example.ui.UdhaarUiState
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.MutedChipGrey
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SecondaryInkGrey
import com.example.ui.theme.SubtleSurfaceGrey
import com.example.util.CalculatedUdhaarSummary
import com.example.util.InterestCalculator

@Composable
fun DashboardScreen(
    uiState: UdhaarUiState,
    onSelectSummaryCurrency: (CurrencyType) -> Unit,
    onSelectFilter: (LedgerFilter) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onAddUdhaarClick: () -> Unit,
    onTransactionClick: (CalculatedUdhaarSummary) -> Unit,
    onWhatsAppClick: (CalculatedUdhaarSummary) -> Unit,
    onTransactionQrClick: (CalculatedUdhaarSummary) -> Unit,
    onGlobalQrClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = PureWhite,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PureWhite)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "UDHAAR",
                            style = MaterialTheme.typography.headlineLarge,
                            color = PureBlack
                        )
                        Text(
                            text = stringResource(R.string.tagline_monochrome_ledger),
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryInkGrey
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onGlobalQrClick,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("global_upi_qr_button"),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, PureBlack),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = "Global UPI QR Code",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("UPI QR", style = MaterialTheme.typography.labelMedium)
                        }

                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier
                                .size(48.dp)
                                .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
                                .testTag("open_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Profile and UPI Settings",
                                tint = PureBlack
                            )
                        }
                    }
                }
                HorizontalDivider(color = BorderGrey, thickness = 1.dp)
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddUdhaarClick,
                containerColor = PureBlack,
                contentColor = PureWhite,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .border(1.dp, PureWhite, RoundedCornerShape(4.dp))
                    .testTag("add_udhaar_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_udhaar_button)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "NEW UDHAAR",
                        style = MaterialTheme.typography.labelLarge,
                        color = PureWhite
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 700.dp)
                    .testTag("transactions_lazy_list"),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top Summary Card with INR | NPR Switcher
                item {
                    TopLedgerSummaryCard(
                        selectedCurrency = uiState.summaryCurrency,
                        totals = uiState.activeSummaryTotals,
                        inrCount = uiState.inrTotals.activeGivenCount + uiState.inrTotals.activeTakenCount,
                        nprCount = uiState.nprTotals.activeGivenCount + uiState.nprTotals.activeTakenCount,
                        onSelectCurrency = onSelectSummaryCurrency
                    )
                }

                // Search & Filter Controls
                item {
                    SearchAndFilterBar(
                        searchQuery = uiState.searchQuery,
                        selectedFilter = uiState.selectedFilter,
                        onSearchQueryChange = onSearchQueryChange,
                        onSelectFilter = onSelectFilter
                    )
                }

                // Section Header with record count
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LEDGER RECORDS (${uiState.filteredItems.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = SecondaryInkGrey
                        )
                        if (uiState.userSettings.upiId.isNotBlank()) {
                            Text(
                                text = "VPA: ${uiState.userSettings.upiId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryInkGrey
                            )
                        }
                    }
                }

                if (uiState.filteredItems.isEmpty()) {
                    item {
                        EmptyLedgerState(
                            hasAnyTransactions = uiState.allCalculatedItems.isNotEmpty(),
                            onAddClick = onAddUdhaarClick,
                            onResetFilter = {
                                onSelectFilter(LedgerFilter.ALL)
                                onSearchQueryChange("")
                            }
                        )
                    }
                } else {
                    items(
                        items = uiState.filteredItems,
                        key = { it.transaction.id }
                    ) { summary ->
                        TransactionCardItem(
                            summary = summary,
                            onCardClick = { onTransactionClick(summary) },
                            onWhatsAppClick = { onWhatsAppClick(summary) },
                            onQrClick = { onTransactionQrClick(summary) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopLedgerSummaryCard(
    selectedCurrency: CurrencyType,
    totals: CurrencyLedgerTotals,
    inrCount: Int,
    nprCount: Int,
    onSelectCurrency: (CurrencyType) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, PureBlack, RoundedCornerShape(4.dp))
            .background(PureWhite, RoundedCornerShape(4.dp))
            .testTag("top_summary_card")
    ) {
        // Currency Switcher Header Bar (INR ₹ | NPR रू)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SubtleSurfaceGrey)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CurrencyType.entries.forEach { curr ->
                val isSelected = selectedCurrency == curr
                val count = if (curr == CurrencyType.INR) inrCount else nprCount
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) PureBlack else BorderGrey,
                            shape = RoundedCornerShape(3.dp)
                        )
                        .background(
                            color = if (isSelected) PureBlack else PureWhite,
                            shape = RoundedCornerShape(3.dp)
                        )
                        .clickable { onSelectCurrency(curr) }
                        .testTag("summary_currency_tab_${curr.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${curr.displayName} • $count ACTIVE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) PureWhite else PureBlack
                    )
                }
            }
        }

        HorizontalDivider(color = PureBlack, thickness = 1.dp)

        // Split Monochrome Card: Left = Black (Total to Receive / Given), Right = White (Total to Pay / Taken)
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left Split: TOTAL TO RECEIVE (GIVEN)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(PureBlack)
                    .padding(16.dp)
            ) {
                Text(
                    text = "TOTAL TO RECEIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = BorderGrey
                )
                Text(
                    text = "GIVEN • ${totals.activeGivenCount} RECORDS",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryInkGrey
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = InterestCalculator.formatCurrency(totals.totalToReceive, selectedCurrency),
                    style = MaterialTheme.typography.headlineLarge,
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("summary_total_to_receive")
                )
            }

            // Right Split: TOTAL TO PAY (TAKEN)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(PureWhite)
                    .padding(16.dp)
            ) {
                Text(
                    text = "TOTAL TO PAY",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryInkGrey
                )
                Text(
                    text = "TAKEN • ${totals.activeTakenCount} RECORDS",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryInkGrey
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = InterestCalculator.formatCurrency(totals.totalToPay, selectedCurrency),
                    style = MaterialTheme.typography.headlineLarge,
                    color = PureBlack,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("summary_total_to_pay")
                )
            }
        }

        HorizontalDivider(color = BorderGrey, thickness = 1.dp)

        // Net Position Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SubtleSurfaceGrey)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NET ${selectedCurrency.code} LEDGER POSITION",
                style = MaterialTheme.typography.labelSmall,
                color = SecondaryInkGrey
            )
            val net = totals.netPosition
            val prefix = when {
                net > 0.005 -> "+ "
                net < -0.005 -> "- "
                else -> ""
            }
            Text(
                text = "$prefix${InterestCalculator.formatCurrency(kotlin.math.abs(net), selectedCurrency)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = PureBlack
            )
        }
    }
}

@Composable
private fun SearchAndFilterBar(
    searchQuery: String,
    selectedFilter: LedgerFilter,
    onSearchQueryChange: (String) -> Unit,
    onSelectFilter: (LedgerFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.search_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryInkGrey
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = PureBlack
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = PureBlack
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(4.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PureBlack,
                unfocusedBorderColor = BorderGrey,
                cursorColor = PureBlack,
                focusedContainerColor = PureWhite,
                unfocusedContainerColor = SubtleSurfaceGrey
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dashboard_search_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LedgerFilter.entries.forEach { filter ->
                val isSelected = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) PureBlack else BorderGrey,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .background(
                            color = if (isSelected) PureBlack else PureWhite,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { onSelectFilter(filter) }
                        .padding(horizontal = 14.dp)
                        .testTag("filter_chip_${filter.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) PureWhite else PureBlack
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionCardItem(
    summary: CalculatedUdhaarSummary,
    onCardClick: () -> Unit,
    onWhatsAppClick: () -> Unit,
    onQrClick: () -> Unit
) {
    val tx = summary.transaction
    val isGiven = tx.transactionType == TransactionType.GIVEN

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (summary.isOverdue) 1.5.dp else 1.dp,
                color = if (summary.isOverdue) PureBlack else BorderGrey,
                shape = RoundedCornerShape(4.dp)
            )
            .background(
                color = if (tx.isSettled) SubtleSurfaceGrey else PureWhite,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onCardClick)
            .padding(14.dp)
            .testTag("transaction_card_${tx.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top Row: Badges + Due Date Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // GIVEN vs TAKEN badge
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isGiven) PureBlack else PureWhite,
                            shape = RoundedCornerShape(2.dp)
                        )
                        .border(1.dp, PureBlack, RoundedCornerShape(2.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isGiven) "GIVEN" else "TAKEN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isGiven) PureWhite else PureBlack
                    )
                }

                // Payment Mode Badge
                Box(
                    modifier = Modifier
                        .background(MutedChipGrey, RoundedCornerShape(2.dp))
                        .border(0.5.dp, BorderGrey, RoundedCornerShape(2.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = tx.paymentMode.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = PureBlack
                    )
                }

                // Currency Badge
                Box(
                    modifier = Modifier
                        .background(MutedChipGrey, RoundedCornerShape(2.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = tx.currency.code,
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryInkGrey
                    )
                }
            }

            // Due Date / Overdue / Settled Status
            Box(
                modifier = Modifier
                    .then(
                        if (summary.isOverdue) {
                            Modifier
                                .background(PureBlack, RoundedCornerShape(2.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        } else {
                            Modifier
                        }
                    )
            ) {
                Text(
                    text = summary.dueStatusText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (summary.isOverdue || tx.isSettled) FontWeight.Bold else FontWeight.Normal,
                    color = if (summary.isOverdue) PureWhite else SecondaryInkGrey
                )
            }
        }

        // Main Row: Party Name + Main Total Amount
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.partyName,
                    style = MaterialTheme.typography.titleLarge,
                    color = PureBlack,
                    textDecoration = if (tx.isSettled) TextDecoration.LineThrough else TextDecoration.None
                )
                if (tx.phoneNumber.isNotBlank()) {
                    Text(
                        text = tx.phoneNumber,
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryInkGrey
                    )
                }
            }

            val displayAmount = if (tx.isSettled) summary.grossTotal else summary.netBalanceDue
            Text(
                text = InterestCalculator.formatCurrency(displayAmount, tx.currency),
                style = MaterialTheme.typography.headlineMedium,
                color = PureBlack,
                textDecoration = if (tx.isSettled) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier.testTag("tx_card_amount_${tx.id}")
            )
        }

        // Small-text calculated interest & breakdown subtitle below principal/total amount
        Text(
            text = summary.subtitleBreakdown,
            style = MaterialTheme.typography.bodySmall,
            color = SecondaryInkGrey,
            modifier = Modifier.testTag("tx_card_breakdown_${tx.id}")
        )

        if (tx.notes.isNotBlank()) {
            Text(
                text = "“${tx.notes}”",
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryInkGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        HorizontalDivider(color = BorderGrey, thickness = 1.dp)

        // Bottom Action Strip: Details / Settle, UPI QR (if INR Given), WhatsApp Remind
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (tx.isSettled) "TAP TO VIEW / REOPEN" else "TAP TO RECORD PAYMENT / SETTLE",
                style = MaterialTheme.typography.labelSmall,
                color = SecondaryInkGrey
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tx.currency == CurrencyType.INR && isGiven && !tx.isSettled) {
                    OutlinedButton(
                        onClick = onQrClick,
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("card_qr_button_${tx.id}"),
                        shape = RoundedCornerShape(3.dp),
                        border = BorderStroke(1.dp, PureBlack),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = "Generate UPI QR for ${tx.partyName}",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("QR", style = MaterialTheme.typography.labelSmall)
                    }
                }

                OutlinedButton(
                    onClick = onWhatsAppClick,
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("card_whatsapp_button_${tx.id}"),
                    shape = RoundedCornerShape(3.dp),
                    border = BorderStroke(1.dp, PureBlack),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = PureBlack,
                        contentColor = PureWhite
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send WhatsApp Reminder to ${tx.partyName}",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("REMIND", style = MaterialTheme.typography.labelSmall, color = PureWhite)
                }
            }
        }
    }
}

@Composable
private fun EmptyLedgerState(
    hasAnyTransactions: Boolean,
    onAddClick: () -> Unit,
    onResetFilter: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
            .background(SubtleSurfaceGrey, RoundedCornerShape(4.dp))
            .padding(28.dp)
            .testTag("empty_ledger_box"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (hasAnyTransactions) "NO MATCHING RECORDS" else "NO UDHAAR RECORDS YET",
            style = MaterialTheme.typography.titleMedium,
            color = PureBlack
        )
        Text(
            text = if (hasAnyTransactions) {
                "No transactions match your current filter or search query."
            } else {
                stringResource(R.string.empty_ledger_subtitle)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryInkGrey,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (hasAnyTransactions) {
            OutlinedButton(
                onClick = onResetFilter,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, PureBlack),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
            ) {
                Text("CLEAR FILTERS", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PureBlack,
                    contentColor = PureWhite
                ),
                modifier = Modifier.testTag("empty_state_add_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("CREATE FIRST UDHAAR", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
