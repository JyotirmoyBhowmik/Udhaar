package com.jyotirmoyb.udhaar.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.ui.theme.SubtleSurfaceGrey
import com.jyotirmoyb.udhaar.util.InterestCalculator

@Composable
fun AddEditPartyAndAmountSection(
    context: Context,
    reducedMotion: Boolean,
    savedParties: List<PartyEntity>,
    partyName: String,
    onPartyNameChange: (String) -> Unit,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    onSelectSavedParty: (PartyEntity) -> Unit,
    transactionType: TransactionType,
    onTransactionTypeChange: (TransactionType) -> Unit,
    enabledCurrencies: List<CurrencyType>,
    currency: CurrencyType,
    onCurrencyChange: (CurrencyType) -> Unit,
    principalText: String,
    onPrincipalTextChange: (String) -> Unit,
    plainAmountLabel: String,
    step1Done: Boolean,
    step2Done: Boolean,
    step3Done: Boolean,
    textFieldColors: TextFieldColors,
    contactPickerLauncher: ActivityResultLauncher<Intent>,
    onOpenInAppContactPicker: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SubtleSurfaceGrey, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("add_udhaar_steps_indicator"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (step1Done) stringResource(R.string.ui_str_1_who_96d2da) else stringResource(R.string.ui_str_1_who_c9da2a),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (!step1Done) FontWeight.Bold else FontWeight.SemiBold,
                color = PureBlack
            )
            Text(
                text = if (step2Done) stringResource(R.string.ui_str_2_how_much_5f25dd) else stringResource(R.string.ui_str_2_how_much_820ed7),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (step1Done && !step2Done) FontWeight.Bold else FontWeight.SemiBold,
                color = if (step1Done || step2Done) PureBlack else SecondaryInkGrey
            )
            Text(
                text = if (step3Done) stringResource(R.string.ui_str_3_when_a2bdd6) else stringResource(R.string.ui_str_3_when_optional_5232a6),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (step1Done && step2Done) FontWeight.Bold else FontWeight.Normal,
                color = if (step1Done && step2Done) PureBlack else SecondaryInkGrey
            )
        }

        AddEditSectionHeader("STEP 1 · WHO IS THIS UDHAAR WITH?")
        val topRecentAndFavorites = remember(savedParties) {
            savedParties.sortedWith(
                compareByDescending<PartyEntity> { it.isFavorite }
                    .thenByDescending { it.lastInteractionMillis }
            ).take(8)
        }
        if (topRecentAndFavorites.isNotEmpty()) {
            Text(
                text = stringResource(R.string.ui_str_recent_favourite_people_d8a902),
                style = MaterialTheme.typography.labelSmall,
                color = SecondaryInkGrey,
                fontWeight = FontWeight.SemiBold
            )
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("recent_people_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(topRecentAndFavorites, key = { "${it.partyId}_${it.name}" }) { savedParty ->
                    val isChosen = partyName.equals(savedParty.name, ignoreCase = true)
                    Row(
                        modifier = udhaarAnimateItem(reducedMotion)
                            .minimumInteractiveComponentSize()
                            .heightIn(min = 48.dp)
                            .pressScaleFeedback()
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (isChosen) PureBlack else SubtleSurfaceGrey)
                            .border(
                                width = 1.dp,
                                color = if (isChosen) PureBlack else BorderGrey,
                                shape = RoundedCornerShape(24.dp)
                            )
                            .clickable { onSelectSavedParty(savedParty) }
                            .padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PersonAvatar(
                            name = savedParty.name,
                            photoUri = savedParty.photoUri,
                            size = 30.dp
                        )
                        Text(
                            text = if (savedParty.isFavorite) stringResource(R.string.ui_fmt_symbol_992757, savedParty.name) else savedParty.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isChosen) PureWhite else PureBlack,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = partyName,
                onValueChange = onPartyNameChange,
                label = { Text(stringResource(R.string.ui_str_person_or_business_name_d8fb90)) },
                placeholder = { Text(stringResource(R.string.ui_str_e_g_ramesh_sharma_7c54c2)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                colors = textFieldColors,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .testTag("party_name_input")
            )
            OutlinedButton(
                onClick = {
                    val hasContactsPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CONTACTS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasContactsPermission) {
                        onOpenInAppContactPicker()
                    } else {
                        com.jyotirmoyb.udhaar.util.ContactPickerHelper.launchSystemContactPicker(
                            context = context,
                            launcher = contactPickerLauncher
                        )
                    }
                },
                modifier = Modifier
                    .heightIn(min = 56.dp)
                    .padding(top = 6.dp)
                    .pressScaleFeedback()
                    .testTag("pick_contact_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, PureBlack),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
            ) {
                Icon(
                    imageVector = Icons.Default.Contacts,
                    contentDescription = stringResource(R.string.ui_str_contacts_b0dd61),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.ui_str_contacts_b0dd61), style = MaterialTheme.typography.labelMedium)
            }
        }

        var showPhoneText by rememberSaveable { mutableStateOf(false) }
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = {
                onPhoneNumberChange(it)
                showPhoneText = true
            },
            label = { Text(stringResource(R.string.ui_str_phone_whatsapp_number_optional_8c9f19)) },
            placeholder = { Text(stringResource(R.string.ui_fmt_98_10_cc0f5d, currency.defaultCountryCode)) },
            singleLine = true,
            visualTransformation = if (showPhoneText) VisualTransformation.None else PhoneMaskVisualTransformation,
            trailingIcon = if (phoneNumber.isNotBlank()) {
                {
                    AppIconButton(onClick = { showPhoneText = !showPhoneText }) {
                        Icon(
                            imageVector = if (showPhoneText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPhoneText) stringResource(R.string.cd_hide_amounts) else stringResource(R.string.cd_show_amounts),
                            tint = PureBlack
                        )
                    }
                }
            } else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next
            ),
            colors = textFieldColors,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag("phone_number_input")
        )

        HorizontalDivider(color = BorderGrey, thickness = 1.dp)

        AddEditSectionHeader("STEP 2 · HOW MUCH?")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TransactionType.entries.forEach { type ->
                val selected = transactionType == type
                MonochromeTogglePill(
                    text = type.label,
                    selected = selected,
                    onClick = { onTransactionTypeChange(type) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tx_type_${type.name.lowercase()}")
                )
            }
        }

        if (enabledCurrencies.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                enabledCurrencies.forEach { curr ->
                    val selected = currency == curr
                    MonochromeTogglePill(
                        text = curr.displayName,
                        selected = selected,
                        onClick = { onCurrencyChange(curr) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("currency_select_${curr.name.lowercase()}")
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("500", "1000", "5000").forEach { quickAmt ->
                val isQuickSelected = principalText.trim() == quickAmt
                MonochromeTogglePill(
                    text = InterestCalculator.formatCurrency(quickAmt.toDouble(), currency),
                    selected = isQuickSelected,
                    onClick = { onPrincipalTextChange(quickAmt) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_amount_$quickAmt")
                )
            }
            val isCustomSelected = principalText.isNotBlank() && principalText.trim() !in setOf("500", "1000", "5000")
            MonochromeTogglePill(
                text = "Custom",
                selected = isCustomSelected,
                onClick = {
                    if (principalText.trim() in setOf("500", "1000", "5000")) {
                        onPrincipalTextChange("")
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("quick_amount_custom")
            )
        }

        OutlinedTextField(
            value = principalText,
            onValueChange = { input ->
                if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) {
                    onPrincipalTextChange(input)
                }
            },
            label = { Text(stringResource(R.string.ui_fmt_symbol_5f451a, plainAmountLabel, currency.symbol)) },
            prefix = {
                Text(
                    text = stringResource(R.string.ui_fmt_symbol_67ce5f, currency.symbol),
                    style = MaterialTheme.typography.titleMedium,
                    color = PureBlack
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            colors = textFieldColors,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag("principal_amount_input")
        )

        if (step1Done && step2Done) {
            Text(
                text = stringResource(R.string.ui_str_ready_to_save_now_or_b4c76e),
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryInkGrey,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun AddEditSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = SecondaryInkGrey
    )
}

@Composable
fun MonochromeTogglePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) PureBlack else PureWhite,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "pill_bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) PureWhite else PureBlack,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "pill_text"
    )
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 48.dp)
            .pressScaleFeedback()
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) PureBlack else BorderGrey,
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                color = bgColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DateSelectorRow(
    label: String,
    valueText: String,
    icon: ImageVector = Icons.Default.CalendarToday,
    onSelectClick: () -> Unit,
    onClearClick: (() -> Unit)?,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .heightIn(min = 48.dp)
            .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
            .background(SubtleSurfaceGrey, RoundedCornerShape(12.dp))
            .clickable(onClick = onSelectClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = PureBlack,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryInkGrey
                )
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.titleMedium,
                    color = PureBlack
                )
            }
        }
        if (onClearClick != null) {
            AppIconButton(onClick = onClearClick) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = stringResource(R.string.cd_clear_search),
                    tint = PureBlack
                )
            }
        }
    }
}
