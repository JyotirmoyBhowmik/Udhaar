package com.jyotirmoyb.udhaar.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.DateRangeFilter
import com.jyotirmoyb.udhaar.data.LedgerFilter
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.TransactionSortOption
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.VerificationStatus
import com.jyotirmoyb.udhaar.ui.ActiveScreen
import com.jyotirmoyb.udhaar.ui.PartyKhataSummary
import com.jyotirmoyb.udhaar.ui.PartySortOption
import com.jyotirmoyb.udhaar.ui.UdhaarUiState
import com.jyotirmoyb.udhaar.ui.sheets.GroupSplitSheet
import com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily
import com.jyotirmoyb.udhaar.ui.theme.PayableCardInk
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.InterestCalculator

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
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
    onExportFullLedgerPdf: () -> Unit = {},
    onToggleHideAmounts: () -> Unit = {},
    onDismissNeedsAttention: () -> Unit = {},
    onDismissCoachMarks: () -> Unit = {},
    onUndoAction: () -> Unit = {},
    onDismissUndo: () -> Unit = {},
    onSnoozeTransaction: (recordId: Long, days: Int) -> Unit = { _, _ -> },
    onStopTransactionReminders: (recordId: Long) -> Unit = {},
    onEnableTransactionReminders: (recordId: Long) -> Unit = {},
    onOpenUpdatesScreen: () -> Unit = {},
    onCreateGroupSplitTransactions: (List<UdhaarTransaction>) -> Unit = {}
) {
    val context = LocalContext.current
    var pendingExtensionSummary by remember { mutableStateOf<CalculatedUdhaarSummary?>(null) }
    var showGroupSplitSheet by rememberSaveable { mutableStateOf(false) }
    var selectedAdaptiveTxId by rememberSaveable { mutableStateOf<Long?>(null) }
    val isPeopleTab = uiState.activeScreen == ActiveScreen.PEOPLE_DIRECTORY
    val enabledCurrencies = uiState.userSettings.enabledCurrencies
    var isHistoryMode by rememberSaveable { mutableStateOf(false) }
    val reducedMotion = rememberReducedMotionEnabled()
    val snackbarHostState = remember { SnackbarHostState() }

    val undoActionLabel = stringResource(R.string.snackbar_action_undo)
    LaunchedEffect(uiState.undoSnackbarMessage, undoActionLabel) {
        val msg = uiState.undoSnackbarMessage
        if (!msg.isNullOrBlank()) {
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = undoActionLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) onUndoAction() else onDismissUndo()
        }
    }

    val selectedPillTab = when {
        isPeopleTab -> HomePillTab.PEOPLE
        isHistoryMode || uiState.selectedFilter == LedgerFilter.SETTLED -> HomePillTab.HISTORY
        else -> HomePillTab.ACTIVE
    }

    val activeRecordsCount = remember(uiState.allCalculatedItems) {
        uiState.allCalculatedItems.count { !it.transaction.isSettled }
    }
    val peopleCount = uiState.partyKhataList.size
    val partyPhotoByName = remember(uiState.allParties) {
        uiState.allParties.associate { it.name.trim().lowercase() to it.photoUri }
    }

    val nowMillis = System.currentTimeMillis()
    val endOfTodayMillis = nowMillis + 24L * 3600 * 1000
    val endOfWeekMillis = nowMillis + 7L * 24 * 3600 * 1000

    val overdueCount = remember(uiState.allCalculatedItems) {
        uiState.allCalculatedItems.count { it.isOverdue && !it.transaction.isSettled }
    }
    val dueTodayCount = remember(uiState.allCalculatedItems, nowMillis) {
        uiState.allCalculatedItems.count { item ->
            val exp = item.transaction.expiryDateMillis
            !item.transaction.isSettled && !item.isOverdue && exp != null && exp in nowMillis..endOfTodayMillis
        }
    }
    val waitingConfirmationCount = remember(uiState.allCalculatedItems, uiState.partialPaymentsByTx) {
        uiState.allCalculatedItems.count { item ->
            val tx = item.transaction
            val txPayments = uiState.partialPaymentsByTx[tx.id].orEmpty()
            !tx.isSettled && (
                tx.needsMyConfirmation ||
                    tx.verificationStatus == VerificationStatus.EDITED_AFTER_CONFIRM ||
                    tx.verificationStatus == VerificationStatus.PENDING ||
                    txPayments.any { it.needsMyAcknowledgement || !it.isAcknowledged }
                )
        }
    }

    val groupedFilteredItems = remember(uiState.filteredItems, uiState.selectedFilter, uiState.userSettings.groupRecordsByPerson, nowMillis) {
        buildGroupedFilteredItems(uiState.filteredItems, uiState.selectedFilter, endOfWeekMillis, groupByPerson = uiState.userSettings.groupRecordsByPerson)
    }

    val ledgerListState = rememberLazyListState()
    val peopleListState = rememberLazyListState()
    val activeListState = if (isPeopleTab) peopleListState else ledgerListState
    var previousIndex by remember(activeListState) { mutableIntStateOf(activeListState.firstVisibleItemIndex) }
    var previousScrollOffset by remember(activeListState) { mutableIntStateOf(activeListState.firstVisibleItemScrollOffset) }
    var isScrollingUp by remember(activeListState) { mutableStateOf(true) }

    val isFabExpanded by remember(activeListState) {
        derivedStateOf {
            val currentIndex = activeListState.firstVisibleItemIndex
            val currentOffset = activeListState.firstVisibleItemScrollOffset
            val atTop = currentIndex == 0 && currentOffset < 24
            val scrollingUp = when {
                atTop -> true
                currentIndex != previousIndex -> currentIndex < previousIndex
                kotlin.math.abs(currentOffset - previousScrollOffset) > 8 -> currentOffset < previousScrollOffset
                else -> isScrollingUp
            }
            previousIndex = currentIndex
            previousScrollOffset = currentOffset
            isScrollingUp = scrollingUp
            atTop || scrollingUp
        }
    }

    BorrowerExtensionDatePickerDialog(
        summary = pendingExtensionSummary,
        userSettings = uiState.userSettings,
        onDismiss = { pendingExtensionSummary = null }
    )

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = PureWhite,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .padding(bottom = 80.dp)
                    .testTag("dashboard_snackbar_host")
            )
        },
        floatingActionButtonPosition = FabPosition.Center,
        topBar = {
            DashboardTopHeaderBar(
                themeSubtitle = uiState.userSettings.themeMode.label,
                selectedPillTab = selectedPillTab,
                activeRecordsCount = activeRecordsCount,
                peopleCount = peopleCount,
                reducedMotion = reducedMotion,
                selectedFilter = uiState.selectedFilter,
                onOpenGroupSplit = { showGroupSplitSheet = true },
                onOpenUpdatesScreen = onOpenUpdatesScreen,
                onSettingsClick = onSettingsClick,
                onSelectActiveTab = {
                    isHistoryMode = false
                    onSwitchTab(ActiveScreen.DASHBOARD)
                    if (uiState.selectedFilter == LedgerFilter.SETTLED) {
                        onSelectFilter(LedgerFilter.ALL)
                    }
                },
                onSelectPeopleTab = {
                    isHistoryMode = false
                    onSwitchTab(ActiveScreen.PEOPLE_DIRECTORY)
                },
                onSelectHistoryTab = {
                    isHistoryMode = true
                    onSwitchTab(ActiveScreen.DASHBOARD)
                    onSelectFilter(LedgerFilter.SETTLED)
                }
            )
        },
        floatingActionButton = {
            DashboardPrimaryFab(
                isPeopleTab = isPeopleTab,
                isFabExpanded = isFabExpanded,
                reducedMotion = reducedMotion,
                onAddPartyClick = onAddPartyClick,
                onAddUdhaarClick = onAddUdhaarClick
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            val isWideScreen = maxWidth >= 600.dp
            val isLandscape = maxWidth > maxHeight
            val rootModifier = if (isLandscape) {
                Modifier
                    .fillMaxSize()
                    .testTag("dashboard_landscape_root")
            } else {
                Modifier.fillMaxSize()
            }

            Box(modifier = rootModifier, contentAlignment = Alignment.TopCenter) {
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
                        onToggleFavorite = onToggleFavoriteParty,
                        isInitialLoading = uiState.isInitialLoading,
                        isAmountsHidden = uiState.isAmountsHidden,
                        listState = peopleListState
                    )
                } else {
                    val selectedDetailSummary = remember(uiState.filteredItems, selectedAdaptiveTxId, uiState.detailTransactionId) {
                        val targetId = selectedAdaptiveTxId ?: uiState.detailTransactionId
                        uiState.filteredItems.firstOrNull { it.transaction.id == targetId }
                            ?: uiState.filteredItems.firstOrNull()
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (isWideScreen) {
                                    Modifier
                                        .padding(horizontal = 20.dp)
                                        .testTag("dashboard_adaptive_two_pane")
                                } else {
                                    Modifier.widthIn(max = 700.dp)
                                }
                            ),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LazyColumn(
                            state = ledgerListState,
                            modifier = Modifier
                                .weight(if (isWideScreen) 0.55f else 1f)
                                .fillMaxHeight()
                                .testTag("transactions_lazy_list"),
                            contentPadding = PaddingValues(
                                start = if (isWideScreen) 0.dp else 16.dp,
                                end = if (isWideScreen) 0.dp else 16.dp,
                                top = 4.dp,
                                bottom = 120.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(UdhaarSpacing.Small)
                        ) {
                            // v19 placement order: SearchAndFilterBar -> TopLedgerSummaryCard -> NeedsAttentionStrip -> LedgerSectionHeaderRow -> Cards
                            item(key = "search_filter_bar_item") {
                                Box(modifier = udhaarAnimateItem(reducedMotion)) {
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
                            }

                            item(key = "top_summary_card_item") {
                                Box(modifier = udhaarAnimateItem(reducedMotion)) {
                                    TopLedgerSummaryCard(
                                        selectedCurrency = uiState.summaryCurrency,
                                        enabledCurrencies = enabledCurrencies,
                                        totals = uiState.activeSummaryTotals,
                                        inrCount = uiState.inrTotals.activeGivenCount + uiState.inrTotals.activeTakenCount,
                                        nprCount = uiState.nprTotals.activeGivenCount + uiState.nprTotals.activeTakenCount,
                                        isAmountsHidden = uiState.isAmountsHidden,
                                        onToggleHideAmounts = onToggleHideAmounts,
                                        onSelectCurrency = onSelectSummaryCurrency
                                    )
                                }
                            }

                            if (!uiState.isNeedsAttentionDismissed &&
                                (overdueCount > 0 || dueTodayCount > 0 || waitingConfirmationCount > 0)
                            ) {
                                item(key = "needs_attention_item") {
                                    Box(modifier = udhaarAnimateItem(reducedMotion)) {
                                        NeedsAttentionStrip(
                                            overdueCount = overdueCount,
                                            dueTodayCount = dueTodayCount,
                                            waitingConfirmationCount = waitingConfirmationCount,
                                            onFilterSelect = { filter ->
                                                isHistoryMode = false
                                                onSelectFilter(filter)
                                            },
                                            onDismiss = onDismissNeedsAttention
                                        )
                                    }
                                }
                            }

                            item(key = "ledger_section_header_item") {
                                Box(modifier = udhaarAnimateItem(reducedMotion)) {
                                    LedgerSectionHeaderRow(
                                        recordCount = uiState.filteredItems.size,
                                        transactionSortOption = uiState.transactionSortOption,
                                        dateRangeFilter = uiState.dateRangeFilter,
                                        onOpenGroupSplit = { showGroupSplitSheet = true },
                                        onTransactionSortOptionChange = onTransactionSortOptionChange,
                                        onDateRangeFilterChange = onDateRangeFilterChange
                                    )
                                }
                            }

                            if (uiState.isInitialLoading || uiState.filteredItems.isEmpty()) {
                                item(key = "empty_or_shimmer_ledger_state_item") {
                                    Box(modifier = udhaarAnimateItem(reducedMotion)) {
                                        Crossfade(
                                            targetState = uiState.isInitialLoading,
                                            animationSpec = tween(durationMillis = if (reducedMotion) 0 else 220),
                                            label = "ledgerInitialLoadingCrossfade"
                                        ) { isLoading ->
                                            if (isLoading) {
                                                ShimmerLedgerPlaceholder()
                                            } else if (uiState.filteredItems.isEmpty()) {
                                                EmptyLedgerState(
                                                    hasAnyTransactions = uiState.allCalculatedItems.isNotEmpty(),
                                                    onAddClick = onAddUdhaarClick,
                                                    onInviteClick = onShareMyPaymentDetailsClick,
                                                    onResetFilter = {
                                                        isHistoryMode = false
                                                        onSelectFilter(LedgerFilter.ALL)
                                                        onSearchQueryChange("")
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                val showGroupStickyHeaders = groupedFilteredItems.size > 1 ||
                                    (groupedFilteredItems.size == 1 && (groupedFilteredItems.first().isOverdueGroup || uiState.userSettings.groupRecordsByPerson))

                                groupedFilteredItems.forEach { groupSection ->
                                    if (showGroupStickyHeaders) {
                                        stickyHeader(key = "group_header_${groupSection.key}") {
                                            val groupTitle = if (groupSection.titleText.isNotBlank()) groupSection.titleText else stringResource(groupSection.titleRes)
                                            Box(
                                                modifier = udhaarAnimateItem(reducedMotion)
                                                    .fillMaxWidth()
                                                    .background(PureWhite.copy(alpha = 0.96f))
                                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                                                    .testTag("sticky_group_header_${groupSection.key}")
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.ui_fmt_symbol_30a1de, groupTitle, groupSection.items.size),
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = if (groupSection.isOverdueGroup) PayableCardInk else SecondaryInkGrey
                                                )
                                            }
                                        }
                                    }

                                    items(
                                        items = groupSection.items,
                                        key = { it.transaction.id }
                                    ) { summary ->
                                        val partyPhotoUri = partyPhotoByName[summary.transaction.partyName.trim().lowercase()].orEmpty()
                                        Box(modifier = udhaarAnimateItem(reducedMotion)) {
                                            SwipeableTransactionCardItem(
                                                summary = summary,
                                                partyPhotoUri = partyPhotoUri,
                                                isAmountsHidden = uiState.isAmountsHidden,
                                                unreadCount = uiState.unreadCountByTx[summary.transaction.id] ?: 0,
                                                attachmentsCount = uiState.attachmentsByTx[summary.transaction.id]?.size ?: 0,
                                                installments = uiState.installmentPlansByTx[summary.transaction.id]?.let { uiState.installmentsByPlan[it.id] }.orEmpty(),
                                                defaultExpanded = false,
                                                onCardClick = {
                                                    selectedAdaptiveTxId = summary.transaction.id
                                                    onTransactionClick(summary)
                                                },
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
                                                },
                                                onSnoozeClick = onSnoozeTransaction,
                                                onStopRemindersClick = onStopTransactionReminders,
                                                onEnableRemindersClick = onEnableTransactionReminders,
                                                onBorrowerAction = { s, actionType ->
                                                    dispatchBorrowerAction(
                                                        context = context,
                                                        userSettings = uiState.userSettings,
                                                        summary = s,
                                                        actionType = actionType,
                                                        onRequestExtension = { pendingExtensionSummary = it }
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            item(key = "footer_vpa_item") {
                                val vpaDisplay = if (uiState.userSettings.upiId.isNotBlank()) {
                                    InterestCalculator.maskUpiId(uiState.userSettings.upiId)
                                } else {
                                    stringResource(R.string.vpa_not_configured)
                                }
                                Text(
                                    text = stringResource(R.string.ui_fmt_vpa_560ef1, vpaDisplay),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = IbmPlexMonoFamily,
                                        fontSize = 14.sp
                                    ),
                                    color = SecondaryInkGrey,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = udhaarAnimateItem(reducedMotion)
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .pressScaleFeedback()
                                        .clickable { onSettingsClick() }
                                        .padding(horizontal = 4.dp, vertical = 10.dp)
                                        .testTag("footer_vpa_line")
                                )
                            }
                        }

                        if (isWideScreen) {
                            val detailPhotoUri = selectedDetailSummary?.let {
                                partyPhotoByName[it.transaction.partyName.trim().lowercase()].orEmpty()
                            }.orEmpty()
                            val detailPayments = selectedDetailSummary?.let {
                                uiState.partialPaymentsByTx[it.transaction.id].orEmpty()
                            }.orEmpty()
                            val detailInstallments = selectedDetailSummary?.let { s ->
                                uiState.installmentPlansByTx[s.transaction.id]?.let { uiState.installmentsByPlan[it.id] }.orEmpty()
                            }.orEmpty()

                            DashboardAdaptiveDetailPane(
                                selectedSummary = selectedDetailSummary,
                                partyPhotoUri = detailPhotoUri,
                                partialPayments = detailPayments,
                                installments = detailInstallments,
                                isAmountsHidden = uiState.isAmountsHidden,
                                onOpenFullDetailSheet = onTransactionClick,
                                onWhatsAppClick = onWhatsAppClick,
                                onQrClick = { s ->
                                    val tx = s.transaction
                                    if (tx.currency == CurrencyType.INR &&
                                        tx.transactionType == TransactionType.GIVEN &&
                                        !tx.isSettled
                                    ) {
                                        onTransactionQrClick(s)
                                    } else {
                                        onTransactionClick(s)
                                    }
                                },
                                onChatClick = onOpenTransactionChat,
                                modifier = Modifier
                                    .weight(0.45f)
                                    .fillMaxHeight()
                                    .padding(top = 4.dp, bottom = 96.dp)
                            )
                        }
                    }
                }
            }

            if (showGroupSplitSheet) {
                GroupSplitSheet(
                    allParties = uiState.allParties,
                    allCalculatedItems = uiState.allCalculatedItems,
                    userSettings = uiState.userSettings,
                    onSelectGroupFilter = { groupName ->
                        isHistoryMode = false
                        onSelectFilter(LedgerFilter.ALL)
                        onSearchQueryChange(groupName)
                    },
                    onCreateSplitTransactions = onCreateGroupSplitTransactions,
                    onDismiss = { showGroupSplitSheet = false }
                )
            }
        }
    }
}
