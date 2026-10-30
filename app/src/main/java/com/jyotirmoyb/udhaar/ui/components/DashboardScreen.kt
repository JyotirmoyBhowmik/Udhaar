package com.jyotirmoyb.udhaar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.DateRangeFilter
import com.jyotirmoyb.udhaar.data.LedgerFilter
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.TransactionSortOption
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.VerificationStatus
import com.jyotirmoyb.udhaar.ui.ActiveScreen
import com.jyotirmoyb.udhaar.ui.CurrencyLedgerTotals
import com.jyotirmoyb.udhaar.ui.PartyKhataSummary
import com.jyotirmoyb.udhaar.ui.PartySortOption
import com.jyotirmoyb.udhaar.ui.UdhaarUiState
import com.jyotirmoyb.udhaar.ui.theme.AccentFabGreen
import com.jyotirmoyb.udhaar.ui.theme.AccentFabInk
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.CardSurfaceWhite
import com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily
import com.jyotirmoyb.udhaar.ui.theme.MutedChipGrey
import com.jyotirmoyb.udhaar.ui.theme.MutedInkGrey
import com.jyotirmoyb.udhaar.ui.theme.PayableCardBg
import com.jyotirmoyb.udhaar.ui.theme.PayableCardInk
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.ui.theme.ReceivableBadgeBg
import com.jyotirmoyb.udhaar.ui.theme.ReceivableCardBg
import com.jyotirmoyb.udhaar.ui.theme.ReceivableCardInk
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.ui.theme.SelectedPillTint
import com.jyotirmoyb.udhaar.ui.theme.SpaceGroteskFamily
import com.jyotirmoyb.udhaar.ui.theme.SubtleSurfaceGrey
import com.jyotirmoyb.udhaar.ui.theme.UdhaarShapeTokens
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.InterestCalculator

