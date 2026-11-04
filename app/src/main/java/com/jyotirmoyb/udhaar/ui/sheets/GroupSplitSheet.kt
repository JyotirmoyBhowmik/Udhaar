package com.jyotirmoyb.udhaar.ui.sheets

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.ui.components.PersonAvatar
import com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily
import com.jyotirmoyb.udhaar.util.ActiveGroupSplitSummary
import com.jyotirmoyb.udhaar.util.CalculatedUdhaarSummary
import com.jyotirmoyb.udhaar.util.GroupMemberDraft
import com.jyotirmoyb.udhaar.util.GroupPayerMode
import com.jyotirmoyb.udhaar.util.GroupSplitCalculator
import com.jyotirmoyb.udhaar.util.GroupSplitMode
import com.jyotirmoyb.udhaar.util.InterestCalculator

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GroupSplitSheet(
    allParties: List<PartyEntity>,
    allCalculatedItems: List<CalculatedUdhaarSummary>,
    userSettings: UserSettings,
    onSelectGroupFilter: (String) -> Unit,
    onCreateSplitTransactions: (List<UdhaarTransaction>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var groupName by rememberSaveable { mutableStateOf("") }
    var totalBillText by rememberSaveable { mutableStateOf("") }
    var selectedCurrency by rememberSaveable { mutableStateOf(userSettings.defaultCurrency) }
    var selectedPaymentMode by rememberSaveable { mutableStateOf(PaymentMode.UPI) }
    var payerMode by rememberSaveable { mutableStateOf(GroupPayerMode.I_PAID) }
    var includeMyself by rememberSaveable { mutableStateOf(true) }
    var splitMode by rememberSaveable { mutableStateOf(GroupSplitMode.EQUAL) }

    var draftMemberName by rememberSaveable { mutableStateOf("") }
    var draftMemberPhone by rememberSaveable { mutableStateOf("") }
    var nextMemberId by rememberSaveable { mutableLongStateOf(1L) }
    var members by remember { mutableStateOf<List<GroupMemberDraft>>(emptyList()) }

    val activeGroups = remember(allCalculatedItems) {
        GroupSplitCalculator.computeActiveGroups(allCalculatedItems)
    }

    val totalBillDouble = totalBillText.trim().toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val splitResult = remember(totalBillDouble, splitMode, includeMyself, members) {
        GroupSplitCalculator.calculateSplit(
            totalBillAmount = totalBillDouble,
            splitMode = splitMode,
            includeMyself = includeMyself,
            members = members
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.testTag("group_split_modal_sheet")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.group_split_sheet_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.group_split_sheet_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_group_split_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.btn_cancel)
                        )
                    }
                }
            }

            if (activeGroups.isNotEmpty()) {
                item(key = "active_groups_section") {
                    ActiveGroupsSummaryBox(
                        activeGroups = activeGroups,
                        onSelectGroup = { group ->
                            onSelectGroupFilter(group.groupName)
                            onDismiss()
                        }
                    )
                }
            }

            item(key = "bill_details_card") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text(stringResource(R.string.group_split_name_label)) },
                        placeholder = { Text(stringResource(R.string.group_split_name_placeholder)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("group_split_name_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = totalBillText,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                    totalBillText = input
                                }
                            },
                            label = {
                                Text(
                                    stringResource(
                                        R.string.ui_fmt_amount_c53f8e,
                                        selectedCurrency.symbol
                                    )
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("group_split_total_input")
                        )

                        if (userSettings.enabledCurrencies.size > 1) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                userSettings.enabledCurrencies.forEach { curr ->
                                    FilterChip(
                                        selected = selectedCurrency == curr,
                                        onClick = { selectedCurrency = curr },
                                        label = { Text(curr.code) }
                                    )
                                }
                            }
                        }
                    }

                    // Who paid toggle
                    Text(
                        text = stringResource(R.string.group_split_who_paid_label),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = payerMode == GroupPayerMode.I_PAID,
                            onClick = { payerMode = GroupPayerMode.I_PAID },
                            label = {
                                Text(
                                    text = stringResource(R.string.group_split_i_paid),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("group_payer_i_paid")
                        )
                        FilterChip(
                            selected = payerMode == GroupPayerMode.OTHER_PAID,
                            onClick = { payerMode = GroupPayerMode.OTHER_PAID },
                            label = {
                                Text(
                                    text = stringResource(R.string.group_split_other_paid),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("group_payer_other_paid")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.group_split_include_myself),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = includeMyself,
                            onCheckedChange = { includeMyself = it },
                            modifier = Modifier.testTag("group_include_myself_switch")
                        )
                    }

                    // Split Mode selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val modes = listOf(
                            GroupSplitMode.EQUAL to R.string.group_split_mode_equal,
                            GroupSplitMode.CUSTOM to R.string.group_split_mode_custom,
                            GroupSplitMode.PERCENTAGE to R.string.group_split_mode_percent
                        )
                        modes.forEach { (mode, labelRes) ->
                            FilterChip(
                                selected = splitMode == mode,
                                onClick = { splitMode = mode },
                                label = {
                                    Text(
                                        text = stringResource(labelRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("group_split_mode_${mode.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Members Selection & Quick Pick
            item(key = "members_card") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.group_split_members_title, members.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (allParties.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.group_split_quick_pick_people),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            allParties.take(12).forEach { party ->
                                val isAdded = members.any {
                                    it.partyId == party.partyId ||
                                        it.name.equals(party.name, ignoreCase = true)
                                }
                                FilterChip(
                                    selected = isAdded,
                                    onClick = {
                                        if (isAdded) {
                                            members = members.filterNot {
                                                it.partyId == party.partyId ||
                                                    it.name.equals(party.name, ignoreCase = true)
                                            }
                                        } else {
                                            members = members + GroupMemberDraft(
                                                id = nextMemberId++,
                                                name = party.name,
                                                phoneNumber = party.phoneNumber,
                                                partyId = party.partyId
                                            )
                                        }
                                    },
                                    label = { Text(party.name) },
                                    leadingIcon = if (isAdded) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else null,
                                    modifier = Modifier.testTag("group_quick_party_${party.partyId}")
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = draftMemberName,
                            onValueChange = { draftMemberName = it },
                            label = { Text(stringResource(R.string.group_split_add_member_name)) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.1f)
                                .testTag("group_add_member_name_input")
                        )
                        OutlinedTextField(
                            value = draftMemberPhone,
                            onValueChange = { draftMemberPhone = it },
                            label = { Text(stringResource(R.string.group_split_add_member_phone)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("group_add_member_phone_input")
                        )
                        Button(
                            onClick = {
                                val cleanName = draftMemberName.trim()
                                if (cleanName.isNotEmpty()) {
                                    members = members + GroupMemberDraft(
                                        id = nextMemberId++,
                                        name = cleanName,
                                        phoneNumber = draftMemberPhone.trim()
                                    )
                                    draftMemberName = ""
                                    draftMemberPhone = ""
                                }
                            },
                            enabled = draftMemberName.trim().isNotEmpty(),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("group_add_member_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.group_split_add_member_btn))
                        }
                    }

                    if (members.isEmpty()) {
                        Text(
                            text = stringResource(R.string.group_split_empty_members_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        if (includeMyself && splitResult.myShareMinor > 0L) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.group_split_my_share_fmt,
                                        InterestCalculator.formatCurrency(splitResult.myShareDouble, selectedCurrency)
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                val totalPeople = members.size + 1
                                if (splitMode == GroupSplitMode.EQUAL && splitResult.memberShares.isNotEmpty()) {
                                    Text(
                                        text = stringResource(
                                            R.string.group_split_per_person_fmt,
                                            InterestCalculator.formatCurrency(
                                                splitResult.memberShares.first().amountDouble,
                                                selectedCurrency
                                            ),
                                            totalPeople
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        splitResult.memberShares.forEach { share ->
                            val member = share.member
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    PersonAvatar(name = member.name, size = 32.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = member.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (member.phoneNumber.isNotBlank()) {
                                            Text(
                                                text = member.phoneNumber,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (splitMode == GroupSplitMode.CUSTOM) {
                                    OutlinedTextField(
                                        value = member.customAmountInput,
                                        onValueChange = { v ->
                                            if (v.isEmpty() || v.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                                members = members.map {
                                                    if (it.id == member.id) it.copy(customAmountInput = v) else it
                                                }
                                            }
                                        },
                                        label = { Text(stringResource(R.string.group_split_custom_amount_label)) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier
                                            .width(120.dp)
                                            .padding(horizontal = 6.dp)
                                    )
                                } else if (splitMode == GroupSplitMode.PERCENTAGE) {
                                    OutlinedTextField(
                                        value = member.percentageInput,
                                        onValueChange = { v ->
                                            if (v.isEmpty() || v.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                                members = members.map {
                                                    if (it.id == member.id) it.copy(percentageInput = v) else it
                                                }
                                            }
                                        },
                                        label = { Text(stringResource(R.string.group_split_percent_label)) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier
                                            .width(100.dp)
                                            .padding(horizontal = 6.dp)
                                    )
                                }

                                Text(
                                    text = InterestCalculator.formatCurrency(share.amountDouble, selectedCurrency),
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = IbmPlexMonoFamily),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                IconButton(
                                    onClick = {
                                        members = members.filterNot { it.id == member.id }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = stringResource(R.string.ui_str_delete_d6f563),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        if (!splitResult.isTotalMatching && totalBillDouble > 0.0) {
                            Text(
                                text = stringResource(
                                    R.string.group_split_total_mismatch_warn,
                                    InterestCalculator.formatCurrency(splitResult.allocatedTotalDouble, selectedCurrency),
                                    InterestCalculator.formatCurrency(splitResult.totalBillDouble, selectedCurrency)
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Action Buttons
            item(key = "actions_row") {
                val validShares = splitResult.memberShares.filter { it.amountDouble > 0.005 }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val msg = GroupSplitCalculator.buildWhatsAppGroupSplitMessage(
                                groupName = groupName,
                                currency = selectedCurrency,
                                calcResult = splitResult,
                                payeeName = userSettings.payeeName,
                                upiId = userSettings.upiId
                            )
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, msg)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(sendIntent, null))
                            }
                        },
                        enabled = validShares.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("group_split_share_whatsapp_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.group_split_share_whatsapp_btn),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = {
                            val cleanGroupTitle = groupName.trim().ifBlank { "Group Split" }
                            val now = System.currentTimeMillis()
                            val txType = if (payerMode == GroupPayerMode.I_PAID) {
                                TransactionType.GIVEN
                            } else {
                                TransactionType.TAKEN
                            }
                            val newTransactions = validShares.map { share ->
                                UdhaarTransaction(
                                    partyId = share.member.partyId,
                                    transactionType = txType,
                                    partyName = share.member.name.trim(),
                                    phoneNumber = InterestCalculator.formatPhoneWithCountryCode(
                                        share.member.phoneNumber,
                                        selectedCurrency
                                    ),
                                    currency = selectedCurrency,
                                    principalAmount = share.amountDouble,
                                    paymentMode = selectedPaymentMode,
                                    startDateMillis = now,
                                    notes = GroupSplitCalculator.formatGroupNote(cleanGroupTitle)
                                )
                            }
                            if (newTransactions.isNotEmpty()) {
                                onCreateSplitTransactions(newTransactions)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.group_split_created_toast, newTransactions.size),
                                    Toast.LENGTH_SHORT
                                ).show()
                                onDismiss()
                            }
                        },
                        enabled = validShares.isNotEmpty(),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(48.dp)
                            .testTag("group_split_save_records_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.group_split_save_records_btn, validShares.size),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveGroupsSummaryBox(
    activeGroups: List<ActiveGroupSplitSummary>,
    onSelectGroup: (ActiveGroupSplitSummary) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.group_split_active_groups_title, activeGroups.size),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        activeGroups.take(4).forEach { group ->
            val progress = if (group.totalMembersCount > 0) {
                group.settledMembersCount.toFloat() / group.totalMembersCount.toFloat()
            } else 0f

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectGroup(group) }
                    .testTag("active_group_card_${group.groupName}")
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = group.groupName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = InterestCalculator.formatCurrency(group.totalAmount, group.currency),
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = IbmPlexMonoFamily),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text(
                        text = stringResource(
                            R.string.group_split_active_group_summary_fmt,
                            group.settledMembersCount,
                            group.totalMembersCount,
                            InterestCalculator.formatCurrency(group.remainingDue, group.currency)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
