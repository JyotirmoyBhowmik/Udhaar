package com.jyotirmoyb.udhaar.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.ui.PartyKhataSummary
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.MutedChipGrey
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.ui.theme.SubtleSurfaceGrey
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.InterestCalculator

/**
 * Full-screen Individual Party Ledger View (`PartyDetailScreen`)
 */
@Composable
fun PartyDetailScreen(
    khata: PartyKhataSummary,
    enabledCurrencies: List<CurrencyType>,
    onBack: () -> Unit,
    onEditParty: () -> Unit,
    onDeleteParty: () -> Unit,
    onAddLoanForParty: () -> Unit,
    onTransactionClick: (CalculatedUdhaarSummary) -> Unit,
    onWhatsAppClick: (CalculatedUdhaarSummary) -> Unit,
    onPayPartyViaUpi: (Double?) -> Unit,
    onShareThermalReceipt: () -> Unit,
    onExportPartyStatement: () -> Unit,
    onSharePartyStatement: () -> Unit,
    onExportPartyPdfStatement: () -> Unit = {},
    onShareMyPaymentDetails: () -> Unit = {},
    onRequestPartyPaymentDetails: () -> Unit = {},
    onRequestVerification: () -> Unit = {},
    onOpenTransactionChat: (CalculatedUdhaarSummary) -> Unit = {},
    onOpenPartyGeneralChat: () -> Unit = {},
    onToggleFavorite: (PartyEntity) -> Unit = {},
    unreadPartyChatCount: Int = 0,
    unreadCountByTx: Map<Long, Int> = emptyMap()
) {
    BackHandler {
        onBack()
    }

    val party = khata.party
    val reducedMotion = rememberReducedMotionEnabled()

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
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                    ) {
                        AppIconButton(
                            onClick = onBack,
                            testTag = "party_detail_back_button",
                            modifier = Modifier.pressScaleFeedback()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                                tint = PureBlack
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        PersonAvatar(
                            name = party.name,
                            photoUri = party.photoUri,
                            size = 36.dp,
                            modifier = Modifier.udhaarSharedElement("party_avatar_${party.partyId}_${party.name}")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = party.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = PureBlack,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.udhaarSharedBounds("party_name_${party.partyId}_${party.name}")
                            )
                            Text(
                                text = stringResource(R.string.ui_str_party_khata_ledger_a8382a),
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryInkGrey,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AppIconButton(
                            onClick = { onToggleFavorite(party) },
                            testTag = "party_top_favorite_button",
                            modifier = Modifier
                                .pressScaleFeedback()
                                .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = if (party.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = stringResource(R.string.cd_favorite_party),
                                tint = PureBlack,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        AppIconButton(
                            onClick = onShareThermalReceipt,
                            testTag = "party_top_thermal_receipt_button",
                            modifier = Modifier
                                .pressScaleFeedback()
                                .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = stringResource(R.string.ui_str_share_receipt_statement_image_pdf_f07d4e),
                                tint = PureBlack,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        AppIconButton(
                            onClick = onEditParty,
                            testTag = "edit_party_top_button",
                            modifier = Modifier
                                .pressScaleFeedback()
                                .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.ui_str_edit_party_profile_728a8a),
                                tint = PureBlack,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (party.partyId > 0L) {
                            AppIconButton(
                                onClick = onDeleteParty,
                                testTag = "delete_party_top_button",
                                modifier = Modifier
                                    .pressScaleFeedback()
                                    .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = stringResource(R.string.ui_str_delete_d6f563),
                                    tint = PureBlack,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = BorderGrey, thickness = 1.dp)
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddLoanForParty,
                containerColor = PureBlack,
                contentColor = PureWhite,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .pressScaleFeedback()
                    .border(1.dp, PureWhite, RoundedCornerShape(12.dp))
                    .testTag("party_add_loan_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.ui_str_new_loan_e72467),
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
                    .testTag("party_detail_lazy_list"),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "party_detail_summary_card") {
                    PartyDetailSummaryCard(
                        khata = khata,
                        enabledCurrencies = enabledCurrencies,
                        unreadCount = unreadPartyChatCount,
                        reducedMotion = reducedMotion,
                        onEditParty = onEditParty,
                        onWhatsAppClick = onWhatsAppClick,
                        onPayPartyViaUpi = onPayPartyViaUpi,
                        onShareThermalReceipt = onShareThermalReceipt,
                        onExportPartyStatement = onExportPartyStatement,
                        onSharePartyStatement = onSharePartyStatement,
                        onExportPartyPdfStatement = onExportPartyPdfStatement,
                        onShareMyPaymentDetails = onShareMyPaymentDetails,
                        onRequestPartyPaymentDetails = onRequestPartyPaymentDetails,
                        onRequestVerification = onRequestVerification,
                        onOpenPartyGeneralChat = onOpenPartyGeneralChat
                    )
                }

                item(key = "party_ledger_records_header") {
                    Text(
                        text = stringResource(R.string.ui_fmt_individual_party_ledger_records_598892, khata.transactions.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = SecondaryInkGrey,
                        modifier = udhaarAnimateItem(reducedMotion)
                    )
                }

                if (khata.transactions.isEmpty()) {
                    item(key = "party_ledger_empty_state") {
                        Box(
                            modifier = udhaarAnimateItem(reducedMotion)
                                .fillMaxWidth()
                                .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                                .background(SubtleSurfaceGrey, RoundedCornerShape(12.dp))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.ui_fmt_no_transactions_recorded_with_yet_301581, party.name),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SecondaryInkGrey,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(
                        items = khata.transactions,
                        key = { it.transaction.id }
                    ) { summary ->
                        val tx = summary.transaction
                        val txUnread = unreadCountByTx[tx.id] ?: 0
                        val isGiven = tx.transactionType == TransactionType.GIVEN
                        Column(
                            modifier = udhaarAnimateItem(reducedMotion)
                                .fillMaxWidth()
                                .udhaarSharedBounds("tx_card_${tx.id}")
                                .pressScaleFeedback(pressedScale = 0.98f)
                                .border(
                                    width = if (txUnread > 0) 1.5.dp else 1.dp,
                                    color = if (txUnread > 0) PureBlack else BorderGrey,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .background(if (tx.isSettled) SubtleSurfaceGrey else PureWhite, RoundedCornerShape(12.dp))
                                .clickable { onTransactionClick(summary) }
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .background(if (isGiven) PureBlack else PureWhite, RoundedCornerShape(8.dp))
                                            .border(1.dp, PureBlack, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isGiven) stringResource(R.string.ui_str_given_567b49) else stringResource(R.string.ui_str_taken_7abbf5),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isGiven) PureWhite else PureBlack
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(MutedChipGrey, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.ui_fmt_symbol_00ae54, tx.paymentMode.label.uppercase(), tx.currency.code),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PureBlack
                                        )
                                    }
                                    if (txUnread > 0) {
                                        Box(
                                            modifier = Modifier
                                                .background(PureBlack, RoundedCornerShape(8.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.ui_fmt_new_6d0cc5, txUnread),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = PureWhite
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = summary.dueStatusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondaryInkGrey
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = summary.subtitleBreakdown,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryInkGrey,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val displayPaise = if (tx.isSettled) summary.grossTotalPaise else summary.netBalanceDuePaise
                                Text(
                                    text = InterestCalculator.formatCurrencyPaise(displayPaise, tx.currency),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = PureBlack,
                                    textDecoration = if (tx.isSettled) TextDecoration.LineThrough else TextDecoration.None,
                                    modifier = Modifier.udhaarSharedBounds("tx_amount_${tx.id}")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.ui_str_tap_for_repayment_details_cc0f87),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondaryInkGrey,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = { onOpenTransactionChat(summary) },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .pressScaleFeedback()
                                            .testTag("party_tx_chat_button_${tx.id}"),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, PureBlack),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (txUnread > 0) PureBlack else PureWhite,
                                            contentColor = if (txUnread > 0) PureWhite else PureBlack
                                        )
                                    ) {
                                        Text(
                                            text = if (txUnread > 0) stringResource(R.string.ui_fmt_chat_12c59c, txUnread) else stringResource(R.string.ui_str_chat_26bc51),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { onWhatsAppClick(summary) },
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .pressScaleFeedback()
                                            .testTag("party_tx_remind_button_${tx.id}"),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, PureBlack),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = PureBlack,
                                            contentColor = PureWhite
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = stringResource(R.string.cd_whatsapp_reminder),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.ui_str_remind_ea51a8), style = MaterialTheme.typography.labelSmall, color = PureWhite)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
