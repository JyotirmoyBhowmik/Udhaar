package com.jyotirmoyb.udhaar.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
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

private data class LedgerGroupSection(
    val key: String,
    val titleRes: Int,
    val isOverdueGroup: Boolean,
    val items: List<CalculatedUdhaarSummary>
)

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
    onEnableTransactionReminders: (recordId: Long) -> Unit = {}
) {
    val context = LocalContext.current
    var pendingExtensionSummary by remember { mutableStateOf<CalculatedUdhaarSummary?>(null) }
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
            if (result == SnackbarResult.ActionPerformed) {
                onUndoAction()
            } else {
                onDismissUndo()
            }
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

    val groupedFilteredItems = remember(uiState.filteredItems, uiState.selectedFilter, nowMillis) {
        if (uiState.selectedFilter == LedgerFilter.SETTLED) {
            listOf(
                LedgerGroupSection(
                    key = "settled",
                    titleRes = R.string.group_header_settled,
                    isOverdueGroup = false,
                    items = uiState.filteredItems
                )
            )
        } else {
            val overdue = mutableListOf<CalculatedUdhaarSummary>()
            val dueThisWeek = mutableListOf<CalculatedUdhaarSummary>()
            val later = mutableListOf<CalculatedUdhaarSummary>()
            val noDueDate = mutableListOf<CalculatedUdhaarSummary>()
            val settled = mutableListOf<CalculatedUdhaarSummary>()

            for (item in uiState.filteredItems) {
                val tx = item.transaction
                when {
                    tx.isSettled -> settled.add(item)
                    item.isOverdue -> overdue.add(item)
                    tx.expiryDateMillis != null && tx.expiryDateMillis <= endOfWeekMillis -> dueThisWeek.add(item)
                    tx.expiryDateMillis != null -> later.add(item)
                    else -> noDueDate.add(item)
                }
            }
            buildList {
                if (overdue.isNotEmpty()) {
                    add(LedgerGroupSection("overdue", R.string.group_header_overdue, true, overdue))
                }
                if (dueThisWeek.isNotEmpty()) {
                    add(LedgerGroupSection("due_this_week", R.string.group_header_due_this_week, false, dueThisWeek))
                }
                if (later.isNotEmpty()) {
                    add(LedgerGroupSection("later", R.string.group_header_later, false, later))
                }
                if (noDueDate.isNotEmpty()) {
                    add(LedgerGroupSection("no_due_date", R.string.group_header_no_due_date, false, noDueDate))
                }
                if (settled.isNotEmpty()) {
                    add(LedgerGroupSection("settled", R.string.group_header_settled, false, settled))
                }
            }
        }
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

    val handleBorrowerAction: (CalculatedUdhaarSummary, String) -> Unit = { summary, actionType ->
        val netDue = InterestCalculator.formatCurrency(summary.netBalanceDue, summary.transaction.currency).removePrefix("₹").removePrefix("रू")
        when (actionType) {
            "i_paid" -> {
                val msg = com.jyotirmoyb.udhaar.reminder.ReminderEngine.renderDraftMessage(
                    template = context.getString(R.string.borrower_msg_i_paid),
                    name = summary.transaction.partyName,
                    myName = uiState.userSettings.payeeName.ifBlank { "Me" },
                    amount = netDue,
                    remaining = netDue,
                    paidSoFar = "",
                    dueDate = "",
                    daysOverdue = 0,
                    upiId = uiState.userSettings.upiId
                )
                com.jyotirmoyb.udhaar.util.WhatsAppReminderLauncher.launchReminder(
                    context = context,
                    rawPhoneNumber = summary.transaction.phoneNumber,
                    currency = summary.transaction.currency,
                    message = msg
                )
            }
            "need_more_time" -> {
                pendingExtensionSummary = summary
            }
            "ask_details" -> {
                val msg = com.jyotirmoyb.udhaar.reminder.ReminderEngine.renderDraftMessage(
                    template = context.getString(R.string.borrower_msg_ask_details),
                    name = summary.transaction.partyName,
                    myName = uiState.userSettings.payeeName.ifBlank { "Me" },
                    amount = netDue,
                    remaining = netDue,
                    paidSoFar = "",
                    dueDate = "",
                    daysOverdue = 0,
                    upiId = uiState.userSettings.upiId
                )
                com.jyotirmoyb.udhaar.util.WhatsAppReminderLauncher.launchReminder(
                    context = context,
                    rawPhoneNumber = summary.transaction.phoneNumber,
                    currency = summary.transaction.currency,
                    message = msg
                )
            }
        }
    }

    if (pendingExtensionSummary != null) {
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis() + 7L * 86_400_000L
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { pendingExtensionSummary = null },
            confirmButton = {
                Button(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis ?: (System.currentTimeMillis() + 7L * 86_400_000L)
                        val dateFormatted = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(selectedMillis))
                        val s = pendingExtensionSummary!!
                        val netDue = InterestCalculator.formatCurrency(s.netBalanceDue, s.transaction.currency).removePrefix("₹").removePrefix("रू")
                        val msg = com.jyotirmoyb.udhaar.reminder.ReminderEngine.renderDraftMessage(
                            template = context.getString(R.string.borrower_msg_need_more_time),
                            name = s.transaction.partyName,
                            myName = uiState.userSettings.payeeName.ifBlank { "Me" },
                            amount = netDue,
                            remaining = netDue,
                            paidSoFar = "",
                            dueDate = "",
                            daysOverdue = 0,
                            upiId = uiState.userSettings.upiId,
                            newDate = dateFormatted
                        )
                        com.jyotirmoyb.udhaar.util.WhatsAppReminderLauncher.launchReminder(
                            context = context,
                            rawPhoneNumber = s.transaction.phoneNumber,
                            currency = s.transaction.currency,
                            message = msg
                        )
                        pendingExtensionSummary = null
                    }
                ) {
                    Text(stringResource(R.string.ui_str_confirm_f6fda1))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingExtensionSummary = null }) {
                    Text(stringResource(R.string.ui_str_cancel_1507c4))
                }
            }
        ) {
            androidx.compose.material3.DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = PureWhite,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.testTag("dashboard_snackbar_host")
            )
        },
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
                            text = stringResource(R.string.ui_str_udhaar_ffc4fd),
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
                            .pressScaleFeedback()
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

                // Three rounded pill tabs: Active (n), People (n), and History with sliding animated pill indicator
                val selectedTabIndex = when (selectedPillTab) {
                    HomePillTab.ACTIVE -> 0
                    HomePillTab.PEOPLE -> 1
                    HomePillTab.HISTORY -> 2
                }
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    val spacing = 10.dp
                    val tabWidth = (maxWidth - spacing * 2) / 3
                    val indicatorOffset by animateDpAsState(
                        targetValue = (tabWidth + spacing) * selectedTabIndex,
                        animationSpec = tween(
                            durationMillis = if (reducedMotion) 0 else 230,
                            easing = FastOutSlowInEasing
                        ),
                        label = "pillTabIndicatorOffset"
                    )

                    // Sliding pill indicator underneath active tab
                    Box(
                        modifier = Modifier
                            .offset(x = indicatorOffset)
                            .width(tabWidth)
                            .height(42.dp)
                            .clip(UdhaarShapeTokens.Pill)
                            .background(SelectedPillTint, UdhaarShapeTokens.Pill)
                            .border(width = 1.5.dp, color = PureBlack, shape = UdhaarShapeTokens.Pill)
                    )

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PillTabItem(
                            label = stringResource(R.string.home_tab_active_fmt, activeRecordsCount),
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
                            label = stringResource(R.string.home_tab_people_fmt, peopleCount),
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
                            label = stringResource(R.string.home_tab_history),
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
            }
        },
        floatingActionButton = {
            val fabCorner by animateDpAsState(
                targetValue = if (isFabExpanded) 16.dp else 26.dp,
                animationSpec = if (reducedMotion) {
                    snap()
                } else {
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                },
                label = "fabCorner"
            )
            ExtendedFloatingActionButton(
                onClick = if (isPeopleTab) onAddPartyClick else onAddUdhaarClick,
                expanded = isFabExpanded,
                icon = {
                    Icon(
                        imageVector = if (isPeopleTab) Icons.Default.PersonAdd else Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_udhaar_button),
                        tint = AccentFabInk,
                        modifier = Modifier.size(22.dp)
                    )
                },
                text = {
                    Text(
                        text = if (isPeopleTab) {
                            stringResource(R.string.ui_str_new_contact_82691e)
                        } else {
                            stringResource(R.string.ui_str_new_udhaar_210bee)
                        },
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp
                        ),
                        color = AccentFabInk,
                        maxLines = 1
                    )
                },
                containerColor = AccentFabGreen,
                contentColor = AccentFabInk,
                shape = RoundedCornerShape(fabCorner),
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 10.dp
                ),
                modifier = Modifier
                    .height(52.dp)
                    .animateContentSize(
                        animationSpec = if (reducedMotion) {
                            snap()
                        } else {
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        }
                    )
                    .pressScaleFeedback()
                    .testTag("add_udhaar_fab")
            )
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
                    onToggleFavorite = onToggleFavoriteParty,
                    isInitialLoading = uiState.isInitialLoading,
                    isAmountsHidden = uiState.isAmountsHidden,
                    listState = peopleListState
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

                    if (uiState.showCoachMarks) {
                        item(key = "coach_marks_item") {
                            Box(modifier = udhaarAnimateItem(reducedMotion)) {
                                FirstRunCoachMarksCard(
                                    onSkipOrFinish = onDismissCoachMarks
                                )
                            }
                        }
                    }

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
                            (groupedFilteredItems.size == 1 && groupedFilteredItems.first().isOverdueGroup)

                        groupedFilteredItems.forEach { groupSection ->
                            if (showGroupStickyHeaders) {
                                stickyHeader(key = "group_header_${groupSection.key}") {
                                    val groupTitle = stringResource(groupSection.titleRes)
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
                                        },
                                        onSnoozeClick = onSnoozeTransaction,
                                        onStopRemindersClick = onStopTransactionReminders,
                                        onEnableRemindersClick = onEnableTransactionReminders,
                                        onBorrowerAction = handleBorrowerAction
                                    )
                                }
                            }
                        }
                    }

                    // Footer: User's UPI ID as a small "VPA:" line under the list
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
                                fontSize = 12.5.sp
                            ),
                            color = SecondaryInkGrey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = udhaarAnimateItem(reducedMotion)
                                .fillMaxWidth()
                                .pressScaleFeedback()
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
        targetValue = if (selected) SelectedPillTint.copy(alpha = 0.65f) else CardSurfaceWhite.copy(alpha = 0.85f),
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "pillBgColor"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) PureBlack else BorderGrey,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "pillBorderColor"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 1.5.dp else 1.dp,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "pillBorderWidth"
    )

    Box(
        modifier = modifier
            .height(42.dp)
            .pressScaleFeedback()
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
    isAmountsHidden: Boolean = false,
    onToggleHideAmounts: () -> Unit = {},
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
                            .pressScaleFeedback()
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
                            text = stringResource(R.string.ui_fmt_symbol_c3451e, curr.displayName, count),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = PureBlack,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        val animatedReceivableText = rememberAnimatedCurrencyText(
            targetAmount = totals.totalToReceive,
            currency = selectedCurrency,
            isAmountsHidden = isAmountsHidden,
            durationMillis = 260
        )
        val animatedPayableText = rememberAnimatedCurrencyText(
            targetAmount = totals.totalToPay,
            currency = selectedCurrency,
            isAmountsHidden = isAmountsHidden,
            durationMillis = 260
        )

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
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag("summary_receivable_card"),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.ui_str_total_receivable_6cb1b1),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = ReceivableCardInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onToggleHideAmounts,
                        modifier = Modifier
                            .size(48.dp)
                            .pressScaleFeedback()
                            .testTag("privacy_hide_amounts_toggle")
                    ) {
                        Icon(
                            imageVector = if (isAmountsHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isAmountsHidden) {
                                stringResource(R.string.cd_show_amounts)
                            } else {
                                stringResource(R.string.cd_hide_amounts)
                            },
                            tint = ReceivableCardInk,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = animatedReceivableText,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = IbmPlexMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    color = ReceivableCardInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("summary_total_to_receive")
                )

                Spacer(modifier = Modifier.height(10.dp))

                val activeRecordLabel = if (totals.activeGivenCount == 1) {
                    stringResource(R.string.summary_one_active_record)
                } else {
                    stringResource(R.string.summary_many_active_records_fmt, totals.activeGivenCount)
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
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag("summary_payable_card"),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.ui_str_total_payable_65e9b2),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = PayableCardInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onToggleHideAmounts,
                        modifier = Modifier
                            .size(48.dp)
                            .pressScaleFeedback()
                            .testTag("privacy_hide_amounts_toggle_payable")
                    ) {
                        Icon(
                            imageVector = if (isAmountsHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isAmountsHidden) {
                                stringResource(R.string.cd_show_amounts)
                            } else {
                                stringResource(R.string.cd_hide_amounts)
                            },
                            tint = PayableCardInk,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = animatedPayableText,
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
                    stringResource(R.string.summary_one_record)
                } else {
                    stringResource(R.string.summary_many_records_fmt, totals.activeTakenCount)
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

        // Below them: Net INR Position row with colour cross-fade and count-up animation
        val net = totals.netPosition
        val reducedMotion = rememberReducedMotionEnabled()
        val netColorDuration = if (reducedMotion) 0 else 240
        val netBgColor by animateColorAsState(
            targetValue = when {
                net > 0.005 -> ReceivableCardBg.copy(alpha = 0.35f)
                net < -0.005 -> PayableCardBg.copy(alpha = 0.35f)
                else -> Color.Transparent
            },
            animationSpec = tween(netColorDuration, easing = FastOutSlowInEasing),
            label = "netPositionBgColor"
        )
        val netAmountColor by animateColorAsState(
            targetValue = when {
                net > 0.005 -> ReceivableCardInk
                net < -0.005 -> PayableCardInk
                else -> SecondaryInkGrey
            },
            animationSpec = tween(netColorDuration, easing = FastOutSlowInEasing),
            label = "netPositionAmountColor"
        )
        val animatedNetAbsText = rememberAnimatedCurrencyText(
            targetAmount = kotlin.math.abs(net),
            currency = selectedCurrency,
            isAmountsHidden = isAmountsHidden,
            durationMillis = 260
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(UdhaarShapeTokens.Corner12)
                .background(netBgColor, UdhaarShapeTokens.Corner12)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("net_position_row"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.ui_fmt_net_position_ebdd5e, selectedCurrency.code),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = PureBlack,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val prefix = when {
                isAmountsHidden -> ""
                net > 0.005 -> "+"
                net < -0.005 -> "-"
                else -> ""
            }
            Text(
                text = stringResource(R.string.ui_fmt_symbol_a68a33, prefix, animatedNetAbsText),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = IbmPlexMonoFamily,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = netAmountColor,
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
                    contentDescription = stringResource(R.string.cd_search),
                    tint = SecondaryInkGrey,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.cd_clear_search),
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

                val reducedMotion = rememberReducedMotionEnabled()
                Box(
                    modifier = udhaarAnimateItem(reducedMotion)
                        .height(38.dp)
                        .pressScaleFeedback()
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
            text = stringResource(R.string.ui_fmt_ledger_records_fbeaca, recordCount),
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
                    .pressScaleFeedback()
                    .clip(UdhaarShapeTokens.Corner8)
                    .clickable { sortMenuExpanded = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("sort_transactions_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = stringResource(R.string.cd_sort_and_filter_date_range),
                    tint = SecondaryInkGrey,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = if (dateRangeFilter == DateRangeFilter.ALL_TIME) {
                        transactionSortOption.label
                    } else {
                        stringResource(R.string.ui_fmt_symbol_7ff20b, dateRangeFilter.label, transactionSortOption.label)
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
                                text = stringResource(R.string.ui_fmt_sort_ac397d, option.label),
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
                                text = stringResource(R.string.ui_fmt_range_7e5925, range.label),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTransactionCardItem(
    summary: CalculatedUdhaarSummary,
    partyPhotoUri: String = "",
    isAmountsHidden: Boolean = false,
    unreadCount: Int = 0,
    attachmentsCount: Int = 0,
    onCardClick: () -> Unit,
    onChatClick: () -> Unit = {},
    onWhatsAppClick: () -> Unit,
    onQrClick: () -> Unit,
    onSnoozeClick: (recordId: Long, days: Int) -> Unit = { _, _ -> },
    onStopRemindersClick: (recordId: Long) -> Unit = {},
    onEnableRemindersClick: (recordId: Long) -> Unit = {},
    onBorrowerAction: ((summary: CalculatedUdhaarSummary, actionType: String) -> Unit)? = null
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onCardClick()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onWhatsAppClick()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val bgColor = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> ReceivableCardBg
                SwipeToDismissBoxValue.EndToStart -> MutedChipGrey
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(UdhaarShapeTokens.Corner16)
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else -> Alignment.CenterEnd
                }
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = stringResource(R.string.cd_record_payment),
                                tint = ReceivableCardInk
                            )
                            Text(
                                text = stringResource(R.string.ui_str_record_payment_86e563),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = ReceivableCardInk
                            )
                        }
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.ui_str_send_reminder_cf5eb3),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = PureBlack
                            )
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = stringResource(R.string.cd_send_reminder),
                                tint = PureBlack
                            )
                        }
                    }
                    SwipeToDismissBoxValue.Settled -> {}
                }
            }
        }
    ) {
        TransactionCardItem(
            summary = summary,
            partyPhotoUri = partyPhotoUri,
            isAmountsHidden = isAmountsHidden,
            unreadCount = unreadCount,
            attachmentsCount = attachmentsCount,
            onCardClick = onCardClick,
            onChatClick = onChatClick,
            onWhatsAppClick = onWhatsAppClick,
            onQrClick = onQrClick,
            onSnoozeClick = onSnoozeClick,
            onStopRemindersClick = onStopRemindersClick,
            onEnableRemindersClick = onEnableRemindersClick,
            onBorrowerAction = onBorrowerAction
        )
    }
}

