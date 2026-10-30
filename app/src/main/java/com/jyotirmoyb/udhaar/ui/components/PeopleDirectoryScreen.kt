package com.jyotirmoyb.udhaar.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    onOpenPartyGeneralChat: (PartyEntity) -> Unit = {}
) {
    LazyColumn(
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
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, PureBlack, RoundedCornerShape(4.dp))
                    .background(PureBlack, RoundedCornerShape(4.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "PEOPLE & PARTIES KHATA",
                        style = MaterialTheme.typography.titleLarge,
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${partyKhataList.size} CONTACTS • TAP FOR INDIVIDUAL LEDGER",
                        style = MaterialTheme.typography.labelSmall,
                        color = BorderGrey,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onAddPartyClick,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, PureWhite),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = PureWhite,
                        contentColor = PureBlack
                    ),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("add_party_directory_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ADD PARTY", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = "Search people by name, phone, UPI ID, or tag…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryInkGrey
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search contacts",
                        tint = PureBlack
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = PureBlack
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PureBlack,
                    unfocusedBorderColor = BorderGrey,
                    cursorColor = PureBlack,
                    focusedContainerColor = PureWhite,
                    unfocusedContainerColor = SubtleSurfaceGrey
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("people_search_input")
            )
        }

        if (partyKhataList.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
                        .background(SubtleSurfaceGrey, RoundedCornerShape(4.dp))
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "NO CONTACTS IN DIRECTORY",
                        style = MaterialTheme.typography.titleMedium,
                        color = PureBlack
                    )
                    Text(
                        text = "Add a contact manually or import via the Contact Picker to open individual party ledger views and store their UPI ID.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryInkGrey,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = onAddPartyClick,
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PureBlack,
                            contentColor = PureWhite
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ADD / IMPORT CONTACT", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        } else {
            items(
                items = partyKhataList,
                key = { "${it.party.partyId}_${it.party.name}" }
            ) { khata ->
                val partyUnread = unreadCountByPartyName[khata.party.name.trim().lowercase()] ?: 0
                PartyDirectoryCard(
                    khata = khata,
                    enabledCurrencies = enabledCurrencies,
                    unreadCount = partyUnread,
                    onClick = { onPartyClick(khata) },
                    onChatClick = { onOpenPartyGeneralChat(khata.party) },
                    onAddLoanClick = { onQuickAddLoanForParty(khata.party) }
                )
            }
        }
    }
}

