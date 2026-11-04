package com.jyotirmoyb.udhaar.ui.sheets.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.ui.components.PersonAvatar
import com.jyotirmoyb.udhaar.ui.components.pressScaleFeedback
import com.jyotirmoyb.udhaar.ui.components.udhaarSharedBounds
import com.jyotirmoyb.udhaar.ui.components.udhaarSharedElement

@Composable
fun TransactionDetailHeader(
    tx: UdhaarTransaction,
    linkedParty: PartyEntity?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PersonAvatar(
                name = tx.partyName,
                photoUri = linkedParty?.photoUri.orEmpty(),
                size = 40.dp,
                modifier = Modifier.udhaarSharedElement("tx_avatar_${tx.id}")
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.partyName.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.udhaarSharedBounds("tx_name_${tx.id}")
                )
                Text(
                    text = stringResource(
                        R.string.ui_fmt_symbol_41590b,
                        tx.transactionType.badgeLabel,
                        tx.currency.code,
                        tx.paymentMode.label
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .pressScaleFeedback()
                .testTag("close_detail_modal_button")
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}
