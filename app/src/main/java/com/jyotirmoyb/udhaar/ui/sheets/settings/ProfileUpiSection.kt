package com.jyotirmoyb.udhaar.ui.sheets.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.ui.common.PhoneMaskVisualTransformation
import com.jyotirmoyb.udhaar.ui.common.SettingsSectionCard
import com.jyotirmoyb.udhaar.ui.common.UpiMaskVisualTransformation
import com.jyotirmoyb.udhaar.util.InterestCalculator

@Composable
fun ProfileUpiSection(
    payeeName: String,
    onPayeeNameChange: (String) -> Unit,
    upiId: String,
    onUpiIdChange: (String) -> Unit,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    profilePhotoUri: String,
    onProfilePhotoChanged: (String) -> Unit,
    onShareMyPaymentDetails: () -> Unit,
    onOpenGlobalQrModal: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showFullUpiId by rememberSaveable { mutableStateOf(false) }
    var showFullPhoneNumber by rememberSaveable { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onProfilePhotoChanged(uri.toString())
        }
    }

    SettingsSectionCard(
        sectionNumber = "1",
        title = "PROFILE AND UPI",
        subtitle = "Business name & UPI ID for QR collection and WhatsApp reminders",
        testTag = "section_profile_and_upi"
    ) {
        OutlinedTextField(
            value = payeeName,
            onValueChange = onPayeeNameChange,
            label = { Text(stringResource(R.string.ui_str_your_business_payee_name_2ece56)) },
            placeholder = { Text(stringResource(R.string.ui_str_e_g_sharma_general_store_99d829)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_payee_name_input")
        )

        OutlinedTextField(
            value = upiId,
            onValueChange = {
                showFullUpiId = true
                onUpiIdChange(it)
            },
            label = { Text(stringResource(R.string.ui_str_your_default_upi_id_for_72f5b9)) },
            placeholder = { Text(stringResource(R.string.ui_str_e_g_merchant_okicici_a9d993)) },
            singleLine = true,
            visualTransformation = if (showFullUpiId || upiId.isBlank()) {
                VisualTransformation.None
            } else {
                UpiMaskVisualTransformation
            },
            trailingIcon = {
                if (upiId.isNotBlank()) {
                    IconButton(
                        onClick = { showFullUpiId = !showFullUpiId },
                        modifier = Modifier.testTag("toggle_settings_upi_visibility")
                    ) {
                        Icon(
                            imageVector = if (showFullUpiId) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showFullUpiId) "Hide UPI ID" else "Show UPI ID"
                        )
                    }
                }
            },
            supportingText = {
                if (upiId.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.ui_fmt_displayed_publicly_as_0e3b13, InterestCalculator.maskUpiId(upiId)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_upi_id_input")
        )

        OutlinedTextField(
            value = phoneNumber,
            onValueChange = {
                showFullPhoneNumber = true
                onPhoneNumberChange(it)
            },
            label = { Text(stringResource(R.string.ui_str_your_phone_number_for_verification_8fdaa6)) },
            placeholder = { Text(stringResource(R.string.ui_str_e_g_91_9876543210_46e6ed)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            visualTransformation = if (showFullPhoneNumber || phoneNumber.isBlank()) {
                VisualTransformation.None
            } else {
                PhoneMaskVisualTransformation
            },
            trailingIcon = {
                if (phoneNumber.isNotBlank()) {
                    IconButton(
                        onClick = { showFullPhoneNumber = !showFullPhoneNumber },
                        modifier = Modifier.testTag("toggle_settings_phone_visibility")
                    ) {
                        Icon(
                            imageVector = if (showFullPhoneNumber) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showFullPhoneNumber) "Hide Phone Number" else "Show Phone Number"
                        )
                    }
                }
            },
            supportingText = {
                if (phoneNumber.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.ui_fmt_displayed_publicly_as_21e544, InterestCalculator.maskPhoneNumber(phoneNumber)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_phone_number_input")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("settings_pick_profile_photo_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = if (profilePhotoUri.isNotBlank()) {
                        stringResource(R.string.ui_str_change_photo_c7c5db)
                    } else {
                        stringResource(R.string.ui_str_optional_photo_89a9b1)
                    },
                    style = MaterialTheme.typography.labelMedium
                )
            }

            OutlinedButton(
                onClick = onShareMyPaymentDetails,
                modifier = Modifier
                    .weight(1.2f)
                    .height(48.dp)
                    .testTag("settings_share_payment_profile_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.ui_str_share_my_details_439f39), style = MaterialTheme.typography.labelMedium)
            }
        }

        OutlinedButton(
            onClick = onOpenGlobalQrModal,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("settings_open_upi_qr_button"),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.ui_str_open_my_upi_qr_code_0821c9), style = MaterialTheme.typography.labelLarge)
        }
    }
}
