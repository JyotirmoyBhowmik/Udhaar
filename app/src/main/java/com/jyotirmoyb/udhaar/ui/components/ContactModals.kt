package com.jyotirmoyb.udhaar.ui.components

import com.jyotirmoyb.udhaar.util.InterestCalculator
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jyotirmoyb.udhaar.ui.DuplicatePartyPair
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.MutedChipGrey
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.ui.theme.SubtleSurfaceGrey
import com.jyotirmoyb.udhaar.util.DeviceContact
import kotlinx.coroutines.launch

/**
 * Short explanation & rationale dialog before requesting READ_CONTACTS.
 */
@Composable
fun ContactsPermissionRationaleDialog(
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "ACCESS CONTACTS",
                style = MaterialTheme.typography.titleLarge,
                color = PureBlack
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Udhaar uses contacts permission to let you select borrowers and lenders without typing their phone numbers manually.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureBlack
                )
                Text(
                    text = "• Private to your device: contacts are never shared or sent to any remote server.\n" +
                            "• Letter-index search and duplicate detection with E.164 normalization.\n" +
                            "• If you decline, you can still type names and numbers manually or use the system single-contact picker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryInkGrey
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onRequestPermission()
                },
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PureBlack,
                    contentColor = PureWhite
                ),
                modifier = Modifier.testTag("allow_contacts_permission_button")
            ) {
                Text("ALLOW ACCESS", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, PureBlack),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack),
                modifier = Modifier.testTag("skip_contacts_permission_button")
            ) {
                Text("MANUAL ENTRY / SKIP", style = MaterialTheme.typography.labelLarge)
            }
        },
        shape = RoundedCornerShape(4.dp),
        containerColor = PureWhite
    )
}

