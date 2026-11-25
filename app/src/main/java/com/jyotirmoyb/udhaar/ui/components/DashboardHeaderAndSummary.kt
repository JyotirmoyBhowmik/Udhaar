package com.jyotirmoyb.udhaar.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Settings
import com.jyotirmoyb.udhaar.ui.icons.UdhaarIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.LedgerFilter
import com.jyotirmoyb.udhaar.ui.CurrencyLedgerTotals
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.CardSurfaceWhite
import com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily
import com.jyotirmoyb.udhaar.ui.theme.PayableCardBg
import com.jyotirmoyb.udhaar.ui.theme.PayableCardInk
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.ui.theme.ReceivableCardBg
import com.jyotirmoyb.udhaar.ui.theme.ReceivableCardInk
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.ui.theme.SelectedPillTint
import com.jyotirmoyb.udhaar.ui.theme.SpaceGroteskFamily
import com.jyotirmoyb.udhaar.ui.theme.UdhaarShapeTokens
import com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing

internal enum class HomePillTab {
    ACTIVE,
    PEOPLE,
    HISTORY
}

internal fun compactCurrencyDisplay(raw: String): String =
    raw.replace("₹ ", "₹\u200A").replace("रू ", "रू\u200A")

@Composable
internal fun DashboardTopHeaderBar(
    themeSubtitle: String,
    selectedPillTab: HomePillTab,
    activeRecordsCount: Int,
    peopleCount: Int,
    reducedMotion: Boolean,
    selectedFilter: LedgerFilter,
    onOpenGroupSplit: () -> Unit,
    onOpenUpdatesScreen: () -> Unit,
    onSettingsClick: () -> Unit,
    onSelectActiveTab: () -> Unit,
    onSelectPeopleTab: () -> Unit,
    onSelectHistoryTab: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PureWhite)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
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
                        letterSpacing = 0.sp
                    ),
                    color = PureBlack,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = themeSubtitle,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = SecondaryInkGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("header_theme_subtitle")
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconButton(
                    onClick = onOpenGroupSplit,
                    modifier = Modifier
                        .pressScaleFeedback()
                        .testTag("header_group_split_button")
                ) {
                    Icon(
                        imageVector = UdhaarIcons.PersonAdd,
                        contentDescription = stringResource(R.string.group_split_sheet_title),
                        tint = PureBlack,
                        modifier = Modifier.size(22.dp)
                    )
                }
                AppIconButton(
                    onClick = onOpenUpdatesScreen,
                    modifier = Modifier
                        .pressScaleFeedback()
                        .testTag("open_inbox_updates_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = stringResource(R.string.updates_screen_title),
                        tint = PureBlack,
                        modifier = Modifier.size(24.dp)
                    )
                }
                AppIconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .pressScaleFeedback()
                        .testTag("open_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.settings_title),
                        tint = PureBlack,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(UdhaarSpacing.Small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillTabItem(
                label = stringResource(R.string.home_tab_active_fmt, activeRecordsCount),
                selected = selectedPillTab == HomePillTab.ACTIVE,
                onClick = onSelectActiveTab,
                modifier = Modifier
                    .weight(1f)
                    .testTag("nav_tab_ledger")
            )

            PillTabItem(
                label = stringResource(R.string.home_tab_people_fmt, peopleCount),
                selected = selectedPillTab == HomePillTab.PEOPLE,
                onClick = onSelectPeopleTab,
                modifier = Modifier
                    .weight(1f)
                    .testTag("nav_tab_people_directory")
            )

            PillTabItem(
                label = stringResource(R.string.home_tab_history),
                selected = selectedPillTab == HomePillTab.HISTORY,
                onClick = onSelectHistoryTab,
                modifier = Modifier
                    .weight(1f)
                    .testTag("nav_tab_history")
            )
        }
    }
}

