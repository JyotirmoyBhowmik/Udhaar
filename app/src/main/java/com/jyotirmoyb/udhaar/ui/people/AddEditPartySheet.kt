package com.jyotirmoyb.udhaar.ui.people

import androidx.compose.runtime.Composable
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.ui.components.AddEditPartyModal

/**
 * Dedicated sheet entry point in `ui/people/AddEditPartySheet.kt` for creating or editing a party profile.
 */
@Composable
fun AddEditPartyContactSheet(
    initialParty: PartyEntity?,
    enabledCurrencies: List<CurrencyType>,
    defaultCurrency: CurrencyType,
    onSave: (PartyEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AddEditPartyModal(
        initialParty = initialParty,
        enabledCurrencies = enabledCurrencies,
        defaultCurrency = defaultCurrency,
        onSave = onSave,
        onDismiss = onDismiss
    )
}