@Composable
internal fun TransactionCardItem(
    summary: CalculatedUdhaarSummary,
    partyPhotoUri: String = "",
    isAmountsHidden: Boolean = false,
    unreadCount: Int = 0,
    attachmentsCount: Int = 0,
    onCardClick: () -> Unit,
    onChatClick: () -> Unit = {},
    onWhatsAppClick: () -> Unit,
    onQrClick: () -> Unit,
    onSnoozeClick: (recordId: Long, days: Int) -> Unit = { _, _ -> },
    onStopRemindersClick: (recordId: Long) -> Unit = {},
    onEnableRemindersClick: (recordId: Long) -> Unit = {},
    onBorrowerAction: ((summary: CalculatedUdhaarSummary, actionType: String) -> Unit)? = null
) {
    val tx = summary.transaction
    val isGiven = tx.transactionType == TransactionType.GIVEN

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .udhaarSharedBounds("tx_card_${tx.id}")
            .pressScaleFeedback(pressedScale = 0.98f)
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
            // Top row: small chips GIVEN, UPI, INR, Status-at-a-glance badge, and the due date on the right
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
                            text = if (isGiven) stringResource(R.string.ui_str_given_567b49) else stringResource(R.string.ui_str_taken_7abbf5),
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

                    // Status at a glance badge: coloured icon + plain-language word (never colour alone)
                    StatusAtAGlanceBadge(
                        summary = summary,
                        modifier = Modifier.testTag("tx_verification_badge_${tx.id}")
                    )

                    if (unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .background(PureBlack, UdhaarShapeTokens.Corner8)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("tx_unread_badge_${tx.id}")
                        ) {
                            Text(
                                text = stringResource(R.string.ui_fmt_symbol_301919, unreadCount),
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
                                text = stringResource(R.string.ui_fmt_symbol_e20cea, attachmentsCount),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = PureBlack,
                                maxLines = 1
                            )
                        }
                    }

                    val now = System.currentTimeMillis()
                    val isSnoozed = tx.snoozedUntilMillis != null && tx.snoozedUntilMillis!! > now
                    if (isSnoozed) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.tertiaryContainer, UdhaarShapeTokens.Corner8)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("tx_snoozed_badge_${tx.id}")
                        ) {
                            Text(
                                text = stringResource(R.string.snoozed_until_fmt, com.jyotirmoyb.udhaar.reminder.ReminderEngine.formatSnoozedUntil(tx.snoozedUntilMillis!!)),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                maxLines = 1
                            )
                        }
                    } else if (tx.remindersDisabled) {
                        Box(
                            modifier = Modifier
                                .background(SubtleSurfaceGrey, UdhaarShapeTokens.Corner8)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("tx_reminders_paused_badge_${tx.id}")
                        ) {
                            Text(
                                text = stringResource(R.string.reminders_stopped_badge),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = SecondaryInkGrey,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Due date on the right in IBM Plex Mono + Reminder Snooze Menu
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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

                    var showReminderMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { showReminderMenu = true },
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("card_reminder_menu_button_${tx.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.cd_more_options),
                                tint = SecondaryInkGrey,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showReminderMenu,
                            onDismissRequest = { showReminderMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.snooze_1_day)) },
                                onClick = {
                                    showReminderMenu = false
                                    onSnoozeClick(tx.id, 1)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.snooze_3_days)) },
                                onClick = {
                                    showReminderMenu = false
                                    onSnoozeClick(tx.id, 3)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.snooze_1_week)) },
                                onClick = {
                                    showReminderMenu = false
                                    onSnoozeClick(tx.id, 7)
                                }
                            )
                            if (tx.remindersDisabled) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.resume_reminders_for_record)) },
                                    onClick = {
                                        showReminderMenu = false
                                        onEnableRemindersClick(tx.id)
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.stop_reminders_for_record)) },
                                    onClick = {
                                        showReminderMenu = false
                                        onStopRemindersClick(tx.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Person's avatar + name with the amount on the right in large type, and the masked phone number below
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PersonAvatar(
                        name = tx.partyName,
                        photoUri = partyPhotoUri,
                        size = 40.dp,
                        modifier = Modifier.udhaarSharedElement("tx_avatar_${tx.id}")
                    )
                    Column(
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
                            textDecoration = if (tx.isSettled) TextDecoration.LineThrough else TextDecoration.None,
                            modifier = Modifier.udhaarSharedBounds("tx_name_${tx.id}")
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
                }

                val displayAmount = if (tx.isSettled) summary.grossTotal else summary.netBalanceDue
                Text(
                    text = InterestCalculator.formatCurrencyPrivacy(displayAmount, tx.currency, isAmountsHidden),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontFamily = IbmPlexMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = PureBlack,
                    maxLines = 1,
                    textDecoration = if (tx.isSettled) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier
                        .udhaarSharedBounds("tx_amount_${tx.id}")
                        .testTag("tx_card_amount_${tx.id}")
                )
            }

            // One line of terms (Amount lent/borrowed, Interest, rate, type, days) and the note in italics
            Text(
                text = InterestCalculator.formatSubtitleBreakdownPrivacy(summary, isAmountsHidden),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = IbmPlexMonoFamily,
                    fontSize = 11.5.sp
                ),
                color = SecondaryInkGrey,
                modifier = Modifier.testTag("tx_card_breakdown_${tx.id}")
            )

            if (tx.notes.isNotBlank()) {
                Text(
                    text = stringResource(R.string.ui_fmt_symbol_c8354b, tx.notes),
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
                    text = if (tx.isSettled) stringResource(R.string.ui_str_tap_for_receipt_693fae) else stringResource(R.string.ui_str_tap_for_repayment_receipt_bc12be),
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
                        .pressScaleFeedback()
                        .clip(UdhaarShapeTokens.Corner8)
                        .clickable(onClick = onChatClick)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("card_chat_button_${tx.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = stringResource(R.string.cd_open_chat_for_fmt, tx.partyName),
                        tint = SecondaryInkGrey,
                        modifier = Modifier.size(16.dp)
                    )
                    if (unreadCount > 0) {
                        Text(
                            text = stringResource(R.string.ui_fmt_chat_40e9c8, unreadCount),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = PureBlack
                        )
                    }
                }
            }

            // Borrower Self-Reminder & Quick Message Actions
            if (!isGiven && !tx.isSettled) {
                val now = System.currentTimeMillis()
                val overdueDays = com.jyotirmoyb.udhaar.reminder.ReminderEngine.calculateOverdueDays(tx.expiryDateMillis, now)
                val dueDateFormatted = tx.expiryDateMillis?.let {
                    java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(it))
                }.orEmpty()
                val selfReminderText = if (overdueDays != null && overdueDays > 0) {
                    stringResource(
                        R.string.borrower_self_reminder_overdue_fmt,
                        InterestCalculator.formatCurrencyPrivacy(summary.netBalanceDue, tx.currency, isAmountsHidden),
                        tx.partyName,
                        overdueDays
                    )
                } else if (overdueDays != null && overdueDays >= -3) {
                    stringResource(
                        R.string.borrower_self_reminder_fmt,
                        InterestCalculator.formatCurrencyPrivacy(summary.netBalanceDue, tx.currency, isAmountsHidden),
                        tx.partyName,
                        dueDateFormatted
                    )
                } else null

                if (selfReminderText != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = UdhaarShapeTokens.Corner8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("borrower_self_reminder_${tx.id}")
                    ) {
                        Text(
                            text = selfReminderText,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { onBorrowerAction?.invoke(summary, "i_paid") },
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("borrower_btn_paid_${tx.id}"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        shape = UdhaarShapeTokens.Corner8
                    ) {
                        Text(stringResource(R.string.borrower_btn_i_paid), style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { onBorrowerAction?.invoke(summary, "need_more_time") },
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("borrower_btn_need_time_${tx.id}"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        shape = UdhaarShapeTokens.Corner8
                    ) {
                        Text(stringResource(R.string.borrower_btn_need_more_time), style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { onBorrowerAction?.invoke(summary, "ask_details") },
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .testTag("borrower_btn_details_${tx.id}"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        shape = UdhaarShapeTokens.Corner8
                    ) {
                        Text(stringResource(R.string.borrower_btn_ask_details), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Two buttons side by side: tonal Get QR/Receipt and filled Send Reminder (minimum 48.dp touch target)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onQrClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .pressScaleFeedback()
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
                        contentDescription = stringResource(R.string.cd_get_qr_or_receipt_for_fmt, tx.partyName),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.ui_str_get_qr_receipt_2aa81f),
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
                        .height(48.dp)
                        .pressScaleFeedback()
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
                        contentDescription = stringResource(R.string.cd_send_reminder_to_fmt, tx.partyName),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.ui_str_send_reminder_1e475a),
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
    onInviteClick: () -> Unit = {},
    onResetFilter: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_ledger_box")
    ) {
        if (hasAnyTransactions) {
            FriendlyEmptyStateCard(
                sentence = stringResource(R.string.empty_filtered_ledger_sentence),
                primaryButtonLabel = stringResource(R.string.empty_filtered_clear_filters),
                onPrimaryClick = onResetFilter
            )
        } else {
            FriendlyEmptyStateCard(
                sentence = stringResource(R.string.empty_ledger_welcome_sentence),
                primaryButtonLabel = stringResource(R.string.empty_ledger_add_first),
                onPrimaryClick = onAddClick,
                secondaryButtonLabel = stringResource(R.string.empty_ledger_invite_friend),
                onSecondaryClick = onInviteClick,
                modifier = Modifier.testTag("empty_state_add_button")
            )
        }
    }
}
