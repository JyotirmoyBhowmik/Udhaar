package com.jyotirmoyb.udhaar.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.repository.LedgerRepository
import com.jyotirmoyb.udhaar.data.repository.PartyRepository
import com.jyotirmoyb.udhaar.data.repository.SettingsRepository
import com.jyotirmoyb.udhaar.ui.ActiveScreen
import com.jyotirmoyb.udhaar.ui.DuplicatePartyPair
import com.jyotirmoyb.udhaar.ui.PartyKhataSummary
import com.jyotirmoyb.udhaar.ui.PartySortOption
import com.jyotirmoyb.udhaar.ui.PreselectedPartyDraft
import com.jyotirmoyb.udhaar.ui.state.AppStateHolder
import com.jyotirmoyb.udhaar.util.DeviceContact
import com.jyotirmoyb.udhaar.util.DeviceContactHelper
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.PdfStatementGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class PeopleUiState(
    val allParties: List<PartyEntity> = emptyList(),
    val partyKhataList: List<PartyKhataSummary> = emptyList(),
    val filteredPartyKhataList: List<PartyKhataSummary> = emptyList(),
    val duplicatePartyPairs: List<DuplicatePartyPair> = emptyList(),
    val peopleSearchQuery: String = "",
    val partySortOption: PartySortOption = PartySortOption.NAME,
    val editingParty: PartyEntity? = null,
    val showAddEditPartyModal: Boolean = false,
    val receiptModalPartyId: Long? = null,
    val showMergeDuplicatesModal: Boolean = false,
    val showInAppContactPicker: Boolean = false,
    val deviceContacts: List<DeviceContact> = emptyList(),
    val isLoadingContacts: Boolean = false
)

