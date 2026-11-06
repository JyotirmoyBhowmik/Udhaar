package com.jyotirmoyb.udhaar.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.Installment
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.CardSurfaceWhite
import com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily
import com.jyotirmoyb.udhaar.ui.theme.MutedChipGrey
import com.jyotirmoyb.udhaar.ui.theme.PayableCardBg
import com.jyotirmoyb.udhaar.ui.theme.PayableCardInk
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.ReceivableCardBg
import com.jyotirmoyb.udhaar.ui.theme.ReceivableCardInk
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.ui.theme.SelectedPillTint
import com.jyotirmoyb.udhaar.ui.theme.SubtleSurfaceGrey
import com.jyotirmoyb.udhaar.ui.theme.UdhaarShapeTokens
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.GroupSplitCalculator
import com.jyotirmoyb.udhaar.util.InstallmentCalculator
import com.jyotirmoyb.udhaar.util.InterestCalculator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeableTransactionCardItem(
    summary: CalculatedUdhaarSummary,
    partyPhotoUri: String = "",
    isAmountsHidden: Boolean = false,
    unreadCount: Int = 0,
    attachmentsCount: Int = 0,
    installments: List<Installment> = emptyList(),
    defaultExpanded: Boolean = false,
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
            installments = installments,
            defaultExpanded = defaultExpanded,
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
    installments: List<Installment> = emptyList(),
    defaultExpanded: Boolean = false,
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
    var isCardExpanded by rememberSaveable(tx.id, defaultExpanded) { mutableStateOf(defaultExpanded) }
    val groupTag = remember(tx.notes) { GroupSplitCalculator.extractGroupName(tx.notes) }
    val installmentProgress = remember(installments) {
        if (installments.isNotEmpty()) {
            InstallmentCalculator.computeProgress(installments)
        } else null
    }

    val displayAmount = if (tx.isSettled) summary.grossTotal else summary.netBalanceDue
    val formattedCardAmount = InterestCalculator.formatCurrencyPrivacy(displayAmount, tx.currency, isAmountsHidden)
    val cardAmountFontSize = when {
        formattedCardAmount.length <= 8 -> 19.sp
        formattedCardAmount.length <= 11 -> 16.sp
        else -> 14.sp
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .udhaarSharedBounds("tx_card_${tx.id}")
            .pressScaleFeedback(pressedScale = 0.98f)
            .testTag("transaction_card_${tx.id}"),
        shape = UdhaarShapeTokens.Corner16,
        color = if (tx.isSettled) SubtleSurfaceGrey else CardSurfaceWhite,
        shadowElevation = 2.dp,
        border = BorderStroke(
            width = if (summary.isOverdue || unreadCount > 0) 1.5.dp else 1.dp,
            color = if (summary.isOverdue) PayableCardInk.copy(alpha = 0.5f) else BorderGrey.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
                .clickable {
                    if (!isCardExpanded) {
                        isCardExpanded = true
                    } else {
                        onCardClick()
                    }
                }
                .padding(horizontal = 12.dp, vertical = if (isCardExpanded) 12.dp else 9.dp),
            verticalArrangement = Arrangement.spacedBy(if (isCardExpanded) 8.dp else 5.dp)
        ) {
            if (!isCardExpanded) {
                // v19 Row 1: [Avatar] [Name...] [Given pill] [Payment mode pill] ... [Amount] [v]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PersonAvatar(
                            name = tx.partyName,
                            photoUri = partyPhotoUri,
                            size = 32.dp,
                            modifier = Modifier.udhaarSharedElement("tx_avatar_${tx.id}")
                        )
                        Text(
                            text = tx.partyName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = PureBlack,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (tx.isSettled) TextDecoration.LineThrough else TextDecoration.None,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .udhaarSharedBounds("tx_name_${tx.id}")
                        )
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isGiven) PureBlack else PayableCardBg,
                                    shape = UdhaarShapeTokens.Pill
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isGiven) stringResource(R.string.ui_str_given_567b49) else stringResource(R.string.ui_str_taken_7abbf5),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (isGiven) CardSurfaceWhite else PayableCardInk,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(MutedChipGrey, UdhaarShapeTokens.Pill)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = tx.paymentMode.shortBadge,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = PureBlack,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        if (groupTag != null) {
                            Box(
                                modifier = Modifier
                                    .background(SelectedPillTint, UdhaarShapeTokens.Pill)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = groupTag,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = PureBlack,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = formattedCardAmount,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontFamily = IbmPlexMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = cardAmountFontSize
                            ),
                            color = PureBlack,
                            maxLines = 1,
                            softWrap = false,
                            textDecoration = if (tx.isSettled) TextDecoration.LineThrough else TextDecoration.None,
                            modifier = Modifier
                                .udhaarSharedBounds("tx_amount_${tx.id}")
                                .testTag("tx_card_amount_${tx.id}")
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.card_expand_details),
                            tint = PureBlack,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // v19 Row 2: [Amount lent: ₹ ... | Interest: ...] ... [Show details]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = InterestCalculator.formatSubtitleBreakdownPrivacy(summary, isAmountsHidden),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = IbmPlexMonoFamily,
                            fontSize = 11.5.sp
                        ),
                        color = SecondaryInkGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                            .testTag("tx_card_breakdown_${tx.id}")
                    )
                    Text(
                        text = stringResource(R.string.card_expand_details),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = PureBlack,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .clickable { isCardExpanded = true }
                            .padding(vertical = 2.dp)
                            .testTag("tx_card_expand_toggle_${tx.id}")
                    )
                }
            } else {
                ExpandedTransactionCardContent(
                    summary = summary,
                    partyPhotoUri = partyPhotoUri,
                    isAmountsHidden = isAmountsHidden,
                    unreadCount = unreadCount,
                    attachmentsCount = attachmentsCount,
                    installmentProgress = installmentProgress,
                    formattedCardAmount = formattedCardAmount,
                    cardAmountFontSize = cardAmountFontSize,
                    onCollapse = { isCardExpanded = false },
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
    }
}
