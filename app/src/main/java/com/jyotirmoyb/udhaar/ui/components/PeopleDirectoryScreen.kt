package com.jyotirmoyb.udhaar.ui.components
import com.jyotirmoyb.udhaar.R

import androidx.compose.ui.res.stringResource

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.ui.PartyKhataSummary
import com.jyotirmoyb.udhaar.ui.PartySortOption
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.MutedChipGrey
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.ui.theme.SubtleSurfaceGrey
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.InterestCalculator

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeopleDirectoryContent(
    partyKhataList: List<PartyKhataSummary>,
    enabledCurrencies: List<CurrencyType>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAddPartyClick: () -> Unit,
    onPartyClick: (PartyKhataSummary) -> Unit,
    onQuickAddLoanForParty: (PartyEntity) -> Unit,
    unreadCountByPartyName: Map<String, Int> = emptyMap(),
    onOpenPartyGeneralChat: (PartyEntity) -> Unit = {},
    partySortOption: PartySortOption = PartySortOption.NAME,
    onSortOptionChange: (PartySortOption) -> Unit = {},
    duplicatePairsCount: Int = 0,
    onOpenMergeDuplicates: () -> Unit = {},
    onOpenContactPicker: () -> Unit = {},
    onToggleFavorite: (PartyEntity) -> Unit = {},
    isInitialLoading: Boolean = false,
    isAmountsHidden: Boolean = false,
    listState: LazyListState = rememberLazyListState()
) {
    val reducedMotion = rememberReducedMotionEnabled()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 700.dp)
            .testTag("people_directory_lazy_list"),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 12.dp,
            bottom = 88.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Directory Header Card
        item(key = "people_header_card") {
            Row(
                modifier = udhaarAnimateItem(reducedMotion)
                    .fillMaxWidth()
                    .border(1.5.dp, PureBlack, RoundedCornerShape(12.dp))
                    .background(PureBlack, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = stringResource(R.string.ui_str_people_parties_khata_815a6c),
                        style = MaterialTheme.typography.titleLarge,
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.ui_fmt_contacts_tap_for_individual_ledger_ccac4b, partyKhataList.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = BorderGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onOpenContactPicker,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PureWhite),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = SubtleSurfaceGrey,
                            contentColor = PureBlack
                        ),
                        modifier = Modifier
                            .height(34.dp)
                            .pressScaleFeedback()
                            .testTag("import_contacts_directory_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Contacts,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.ui_str_import_b034fe), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onAddPartyClick,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PureWhite),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = PureWhite,
                            contentColor = PureBlack
                        ),
                        modifier = Modifier
                            .height(34.dp)
                            .pressScaleFeedback()
                            .testTag("add_party_directory_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.ui_str_add_party_8ae111), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Duplicate Parties Alert Banner
        if (duplicatePairsCount > 0) {
            item(key = "duplicate_parties_banner") {
                Row(
                    modifier = udhaarAnimateItem(reducedMotion)
                        .fillMaxWidth()
                        .border(1.5.dp, PureBlack, RoundedCornerShape(12.dp))
                        .background(SubtleSurfaceGrey, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = PureBlack, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.ui_fmt_duplicate_parties_detected_f2a253, duplicatePairsCount),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = PureBlack
                            )
                            Text(
                                text = stringResource(R.string.ui_str_matching_normalized_phone_or_name_5dde72),
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryInkGrey
                            )
                        }
                    }
                    Button(
                        onClick = onOpenMergeDuplicates,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PureBlack, contentColor = PureWhite),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .pressScaleFeedback()
                            .testTag("open_merge_duplicates_button")
                    ) {
                        Icon(Icons.Default.Merge, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.ui_str_merge_1e0d71), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Search Bar
        item(key = "people_search_bar") {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.ui_str_search_people_by_name_phone_d675ac),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryInkGrey
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.cd_search),
                        tint = PureBlack
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.cd_clear_search),
                                tint = PureBlack
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PureBlack,
                    unfocusedBorderColor = BorderGrey,
                    cursorColor = PureBlack,
                    focusedContainerColor = PureWhite,
                    unfocusedContainerColor = SubtleSurfaceGrey
                ),
                modifier = udhaarAnimateItem(reducedMotion)
                    .fillMaxWidth()
                    .testTag("people_search_input")
            )
        }

        // Sort Options Filter Chips
        item(key = "people_sort_options") {
            FlowRow(
                modifier = udhaarAnimateItem(reducedMotion)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PartySortOption.entries.forEach { option ->
                    val isSelected = partySortOption == option
                    MonochromeTogglePill(
                        text = stringResource(R.string.people_sort_chip_fmt, option.label.uppercase()),
                        selected = isSelected,
                        onClick = { onSortOptionChange(option) },
                        modifier = Modifier.testTag("sort_party_${option.name.lowercase()}")
                    )
                }
            }
        }

        if (isInitialLoading || partyKhataList.isEmpty()) {
            item(key = "people_empty_or_shimmer_state") {
                Box(modifier = udhaarAnimateItem(reducedMotion)) {
                    Crossfade(
                        targetState = isInitialLoading,
                        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 220),
                        label = "peopleInitialLoadingCrossfade"
                    ) { isLoading ->
                        if (isLoading) {
                            ShimmerLedgerPlaceholder()
                        } else if (partyKhataList.isEmpty()) {
                            FriendlyEmptyStateCard(
                                sentence = stringResource(R.string.people_empty_sentence),
                                primaryButtonLabel = stringResource(R.string.people_empty_add_first),
                                onPrimaryClick = onAddPartyClick,
                                secondaryButtonLabel = stringResource(R.string.people_empty_import_contacts),
                                onSecondaryClick = onOpenContactPicker
                            )
                        }
                    }
                }
            }
        } else {
            items(
                items = partyKhataList,
                key = { "${it.party.partyId}_${it.party.name}" }
            ) { khata ->
                val partyUnread = unreadCountByPartyName[khata.party.name.trim().lowercase()] ?: 0
                Box(modifier = udhaarAnimateItem(reducedMotion)) {
                    PartyDirectoryCard(
                        khata = khata,
                        enabledCurrencies = enabledCurrencies,
                        isAmountsHidden = isAmountsHidden,
                        unreadCount = partyUnread,
                        onClick = { onPartyClick(khata) },
                        onChatClick = { onOpenPartyGeneralChat(khata.party) },
                        onAddLoanClick = { onQuickAddLoanForParty(khata.party) },
                        onToggleFavorite = { onToggleFavorite(khata.party) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PartyDirectoryCard(
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
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
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
                            text = party.tagOrNotes.uppercase(),
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
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(48.dp)
                        .pressScaleFeedback()
                        .testTag("party_fav_button_${party.name}")
                ) {
                    Icon(
                        imageVector = if (party.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (party.isFavorite) "Favorite" else "Not favorite",
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
                val inrNet = khata.inrNet
                val nprNet = khata.nprNet
                if (inrEnabled) {
                    val prefix = when {
                        inrNet > 0.01 -> "RECEIVE "
                        inrNet < -0.01 -> "PAY "
                        else -> "SETTLED "
                    }
                    val animatedInrAbs = rememberAnimatedCurrencyText(
                        targetAmount = kotlin.math.abs(inrNet),
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
                if (nprEnabled && (kotlin.math.abs(nprNet) >= 0.01 || !inrEnabled || party.preferredCurrency == CurrencyType.NPR)) {
                    val prefix = when {
                        nprNet > 0.01 -> "RECEIVE "
                        nprNet < -0.01 -> "PAY "
                        else -> "SETTLED "
                    }
                    val animatedNprAbs = rememberAnimatedCurrencyText(
                        targetAmount = kotlin.math.abs(nprNet),
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
                        .height(32.dp)
                        .pressScaleFeedback()
                        .testTag("party_card_chat_${party.name}"),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, PureBlack),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
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
                        .height(32.dp)
                        .pressScaleFeedback(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, PureBlack),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
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

/**
 * Full-screen Individual Party Ledger View (`PartyDetailScreen`)
 */
@OptIn(ExperimentalLayoutApi::class)
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

    val context = LocalContext.current
    val party = khata.party
    val unreadCount = unreadPartyChatCount
    val inrEnabled = CurrencyType.INR in enabledCurrencies
    val nprEnabled = CurrencyType.NPR in enabledCurrencies
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
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .pressScaleFeedback()
                                .testTag("party_detail_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to People Directory",
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

                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        IconButton(
                            onClick = { onToggleFavorite(party) },
                            modifier = Modifier
                                .size(36.dp)
                                .pressScaleFeedback()
                                .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                                .testTag("party_top_favorite_button")
                        ) {
                            Icon(
                                imageVector = if (party.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = if (party.isFavorite) "Favorite" else "Not favorite",
                                tint = PureBlack,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onShareThermalReceipt,
                            modifier = Modifier
                                .size(36.dp)
                                .pressScaleFeedback()
                                .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                                .testTag("party_top_thermal_receipt_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = "Share Receipt / Statement",
                                tint = PureBlack,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onEditParty,
                            modifier = Modifier
                                .size(36.dp)
                                .pressScaleFeedback()
                                .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                                .testTag("edit_party_top_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Party Profile",
                                tint = PureBlack,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (party.partyId > 0L) {
                            IconButton(
                                onClick = onDeleteParty,
                                modifier = Modifier
                                    .size(36.dp)
                                    .pressScaleFeedback()
                                    .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                                    .testTag("delete_party_top_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Party Profile",
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
                    Icon(Icons.Default.Add, contentDescription = "Add Loan", modifier = Modifier.size(18.dp))
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
                // Party Metadata & Balance Summary Card
                item(key = "party_detail_summary_card") {
                    Column(
                        modifier = udhaarAnimateItem(reducedMotion)
                            .fillMaxWidth()
                            .udhaarSharedBounds("party_card_${party.partyId}_${party.name}")
                            .border(1.5.dp, PureBlack, RoundedCornerShape(12.dp))
                            .background(PureWhite, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val meta = buildString {
                            if (party.tagOrNotes.isNotBlank()) append("Tag: ${party.tagOrNotes}")
                            if (party.phoneNumber.isNotBlank()) {
                                if (isNotEmpty()) append("  •  ")
                                append("Phone: ${InterestCalculator.maskPhoneNumber(party.phoneNumber)}")
                            }
                            if (party.upiId.isNotBlank()) {
                                if (isNotEmpty()) append("  •  ")
                                append("UPI VPA: ${InterestCalculator.maskUpiId(party.upiId)}")
                            }
                            if (party.email.isNotBlank()) {
                                if (isNotEmpty()) append("  •  ")
                                append("Email: ${party.email}")
                            }
                        }
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryInkGrey
                            )
                        }

                        // Phase 7: Owner Trust Score & Repayment Metrics Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, PureBlack, RoundedCornerShape(12.dp))
                                .background(SubtleSurfaceGrey, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                                .testTag("party_trust_profile_card"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.ui_str_repayment_trust_summary_015512),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PureBlack
                                )
                                Box(
                                    modifier = Modifier
                                        .background(PureBlack, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.ui_fmt_symbol_e770b0, khata.trustBadge, khata.trustScorePercent),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PureWhite,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.ui_str_oldest_overdue_14e0f2), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = SecondaryInkGrey)
                                    Text(
                                        text = if (khata.oldestOverdueDays > 0) stringResource(R.string.ui_fmt_days_3773dd, khata.oldestOverdueDays) else stringResource(R.string.ui_str_none_on_time_b96c7f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = PureBlack
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.ui_str_avg_repayment_time_433a7d), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = SecondaryInkGrey)
                                    Text(
                                        text = if (khata.averageRepaymentDays > 0) stringResource(R.string.ui_fmt_days_75db18, khata.averageRepaymentDays.toInt()) else stringResource(R.string.ui_str_no_settlements_0c38ad),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = PureBlack
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.ui_str_trust_rating_visible_only_to_32c545),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = SecondaryInkGrey
                            )
                        }

                        // Enabled Currencies Net Balance Box
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, PureBlack, RoundedCornerShape(12.dp))
                        ) {
                            if (inrEnabled) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(PureBlack)
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.ui_str_inr_net_position_02ba82),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BorderGrey,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val inrLabel = when {
                                        khata.inrNet > 0.01 -> "RECEIVE ${InterestCalculator.formatCurrency(khata.inrNet, CurrencyType.INR)}"
                                        khata.inrNet < -0.01 -> "PAY ${InterestCalculator.formatCurrency(-khata.inrNet, CurrencyType.INR)}"
                                        else -> "₹0 (SETTLED)"
                                    }
                                    Text(
                                        text = inrLabel,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = PureWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.ui_fmt_given_taken_5521ec, InterestCalculator.formatCurrency(khata.inrToReceive, CurrencyType.INR), InterestCalculator.formatCurrency(khata.inrToPay, CurrencyType.INR)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BorderGrey,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (nprEnabled) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (!inrEnabled) PureBlack else PureWhite)
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.ui_str_npr_net_position_d959f5),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (!inrEnabled) BorderGrey else SecondaryInkGrey,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val nprLabel = when {
                                        khata.nprNet > 0.01 -> "RECEIVE ${InterestCalculator.formatCurrency(khata.nprNet, CurrencyType.NPR)}"
                                        khata.nprNet < -0.01 -> "PAY ${InterestCalculator.formatCurrency(-khata.nprNet, CurrencyType.NPR)}"
                                        else -> "रू0 (SETTLED)"
                                    }
                                    Text(
                                        text = nprLabel,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = if (!inrEnabled) PureWhite else PureBlack,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = stringResource(R.string.ui_fmt_given_taken_962d59, InterestCalculator.formatCurrency(khata.nprToReceive, CurrencyType.NPR), InterestCalculator.formatCurrency(khata.nprToPay, CurrencyType.NPR)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (!inrEnabled) BorderGrey else SecondaryInkGrey,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Quick Actions: Call, WhatsApp, SMS, Email
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (party.phoneNumber.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        val dialIntent = Intent(
                                            Intent.ACTION_DIAL,
                                            Uri.parse("tel:${party.phoneNumber.filter { it.isDigit() || it == '+' }}")
                                        )
                                        runCatching { context.startActivity(dialIntent) }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, PureBlack),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.ui_str_call_1b3cb4), style = MaterialTheme.typography.labelMedium)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val firstUnsettled = khata.transactions.firstOrNull { !it.transaction.isSettled }
                                            ?: khata.transactions.firstOrNull()
                                        if (firstUnsettled != null) {
                                            onWhatsAppClick(firstUnsettled)
                                        } else {
                                            val msg = "Hello ${party.name}, sharing our current Udhaar ledger summary."
                                            com.jyotirmoyb.udhaar.util.WhatsAppReminderLauncher.launchReminder(
                                                context = context,
                                                rawPhoneNumber = party.phoneNumber,
                                                currency = party.preferredCurrency,
                                                message = msg
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, PureBlack),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack),
                                    modifier = Modifier.testTag("party_whatsapp_quick_action_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.ui_str_whatsapp_5c39d5), style = MaterialTheme.typography.labelMedium)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val smsIntent = Intent(
                                            Intent.ACTION_SENDTO,
                                            Uri.parse("smsto:${party.phoneNumber.filter { it.isDigit() || it == '+' }}")
                                        )
                                        runCatching { context.startActivity(smsIntent) }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, PureBlack),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack),
                                    modifier = Modifier.testTag("party_sms_quick_action_button")
                                ) {
                                    Icon(Icons.Default.Message, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.ui_str_sms_9e10d4), style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            if (party.email.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        val mailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("mailto:${party.email.trim()}")
                                            putExtra(Intent.EXTRA_SUBJECT, "Udhaar Ledger Statement — ${party.name}")
                                        }
                                        runCatching {
                                            context.startActivity(Intent.createChooser(mailIntent, "Email ${party.name}"))
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, PureBlack),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                                ) {
                                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.ui_str_email_564269), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        // Quick Action: Request Verification / Link User ID
                        OutlinedButton(
                            onClick = onRequestVerification,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("party_request_verification_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, PureBlack),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                        ) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (party.linkedUserId.isNotBlank()) stringResource(R.string.ui_fmt_verified_contact_833521, party.linkedUserId) else stringResource(R.string.ui_str_request_verification_link_id_40daa2),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Enhancement B: Thermal-Style Monochrome Statement Receipt (Image & PDF Share)
                        Button(
                            onClick = onShareThermalReceipt,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("party_share_thermal_receipt_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PureBlack,
                                contentColor = PureWhite
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.ui_str_share_receipt_statement_image_pdf_f07d4e),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        // Phase 8: Export Official Printable PDF Statement
                        OutlinedButton(
                            onClick = onExportPartyPdfStatement,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("party_export_pdf_statement_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, PureBlack),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.ui_str_export_pdf_statement_audit_trail_c9613d),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Enhancement D: Pay Party via UPI Action
                        val amountWeOweInr = khata.inrToPay.takeIf { it > 0.0 }
                        if (inrEnabled) {
                            OutlinedButton(
                                onClick = {
                                    if (party.upiId.isNotBlank()) {
                                        onPayPartyViaUpi(amountWeOweInr)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Add ${party.name}'s UPI ID to generate their Payback QR.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onEditParty()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("pay_party_via_upi_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, PureBlack),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                            ) {
                                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (party.upiId.isNotBlank()) {
                                        stringResource(R.string.ui_fmt_pay_party_via_upi_27379b, InterestCalculator.maskUpiId(party.upiId))
                                    } else {
                                        stringResource(R.string.ui_str_set_party_upi_id_to_58d127)
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Phase 3: Share my payment details & Request their payment details (QR and link)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onShareMyPaymentDetails,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("party_share_my_payment_details_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, PureBlack),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                            ) {
                                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.ui_str_share_my_payment_details_c9cc51),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(
                                onClick = onRequestPartyPaymentDetails,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("party_request_payment_details_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, PureBlack),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.ui_str_request_their_details_03e3b9),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Phase 4: Open General Party Chat Thread
                        Button(
                            onClick = onOpenPartyGeneralChat,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("party_open_general_chat_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PureBlack,
                                contentColor = PureWhite
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (unreadCount > 0) {
                                    stringResource(R.string.ui_fmt_open_party_chat_thread_unread_55743b, unreadCount)
                                } else {
                                    stringResource(R.string.ui_str_open_party_chat_thread_b19bc9)
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        // Export / Share Statement JSON Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onExportPartyStatement,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("export_party_statement_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, PureBlack),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                            ) {
                                Text(stringResource(R.string.ui_str_save_statement_json_d2f264), style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = onSharePartyStatement,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("share_party_statement_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, PureBlack),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.ui_str_share_backup_json_525a50), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Party Ledger Records Header
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
                                val displayAmt = if (tx.isSettled) summary.grossTotal else summary.netBalanceDue
                                Text(
                                    text = InterestCalculator.formatCurrency(displayAmt, tx.currency),
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
                                    color = SecondaryInkGrey
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = { onOpenTransactionChat(summary) },
                                        modifier = Modifier
                                            .height(40.dp)
                                            .pressScaleFeedback()
                                            .testTag("party_tx_chat_button_${tx.id}"),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, PureBlack),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
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
                                            .height(40.dp)
                                            .pressScaleFeedback()
                                            .testTag("party_tx_remind_button_${tx.id}"),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, PureBlack),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = PureBlack,
                                            contentColor = PureWhite
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = "Remind on WhatsApp",
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditPartyModal(
    initialParty: PartyEntity?,
    enabledCurrencies: List<CurrencyType>,
    defaultCurrency: CurrencyType,
    onSave: (PartyEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var name by rememberSaveable { mutableStateOf(initialParty?.name ?: "") }
    var phoneNumber by rememberSaveable { mutableStateOf(initialParty?.phoneNumber ?: "") }
    var alternatePhoneNumber by rememberSaveable { mutableStateOf(initialParty?.alternatePhoneNumber ?: "") }
    var upiId by rememberSaveable { mutableStateOf(initialParty?.upiId ?: "") }
    var email by rememberSaveable { mutableStateOf(initialParty?.email ?: "") }
    var tagOrNotes by rememberSaveable { mutableStateOf(initialParty?.tagOrNotes ?: "") }
    var notes by rememberSaveable { mutableStateOf(initialParty?.notes ?: "") }
    var photoUri by rememberSaveable { mutableStateOf(initialParty?.photoUri ?: "") }
    var preferredCurrency by rememberSaveable {
        mutableStateOf(
            initialParty?.preferredCurrency?.takeIf { it in enabledCurrencies }
                ?: defaultCurrency
        )
    }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            photoUri = uri.toString()
        }
    }

    // Zero-permission Phone Contact Picker
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        com.jyotirmoyb.udhaar.util.ContactPickerHelper.handleContactPickerResult(
            context = context,
            result = result
        ) { pickedName, pickedPhone ->
            if (pickedName.isNotBlank()) {
                name = pickedName
                errorText = null
            }
            if (pickedPhone.isNotBlank()) {
                phoneNumber = pickedPhone
            }
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PureBlack,
        unfocusedBorderColor = BorderGrey,
        focusedLabelColor = PureBlack,
        unfocusedLabelColor = SecondaryInkGrey,
        cursorColor = PureBlack,
        focusedContainerColor = PureWhite,
        unfocusedContainerColor = SubtleSurfaceGrey
    )

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PureWhite,
        contentColor = PureBlack,
        contentWindowInsets = { WindowInsets.navigationBars.union(WindowInsets.ime) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .align(Alignment.CenterHorizontally)
                .imePadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (initialParty == null) stringResource(R.string.ui_str_add_party_to_directory_b2e803) else stringResource(R.string.ui_str_edit_party_profile_728a8a),
                        style = MaterialTheme.typography.titleLarge,
                        color = PureBlack
                    )
                    Text(
                        text = stringResource(R.string.ui_str_people_contact_management_ed3067),
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryInkGrey
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_party_modal_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = PureBlack)
                }
            }

            HorizontalDivider(color = BorderGrey, thickness = 1.dp)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = PureBlack,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Avatar & Photo Picker Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                        .background(SubtleSurfaceGrey, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (photoUri.isNotBlank()) {
                        AsyncImage(
                            model = photoUri,
                            contentDescription = "Party Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .border(1.dp, BorderGrey, CircleShape)
                        )
                    } else {
                        val init = name.trim().take(1).uppercase()
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(PureBlack, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = init.ifEmpty { stringResource(R.string.ui_str_symbol_5bab61) },
                                style = MaterialTheme.typography.titleMedium,
                                color = PureWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.ui_str_contact_avatar_photo_0e492b),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PureBlack
                        )
                        Text(
                            text = stringResource(R.string.ui_str_add_a_photo_from_your_06773b),
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryInkGrey
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, PureBlack),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp).testTag("party_pick_photo_button")
                        ) {
                            Text(stringResource(R.string.ui_str_pick_photo_52f2aa), style = MaterialTheme.typography.labelSmall)
                        }
                        if (photoUri.isNotBlank()) {
                            OutlinedButton(
                                onClick = { photoUri = "" },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(stringResource(R.string.ui_str_clear_16ec74), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            errorText = null
                        },
                        label = { Text(stringResource(R.string.ui_str_contact_party_name_197908)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        colors = textFieldColors,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("party_modal_name_input")
                    )
                    OutlinedButton(
                        onClick = {
                            com.jyotirmoyb.udhaar.util.ContactPickerHelper.launchSystemContactPicker(
                                context = context,
                                launcher = contactPickerLauncher
                            )
                        },
                        modifier = Modifier
                            .height(56.dp)
                            .padding(top = 6.dp)
                            .testTag("party_modal_import_contact_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PureBlack),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                    ) {
                        Icon(Icons.Default.Contacts, contentDescription = "Import Contact", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.ui_str_import_b034fe), style = MaterialTheme.typography.labelSmall)
                    }
                }

                var showPrimaryPhone by rememberSaveable { mutableStateOf(false) }
                var showAlternatePhone by rememberSaveable { mutableStateOf(false) }
                var showPartyUpi by rememberSaveable { mutableStateOf(false) }

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = {
                        phoneNumber = it
                        showPrimaryPhone = true
                    },
                    label = { Text(stringResource(R.string.ui_fmt_primary_phone_auto_formats_ea3fe8, preferredCurrency.defaultCountryCode)) },
                    placeholder = { Text(stringResource(R.string.ui_fmt_98_10_db26d9, preferredCurrency.defaultCountryCode)) },
                    singleLine = true,
                    visualTransformation = if (showPrimaryPhone) VisualTransformation.None else PhoneMaskVisualTransformation,
                    trailingIcon = if (phoneNumber.isNotBlank()) {
                        {
                            IconButton(onClick = { showPrimaryPhone = !showPrimaryPhone }) {
                                Icon(
                                    imageVector = if (showPrimaryPhone) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPrimaryPhone) "Mask phone number" else "Show phone number",
                                    tint = PureBlack
                                )
                            }
                        }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("party_modal_phone_input")
                )

                OutlinedTextField(
                    value = alternatePhoneNumber,
                    onValueChange = {
                        alternatePhoneNumber = it
                        showAlternatePhone = true
                    },
                    label = { Text(stringResource(R.string.ui_str_alternate_phone_optional_cab788)) },
                    placeholder = { Text(stringResource(R.string.ui_fmt_98_10_db26d9, preferredCurrency.defaultCountryCode)) },
                    singleLine = true,
                    visualTransformation = if (showAlternatePhone) VisualTransformation.None else PhoneMaskVisualTransformation,
                    trailingIcon = if (alternatePhoneNumber.isNotBlank()) {
                        {
                            IconButton(onClick = { showAlternatePhone = !showAlternatePhone }) {
                                Icon(
                                    imageVector = if (showAlternatePhone) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showAlternatePhone) "Mask alternate phone" else "Show alternate phone",
                                    tint = PureBlack
                                )
                            }
                        }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("party_modal_alternate_phone_input")
                )

                OutlinedTextField(
                    value = upiId,
                    onValueChange = {
                        upiId = it
                        showPartyUpi = true
                    },
                    label = { Text(stringResource(R.string.ui_str_party_s_personal_upi_id_cac571)) },
                    placeholder = { Text(stringResource(R.string.ui_str_e_g_98_10_okaxis_1930b4)) },
                    singleLine = true,
                    visualTransformation = if (showPartyUpi) VisualTransformation.None else UpiMaskVisualTransformation,
                    trailingIcon = if (upiId.isNotBlank()) {
                        {
                            IconButton(onClick = { showPartyUpi = !showPartyUpi }) {
                                Icon(
                                    imageVector = if (showPartyUpi) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPartyUpi) "Mask UPI ID" else "Show UPI ID",
                                    tint = PureBlack
                                )
                            }
                        }
                    } else null,
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("party_modal_upi_input")
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.ui_str_email_address_optional_84af0a)) },
                    placeholder = { Text(stringResource(R.string.ui_str_contact_example_com_8ff597)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("party_modal_email_input")
                )

                OutlinedTextField(
                    value = tagOrNotes,
                    onValueChange = { tagOrNotes = it },
                    label = { Text(stringResource(R.string.ui_str_tag_e_g_friend_supplier_914be3)) },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("party_modal_tag_input")
                )

                // Quick Tag Chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Friend", "Supplier", "Client", "Family", "Staff").forEach { presetTag ->
                        MonochromeTogglePill(
                            text = presetTag,
                            selected = tagOrNotes.equals(presetTag, ignoreCase = true),
                            onClick = { tagOrNotes = presetTag }
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.ui_str_private_notes_optional_826160)) },
                    placeholder = { Text(stringResource(R.string.ui_str_e_g_preferred_repayment_timing_5fc919)) },
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("party_modal_notes_input")
                )

                if (enabledCurrencies.size > 1) {
                    Text(
                        text = stringResource(R.string.ui_str_preferred_currency_e4d09c),
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryInkGrey
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        enabledCurrencies.forEach { curr ->
                            MonochromeTogglePill(
                                text = curr.displayName,
                                selected = preferredCurrency == curr,
                                onClick = { preferredCurrency = curr },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        if (name.trim().isEmpty()) {
                            errorText = "Please enter the Party / Contact Name."
                            return@Button
                        }
                        val entity = (initialParty ?: PartyEntity(name = "")).copy(
                            partyId = initialParty?.partyId?.takeIf { it > 0L } ?: 0L,
                            name = name.trim(),
                            phoneNumber = phoneNumber.trim(),
                            alternatePhoneNumber = alternatePhoneNumber.trim(),
                            upiId = upiId.trim(),
                            email = email.trim(),
                            tagOrNotes = tagOrNotes.trim(),
                            notes = notes.trim(),
                            photoUri = photoUri.trim(),
                            preferredCurrency = preferredCurrency
                        )
                        onSave(entity)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_party_modal_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PureBlack,
                        contentColor = PureWhite
                    )
                ) {
                    Text(stringResource(R.string.ui_str_save_contact_profile_53021f), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