@Composable
private fun PartyDirectoryCard(
    khata: PartyKhataSummary,
    enabledCurrencies: List<CurrencyType>,
    unreadCount: Int = 0,
    onClick: () -> Unit,
    onChatClick: () -> Unit = {},
    onAddLoanClick: () -> Unit
) {
    val party = khata.party
    val inrEnabled = CurrencyType.INR in enabledCurrencies
    val nprEnabled = CurrencyType.NPR in enabledCurrencies

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
            .background(PureWhite, RoundedCornerShape(4.dp))
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
                        .background(PureBlack, RoundedCornerShape(2.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = party.preferredCurrency.code,
                        style = MaterialTheme.typography.labelSmall,
                        color = PureWhite,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (party.tagOrNotes.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .background(MutedChipGrey, RoundedCornerShape(2.dp))
                            .border(0.5.dp, BorderGrey, RoundedCornerShape(2.dp))
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
                            .background(MutedChipGrey, RoundedCornerShape(2.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "VPA: ${party.upiId}",
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
                            .background(PureBlack, RoundedCornerShape(2.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("party_unread_badge_${party.name}")
                    ) {
                        Text(
                            text = "💬 $unreadCount NEW",
                            style = MaterialTheme.typography.labelSmall,
                            color = PureWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = "${khata.transactions.size} RECORDS",
                style = MaterialTheme.typography.labelSmall,
                color = SecondaryInkGrey
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = party.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = PureBlack
                )
                val contactSub = buildString {
                    if (party.phoneNumber.isNotBlank()) append(party.phoneNumber)
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

            Column(horizontalAlignment = Alignment.End) {
                val inrNet = khata.inrNet
                val nprNet = khata.nprNet
                if (inrEnabled) {
                    val prefix = when {
                        inrNet > 0.01 -> "RECEIVE "
                        inrNet < -0.01 -> "PAY "
                        else -> "SETTLED "
                    }
                    Text(
                        text = "$prefix${InterestCalculator.formatCurrency(kotlin.math.abs(inrNet), CurrencyType.INR)}",
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
                    Text(
                        text = "$prefix${InterestCalculator.formatCurrency(kotlin.math.abs(nprNet), CurrencyType.NPR)}",
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
                text = "TAP FOR PARTY LEDGER",
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
                        .testTag("party_card_chat_${party.name}"),
                    shape = RoundedCornerShape(3.dp),
                    border = BorderStroke(1.dp, PureBlack),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (unreadCount > 0) PureBlack else PureWhite,
                        contentColor = if (unreadCount > 0) PureWhite else PureBlack
                    )
                ) {
                    Text(
                        text = if (unreadCount > 0) "CHAT ($unreadCount)" else "CHAT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = onAddLoanClick,
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(3.dp),
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
                    Text("NEW LOAN", style = MaterialTheme.typography.labelSmall)
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
    onShareMyPaymentDetails: () -> Unit = {},
    onRequestPartyPaymentDetails: () -> Unit = {},
    onOpenTransactionChat: (CalculatedUdhaarSummary) -> Unit = {},
    onOpenPartyGeneralChat: () -> Unit = {},
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
                            modifier = Modifier.testTag("party_detail_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to People Directory",
                                tint = PureBlack
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = party.name,
                                style = MaterialTheme.typography.titleLarge,
                                color = PureBlack,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "PARTY KHATA LEDGER",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryInkGrey,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        IconButton(
                            onClick = onShareThermalReceipt,
                            modifier = Modifier
                                .size(36.dp)
                                .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
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
                                .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
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
                                    .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
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
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .border(1.dp, PureWhite, RoundedCornerShape(4.dp))
                    .testTag("party_add_loan_fab")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Loan", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "NEW LOAN",
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
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, PureBlack, RoundedCornerShape(4.dp))
                            .background(PureWhite, RoundedCornerShape(4.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val meta = buildString {
                            if (party.tagOrNotes.isNotBlank()) append("Tag: ${party.tagOrNotes}")
                            if (party.phoneNumber.isNotBlank()) {
                                if (isNotEmpty()) append("  •  ")
                                append("Phone: ${party.phoneNumber}")
                            }
                            if (party.upiId.isNotBlank()) {
                                if (isNotEmpty()) append("  •  ")
                                append("UPI VPA: ${party.upiId}")
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

                        // Enabled Currencies Net Balance Box
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, PureBlack, RoundedCornerShape(4.dp))
                        ) {
                            if (inrEnabled) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(PureBlack)
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = "INR (₹) NET POSITION",
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
                                        text = "Given: ${InterestCalculator.formatCurrency(khata.inrToReceive, CurrencyType.INR)} | Taken: ${InterestCalculator.formatCurrency(khata.inrToPay, CurrencyType.INR)}",
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
                                        text = "NPR (रू) NET POSITION",
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
                                        text = "Given: ${InterestCalculator.formatCurrency(khata.nprToReceive, CurrencyType.NPR)} | Taken: ${InterestCalculator.formatCurrency(khata.nprToPay, CurrencyType.NPR)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (!inrEnabled) BorderGrey else SecondaryInkGrey,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Quick Actions: Call, WhatsApp, Email
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
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, PureBlack),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("CALL", style = MaterialTheme.typography.labelMedium)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val sanitized = InterestCalculator.sanitizePhoneForWhatsApp(
                                            party.phoneNumber,
                                            party.preferredCurrency
                                        )
                                        val msg = "Hello ${party.name}, sharing our current Udhaar ledger summary."
                                        val url = "https://wa.me/$sanitized?text=${Uri.encode(msg)}"
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, PureBlack),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("WHATSAPP", style = MaterialTheme.typography.labelMedium)
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
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, PureBlack),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                                ) {
                                    Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("EMAIL", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        // Enhancement B: Thermal-Style Monochrome Statement Receipt (Image & PDF Share)
                        Button(
                            onClick = onShareThermalReceipt,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("party_share_thermal_receipt_button"),
                            shape = RoundedCornerShape(4.dp),
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
                                text = "SHARE RECEIPT / STATEMENT (IMAGE & PDF)",
                                style = MaterialTheme.typography.labelLarge
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
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.5.dp, PureBlack),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                            ) {
                                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (party.upiId.isNotBlank()) {
                                        "PAY PARTY VIA UPI (${party.upiId})"
                                    } else {
                                        "SET PARTY UPI ID TO PAY DIRECTLY"
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
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.5.dp, PureBlack),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                            ) {
                                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SHARE MY PAYMENT DETAILS",
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
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.5.dp, PureBlack),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "REQUEST THEIR DETAILS",
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
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PureBlack,
                                contentColor = PureWhite
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (unreadCount > 0) {
                                    "OPEN PARTY CHAT THREAD (💬 $unreadCount UNREAD)"
                                } else {
                                    "OPEN PARTY CHAT THREAD"
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
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, PureBlack),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                            ) {
                                Text("SAVE STATEMENT (JSON)", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = onSharePartyStatement,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .testTag("share_party_statement_button"),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, PureBlack),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SHARE BACKUP (JSON)", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Party Ledger Records Header
                item {
                    Text(
                        text = "INDIVIDUAL PARTY LEDGER RECORDS (${khata.transactions.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = SecondaryInkGrey
                    )
                }

                if (khata.transactions.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
                                .background(SubtleSurfaceGrey, RoundedCornerShape(4.dp))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No transactions recorded with ${party.name} yet. Tap + New Loan below to add one.",
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = if (txUnread > 0) 1.5.dp else 1.dp,
                                    color = if (txUnread > 0) PureBlack else BorderGrey,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .background(if (tx.isSettled) SubtleSurfaceGrey else PureWhite, RoundedCornerShape(4.dp))
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
                                            .background(if (isGiven) PureBlack else PureWhite, RoundedCornerShape(2.dp))
                                            .border(1.dp, PureBlack, RoundedCornerShape(2.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isGiven) "GIVEN" else "TAKEN",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isGiven) PureWhite else PureBlack
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(MutedChipGrey, RoundedCornerShape(2.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${tx.paymentMode.label.uppercase()} • ${tx.currency.code}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = PureBlack
                                        )
                                    }
                                    if (txUnread > 0) {
                                        Box(
                                            modifier = Modifier
                                                .background(PureBlack, RoundedCornerShape(2.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "💬 $txUnread NEW",
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
                                    textDecoration = if (tx.isSettled) TextDecoration.LineThrough else TextDecoration.None
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TAP FOR REPAYMENT & DETAILS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondaryInkGrey
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = { onOpenTransactionChat(summary) },
                                        modifier = Modifier.height(34.dp),
                                        shape = RoundedCornerShape(3.dp),
                                        border = BorderStroke(1.dp, PureBlack),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (txUnread > 0) PureBlack else PureWhite,
                                            contentColor = if (txUnread > 0) PureWhite else PureBlack
                                        )
                                    ) {
                                        Text(
                                            text = if (txUnread > 0) "CHAT ($txUnread)" else "CHAT",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { onWhatsAppClick(summary) },
                                        modifier = Modifier.height(34.dp),
                                        shape = RoundedCornerShape(3.dp),
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
                                        Text("REMIND", style = MaterialTheme.typography.labelSmall, color = PureWhite)
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
    var upiId by rememberSaveable { mutableStateOf(initialParty?.upiId ?: "") }
    var email by rememberSaveable { mutableStateOf(initialParty?.email ?: "") }
    var tagOrNotes by rememberSaveable { mutableStateOf(initialParty?.tagOrNotes ?: "") }
    var preferredCurrency by rememberSaveable {
        mutableStateOf(
            initialParty?.preferredCurrency?.takeIf { it in enabledCurrencies }
                ?: defaultCurrency
        )
    }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }

    // Zero-permission Phone Contact Picker
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val contactUri = result.data?.data ?: return@rememberLauncherForActivityResult
        val extracted = com.jyotirmoyb.udhaar.util.ContactPickerHelper.extractContactInfo(context, contactUri)
        if (extracted != null) {
            val (pickedName, pickedPhone) = extracted
            if (pickedName.isNotBlank()) name = pickedName
            if (pickedPhone.isNotBlank()) phoneNumber = pickedPhone
            android.widget.Toast.makeText(context, "Imported contact: $pickedName", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(context, "Could not load contact details. Please enter name manually.", android.widget.Toast.LENGTH_SHORT).show()
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
                        text = if (initialParty == null) "ADD PARTY TO DIRECTORY" else "EDIT PARTY PROFILE",
                        style = MaterialTheme.typography.titleLarge,
                        color = PureBlack
                    )
                    Text(
                        text = "PEOPLE & CONTACT MANAGEMENT",
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
                        label = { Text("Contact / Party Name *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        colors = textFieldColors,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("party_modal_name_input")
                    )
                    OutlinedButton(
                        onClick = {
                            val pickIntent = com.jyotirmoyb.udhaar.util.ContactPickerHelper.createPickContactIntent()
                            runCatching {
                                contactPickerLauncher.launch(pickIntent)
                            }.onFailure {
                                val fallbackIntent = com.jyotirmoyb.udhaar.util.ContactPickerHelper.createFallbackPickContactIntent()
                                runCatching { contactPickerLauncher.launch(fallbackIntent) }
                            }
                        },
                        modifier = Modifier
                            .height(56.dp)
                            .padding(top = 6.dp)
                            .testTag("party_modal_import_contact_button"),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, PureBlack),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                    ) {
                        Icon(Icons.Default.Contacts, contentDescription = "Import Contact", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("IMPORT", style = MaterialTheme.typography.labelSmall)
                    }
                }

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number (Auto-formats ${preferredCurrency.defaultCountryCode})") },
                    placeholder = { Text("${preferredCurrency.defaultCountryCode} 98XXXXXXXX") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("party_modal_phone_input")
                )

                OutlinedTextField(
                    value = upiId,
                    onValueChange = { upiId = it },
                    label = { Text("Party's Personal UPI ID (Optional • For Payback)") },
                    placeholder = { Text("e.g. friend@okaxis") },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("party_modal_upi_input")
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address (Optional)") },
                    placeholder = { Text("contact@example.com") },
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
                    label = { Text("Tag or Notes (e.g. Friend, Supplier, Client)") },
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

                if (enabledCurrencies.size > 1) {
                    Text(
                        text = "PREFERRED CURRENCY",
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
                            upiId = upiId.trim(),
                            email = email.trim(),
                            tagOrNotes = tagOrNotes.trim(),
                            preferredCurrency = preferredCurrency
                        )
                        onSave(entity)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_party_modal_button"),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PureBlack,
                        contentColor = PureWhite
                    )
                ) {
                    Text("SAVE CONTACT PROFILE", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
