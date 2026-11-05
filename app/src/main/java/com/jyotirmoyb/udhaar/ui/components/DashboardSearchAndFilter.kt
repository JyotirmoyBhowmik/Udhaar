package com.jyotirmoyb.udhaar.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
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
import com.jyotirmoyb.udhaar.data.PartialPayment
import com.jyotirmoyb.udhaar.data.TransactionSortOption
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.VerificationStatus
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.CardSurfaceWhite
import com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily
import com.jyotirmoyb.udhaar.ui.theme.MutedInkGrey
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.ui.theme.SelectedPillTint
import com.jyotirmoyb.udhaar.ui.theme.SubtleSurfaceGrey
import com.jyotirmoyb.udhaar.ui.theme.UdhaarShapeTokens
import com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary

@Composable
internal fun SearchAndFilterBar(
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

    Column(verticalArrangement = Arrangement.spacedBy(UdhaarSpacing.Small)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(UdhaarShapeTokens.Corner12)
                .background(CardSurfaceWhite)
                .border(1.dp, BorderGrey, UdhaarShapeTokens.Corner12)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.cd_search),
                    tint = SecondaryInkGrey,
                    modifier = Modifier.size(18.dp)
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_placeholder),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = MutedInkGrey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            color = PureBlack
                        ),
                        cursorBrush = SolidColor(PureBlack),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_search_input")
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .clip(CircleShape)
                            .clickable { onSearchQueryChange("") }
                            .testTag("clear_search_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.cd_clear_search),
                            tint = SecondaryInkGrey,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        LazyRow(
            state = lazyRowState,
            flingBehavior = snapFlingBehavior,
            contentPadding = PaddingValues(start = 2.dp, end = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                        .heightIn(min = UdhaarSpacing.MinTouchTarget)
                        .pressScaleFeedback()
                        .clip(UdhaarShapeTokens.Corner8)
                        .background(color = chipBg, shape = UdhaarShapeTokens.Corner8)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) PureBlack else BorderGrey,
                            shape = UdhaarShapeTokens.Corner8
                        )
                        .clickable { onSelectFilter(filter) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("filter_chip_${filter.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(UdhaarSpacing.Small)
                    ) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
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
                                .padding(horizontal = 5.dp, vertical = 0.5.dp)
                                .testTag("filter_badge_${filter.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = IbmPlexMonoFamily,
                                    fontSize = 12.sp,
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
internal fun LedgerSectionHeaderRow(
    recordCount: Int,
    transactionSortOption: TransactionSortOption,
    dateRangeFilter: DateRangeFilter,
    onOpenGroupSplit: () -> Unit = {},
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .pressScaleFeedback()
                    .clip(UdhaarShapeTokens.Corner8)
                    .background(SelectedPillTint.copy(alpha = 0.55f), UdhaarShapeTokens.Corner8)
                    .border(1.dp, PureBlack.copy(alpha = 0.4f), UdhaarShapeTokens.Corner8)
                    .clickable { onOpenGroupSplit() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("open_group_split_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = stringResource(R.string.group_split_button),
                    tint = PureBlack,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = stringResource(R.string.group_split_button),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = PureBlack,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Box {
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .pressScaleFeedback()
                        .clip(UdhaarShapeTokens.Corner8)
                        .background(CardSurfaceWhite, UdhaarShapeTokens.Corner8)
                        .border(1.dp, BorderGrey, UdhaarShapeTokens.Corner8)
                        .clickable { sortMenuExpanded = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("sort_transactions_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = stringResource(R.string.cd_sort_and_filter_date_range),
                        tint = PureBlack,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (dateRangeFilter == DateRangeFilter.ALL_TIME) {
                            transactionSortOption.label
                        } else {
                            stringResource(R.string.ui_fmt_symbol_7ff20b, dateRangeFilter.label, transactionSortOption.label)
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = PureBlack,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
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
}