@Composable
internal fun PillTabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) SelectedPillTint else CardSurfaceWhite,
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
            .heightIn(min = 48.dp)
            .pressScaleFeedback()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(UdhaarShapeTokens.Pill)
                .background(color = bgColor, shape = UdhaarShapeTokens.Pill)
                .border(width = borderWidth, color = borderColor, shape = UdhaarShapeTokens.Pill)
                .padding(horizontal = 6.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = PureBlack,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TopLedgerSummaryCard(
    selectedCurrency: CurrencyType,
    enabledCurrencies: List<CurrencyType>,
    totals: CurrencyLedgerTotals,
    inrCount: Int,
    nprCount: Int,
    selectedFilter: LedgerFilter = LedgerFilter.ALL,
    isAmountsHidden: Boolean = false,
    onToggleHideAmounts: () -> Unit = {},
    onSelectCurrency: (CurrencyType) -> Unit,
    onSelectReceivableFilter: () -> Unit = {},
    onSelectPayableFilter: () -> Unit = {},
    onDoubleTapReceivable: () -> Unit = {},
    onDoubleTapPayable: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("top_summary_card"),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (enabledCurrencies.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                            .heightIn(min = 48.dp)
                            .pressScaleFeedback()
                            .clickable { onSelectCurrency(curr) }
                            .testTag("summary_currency_tab_${curr.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(UdhaarShapeTokens.Corner8)
                                .background(chipBg, UdhaarShapeTokens.Corner8)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) PureBlack else BorderGrey,
                                    shape = UdhaarShapeTokens.Corner8
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.ui_fmt_symbol_c3451e, curr.displayName, count),
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = PureBlack,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        val animatedReceivableText = compactCurrencyDisplay(
            rememberAnimatedCurrencyText(
                targetAmount = totals.totalToReceive,
                currency = selectedCurrency,
                isAmountsHidden = isAmountsHidden,
                durationMillis = 260
            )
        )
        val animatedPayableText = compactCurrencyDisplay(
            rememberAnimatedCurrencyText(
                targetAmount = totals.totalToPay,
                currency = selectedCurrency,
                isAmountsHidden = isAmountsHidden,
                durationMillis = 260
            )
        )

        val isReceivableSelected = selectedFilter == LedgerFilter.GIVEN
        val isPayableSelected = selectedFilter == LedgerFilter.TAKEN

        val receivableRecordLabel = if (totals.activeGivenCount == 1) {
            stringResource(R.string.summary_one_active_record)
        } else {
            stringResource(R.string.summary_many_active_records_fmt, totals.activeGivenCount)
        }
        val payableRecordLabel = if (totals.activeTakenCount == 1) {
            stringResource(R.string.summary_one_record)
        } else {
            stringResource(R.string.summary_many_records_fmt, totals.activeTakenCount)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryMetricCard(
                title = stringResource(R.string.ui_str_total_receivable_6cb1b1),
                amountText = animatedReceivableText,
                recordCountLabel = receivableRecordLabel,
                baseBgColor = ReceivableCardBg,
                inkColor = ReceivableCardInk,
                isSelected = isReceivableSelected,
                isAmountsHidden = isAmountsHidden,
                cardTestTag = "summary_receivable_card",
                amountTestTag = "summary_total_to_receive",
                privacyToggleTestTag = "privacy_hide_amounts_toggle",
                onClick = onSelectReceivableFilter,
                onDoubleClick = onDoubleTapReceivable,
                onToggleHideAmounts = onToggleHideAmounts,
                modifier = Modifier.weight(1f)
            )

            SummaryMetricCard(
                title = stringResource(R.string.ui_str_total_payable_65e9b2),
                amountText = animatedPayableText,
                recordCountLabel = payableRecordLabel,
                baseBgColor = PayableCardBg,
                inkColor = PayableCardInk,
                isSelected = isPayableSelected,
                isAmountsHidden = isAmountsHidden,
                cardTestTag = "summary_payable_card",
                amountTestTag = "summary_total_to_pay",
                privacyToggleTestTag = "privacy_hide_amounts_toggle_payable",
                onClick = onSelectPayableFilter,
                onDoubleClick = onDoubleTapPayable,
                onToggleHideAmounts = onToggleHideAmounts,
                modifier = Modifier.weight(1f)
            )
        }

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
        val animatedNetAbsText = compactCurrencyDisplay(
            rememberAnimatedCurrencyText(
                targetAmount = kotlin.math.abs(net),
                currency = selectedCurrency,
                isAmountsHidden = isAmountsHidden,
                durationMillis = 260
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(UdhaarShapeTokens.Corner8)
                .background(netBgColor, UdhaarShapeTokens.Corner8)
                .border(1.dp, BorderGrey.copy(alpha = 0.5f), UdhaarShapeTokens.Corner8)
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .testTag("net_position_row"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.ui_fmt_net_position_ebdd5e, selectedCurrency.code),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 11.5.sp,
                    letterSpacing = (-0.2).sp,
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
                    fontSize = 13.sp,
                    letterSpacing = (-0.5).sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = netAmountColor,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SummaryMetricCard(
    title: String,
    amountText: String,
    recordCountLabel: String,
    baseBgColor: Color,
    inkColor: Color,
    isSelected: Boolean,
    isAmountsHidden: Boolean,
    cardTestTag: String,
    amountTestTag: String,
    privacyToggleTestTag: String,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onToggleHideAmounts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardBg by animateColorAsState(
        targetValue = if (isSelected) lerp(baseBgColor, inkColor, 0.14f) else baseBgColor,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "${cardTestTag}_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) inkColor else inkColor.copy(alpha = 0.22f),
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "${cardTestTag}_border"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "${cardTestTag}_border_width"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .then(
                if (isSelected) Modifier.shadow(3.dp, UdhaarShapeTokens.Corner16) else Modifier
            )
            .clip(UdhaarShapeTokens.Corner16)
            .background(cardBg, UdhaarShapeTokens.Corner16)
            .border(width = borderWidth, color = borderColor, shape = UdhaarShapeTokens.Corner16)
            .semantics { selected = isSelected }
            .combinedClickable(
                onClick = onClick,
                onDoubleClick = onDoubleClick
            )
            .testTag(cardTestTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(end = 22.dp)
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(inkColor, CircleShape)
                            .testTag("${cardTestTag}_selected_dot")
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 11.5.sp,
                        lineHeight = 14.sp,
                        letterSpacing = (-0.25).sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                    ),
                    color = inkColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            BasicText(
                text = amountText,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = IbmPlexMonoFamily,
                    fontWeight = FontWeight.Bold,
                    lineHeight = TextUnit.Unspecified,
                    letterSpacing = (-0.85).sp
                ),
                color = { inkColor },
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 8.5.sp,
                    maxFontSize = 15.sp,
                    stepSize = 0.5.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(amountTestTag)
            )

            Text(
                text = recordCountLabel,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 10.5.sp,
                    letterSpacing = (-0.2).sp,
                    fontWeight = FontWeight.Medium
                ),
                color = inkColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .clip(UdhaarShapeTokens.Corner8)
                .pressScaleFeedback()
                .clickable(onClick = onToggleHideAmounts)
                .padding(top = 8.dp, end = 8.dp)
                .testTag(privacyToggleTestTag),
            contentAlignment = Alignment.TopEnd
        ) {
            Icon(
                imageVector = if (isAmountsHidden) UdhaarIcons.VisibilityOff else UdhaarIcons.Visibility,
                contentDescription = if (isAmountsHidden) {
                    stringResource(R.string.cd_show_amounts)
                } else {
                    stringResource(R.string.cd_hide_amounts)
                },
                tint = inkColor,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
internal fun DashboardFooterVpaLine(
    upiId: String,
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit
) {
    val vpaDisplay = if (upiId.isNotBlank()) {
        com.jyotirmoyb.udhaar.util.InterestCalculator.maskUpiId(upiId)
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
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .pressScaleFeedback()
            .clickable { onSettingsClick() }
            .padding(horizontal = 4.dp, vertical = 10.dp)
            .testTag("footer_vpa_line")
    )
}
