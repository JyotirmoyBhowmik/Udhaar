package com.jyotirmoyb.udhaar.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.ui.theme.SubtleSurfaceGrey

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
    var language by rememberSaveable { mutableStateOf(initialParty?.language ?: "en") }
    var quietHoursOverride by rememberSaveable { mutableStateOf(initialParty?.quietHoursOverride ?: false) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            photoUri = uri.toString()
        }
    }

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
                AppIconButton(
                    onClick = onDismiss,
                    testTag = "close_party_modal_button"
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close), tint = PureBlack)
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
                val currentErrorText = errorText
                if (currentErrorText != null) {
                    Text(
                        text = currentErrorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = PureBlack,
                        fontWeight = FontWeight.Bold
                    )
                }

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
                            contentDescription = stringResource(R.string.ui_str_contact_avatar_photo_0e492b),
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
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("party_pick_photo_button")
                        ) {
                            Text(stringResource(R.string.ui_str_pick_photo_52f2aa), style = MaterialTheme.typography.labelSmall)
                        }
                        if (photoUri.isNotBlank()) {
                            OutlinedButton(
                                onClick = { photoUri = "" },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
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
                            .heightIn(min = 56.dp)
                            .padding(top = 6.dp)
                            .testTag("party_modal_import_contact_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PureBlack),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                    ) {
                        Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(18.dp))
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
                            AppIconButton(onClick = { showPrimaryPhone = !showPrimaryPhone }) {
                                Icon(
                                    imageVector = if (showPrimaryPhone) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPrimaryPhone) stringResource(R.string.cd_hide_amounts) else stringResource(R.string.cd_show_amounts),
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
                            AppIconButton(onClick = { showAlternatePhone = !showAlternatePhone }) {
                                Icon(
                                    imageVector = if (showAlternatePhone) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showAlternatePhone) stringResource(R.string.cd_hide_amounts) else stringResource(R.string.cd_show_amounts),
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
                            AppIconButton(onClick = { showPartyUpi = !showPartyUpi }) {
                                Icon(
                                    imageVector = if (showPartyUpi) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPartyUpi) stringResource(R.string.cd_hide_amounts) else stringResource(R.string.cd_show_amounts),
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

                Text(
                    text = stringResource(R.string.preferred_language_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryInkGrey
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val languages = listOf(
                        "en" to "English",
                        "hi" to "हिन्दी",
                        "bn" to "বাংলা",
                        "as" to "অসমীয়া",
                        "ne" to "नेपाली"
                    )
                    languages.forEach { (code, label) ->
                        MonochromeTogglePill(
                            text = label,
                            selected = language == code,
                            onClick = { language = code }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                        .background(SubtleSurfaceGrey, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.quiet_hours_override_label),
                            style = MaterialTheme.typography.titleSmall,
                            color = PureBlack
                        )
                        Text(
                            text = stringResource(R.string.quiet_hours_override_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryInkGrey
                        )
                    }
                    UdhaarAnimatedSwitch(
                        checked = quietHoursOverride,
                        onCheckedChange = { quietHoursOverride = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PureWhite,
                            checkedTrackColor = PureBlack
                        )
                    )
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
                            preferredCurrency = preferredCurrency,
                            language = language,
                            quietHoursOverride = quietHoursOverride
                        )
                        onSave(entity)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
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