@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val partyRepository: PartyRepository,
    private val ledgerRepository: LedgerRepository,
    private val settingsRepository: SettingsRepository,
    private val appStateHolder: AppStateHolder,
    @ApplicationContext private val appContext: Context? = null
) : ViewModel() {

    private val _peopleSearchQuery = MutableStateFlow("")
    val peopleSearchQuery: StateFlow<String> = _peopleSearchQuery.asStateFlow()

    private val _partySortOption = MutableStateFlow(PartySortOption.NAME)
    val partySortOption: StateFlow<PartySortOption> = _partySortOption.asStateFlow()

    private val _editingParty = MutableStateFlow<PartyEntity?>(null)
    val editingParty: StateFlow<PartyEntity?> = _editingParty.asStateFlow()

    private val _showAddEditPartyModal = MutableStateFlow(false)
    val showAddEditPartyModal: StateFlow<Boolean> = _showAddEditPartyModal.asStateFlow()

    private val _receiptModalPartyId = MutableStateFlow<Long?>(null)
    val receiptModalPartyId: StateFlow<Long?> = _receiptModalPartyId.asStateFlow()

    private val _showMergeDuplicatesModal = MutableStateFlow(false)
    val showMergeDuplicatesModal: StateFlow<Boolean> = _showMergeDuplicatesModal.asStateFlow()

    private val _showInAppContactPicker = MutableStateFlow(false)
    val showInAppContactPicker: StateFlow<Boolean> = _showInAppContactPicker.asStateFlow()

    private val _deviceContacts = MutableStateFlow<List<DeviceContact>>(emptyList())
    val deviceContacts: StateFlow<List<DeviceContact>> = _deviceContacts.asStateFlow()

    private val _isLoadingContacts = MutableStateFlow(false)
    val isLoadingContacts: StateFlow<Boolean> = _isLoadingContacts.asStateFlow()

    val uiState: StateFlow<PeopleUiState> = combine(
        partyRepository.allParties,
        ledgerRepository.allTransactions,
        settingsRepository.userSettings,
        combine(
            _peopleSearchQuery,
            _partySortOption,
            _editingParty,
            _showAddEditPartyModal,
            _receiptModalPartyId
        ) { query, sort, editing, showModal, receiptPartyId ->
            PeopleParams(query, sort, editing, showModal, receiptPartyId)
        },
        combine(
            _showMergeDuplicatesModal,
            _showInAppContactPicker,
            _deviceContacts,
            _isLoadingContacts
        ) { merge, picker, contacts, loading ->
            ContactParams(merge, picker, contacts, loading)
        }
    ) { parties, transactions, settings, pParams, cParams ->
        val nowMillis = System.currentTimeMillis()
        val enabledCurrencySet = settings.enabledCurrencies.toSet()

        val calculatedList = transactions.map { tx ->
            InterestCalculator.calculate(tx, nowMillis)
        }

        val savedPartiesByName = parties.associateBy { it.name.trim().lowercase() }.toMutableMap()
        val syntheticParties = mutableListOf<PartyEntity>()
        var syntheticIdCounter = -1L
        for (calc in calculatedList) {
            val key = calc.transaction.partyName.trim().lowercase()
            if (key.isNotEmpty() && !savedPartiesByName.containsKey(key)) {
                val synthetic = PartyEntity(
                    partyId = syntheticIdCounter--,
                    name = calc.transaction.partyName.trim(),
                    phoneNumber = calc.transaction.phoneNumber,
                    preferredCurrency = calc.transaction.currency,
                    createdAtMillis = calc.transaction.createdAtMillis
                )
                savedPartiesByName[key] = synthetic
                syntheticParties.add(synthetic)
            }
        }
        val combinedParties = (parties + syntheticParties).sortedBy { it.name.lowercase() }

        val khataList = combinedParties.map { party ->
            val partyTxs = calculatedList.filter { item ->
                ((party.partyId > 0L && item.transaction.partyId == party.partyId) ||
                    item.transaction.partyName.trim().equals(party.name.trim(), ignoreCase = true)) &&
                    item.transaction.currency in enabledCurrencySet
            }
            val activePartyTxs = partyTxs.filter { !it.transaction.isSettled }
            val inrGiven = activePartyTxs.filter {
                it.transaction.currency == CurrencyType.INR && it.transaction.transactionType == TransactionType.GIVEN
            }.sumOf { it.netBalanceDue }
            val inrTaken = activePartyTxs.filter {
                it.transaction.currency == CurrencyType.INR && it.transaction.transactionType == TransactionType.TAKEN
            }.sumOf { it.netBalanceDue }
            val nprGiven = activePartyTxs.filter {
                it.transaction.currency == CurrencyType.NPR && it.transaction.transactionType == TransactionType.GIVEN
            }.sumOf { it.netBalanceDue }
            val nprTaken = activePartyTxs.filter {
                it.transaction.currency == CurrencyType.NPR && it.transaction.transactionType == TransactionType.TAKEN
            }.sumOf { it.netBalanceDue }

            val overdueTxs = activePartyTxs.filter { it.isOverdue }
            val oldestOverdueDays = if (overdueTxs.isNotEmpty()) {
                val oldestExpiry = overdueTxs.mapNotNull { it.transaction.expiryDateMillis }.minOrNull()
                if (oldestExpiry != null && nowMillis > oldestExpiry) {
                    (nowMillis - oldestExpiry) / 86_400_000L
                } else 0L
            } else 0L

            val settledTxs = partyTxs.filter { it.transaction.isSettled }
            val averageRepaymentDays = if (settledTxs.isNotEmpty()) {
                val totalDays = settledTxs.sumOf { s ->
                    val end = s.transaction.settledAtMillis ?: nowMillis
                    maxOf(1L, (end - s.transaction.startDateMillis) / 86_400_000L)
                }
                totalDays.toDouble() / settledTxs.size
            } else 0.0

            val totalCompleted = settledTxs.size
            val overdueCount = overdueTxs.size
            val (trustScore, trustBadge) = when {
                partyTxs.isEmpty() -> Pair(100, "NEW")
                overdueCount == 0 && totalCompleted > 0 -> Pair(100, "EXCELLENT")
                overdueCount == 0 && totalCompleted == 0 -> Pair(95, "GOOD")
                overdueCount > 0 && totalCompleted > overdueCount -> {
                    val score = maxOf(40, (100 - (overdueCount * 25)))
                    Pair(score, "FAIR")
                }
                overdueCount > 0 -> {
                    val score = maxOf(10, (60 - (overdueCount * 20)))
                    Pair(score, "CAUTION")
                }
                else -> Pair(80, "GOOD")
            }

            PartyKhataSummary(
                party = party,
                transactions = partyTxs,
                inrToReceive = inrGiven,
                inrToPay = inrTaken,
                nprToReceive = nprGiven,
                nprToPay = nprTaken,
                oldestOverdueDays = oldestOverdueDays,
                averageRepaymentDays = averageRepaymentDays,
                trustScorePercent = trustScore,
                trustBadge = trustBadge
            )
        }

        val duplicatePairs = mutableListOf<DuplicatePartyPair>()
        val phonePartyMap = mutableMapOf<String, PartyEntity>()
        val namePartyMap = mutableMapOf<String, PartyEntity>()

        for (p in parties) {
            val normPhone = InterestCalculator.normalizeToE164(p.phoneNumber, p.preferredCurrency)
            if (normPhone.isNotBlank()) {
                val existing = phonePartyMap[normPhone]
                if (existing != null && existing.partyId != p.partyId) {
                    duplicatePairs.add(
                        DuplicatePartyPair(
                            primaryParty = existing,
                            duplicateParty = p,
                            matchReason = "Same Phone Number ($normPhone)"
                        )
                    )
                } else {
                    phonePartyMap[normPhone] = p
                }
            }
            val cleanName = p.name.trim().lowercase()
            if (cleanName.isNotBlank()) {
                val existing = namePartyMap[cleanName]
                if (existing != null && existing.partyId != p.partyId && !duplicatePairs.any { it.duplicateParty.partyId == p.partyId && it.primaryParty.partyId == existing.partyId }) {
                    duplicatePairs.add(
                        DuplicatePartyPair(
                            primaryParty = existing,
                            duplicateParty = p,
                            matchReason = "Identical Name (${p.name})"
                        )
                    )
                } else {
                    namePartyMap[cleanName] = p
                }
            }
        }

        val sortedKhataList = when (pParams.sort) {
            PartySortOption.NAME -> khataList.sortedWith(
                compareByDescending<PartyKhataSummary> { it.party.isFavorite }
                    .thenBy { it.party.name.lowercase() }
            )
            PartySortOption.HIGHEST_BALANCE -> khataList.sortedWith(
                compareByDescending<PartyKhataSummary> { it.party.isFavorite }
                    .thenByDescending { it.maxNetBalance }
                    .thenBy { it.party.name.lowercase() }
            )
            PartySortOption.MOST_OVERDUE -> khataList.sortedWith(
                compareByDescending<PartyKhataSummary> { it.party.isFavorite }
                    .thenByDescending { it.oldestOverdueDays }
                    .thenByDescending { it.transactions.count { t -> t.isOverdue } }
                    .thenBy { it.party.name.lowercase() }
            )
            PartySortOption.RECENT_INTERACTION -> khataList.sortedWith(
                compareByDescending<PartyKhataSummary> { it.party.isFavorite }
                    .thenByDescending { maxOf(it.party.lastInteractionMillis, it.party.createdAtMillis) }
                    .thenBy { it.party.name.lowercase() }
            )
        }

        val pQuery = pParams.query.trim().lowercase()
        val filteredKhataList = if (pQuery.isEmpty()) {
            sortedKhataList
        } else {
            sortedKhataList.filter { khata ->
                val p = khata.party
                p.name.lowercase().contains(pQuery) ||
                    p.phoneNumber.lowercase().contains(pQuery) ||
                    p.alternatePhoneNumber.lowercase().contains(pQuery) ||
                    p.upiId.lowercase().contains(pQuery) ||
                    p.email.lowercase().contains(pQuery) ||
                    p.tagOrNotes.lowercase().contains(pQuery) ||
                    p.notes.lowercase().contains(pQuery)
            }
        }

        PeopleUiState(
            allParties = combinedParties,
            partyKhataList = khataList,
            filteredPartyKhataList = filteredKhataList,
            duplicatePartyPairs = duplicatePairs,
            peopleSearchQuery = pParams.query,
            partySortOption = pParams.sort,
            editingParty = pParams.editing,
            showAddEditPartyModal = pParams.showModal,
            receiptModalPartyId = pParams.receiptPartyId,
            showMergeDuplicatesModal = cParams.merge,
            showInAppContactPicker = cParams.picker,
            deviceContacts = cParams.contacts,
            isLoadingContacts = cParams.loading
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PeopleUiState()
    )

    private data class PeopleParams(
        val query: String,
        val sort: PartySortOption,
        val editing: PartyEntity?,
        val showModal: Boolean,
        val receiptPartyId: Long?
    )

    private data class ContactParams(
        val merge: Boolean,
        val picker: Boolean,
        val contacts: List<DeviceContact>,
        val loading: Boolean
    )

    fun setPeopleSearchQuery(query: String) {
        _peopleSearchQuery.value = query
    }

    fun setPartySortOption(option: PartySortOption) {
        _partySortOption.value = option
    }

    fun openAddPartyModal() {
        _editingParty.value = null
        _showAddEditPartyModal.value = true
    }

    fun openEditPartyModal(party: PartyEntity) {
        _editingParty.value = party
        _showAddEditPartyModal.value = true
    }

    fun closeAddEditPartyModal() {
        _editingParty.value = null
        _showAddEditPartyModal.value = false
    }

    fun saveParty(party: PartyEntity) {
        viewModelScope.launch {
            partyRepository.saveParty(party)
            closeAddEditPartyModal()
        }
    }

    fun deleteParty(party: PartyEntity) {
        viewModelScope.launch {
            partyRepository.deleteParty(party)
            if (appStateHolder.selectedPartyIdForSheet.value == party.partyId) {
                appStateHolder.closePartyLedgerScreen()
            }
        }
    }

    fun togglePartyFavorite(party: PartyEntity) {
        viewModelScope.launch {
            if (party.partyId > 0L) {
                partyRepository.togglePartyFavorite(party.partyId, !party.isFavorite)
            } else {
                partyRepository.saveParty(party.copy(partyId = 0L, isFavorite = true))
            }
        }
    }

    fun openMergeDuplicatesModal() {
        _showMergeDuplicatesModal.value = true
    }

    fun closeMergeDuplicatesModal() {
        _showMergeDuplicatesModal.value = false
    }

    fun mergeDuplicatePair(pair: DuplicatePartyPair) {
        viewModelScope.launch {
            partyRepository.mergeParties(pair.primaryParty, pair.duplicateParty)
        }
    }

    fun openInAppContactPicker() {
        _showInAppContactPicker.value = true
        loadDeviceContacts()
    }

    fun closeInAppContactPicker() {
        _showInAppContactPicker.value = false
    }

    fun loadDeviceContacts() {
        if (appContext == null) return
        _isLoadingContacts.value = true
        viewModelScope.launch {
            val contacts = runCatching {
                DeviceContactHelper.fetchDeviceContacts(
                    context = appContext,
                    currency = appStateHolder.summaryCurrency.value ?: CurrencyType.INR,
                    existingParties = uiState.value.allParties
                )
            }.getOrDefault(emptyList())
            _deviceContacts.value = contacts
            _isLoadingContacts.value = false
        }
    }

    fun importSelectedContacts(selectedContacts: List<DeviceContact>) {
        viewModelScope.launch {
            val preferredCurr = appStateHolder.summaryCurrency.value ?: CurrencyType.INR
            val newParties = selectedContacts.map { c ->
                PartyEntity(
                    partyId = 0L,
                    name = c.name,
                    phoneNumber = c.normalizedPhoneNumber.ifBlank { c.rawPhoneNumber },
                    photoUri = c.photoUri,
                    email = c.email,
                    preferredCurrency = preferredCurr,
                    createdAtMillis = System.currentTimeMillis(),
                    lastInteractionMillis = System.currentTimeMillis()
                )
            }
            partyRepository.saveParties(newParties)
            val firstPicked = selectedContacts.firstOrNull()
            if (firstPicked != null && appStateHolder.activeScreen.value == ActiveScreen.ADD_EDIT) {
                appStateHolder.openAddTransaction(
                    PreselectedPartyDraft(
                        partyId = null,
                        name = firstPicked.name,
                        phoneNumber = firstPicked.normalizedPhoneNumber.ifBlank { firstPicked.rawPhoneNumber },
                        preferredCurrency = preferredCurr
                    )
                )
            }
            closeInAppContactPicker()
        }
    }

    fun openReceiptModalForParty(partyId: Long) {
        _receiptModalPartyId.value = partyId
    }

    fun closeReceiptModal() {
        _receiptModalPartyId.value = null
    }

    fun generatePartyStatementPdf(
        khata: PartyKhataSummary,
        installmentPlansByTx: Map<Long, com.jyotirmoyb.udhaar.data.InstallmentPlan> = emptyMap(),
        installmentsByPlan: Map<Long, List<com.jyotirmoyb.udhaar.data.Installment>> = emptyMap(),
        onReady: (File) -> Unit
    ) {
        if (appContext == null) return
        viewModelScope.launch {
            val settings = settingsRepository.userSettings.first()
            val file = PdfStatementGenerator.generatePartyStatementPdf(
                context = appContext,
                khata = khata,
                userSettings = settings,
                enabledCurrencies = settings.enabledCurrencies,
                installmentPlansByTx = installmentPlansByTx,
                installmentsByPlan = installmentsByPlan
            )
            onReady(file)
        }
    }

    fun generateFullLedgerPdf(onReady: (File) -> Unit) {
        if (appContext == null) return
        viewModelScope.launch {
            val settings = settingsRepository.userSettings.first()
            val file = PdfStatementGenerator.generateFullLedgerPdf(
                context = appContext,
                parties = uiState.value.allParties,
                khataList = uiState.value.partyKhataList,
                userSettings = settings,
                enabledCurrencies = settings.enabledCurrencies
            )
            onReady(file)
        }
    }
}