/**
 * In-App Contact Picker Modal:
 * Searchable list from ContactsContract with letter-index fast scroll,
 * avatars (photo thumbnail or monochrome initials), multi-select checkboxes,
 * and badges for people already in ledger or on Udhaar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppContactPickerModal(
    contacts: List<DeviceContact>,
    isLoading: Boolean,
    onImportContacts: (List<DeviceContact>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    val selectedLookupKeys = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val filteredContacts by remember(contacts, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                contacts
            } else {
                val query = searchQuery.trim().lowercase()
                contacts.filter { contact ->
                    contact.displayName.lowercase().contains(query) ||
                            contact.normalizedPhoneNumber.contains(query) ||
                            contact.rawPhoneNumber.contains(query)
                }
            }
        }
    }

    // Alphabet index mapping
    val alphabet = remember { listOf("#") + ('A'..'Z').map { it.toString() } }
    val firstIndexByLetter by remember(filteredContacts) {
        derivedStateOf {
            val map = mutableMapOf<String, Int>()
            filteredContacts.forEachIndexed { index, contact ->
                val firstChar = contact.displayName.trim().firstOrNull()?.uppercaseChar() ?: '#'
                val key = if (firstChar in 'A'..'Z') firstChar.toString() else "#"
                if (!map.containsKey(key)) {
                    map[key] = index
                }
            }
            map
        }
    }

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
                .widthIn(max = 650.dp)
                .fillMaxHeight(0.92f)
                .align(Alignment.CenterHorizontally)
                .navigationBarsPadding()
                .imePadding()
                .testTag("in_app_contact_picker_sheet")
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "IMPORT FROM CONTACTS",
                        style = MaterialTheme.typography.titleLarge,
                        color = PureBlack
                    )
                    Text(
                        text = "${contacts.size} DEVICE CONTACTS • E.164 NORMALIZED",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryInkGrey
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_contact_picker_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = PureBlack)
                }
            }

            HorizontalDivider(color = BorderGrey, thickness = 1.dp)

            // Search Bar
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search contacts by name or phone…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryInkGrey
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = PureBlack)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = PureBlack)
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
                        .testTag("contact_picker_search_input")
                )
            }

            // Quick Selection Actions Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedLookupKeys.size} of ${filteredContacts.size} SELECTED",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryInkGrey,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val allVisibleKeys = filteredContacts.map { it.lookupKey }
                            selectedLookupKeys.clear()
                            selectedLookupKeys.addAll(allVisibleKeys)
                        },
                        shape = RoundedCornerShape(3.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("SELECT ALL", style = MaterialTheme.typography.labelSmall)
                    }
                    if (selectedLookupKeys.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { selectedLookupKeys.clear() },
                            shape = RoundedCornerShape(3.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("DESELECT", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            HorizontalDivider(color = BorderGrey, thickness = 0.5.dp)

            // Content Area: Contact List + Alphabet Fast Scroll Index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = PureBlack, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading device contacts…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryInkGrey
                        )
                    }
                } else if (filteredContacts.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "NO CONTACTS FOUND ON DEVICE" else "NO MATCHING CONTACTS",
                            style = MaterialTheme.typography.titleMedium,
                            color = PureBlack
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Check that your phone has contacts saved or enter the party details manually.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryInkGrey,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Contact List
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("contacts_lazy_list"),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            itemsIndexed(
                                items = filteredContacts,
                                key = { _, contact -> contact.lookupKey }
                            ) { _, contact ->
                                val isSelected = contact.lookupKey in selectedLookupKeys
                                ContactRowItem(
                                    contact = contact,
                                    isSelected = isSelected,
                                    onToggle = {
                                        if (isSelected) {
                                            selectedLookupKeys.remove(contact.lookupKey)
                                        } else {
                                            selectedLookupKeys.add(contact.lookupKey)
                                        }
                                    }
                                )
                                HorizontalDivider(color = BorderGrey.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }
                        }

                        // Alphabet Fast Scroll Index Bar
                        Column(
                            modifier = Modifier
                                .width(28.dp)
                                .fillMaxHeight()
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceAround
                        ) {
                            alphabet.forEach { letter ->
                                val targetIndex = firstIndexByLetter[letter]
                                val hasItems = targetIndex != null
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = hasItems) {
                                            if (targetIndex != null) {
                                                coroutineScope.launch {
                                                    listState.scrollToItem(targetIndex)
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = letter,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = if (hasItems) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = if (hasItems) PureBlack else BorderGrey
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom Bar: Import Button
            HorizontalDivider(color = BorderGrey, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, PureBlack),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("CANCEL", style = MaterialTheme.typography.labelLarge)
                }

                Button(
                    onClick = {
                        val toImport = contacts.filter { it.lookupKey in selectedLookupKeys }
                        onImportContacts(toImport)
                    },
                    enabled = selectedLookupKeys.isNotEmpty(),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PureBlack,
                        contentColor = PureWhite,
                        disabledContainerColor = BorderGrey,
                        disabledContentColor = PureWhite
                    ),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(48.dp)
                        .testTag("import_selected_contacts_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedLookupKeys.isEmpty()) "SELECT CONTACTS" else "IMPORT (${selectedLookupKeys.size})",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactRowItem(
    contact: DeviceContact,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .background(if (isSelected) SubtleSurfaceGrey else PureWhite)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("contact_item_${contact.displayName}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = PureBlack,
                checkmarkColor = PureWhite,
                uncheckedColor = PureBlack
            ),
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        // Avatar (Photo or Colored/Monochrome Initial)
        if (!contact.photoThumbnailUri.isNullOrBlank()) {
            AsyncImage(
                model = contact.photoThumbnailUri,
                contentDescription = contact.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(1.dp, BorderGrey, CircleShape)
            )
        } else {
            val initial = contact.displayName.trim().take(1).uppercase()
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(PureBlack, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial.ifEmpty { "?" },
                    style = MaterialTheme.typography.titleMedium,
                    color = PureWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = contact.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = PureBlack,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = InterestCalculator.maskPhoneNumber(
                        contact.normalizedPhoneNumber.ifEmpty { contact.rawPhoneNumber }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryInkGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Badges row
            if (contact.isInLedger || contact.hasUdhaarApp) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (contact.isInLedger) {
                        Box(
                            modifier = Modifier
                                .background(PureBlack, RoundedCornerShape(2.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "IN LEDGER",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = PureWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (contact.hasUdhaarApp) {
                        Box(
                            modifier = Modifier
                                .background(MutedChipGrey, RoundedCornerShape(2.dp))
                                .border(0.5.dp, PureBlack, RoundedCornerShape(2.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "ON UDHAAR ✓",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = PureBlack,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Merge Duplicates Modal:
 * Tool in the People tab to inspect detected duplicates (matching normalized E.164 phone or name)
 * and merge secondary records safely into primary contact.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeDuplicatesModal(
    duplicatePairs: List<DuplicatePartyPair>,
    onMergePair: (DuplicatePartyPair) -> Unit,
    onDismiss: () -> Unit
) {
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
                .widthIn(max = 650.dp)
                .fillMaxHeight(0.85f)
                .align(Alignment.CenterHorizontally)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .testTag("merge_duplicates_sheet")
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MERGE DUPLICATE PARTIES",
                        style = MaterialTheme.typography.titleLarge,
                        color = PureBlack
                    )
                    Text(
                        text = "${duplicatePairs.size} POTENTIAL DUPLICATES DETECTED",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryInkGrey
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_merge_modal_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = PureBlack)
                }
            }

            HorizontalDivider(color = BorderGrey, thickness = 1.dp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SubtleSurfaceGrey)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Parties below share the same normalized E.164 phone number. Merging transfers all loans, partial payments, and chats into the primary contact and safely deletes the duplicate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PureBlack
                )
            }

            if (duplicatePairs.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "NO DUPLICATES FOUND",
                        style = MaterialTheme.typography.titleMedium,
                        color = PureBlack
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "All parties in your ledger have unique phone numbers and names.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryInkGrey,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        count = duplicatePairs.size,
                        key = { idx -> "${duplicatePairs[idx].primaryParty.partyId}_${duplicatePairs[idx].duplicateParty.partyId}" }
                    ) { idx ->
                        val pair = duplicatePairs[idx]
                        DuplicatePairCard(pair = pair, onMerge = { onMergePair(pair) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicatePairCard(
    pair: DuplicatePartyPair,
    onMerge: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, PureBlack, RoundedCornerShape(4.dp))
            .background(PureWhite, RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(PureBlack, RoundedCornerShape(2.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "MATCH: ${pair.matchReason.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PureWhite,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onMerge,
                shape = RoundedCornerShape(3.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PureBlack,
                    contentColor = PureWhite
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("merge_pair_${pair.primaryParty.partyId}_${pair.duplicateParty.partyId}")
            ) {
                Icon(Icons.Default.Merge, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("MERGE INTO PRIMARY", style = MaterialTheme.typography.labelSmall)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Primary Party Box
            Column(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, BorderGrey, RoundedCornerShape(3.dp))
                    .background(SubtleSurfaceGrey, RoundedCornerShape(3.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "PRIMARY CONTACT (KEPT)",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryInkGrey,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = pair.primaryParty.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = PureBlack,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (pair.primaryParty.phoneNumber.isNotBlank()) {
                        InterestCalculator.maskPhoneNumber(pair.primaryParty.phoneNumber)
                    } else {
                        "No phone"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryInkGrey
                )
                if (pair.primaryParty.upiId.isNotBlank()) {
                    Text(
                        text = "UPI: ${InterestCalculator.maskUpiId(pair.primaryParty.upiId)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryInkGrey
                    )
                }
            }

            // Duplicate Party Box
            Column(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, BorderGrey, RoundedCornerShape(3.dp))
                    .background(PureWhite, RoundedCornerShape(3.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "DUPLICATE (MERGED & REMOVED)",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryInkGrey,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = pair.duplicateParty.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = PureBlack,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (pair.duplicateParty.phoneNumber.isNotBlank()) {
                        InterestCalculator.maskPhoneNumber(pair.duplicateParty.phoneNumber)
                    } else {
                        "No phone"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryInkGrey
                )
                if (pair.duplicateParty.upiId.isNotBlank()) {
                    Text(
                        text = "UPI: ${InterestCalculator.maskUpiId(pair.duplicateParty.upiId)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryInkGrey
                    )
                }
            }
        }
    }
}
