package com.jyotirmoyb.udhaar.ui.people

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.ui.PartyKhataSummary
import com.jyotirmoyb.udhaar.ui.PartySortOption

/**
 * Modular People Directory list screen entrypoint in `ui/people/`.
 */
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
    com.jyotirmoyb.udhaar.ui.components.PeopleDirectoryContent(
        partyKhataList = partyKhataList,
        enabledCurrencies = enabledCurrencies,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onAddPartyClick = onAddPartyClick,
        onPartyClick = onPartyClick,
        onQuickAddLoanForParty = onQuickAddLoanForParty,
        unreadCountByPartyName = unreadCountByPartyName,
        onOpenPartyGeneralChat = onOpenPartyGeneralChat,
        partySortOption = partySortOption,
        onSortOptionChange = onSortOptionChange,
        duplicatePairsCount = duplicatePairsCount,
        onOpenMergeDuplicates = onOpenMergeDuplicates,
        onOpenContactPicker = onOpenContactPicker,
        onToggleFavorite = onToggleFavorite,
        isInitialLoading = isInitialLoading,
        isAmountsHidden = isAmountsHidden,
        listState = listState
    )
}
