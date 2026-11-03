package com.jyotirmoyb.udhaar.ui.people

import androidx.compose.runtime.Composable
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.ui.PartyKhataSummary
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary

/**
 * Modular Party Khata detail screen entrypoint in `ui/people/`.
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
    com.jyotirmoyb.udhaar.ui.components.PartyDetailScreen(
        khata = khata,
        enabledCurrencies = enabledCurrencies,
        onBack = onBack,
        onEditParty = onEditParty,
        onDeleteParty = onDeleteParty,
        onAddLoanForParty = onAddLoanForParty,
        onTransactionClick = onTransactionClick,
        onWhatsAppClick = onWhatsAppClick,
        onPayPartyViaUpi = onPayPartyViaUpi,
        onShareThermalReceipt = onShareThermalReceipt,
        onExportPartyStatement = onExportPartyStatement,
        onSharePartyStatement = onSharePartyStatement,
        onExportPartyPdfStatement = onExportPartyPdfStatement,
        onShareMyPaymentDetails = onShareMyPaymentDetails,
        onRequestPartyPaymentDetails = onRequestPartyPaymentDetails,
        onRequestVerification = onRequestVerification,
        onOpenTransactionChat = onOpenTransactionChat,
        onOpenPartyGeneralChat = onOpenPartyGeneralChat,
        onToggleFavorite = onToggleFavorite,
        unreadPartyChatCount = unreadPartyChatCount,
        unreadCountByTx = unreadCountByTx
    )
}
