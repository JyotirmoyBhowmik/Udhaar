package com.jyotirmoyb.udhaar.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.ui.PartyKhataSummary
import com.jyotirmoyb.udhaar.ui.PartySortOption
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.ui.theme.SubtleSurfaceGrey

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
    var selectedAdaptivePartyId by rememberSaveable { mutableStateOf<Long?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWidePane = maxWidth >= 600.dp
        val activeKhata = partyKhataList.firstOrNull { it.party.partyId == selectedAdaptivePartyId }
            ?: partyKhataList.firstOrNull()

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(if (isWidePane) 0.55f else 1f)
                    .fillMaxHeight()
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
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = SubtleSurfaceGrey,
                                    contentColor = PureBlack
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
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
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = PureWhite,
                                    contentColor = PureBlack
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
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
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
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
                                AppIconButton(onClick = { onSearchQueryChange("") }) {
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
                                onClick = {
                                    selectedAdaptivePartyId = khata.party.partyId
                                    onPartyClick(khata)
                                },
                                onChatClick = { onOpenPartyGeneralChat(khata.party) },
                                onAddLoanClick = { onQuickAddLoanForParty(khata.party) },
                                onToggleFavorite = { onToggleFavorite(khata.party) }
                            )
                        }
                    }
                }
            }

            if (isWidePane && activeKhata != null) {
                Box(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                        .padding(top = 12.dp, end = 12.dp, bottom = 12.dp)
                        .testTag("people_adaptive_detail_pane")
                ) {
                    PartyDetailSummaryCard(
                        khata = activeKhata,
                        enabledCurrencies = enabledCurrencies,
                        unreadCount = unreadCountByPartyName[activeKhata.party.name.trim().lowercase()] ?: 0,
                        reducedMotion = reducedMotion,
                        onEditParty = { onPartyClick(activeKhata) },
                        onWhatsAppClick = {},
                        onPayPartyViaUpi = {},
                        onShareThermalReceipt = { onPartyClick(activeKhata) },
                        onExportPartyStatement = { onPartyClick(activeKhata) },
                        onSharePartyStatement = { onPartyClick(activeKhata) },
                        onExportPartyPdfStatement = { onPartyClick(activeKhata) },
                        onShareMyPaymentDetails = {},
                        onRequestPartyPaymentDetails = {},
                        onRequestVerification = { onPartyClick(activeKhata) },
                        onOpenPartyGeneralChat = { onOpenPartyGeneralChat(activeKhata.party) }
                    )
                }
            }
        }
    }
}
