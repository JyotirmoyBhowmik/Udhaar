package com.jyotirmoyb.udhaar.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.LedgerFilter
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.VerificationStatus
import com.jyotirmoyb.udhaar.ui.ActiveScreen
import com.jyotirmoyb.udhaar.ui.CurrencyLedgerTotals
import com.jyotirmoyb.udhaar.ui.PartyKhataSummary
import com.jyotirmoyb.udhaar.ui.PartySortOption
import com.jyotirmoyb.udhaar.ui.UdhaarUiState
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.MutedChipGrey
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.ui.theme.SubtleSurfaceGrey
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.InterestCalculator

@Composable
fun DashboardScreen(
    uiState: UdhaarUiState,
    onSwitchTab: (ActiveScreen) -> Unit,
    onSelectSummaryCurrency: (CurrencyType) -> Unit,
    onToggleCurrencyEnabled: (CurrencyType, Boolean) -> Unit,
    onCycleTheme: () -> Unit,
    onSelectFilter: (LedgerFilter) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onPeopleSearchQueryChange: (String) -> Unit,
    onAddUdhaarClick: () -> Unit,
    onAddPartyClick: () -> Unit,
    onPartyClick: (PartyKhataSummary) -> Unit,
    onQuickAddLoanForParty: (PartyEntity) -> Unit,
    onTransactionClick: (CalculatedUdhaarSummary) -> Unit,
    onWhatsAppClick: (CalculatedUdhaarSummary) -> Unit,
    onTransactionQrClick: (CalculatedUdhaarSummary) -> Unit,
    onGlobalQrClick: () -> Unit,
    onShareAppClick: () -> Unit,
    onLockAppClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onShareMyPaymentDetailsClick: () -> Unit = {},
    onOpenTransactionChat: (CalculatedUdhaarSummary) -> Unit = {},
    onOpenPartyGeneralChat: (PartyEntity) -> Unit = {},
    onPartySortOptionChange: (PartySortOption) -> Unit = {},
    onOpenMergeDuplicates: () -> Unit = {},
    onOpenContactPicker: () -> Unit = {},
    onToggleFavoriteParty: (PartyEntity) -> Unit = {}
) {
    val isPeopleTab = uiState.activeScreen == ActiveScreen.PEOPLE_DIRECTORY
    val enabledCurrencies = uiState.userSettings.enabledCurrencies
    var showMoreMenu by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = PureWhite,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PureWhite)
                    .statusBarsPadding()
                    .padding(top = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = "UDHAAR",
                            style = MaterialTheme.typography.headlineMedium,
                            color = PureBlack,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${uiState.userSettings.themeMode.label.uppercase()} • ${enabledCurrencies.joinToString("/") { it.code }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryInkGrey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Labeled Lock Button in Top Bar
                        OutlinedButton(
                            onClick = onLockAppClick,
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("lock_app_top_button"),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, BorderGrey),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = PureWhite,
                                contentColor = PureBlack
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock Udhaar App",
                                tint = PureBlack,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Lock",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PureBlack,
                                maxLines = 1
                            )
                        }

                        // Labeled Settings Button in Top Bar
                        OutlinedButton(
                            onClick = onSettingsClick,
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("open_settings_button"),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, PureBlack),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = PureWhite,
                                contentColor = PureBlack
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = "Settings",
                                tint = PureBlack,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PureBlack,
                                maxLines = 1
                            )
                        }

                        // "More" Menu grouping QR, Theme, and Share
                        Box {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier
                                    .size(38.dp)
                                    .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
                                    .testTag("more_options_top_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options (UPI QR, Theme, Share)",
                                    tint = PureBlack,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                                modifier = Modifier
                                    .background(PureWhite)
                                    .border(1.dp, PureBlack, RoundedCornerShape(4.dp))
                            ) {
                                if (uiState.userSettings.inrEnabled) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Receive via UPI QR",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = PureBlack
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.QrCode2,
                                                contentDescription = null,
                                                tint = PureBlack
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            onGlobalQrClick()
                                        },
                                        modifier = Modifier.testTag("global_upi_qr_button")
                                    )
                                }

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Switch Theme (${uiState.userSettings.themeMode.label})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = PureBlack
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Palette,
                                            contentDescription = null,
                                            tint = PureBlack
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        onCycleTheme()
                                    },
                                    modifier = Modifier.testTag("cycle_theme_top_button")
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Share My Payment Details",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = PureBlack
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.QrCode2,
                                            contentDescription = null,
                                            tint = PureBlack
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        onShareMyPaymentDetailsClick()
                                    },
                                    modifier = Modifier.testTag("share_payment_profile_top_button")
                                )

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Share Udhaar App",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = PureBlack
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            tint = PureBlack
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        onShareAppClick()
                                    },
                                    modifier = Modifier.testTag("share_app_top_button")
                                )
                            }
                        }
                    }
                }

                // Navigation Switcher: LEDGER vs PEOPLE DIRECTORY (KHATA)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isLedger = uiState.activeScreen == ActiveScreen.DASHBOARD
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .border(
                                width = if (isLedger) 1.5.dp else 1.dp,
                                color = if (isLedger) PureBlack else BorderGrey,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .background(
                                color = if (isLedger) PureBlack else PureWhite,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { onSwitchTab(ActiveScreen.DASHBOARD) }
                            .padding(horizontal = 8.dp)
                            .testTag("nav_tab_ledger"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                tint = if (isLedger) PureWhite else PureBlack,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "LEDGER (${uiState.allCalculatedItems.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isLedger) PureWhite else PureBlack,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .border(
                                width = if (isPeopleTab) 1.5.dp else 1.dp,
                                color = if (isPeopleTab) PureBlack else BorderGrey,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .background(
                                color = if (isPeopleTab) PureBlack else PureWhite,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .clickable { onSwitchTab(ActiveScreen.PEOPLE_DIRECTORY) }
                            .padding(horizontal = 8.dp)
                            .testTag("nav_tab_people_directory"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = if (isPeopleTab) PureWhite else PureBlack,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "PEOPLE (${uiState.partyKhataList.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isPeopleTab) PureWhite else PureBlack,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = BorderGrey, thickness = 1.dp)
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = if (isPeopleTab) onAddPartyClick else onAddUdhaarClick,
                containerColor = PureBlack,
                contentColor = PureWhite,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .border(1.dp, PureWhite, RoundedCornerShape(4.dp))
                    .testTag("add_udhaar_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isPeopleTab) Icons.Default.PersonAdd else Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_udhaar_button),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPeopleTab) "NEW CONTACT" else "NEW UDHAAR",
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
            if (isPeopleTab) {
                PeopleDirectoryContent(
                    partyKhataList = uiState.filteredPartyKhataList,
                    enabledCurrencies = enabledCurrencies,
                    searchQuery = uiState.peopleSearchQuery,
                    onSearchQueryChange = onPeopleSearchQueryChange,
                    onAddPartyClick = onAddPartyClick,
                    onPartyClick = onPartyClick,
                    onQuickAddLoanForParty = onQuickAddLoanForParty,
                    unreadCountByPartyName = uiState.unreadCountByPartyName,
                    onOpenPartyGeneralChat = onOpenPartyGeneralChat,
                    partySortOption = uiState.partySortOption,
                    onSortOptionChange = onPartySortOptionChange,
                    duplicatePairsCount = uiState.duplicatePartyPairs.size,
                    onOpenMergeDuplicates = onOpenMergeDuplicates,
                    onOpenContactPicker = onOpenContactPicker,
                    onToggleFavorite = onToggleFavoriteParty
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 700.dp)
                        .testTag("transactions_lazy_list"),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 12.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        TopLedgerSummaryCard(
                            selectedCurrency = uiState.summaryCurrency,
                            enabledCurrencies = enabledCurrencies,
                            totals = uiState.activeSummaryTotals,
                            inrCount = uiState.inrTotals.activeGivenCount + uiState.inrTotals.activeTakenCount,
                            nprCount = uiState.nprTotals.activeGivenCount + uiState.nprTotals.activeTakenCount,
                            onSelectCurrency = onSelectSummaryCurrency
                        )
                    }

                    item {
                        SearchAndFilterBar(
                            searchQuery = uiState.searchQuery,
                            selectedFilter = uiState.selectedFilter,
                            enabledCurrencies = enabledCurrencies,
                            allCalculatedItems = uiState.allCalculatedItems,
                            partialPaymentsByTx = uiState.partialPaymentsByTx,
                            onSearchQueryChange = onSearchQueryChange,
                            onSelectFilter = onSelectFilter
                        )
                    }

                    item {
                        val verifiedCount = uiState.allCalculatedItems.count {
                            it.transaction.verificationStatus == VerificationStatus.CONFIRMED
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LEDGER RECORDS (${uiState.filteredItems.size}) • $verifiedCount VERIFIED ✓",
                                style = MaterialTheme.typography.labelMedium,
                                color = SecondaryInkGrey
                            )
                            if (uiState.userSettings.upiId.isNotBlank() && uiState.userSettings.inrEnabled) {
                                Text(
                                    text = "VPA: ${uiState.userSettings.upiId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondaryInkGrey,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
                                unreadCount = uiState.unreadCountByTx[summary.transaction.id] ?: 0,
                                attachmentsCount = uiState.attachmentsByTx[summary.transaction.id]?.size ?: 0,
                                onCardClick = { onTransactionClick(summary) },
                                onChatClick = { onOpenTransactionChat(summary) },
                                onWhatsAppClick = { onWhatsAppClick(summary) },
                                onQrClick = { onTransactionQrClick(summary) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopLedgerSummaryCard(
    selectedCurrency: CurrencyType,
    enabledCurrencies: List<CurrencyType>,
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
        // Currency tabs shown only for currencies enabled in Settings (no NPR enable button on home screen)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SubtleSurfaceGrey)
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            enabledCurrencies.forEach { curr ->
                val isSelected = selectedCurrency == curr
                val count = if (curr == CurrencyType.INR) inrCount else nprCount
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
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
                        .padding(horizontal = 8.dp)
                        .testTag("summary_currency_tab_${curr.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${curr.displayName} • $count ACTIVE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) PureWhite else PureBlack,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        HorizontalDivider(color = PureBlack, thickness = 1.dp)

        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(PureBlack)
                    .padding(12.dp)
            ) {
                Text(
                    text = "TOTAL TO RECEIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = BorderGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "GIVEN • ${totals.activeGivenCount} RECORDS",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryInkGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = InterestCalculator.formatCurrency(totals.totalToReceive, selectedCurrency),
                    style = MaterialTheme.typography.headlineMedium,
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("summary_total_to_receive")
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(PureWhite)
                    .padding(12.dp)
            ) {
                Text(
                    text = "TOTAL TO PAY",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryInkGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "TAKEN • ${totals.activeTakenCount} RECORDS",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryInkGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = InterestCalculator.formatCurrency(totals.totalToPay, selectedCurrency),
                    style = MaterialTheme.typography.headlineMedium,
                    color = PureBlack,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("summary_total_to_pay")
                )
            }
        }

        HorizontalDivider(color = BorderGrey, thickness = 1.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SubtleSurfaceGrey)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NET ${selectedCurrency.code} LEDGER POSITION",
                style = MaterialTheme.typography.labelSmall,
                color = SecondaryInkGrey,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
                color = PureBlack,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SearchAndFilterBar(
    searchQuery: String,
    selectedFilter: LedgerFilter,
    enabledCurrencies: List<CurrencyType>,
    allCalculatedItems: List<CalculatedUdhaarSummary>,
    partialPaymentsByTx: Map<Long, List<PartialPayment>>,
    onSearchQueryChange: (String) -> Unit,
    onSelectFilter: (LedgerFilter) -> Unit
) {
    val visibleFilters = LedgerFilter.entries.filter { filter ->
        when (filter) {
            LedgerFilter.INR -> enabledCurrencies.size > 1 && CurrencyType.INR in enabledCurrencies
            LedgerFilter.NPR -> enabledCurrencies.size > 1 && CurrencyType.NPR in enabledCurrencies
            else -> true
        }
    }

    val filterCounts = remember(allCalculatedItems, partialPaymentsByTx) {
        LedgerFilter.entries.associateWith { filter ->
            when (filter) {
                LedgerFilter.ALL -> allCalculatedItems.size
                LedgerFilter.GIVEN -> allCalculatedItems.count {
                    it.transaction.transactionType == TransactionType.GIVEN && !it.transaction.isSettled
                }
                LedgerFilter.TAKEN -> allCalculatedItems.count {
                    it.transaction.transactionType == TransactionType.TAKEN && !it.transaction.isSettled
                }
                LedgerFilter.NEEDS_CONFIRMATION -> allCalculatedItems.count { item ->
                    val tx = item.transaction
                    val txPayments = partialPaymentsByTx[tx.id].orEmpty()
                    !tx.isSettled && (
                        tx.needsMyConfirmation ||
                            tx.verificationStatus == VerificationStatus.EDITED_AFTER_CONFIRM ||
                            tx.verificationStatus == VerificationStatus.PENDING ||
                            txPayments.any { it.needsMyAcknowledgement || !it.isAcknowledged }
                        )
                }
                LedgerFilter.OVERDUE -> allCalculatedItems.count {
                    it.isOverdue && !it.transaction.isSettled
                }
                LedgerFilter.INR -> allCalculatedItems.count {
                    it.transaction.currency == CurrencyType.INR
                }
                LedgerFilter.NPR -> allCalculatedItems.count {
                    it.transaction.currency == CurrencyType.NPR
                }
                LedgerFilter.SETTLED -> allCalculatedItems.count {
                    it.transaction.isSettled
                }
            }
        }
    }

    val lazyRowState = rememberLazyListState()
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = lazyRowState)
    val canScrollLeft = lazyRowState.canScrollBackward
    val canScrollRight = lazyRowState.canScrollForward

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.search_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryInkGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = PureBlack,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = PureBlack,
                            modifier = Modifier.size(18.dp)
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

        LazyRow(
            state = lazyRowState,
            flingBehavior = snapFlingBehavior,
            contentPadding = PaddingValues(start = 2.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    val fadeWidthPx = 24.dp.toPx()
                    if (canScrollLeft && size.width > fadeWidthPx * 2) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startX = 0f,
                                endX = fadeWidthPx
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                    if (canScrollRight && size.width > fadeWidthPx * 2) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startX = size.width - fadeWidthPx,
                                endX = size.width
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
                .testTag("filter_chips_lazy_row")
        ) {
            items(
                items = visibleFilters,
                key = { it.name }
            ) { filter ->
                val isSelected = selectedFilter == filter
                val count = filterCounts[filter] ?: 0
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) PureBlack else BorderGrey,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .background(
                            color = if (isSelected) PureBlack else PureWhite,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { onSelectFilter(filter) }
                        .padding(horizontal = 10.dp)
                        .testTag("filter_chip_${filter.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) PureWhite else PureBlack,
                            maxLines = 1
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isSelected) PureWhite else SubtleSurfaceGrey,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .border(
                                    width = 0.5.dp,
                                    color = if (isSelected) PureWhite else BorderGrey,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                                .testTag("filter_badge_${filter.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = PureBlack,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionCardItem(
    summary: CalculatedUdhaarSummary,
    unreadCount: Int = 0,
    attachmentsCount: Int = 0,
    onCardClick: () -> Unit,
    onChatClick: () -> Unit = {},
    onWhatsAppClick: () -> Unit,
    onQrClick: () -> Unit
) {
    val tx = summary.transaction
    val isGiven = tx.transactionType == TransactionType.GIVEN

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (summary.isOverdue || unreadCount > 0) 1.5.dp else 1.dp,
                color = if (summary.isOverdue || unreadCount > 0) PureBlack else BorderGrey,
                shape = RoundedCornerShape(4.dp)
            )
            .background(
                color = if (tx.isSettled) SubtleSurfaceGrey else PureWhite,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onCardClick)
            .padding(12.dp)
            .testTag("transaction_card_${tx.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(end = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isGiven) PureBlack else PureWhite,
                            shape = RoundedCornerShape(2.dp)
                        )
                        .border(1.dp, PureBlack, RoundedCornerShape(2.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isGiven) "GIVEN" else "TAKEN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isGiven) PureWhite else PureBlack
                    )
                }

                Box(
                    modifier = Modifier
                        .background(MutedChipGrey, RoundedCornerShape(2.dp))
                        .border(0.5.dp, BorderGrey, RoundedCornerShape(2.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = tx.paymentMode.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = PureBlack
                    )
                }

                Box(
                    modifier = Modifier
                        .background(MutedChipGrey, RoundedCornerShape(2.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = tx.currency.code,
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryInkGrey
                    )
                }

                val isVerified = tx.verificationStatus == VerificationStatus.CONFIRMED
                val verBadgeText = if (tx.needsMyConfirmation) {
                    "CONFIRM?"
                } else {
                    tx.verificationStatus.shortBadge
                }
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isVerified) PureBlack else SubtleSurfaceGrey,
                            shape = RoundedCornerShape(2.dp)
                        )
                        .border(
                            width = if (isVerified || tx.verificationStatus != VerificationStatus.LOCAL_ONLY) 1.dp else 0.5.dp,
                            color = if (isVerified) PureBlack else BorderGrey,
                            shape = RoundedCornerShape(2.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                        .testTag("tx_verification_badge_${tx.id}")
                ) {
                    Text(
                        text = verBadgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isVerified || tx.needsMyConfirmation) FontWeight.Bold else FontWeight.Medium,
                        color = if (isVerified) PureWhite else PureBlack,
                        maxLines = 1
                    )
                }

                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .background(PureBlack, RoundedCornerShape(2.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                            .testTag("tx_unread_badge_${tx.id}")
                    ) {
                        Text(
                            text = "💬 $unreadCount NEW",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite,
                            maxLines = 1
                        )
                    }
                }

                if (attachmentsCount > 0) {
                    Box(
                        modifier = Modifier
                            .background(SubtleSurfaceGrey, RoundedCornerShape(2.dp))
                            .border(0.5.dp, BorderGrey, RoundedCornerShape(2.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                            .testTag("tx_attachments_badge_${tx.id}")
                    ) {
                        Text(
                            text = "📎 $attachmentsCount",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PureBlack,
                            maxLines = 1
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .then(
                        if (summary.isOverdue) {
                            Modifier
                                .background(PureBlack, RoundedCornerShape(2.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        } else {
                            Modifier
                        }
                    )
            ) {
                Text(
                    text = summary.dueStatusText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (summary.isOverdue || tx.isSettled) FontWeight.Bold else FontWeight.Normal,
                    color = if (summary.isOverdue) PureWhite else SecondaryInkGrey,
                    maxLines = 1
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = tx.partyName,
                    style = MaterialTheme.typography.titleLarge,
                    color = PureBlack,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (tx.isSettled) TextDecoration.LineThrough else TextDecoration.None
                )
                if (tx.phoneNumber.isNotBlank()) {
                    Text(
                        text = tx.phoneNumber,
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryInkGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val displayAmount = if (tx.isSettled) summary.grossTotal else summary.netBalanceDue
            Text(
                text = InterestCalculator.formatCurrency(displayAmount, tx.currency),
                style = MaterialTheme.typography.headlineMedium,
                color = PureBlack,
                maxLines = 1,
                textDecoration = if (tx.isSettled) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier.testTag("tx_card_amount_${tx.id}")
            )
        }

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (tx.isSettled) "TAP FOR RECEIPT" else "TAP FOR REPAYMENT / RECEIPT",
                style = MaterialTheme.typography.labelSmall,
                color = SecondaryInkGrey,
                modifier = Modifier.weight(1f).padding(end = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onChatClick,
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("card_chat_button_${tx.id}"),
                    shape = RoundedCornerShape(3.dp),
                    border = BorderStroke(1.dp, PureBlack),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (unreadCount > 0) PureBlack else PureWhite,
                        contentColor = if (unreadCount > 0) PureWhite else PureBlack
                    )
                ) {
                    Text(
                        text = if (unreadCount > 0) "CHAT ($unreadCount)" else "CHAT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (tx.currency == CurrencyType.INR && isGiven && !tx.isSettled) {
                    OutlinedButton(
                        onClick = onQrClick,
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("card_qr_button_${tx.id}"),
                        shape = RoundedCornerShape(3.dp),
                        border = BorderStroke(1.dp, PureBlack),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = "Generate UPI QR for ${tx.partyName}",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("QR", style = MaterialTheme.typography.labelSmall)
                    }
                }

                OutlinedButton(
                    onClick = onWhatsAppClick,
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("card_whatsapp_button_${tx.id}"),
                    shape = RoundedCornerShape(3.dp),
                    border = BorderStroke(1.dp, PureBlack),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = PureBlack,
                        contentColor = PureWhite
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send WhatsApp Reminder to ${tx.partyName}",
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
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
            .padding(22.dp)
            .testTag("empty_ledger_box"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("CREATE FIRST UDHAAR", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
