package com.jyotirmoyb.udhaar.ui.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.ui.PartyKhataSummary
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.MutedChipGrey
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.ui.theme.SubtleSurfaceGrey
import com.jyotirmoyb.udhaar.util.InterestCalculator

@Composable
internal fun PartyDirectoryCard(
    khata: PartyKhataSummary,
    enabledCurrencies: List<CurrencyType>,
    isAmountsHidden: Boolean = false,
    unreadCount: Int = 0,
    onClick: () -> Unit,
    onChatClick: () -> Unit = {},
    onAddLoanClick: () -> Unit,
    onToggleFavorite: () -> Unit = {}
) {
    val party = khata.party
    val inrEnabled = CurrencyType.INR in enabledCurrencies
    val nprEnabled = CurrencyType.NPR in enabledCurrencies

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .udhaarSharedBounds("party_card_${party.partyId}_${party.name}")
            .pressScaleFeedback(pressedScale = 0.98f)
            .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
            .background(PureWhite, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag("party_card_${party.name}"),
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
                        .background(PureBlack, RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = party.preferredCurrency.code,
                        style = MaterialTheme.typography.labelSmall,
                        color = PureWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .background(SubtleSurfaceGrey, RoundedCornerShape(8.dp))
                        .border(0.5.dp, BorderGrey, RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ui_fmt_symbol_df7ffb, khata.trustScorePercent, khata.trustBadge),
                        style = MaterialTheme.typography.labelSmall,
                        color = PureBlack,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (party.tagOrNotes.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .background(MutedChipGrey, RoundedCornerShape(8.dp))
                            .border(0.5.dp, BorderGrey, RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = party.tagOrNotes,
                            style = MaterialTheme.typography.labelSmall,
                            color = PureBlack,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (party.upiId.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .background(MutedChipGrey, RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ui_fmt_vpa_12c694, InterestCalculator.maskUpiId(party.upiId)),
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryInkGrey,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .background(PureBlack, RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("party_unread_badge_${party.name}")
                    ) {
                        Text(
                            text = stringResource(R.string.ui_fmt_new_8caadb, unreadCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconButton(
                    onClick = onToggleFavorite,
                    testTag = "party_fav_button_${party.name}",
                    modifier = Modifier.pressScaleFeedback()
                ) {
                    Icon(
                        imageVector = if (party.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = stringResource(R.string.cd_favorite_party),
                        tint = PureBlack,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.ui_fmt_records_b09e18, khata.transactions.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryInkGrey
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PersonAvatar(
                    name = party.name,
                    photoUri = party.photoUri,
                    size = 40.dp,
                    modifier = Modifier.udhaarSharedElement("party_avatar_${party.partyId}_${party.name}")
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = party.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = PureBlack,
                        modifier = Modifier.udhaarSharedBounds("party_name_${party.partyId}_${party.name}")
                    )
                    val contactSub = buildString {
                        if (party.phoneNumber.isNotBlank()) append(InterestCalculator.maskPhoneNumber(party.phoneNumber))
                        if (party.alternatePhoneNumber.isNotBlank()) {
                            if (isNotEmpty()) append(" • Alt: ") else append("Alt: ")
                            append(InterestCalculator.maskPhoneNumber(party.alternatePhoneNumber))
                        }
                        if (party.email.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(party.email)
                        }
                    }
                    if (contactSub.isNotBlank()) {
                        Text(
                            text = contactSub,
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryInkGrey
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val inrNetPaise = khata.inrNetPaise
                val nprNetPaise = khata.nprNetPaise
                if (inrEnabled) {
                    val prefix = when {
                        inrNetPaise > 0L -> "RECEIVE "
                        inrNetPaise < 0L -> "PAY "
                        else -> "SETTLED "
                    }
                    val animatedInrAbs = rememberAnimatedCurrencyText(
                        targetAmount = InterestCalculator.paiseToRupees(kotlin.math.abs(inrNetPaise)),
                        currency = CurrencyType.INR,
                        isAmountsHidden = isAmountsHidden,
                        durationMillis = 240
                    )
                    Text(
                        text = stringResource(R.string.ui_fmt_symbol_3d2c34, prefix, animatedInrAbs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = PureBlack
                    )
                }
                if (nprEnabled && (kotlin.math.abs(nprNetPaise) > 0L || !inrEnabled || party.preferredCurrency == CurrencyType.NPR)) {
                    val prefix = when {
                        nprNetPaise > 0L -> "RECEIVE "
                        nprNetPaise < 0L -> "PAY "
                        else -> "SETTLED "
                    }
                    val animatedNprAbs = rememberAnimatedCurrencyText(
                        targetAmount = InterestCalculator.paiseToRupees(kotlin.math.abs(nprNetPaise)),
                        currency = CurrencyType.NPR,
                        isAmountsHidden = isAmountsHidden,
                        durationMillis = 240
                    )
                    Text(
                        text = stringResource(R.string.ui_fmt_symbol_808375, prefix, animatedNprAbs),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SecondaryInkGrey
                    )
                }
            }
        }

        HorizontalDivider(color = BorderGrey, thickness = 1.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.ui_str_tap_for_party_ledger_024e09),
                style = MaterialTheme.typography.labelSmall,
                color = SecondaryInkGrey,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onChatClick,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .pressScaleFeedback()
                        .testTag("party_card_chat_${party.name}"),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, PureBlack),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (unreadCount > 0) PureBlack else PureWhite,
                        contentColor = if (unreadCount > 0) PureWhite else PureBlack
                    )
                ) {
                    Text(
                        text = if (unreadCount > 0) stringResource(R.string.ui_fmt_chat_d40839, unreadCount) else stringResource(R.string.ui_str_chat_26bc51),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = onAddLoanClick,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .pressScaleFeedback(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, PureBlack),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.ui_str_new_loan_e72467), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