private enum class HomePillTab {
    ACTIVE,
    PEOPLE,
    HISTORY
}

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
    onToggleFavoriteParty: (PartyEntity) -> Unit = {},
    onTransactionSortOptionChange: (TransactionSortOption) -> Unit = {},
    onDateRangeFilterChange: (DateRangeFilter) -> Unit = {},
    onExportFullLedgerPdf: () -> Unit = {}
) {
    val isPeopleTab = uiState.activeScreen == ActiveScreen.PEOPLE_DIRECTORY
    val enabledCurrencies = uiState.userSettings.enabledCurrencies
    var isHistoryMode by rememberSaveable { mutableStateOf(false) }

    val selectedPillTab = when {
        isPeopleTab -> HomePillTab.PEOPLE
        isHistoryMode || uiState.selectedFilter == LedgerFilter.SETTLED -> HomePillTab.HISTORY
        else -> HomePillTab.ACTIVE
    }

    val activeRecordsCount = remember(uiState.allCalculatedItems) {
        uiState.allCalculatedItems.count { !it.transaction.isSettled }
    }
    val peopleCount = uiState.partyKhataList.size

    val ledgerListState = rememberLazyListState()
    val isFabExpanded by remember {
        derivedStateOf {
            ledgerListState.firstVisibleItemIndex == 0 && ledgerListState.firstVisibleItemScrollOffset < 60
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = PureWhite,
        floatingActionButtonPosition = FabPosition.Center,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PureWhite)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Header: UDHAAR + Theme Name on left, single Settings (gear) icon on right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = "UDHAAR",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontFamily = SpaceGroteskFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 24.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = PureBlack,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = uiState.userSettings.themeMode.label,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = SecondaryInkGrey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("header_theme_subtitle")
                        )
                    }

                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("open_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = PureBlack,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Three rounded pill tabs: Active (n), People (n), and History
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PillTabItem(
                        label = "Active ($activeRecordsCount)",
                        selected = selectedPillTab == HomePillTab.ACTIVE,
                        onClick = {
                            isHistoryMode = false
                            onSwitchTab(ActiveScreen.DASHBOARD)
                            if (uiState.selectedFilter == LedgerFilter.SETTLED) {
                                onSelectFilter(LedgerFilter.ALL)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_tab_ledger")
                    )

                    PillTabItem(
                        label = "People ($peopleCount)",
                        selected = selectedPillTab == HomePillTab.PEOPLE,
                        onClick = {
                            isHistoryMode = false
                            onSwitchTab(ActiveScreen.PEOPLE_DIRECTORY)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_tab_people_directory")
                    )

                    PillTabItem(
                        label = "History",
                        selected = selectedPillTab == HomePillTab.HISTORY,
                        onClick = {
                            isHistoryMode = true
                            onSwitchTab(ActiveScreen.DASHBOARD)
                            onSelectFilter(LedgerFilter.SETTLED)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("nav_tab_history")
                    )
                }
            }
        },
        floatingActionButton = {
            val fabCorner by animateDpAsState(
                targetValue = if (isFabExpanded) 16.dp else 28.dp,
                animationSpec = tween(durationMillis = 220),
                label = "fabCorner"
            )
            FloatingActionButton(
                onClick = if (isPeopleTab) onAddPartyClick else onAddUdhaarClick,
                containerColor = AccentFabGreen,
                contentColor = AccentFabInk,
                shape = RoundedCornerShape(fabCorner),
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 10.dp
                ),
                modifier = Modifier
                    .height(52.dp)
                    .testTag("add_udhaar_fab")
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = if (isFabExpanded) 20.dp else 14.dp,
                        vertical = 10.dp
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isPeopleTab) Icons.Default.PersonAdd else Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_udhaar_button),
                        tint = AccentFabInk,
                        modifier = Modifier.size(22.dp)
                    )
                    AnimatedVisibility(
                        visible = isFabExpanded,
                        enter = fadeIn(tween(180)) + expandHorizontally(tween(220)),
                        exit = fadeOut(tween(150)) + shrinkHorizontally(tween(200))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isPeopleTab) "NEW CONTACT" else "NEW UDHAAR",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.4.sp
                                ),
                                color = AccentFabInk,
                                maxLines = 1
                            )
                        }
                    }
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
                    state = ledgerListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 700.dp)
                        .testTag("transactions_lazy_list"),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
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
                            onSelectFilter = { filter ->
                                isHistoryMode = (filter == LedgerFilter.SETTLED)
                                onSelectFilter(filter)
                            }
                        )
                    }

                    item {
                        LedgerSectionHeaderRow(
                            recordCount = uiState.filteredItems.size,
                            transactionSortOption = uiState.transactionSortOption,
                            dateRangeFilter = uiState.dateRangeFilter,
                            onTransactionSortOptionChange = onTransactionSortOptionChange,
                            onDateRangeFilterChange = onDateRangeFilterChange
                        )
                    }

                    if (uiState.filteredItems.isEmpty()) {
                        item {
                            EmptyLedgerState(
                                hasAnyTransactions = uiState.allCalculatedItems.isNotEmpty(),
                                onAddClick = onAddUdhaarClick,
                                onResetFilter = {
                                    isHistoryMode = false
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
                                onQrClick = {
                                    val tx = summary.transaction
                                    if (tx.currency == CurrencyType.INR &&
                                        tx.transactionType == TransactionType.GIVEN &&
                                        !tx.isSettled
                                    ) {
                                        onTransactionQrClick(summary)
                                    } else {
                                        onTransactionClick(summary)
                                    }
                                }
                            )
                        }
                    }

                    // Footer: User's UPI ID as a small "VPA:" line under the list
                    item {
                        val vpaDisplay = if (uiState.userSettings.upiId.isNotBlank()) {
                            InterestCalculator.maskUpiId(uiState.userSettings.upiId)
                        } else {
                            "Not configured (Tap Settings to add UPI ID)"
                        }
                        Text(
                            text = "VPA: $vpaDisplay",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = IbmPlexMonoFamily,
                                fontSize = 12.5.sp
                            ),
                            color = SecondaryInkGrey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSettingsClick() }
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .testTag("footer_vpa_line")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PillTabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) SelectedPillTint else CardSurfaceWhite,
        animationSpec = tween(durationMillis = 220),
        label = "pillBgColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) PureBlack else BorderGrey,
        animationSpec = tween(durationMillis = 220),
        label = "pillBorderColor"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 1.5.dp else 1.dp,
        animationSpec = tween(durationMillis = 220),
        label = "pillBorderWidth"
    )

    Box(
        modifier = modifier
            .height(42.dp)
            .clip(UdhaarShapeTokens.Pill)
            .background(color = bgColor, shape = UdhaarShapeTokens.Pill)
            .border(width = borderWidth, color = borderColor, shape = UdhaarShapeTokens.Pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 13.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = PureBlack,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
            .testTag("top_summary_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Small currency chips above the cards only if more than one currency is enabled
        if (enabledCurrencies.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                enabledCurrencies.forEach { curr ->
                    val isSelected = selectedCurrency == curr
                    val count = if (curr == CurrencyType.INR) inrCount else nprCount
                    val chipBg by animateColorAsState(
                        targetValue = if (isSelected) SelectedPillTint else CardSurfaceWhite,
                        animationSpec = tween(180),
                        label = "currChipBg"
                    )
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(UdhaarShapeTokens.Corner12)
                            .background(chipBg, UdhaarShapeTokens.Corner12)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) PureBlack else BorderGrey,
                                shape = UdhaarShapeTokens.Corner12
                            )
                            .clickable { onSelectCurrency(curr) }
                            .padding(horizontal = 12.dp)
                            .testTag("summary_currency_tab_${curr.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${curr.displayName} ($count)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = PureBlack,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Two soft summary cards side by side with 24 dp corner radius
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left Card: Green, Total Receivable
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(UdhaarShapeTokens.Corner24)
                    .background(ReceivableCardBg, UdhaarShapeTokens.Corner24)
                    .padding(horizontal = 16.dp, vertical = 18.dp)
                    .testTag("summary_receivable_card"),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Receivable:",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = ReceivableCardInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = InterestCalculator.formatCurrency(totals.totalToReceive, selectedCurrency),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontFamily = IbmPlexMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = ReceivableCardInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .testTag("summary_total_to_receive")
                    )

                    Box(
                        modifier = Modifier
                            .background(ReceivableBadgeBg, UdhaarShapeTokens.Corner8)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "RECEIVED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.3.sp
                            ),
                            color = ReceivableCardInk,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val activeRecordLabel = if (totals.activeGivenCount == 1) {
                    "1 Active Record"
                } else {
                    "${totals.activeGivenCount} Active Records"
                }
                Text(
                    text = activeRecordLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = ReceivableCardInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right Card: Soft Red, Total Payable
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(UdhaarShapeTokens.Corner24)
                    .background(PayableCardBg, UdhaarShapeTokens.Corner24)
                    .padding(horizontal = 16.dp, vertical = 18.dp)
                    .testTag("summary_payable_card"),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Payable:",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = PayableCardInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = InterestCalculator.formatCurrency(totals.totalToPay, selectedCurrency),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = IbmPlexMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    color = PayableCardInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("summary_total_to_pay")
                )

                Spacer(modifier = Modifier.height(10.dp))

                val payableRecordLabel = if (totals.activeTakenCount == 1) {
                    "1 Record"
                } else {
                    "${totals.activeTakenCount} Records"
                }
                Text(
                    text = payableRecordLabel,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = PayableCardInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Below them: Net INR Position row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .testTag("net_position_row"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Net ${selectedCurrency.code} Position",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = PureBlack,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val net = totals.netPosition
            val prefix = when {
                net > 0.005 -> "+"
                net < -0.005 -> "-"
                else -> ""
            }
            Text(
                text = "$prefix${InterestCalculator.formatCurrency(kotlin.math.abs(net), selectedCurrency)}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = IbmPlexMonoFamily,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = SecondaryInkGrey,
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
                LedgerFilter.OVERDUE -> allCalculatedItems.count {
                    it.isOverdue && !it.transaction.isSettled
                }
                LedgerFilter.SETTLED -> allCalculatedItems.count {
                    it.transaction.isSettled
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
                LedgerFilter.INR -> allCalculatedItems.count {
                    it.transaction.currency == CurrencyType.INR
                }
                LedgerFilter.NPR -> allCalculatedItems.count {
                    it.transaction.currency == CurrencyType.NPR
                }
            }
        }
    }

    val visibleFilters = remember(enabledCurrencies, filterCounts, selectedFilter) {
        LedgerFilter.entries.filter { filter ->
            when (filter) {
                LedgerFilter.ALL,
                LedgerFilter.GIVEN,
                LedgerFilter.TAKEN,
                LedgerFilter.OVERDUE,
                LedgerFilter.SETTLED -> true
                LedgerFilter.NEEDS_CONFIRMATION ->
                    (filterCounts[LedgerFilter.NEEDS_CONFIRMATION] ?: 0) > 0 || selectedFilter == LedgerFilter.NEEDS_CONFIRMATION
                LedgerFilter.INR -> enabledCurrencies.size > 1 && CurrencyType.INR in enabledCurrencies
                LedgerFilter.NPR -> enabledCurrencies.size > 1 && CurrencyType.NPR in enabledCurrencies
            }
        }
    }

    val lazyRowState = rememberLazyListState()
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = lazyRowState)
    val canScrollLeft = lazyRowState.canScrollBackward
    val canScrollRight = lazyRowState.canScrollForward

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Rounded white search bar with search icon and hint "Find a transaction or person…"
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.search_placeholder),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.5.sp),
                    color = MutedInkGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = SecondaryInkGrey,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = SecondaryInkGrey,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = UdhaarShapeTokens.Corner12,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PureBlack,
                unfocusedBorderColor = BorderGrey,
                cursorColor = PureBlack,
                focusedContainerColor = CardSurfaceWhite,
                unfocusedContainerColor = CardSurfaceWhite
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("dashboard_search_input")
        )

        // Horizontal Filter Chips row with right-edge fade and count on each chip
        LazyRow(
            state = lazyRowState,
            flingBehavior = snapFlingBehavior,
            contentPadding = PaddingValues(start = 2.dp, end = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    val fadeWidthPx = 28.dp.toPx()
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
                val chipBg by animateColorAsState(
                    targetValue = if (isSelected) PureBlack else CardSurfaceWhite,
                    animationSpec = tween(180),
                    label = "filterChipBg"
                )
                val chipContentColor by animateColorAsState(
                    targetValue = if (isSelected) CardSurfaceWhite else PureBlack,
                    animationSpec = tween(180),
                    label = "filterChipContent"
                )

                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(UdhaarShapeTokens.Corner8)
                        .background(color = chipBg, shape = UdhaarShapeTokens.Corner8)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) PureBlack else BorderGrey,
                            shape = UdhaarShapeTokens.Corner8
                        )
                        .clickable { onSelectFilter(filter) }
                        .padding(horizontal = 12.dp)
                        .testTag("filter_chip_${filter.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            ),
                            color = chipContentColor,
                            maxLines = 1,
                            softWrap = false
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isSelected) CardSurfaceWhite.copy(alpha = 0.2f) else SubtleSurfaceGrey,
                                    shape = UdhaarShapeTokens.Corner8
                                )
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                                .testTag("filter_badge_${filter.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = IbmPlexMonoFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = chipContentColor,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerSectionHeaderRow(
    recordCount: Int,
    transactionSortOption: TransactionSortOption,
    dateRangeFilter: DateRangeFilter,
    onTransactionSortOptionChange: (TransactionSortOption) -> Unit,
    onDateRangeFilterChange: (DateRangeFilter) -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "LEDGER RECORDS ($recordCount)",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            ),
            color = MutedInkGrey
        )

        Box {
            Row(
                modifier = Modifier
                    .clip(UdhaarShapeTokens.Corner8)
                    .clickable { sortMenuExpanded = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("sort_transactions_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = "Sort and filter date range",
                    tint = SecondaryInkGrey,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = if (dateRangeFilter == DateRangeFilter.ALL_TIME) {
                        transactionSortOption.label
                    } else {
                        "${dateRangeFilter.label} • ${transactionSortOption.label}"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = SecondaryInkGrey
                )
            }

            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false },
                modifier = Modifier.background(CardSurfaceWhite)
            ) {
                TransactionSortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Sort: ${option.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (option == transactionSortOption) FontWeight.Bold else FontWeight.Normal,
                                color = PureBlack
                            )
                        },
                        onClick = {
                            sortMenuExpanded = false
                            onTransactionSortOptionChange(option)
                        }
                    )
                }
                HorizontalDivider(color = BorderGrey)
                DateRangeFilter.entries.forEach { range ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Range: ${range.label}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (range == dateRangeFilter) FontWeight.Bold else FontWeight.Normal,
                                color = PureBlack
                            )
                        },
                        onClick = {
                            sortMenuExpanded = false
                            onDateRangeFilterChange(range)
                        },
                        modifier = Modifier.testTag("date_range_${range.name.lowercase()}")
                    )
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transaction_card_${tx.id}"),
        shape = UdhaarShapeTokens.Corner16,
        color = if (tx.isSettled) SubtleSurfaceGrey else CardSurfaceWhite,
        shadowElevation = 3.dp,
        border = BorderStroke(
            width = if (summary.isOverdue || unreadCount > 0) 1.5.dp else 1.dp,
            color = if (summary.isOverdue) PayableCardInk.copy(alpha = 0.5f) else BorderGrey.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCardClick)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top row: small chips GIVEN, UPI, INR and the due date on the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    // GIVEN / TAKEN chip
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isGiven) PureBlack else PayableCardBg,
                                shape = UdhaarShapeTokens.Corner8
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isGiven) "GIVEN" else "TAKEN",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (isGiven) CardSurfaceWhite else PayableCardInk
                        )
                    }

                    // Payment Mode chip (e.g. UPI)
                    Box(
                        modifier = Modifier
                            .background(MutedChipGrey, UdhaarShapeTokens.Corner8)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = tx.paymentMode.shortBadge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = PureBlack
                        )
                    }

                    // Currency chip (e.g. INR)
                    Box(
                        modifier = Modifier
                            .background(MutedChipGrey, UdhaarShapeTokens.Corner8)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = tx.currency.code,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = SecondaryInkGrey
                        )
                    }

                    // Optional Verification badge when verified or pending action
                    if (tx.verificationStatus != VerificationStatus.LOCAL_ONLY || tx.needsMyConfirmation) {
                        val isVerified = tx.verificationStatus == VerificationStatus.CONFIRMED
                        val verBadgeText = if (tx.needsMyConfirmation) {
                            "Confirm?"
                        } else {
                            tx.verificationStatus.shortBadge
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isVerified) SelectedPillTint else SubtleSurfaceGrey,
                                    shape = UdhaarShapeTokens.Corner8
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isVerified) PureBlack else BorderGrey,
                                    shape = UdhaarShapeTokens.Corner8
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("tx_verification_badge_${tx.id}")
                        ) {
                            Text(
                                text = verBadgeText,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = PureBlack,
                                maxLines = 1
                            )
                        }
                    }

                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .background(PureBlack, UdhaarShapeTokens.Corner8)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("tx_unread_badge_${tx.id}")
                        ) {
                            Text(
                                text = "💬 $unreadCount",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = CardSurfaceWhite,
                                maxLines = 1
                            )
                        }
                    }

                    if (attachmentsCount > 0) {
                        Box(
                            modifier = Modifier
                                .background(SubtleSurfaceGrey, UdhaarShapeTokens.Corner8)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("tx_attachments_badge_${tx.id}")
                        ) {
                            Text(
                                text = "📎 $attachmentsCount",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = PureBlack,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Due date on the right in IBM Plex Mono
                Text(
                    text = summary.dueStatusText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = IbmPlexMonoFamily,
                        fontSize = 11.sp,
                        fontWeight = if (summary.isOverdue || tx.isSettled) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    color = if (summary.isOverdue) PayableCardInk else SecondaryInkGrey,
                    maxLines = 1
                )
            }

            // Person's name with the amount on the right in large type, and the masked phone number below
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = tx.partyName,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = PureBlack,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (tx.isSettled) TextDecoration.LineThrough else TextDecoration.None
                    )
                    if (tx.phoneNumber.isNotBlank()) {
                        Text(
                            text = InterestCalculator.maskPhoneNumber(tx.phoneNumber),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = IbmPlexMonoFamily,
                                fontSize = 12.sp
                            ),
                            color = SecondaryInkGrey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val displayAmount = if (tx.isSettled) summary.grossTotal else summary.netBalanceDue
                Text(
                    text = InterestCalculator.formatCurrency(displayAmount, tx.currency),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = IbmPlexMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = PureBlack,
                    maxLines = 1,
                    textDecoration = if (tx.isSettled) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.testTag("tx_card_amount_${tx.id}")
                )
            }

            // One line of terms (Principal, Interest, rate, type, days) and the note in italics
            Text(
                text = summary.subtitleBreakdown,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = IbmPlexMonoFamily,
                    fontSize = 11.5.sp
                ),
                color = SecondaryInkGrey,
                modifier = Modifier.testTag("tx_card_breakdown_${tx.id}")
            )

            if (tx.notes.isNotBlank()) {
                Text(
                    text = "“${tx.notes}”",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontStyle = FontStyle.Italic,
                        fontSize = 13.5.sp
                    ),
                    color = SecondaryInkGrey,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = BorderGrey.copy(alpha = 0.7f), thickness = 1.dp)

            // "Tap for Repayment / Receipt" row (plus subtle chat trigger)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (tx.isSettled) "Tap for Receipt" else "Tap for Repayment / Receipt",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = PureBlack,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier
                        .clip(UdhaarShapeTokens.Corner8)
                        .clickable(onClick = onChatClick)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("card_chat_button_${tx.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = "Open chat for ${tx.partyName}",
                        tint = SecondaryInkGrey,
                        modifier = Modifier.size(15.dp)
                    )
                    if (unreadCount > 0) {
                        Text(
                            text = "Chat ($unreadCount)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = PureBlack
                        )
                    }
                }
            }

            // Two buttons side by side: tonal Get QR/Receipt and filled Send Reminder
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onQrClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("card_qr_button_${tx.id}"),
                    shape = UdhaarShapeTokens.Corner12,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MutedChipGrey,
                        contentColor = PureBlack
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = "Get QR or Receipt for ${tx.partyName}",
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Get QR/Receipt",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onWhatsAppClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("card_whatsapp_button_${tx.id}"),
                    shape = UdhaarShapeTokens.Corner12,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PureBlack,
                        contentColor = CardSurfaceWhite
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Send Reminder to ${tx.partyName}",
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Send Reminder",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = CardSurfaceWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_ledger_box"),
        shape = UdhaarShapeTokens.Corner16,
        color = CardSurfaceWhite,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, BorderGrey)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (hasAnyTransactions) "No Matching Records" else "No Udhaar Records Yet",
                style = MaterialTheme.typography.titleLarge,
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
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (hasAnyTransactions) {
                OutlinedButton(
                    onClick = onResetFilter,
                    shape = UdhaarShapeTokens.Corner12,
                    border = BorderStroke(1.dp, PureBlack),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                ) {
                    Text("Clear Filters", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                Button(
                    onClick = onAddClick,
                    shape = UdhaarShapeTokens.Corner12,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentFabGreen,
                        contentColor = AccentFabInk
                    ),
                    modifier = Modifier.testTag("empty_state_add_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create First Udhaar", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
