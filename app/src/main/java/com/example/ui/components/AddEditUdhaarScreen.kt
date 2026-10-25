package com.example.ui.components

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.CompoundFrequency
import com.example.data.CurrencyType
import com.example.data.InterestRatePeriod
import com.example.data.InterestType
import com.example.data.PaymentMode
import com.example.data.TransactionType
import com.example.data.UdhaarTransaction
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.PureBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SecondaryInkGrey
import com.example.ui.theme.SubtleSurfaceGrey
import com.example.util.InterestCalculator
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditUdhaarScreen(
    initialTransaction: UdhaarTransaction?,
    defaultCurrency: CurrencyType,
    onSave: (UdhaarTransaction) -> Unit,
    onCancel: () -> Unit
) {
    BackHandler {
        onCancel()
    }

    val context = LocalContext.current

    var transactionType by rememberSaveable {
        mutableStateOf(initialTransaction?.transactionType ?: TransactionType.GIVEN)
    }
    var currency by rememberSaveable {
        mutableStateOf(initialTransaction?.currency ?: defaultCurrency)
    }
    var partyName by rememberSaveable {
        mutableStateOf(initialTransaction?.partyName ?: "")
    }
    var phoneNumber by rememberSaveable {
        mutableStateOf(initialTransaction?.phoneNumber ?: "")
    }
    var principalText by rememberSaveable {
        mutableStateOf(
            initialTransaction?.principalAmount?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: ""
        )
    }
    var paymentMode by rememberSaveable {
        mutableStateOf(initialTransaction?.paymentMode ?: PaymentMode.CASH)
    }
    var isInterestEnabled by rememberSaveable {
        mutableStateOf(initialTransaction?.isInterestEnabled ?: false)
    }
    var interestType by rememberSaveable {
        mutableStateOf(initialTransaction?.interestType ?: InterestType.SIMPLE)
    }
    var interestRateText by rememberSaveable {
        mutableStateOf(
            initialTransaction?.interestRate?.takeIf { it > 0.0 }?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: "2"
        )
    }
    var interestRatePeriod by rememberSaveable {
        mutableStateOf(initialTransaction?.interestRatePeriod ?: InterestRatePeriod.PER_MONTH)
    }
    var compoundFrequency by rememberSaveable {
        mutableStateOf(initialTransaction?.compoundFrequency ?: CompoundFrequency.MONTHLY)
    }
    var startDateMillis by rememberSaveable {
        mutableStateOf(initialTransaction?.startDateMillis ?: System.currentTimeMillis())
    }
    var expiryDateMillis by rememberSaveable {
        mutableStateOf<Long?>(initialTransaction?.expiryDateMillis)
    }
    var reminderDateMillis by rememberSaveable {
        mutableStateOf<Long?>(initialTransaction?.reminderDateMillis)
    }
    var notes by rememberSaveable {
        mutableStateOf(initialTransaction?.notes ?: "")
    }
    var validationError by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showExpiryDatePicker by remember { mutableStateOf(false) }
    var showReminderDatePicker by remember { mutableStateOf(false) }

    // Zero-permission Phone Contact Picker using CommonDataKinds.Phone.CONTENT_URI
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val contactUri = result.data?.data ?: return@rememberLauncherForActivityResult
        runCatching {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            context.contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (nameIndex >= 0) {
                        val pickedName = cursor.getString(nameIndex) ?: ""
                        if (pickedName.isNotBlank()) partyName = pickedName
                    }
                    if (numberIndex >= 0) {
                        val pickedNumber = cursor.getString(numberIndex) ?: ""
                        if (pickedNumber.isNotBlank()) phoneNumber = pickedNumber
                    }
                }
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        showReminderDatePicker = true
    }

    // Live interest calculation preview
    val previewSummary = remember(
        principalText,
        currency,
        isInterestEnabled,
        interestType,
        interestRateText,
        interestRatePeriod,
        compoundFrequency,
        startDateMillis,
        expiryDateMillis
    ) {
        val principalVal = principalText.toDoubleOrNull() ?: 0.0
        val rateVal = interestRateText.toDoubleOrNull() ?: 0.0
        val tempTx = UdhaarTransaction(
            partyName = partyName.ifBlank { "Party" },
            currency = currency,
            principalAmount = principalVal,
            isInterestEnabled = isInterestEnabled,
            interestType = interestType,
            interestRate = rateVal,
            interestRatePeriod = interestRatePeriod,
            compoundFrequency = compoundFrequency,
            startDateMillis = startDateMillis,
            expiryDateMillis = expiryDateMillis
        )
        // Preview accrued interest up to expiry date (if future) or today
        val targetMillis = expiryDateMillis?.takeIf { it > System.currentTimeMillis() }
            ?: System.currentTimeMillis()
        InterestCalculator.calculate(tempTx, targetMillis)
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

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = PureWhite,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PureWhite)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onCancel,
                            modifier = Modifier.testTag("back_to_dashboard_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = PureBlack
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = if (initialTransaction == null) "NEW UDHAAR RECORD" else "EDIT UDHAAR RECORD",
                                style = MaterialTheme.typography.titleLarge,
                                color = PureBlack
                            )
                            Text(
                                text = "MONOCHROME LEDGER ENTRY",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryInkGrey
                            )
                        }
                    }
                }
                HorizontalDivider(color = BorderGrey, thickness = 1.dp)
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PureWhite)
                    .navigationBarsPadding()
            ) {
                HorizontalDivider(color = BorderGrey, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("cancel_udhaar_button"),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, PureBlack),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                    ) {
                        Text("CANCEL", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = {
                            val principal = principalText.trim().toDoubleOrNull()
                            if (partyName.trim().isEmpty()) {
                                validationError = "Please enter a Party / Contact Name."
                                return@Button
                            }
                            if (principal == null || principal <= 0.0) {
                                validationError = "Please enter a valid Principal Amount greater than 0."
                                return@Button
                            }
                            val rate = if (isInterestEnabled) {
                                interestRateText.trim().toDoubleOrNull() ?: 0.0
                            } else {
                                0.0
                            }

                            val record = (initialTransaction ?: UdhaarTransaction(partyName = "", principalAmount = principal)).copy(
                                transactionType = transactionType,
                                partyName = partyName.trim(),
                                phoneNumber = phoneNumber.trim(),
                                currency = currency,
                                principalAmount = principal,
                                paymentMode = paymentMode,
                                isInterestEnabled = isInterestEnabled,
                                interestType = interestType,
                                interestRate = rate,
                                interestRatePeriod = interestRatePeriod,
                                compoundFrequency = compoundFrequency,
                                startDateMillis = startDateMillis,
                                expiryDateMillis = expiryDateMillis,
                                reminderDateMillis = reminderDateMillis,
                                notes = notes.trim()
                            )
                            onSave(record)
                        },
                        modifier = Modifier
                            .weight(1.5f)
                            .height(50.dp)
                            .testTag("save_udhaar_button"),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PureBlack,
                            contentColor = PureWhite
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SAVE UDHAAR", style = MaterialTheme.typography.labelLarge)
                    }
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (validationError != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, PureBlack, RoundedCornerShape(4.dp))
                            .background(SubtleSurfaceGrey, RoundedCornerShape(4.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = validationError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = PureBlack
                        )
                    }
                }

                // 1. Transaction Type & Currency Row
                SectionHeader("1. TRANSACTION TYPE & CURRENCY")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TransactionType.entries.forEach { type ->
                        val selected = transactionType == type
                        MonochromeTogglePill(
                            text = type.label,
                            selected = selected,
                            onClick = { transactionType = type },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tx_type_${type.name.lowercase()}")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CurrencyType.entries.forEach { curr ->
                        val selected = currency == curr
                        MonochromeTogglePill(
                            text = curr.displayName,
                            selected = selected,
                            onClick = { currency = curr },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("currency_select_${curr.name.lowercase()}")
                        )
                    }
                }

                // 2. Party / Contact Name & Phone Number
                SectionHeader("2. PARTY / CONTACT DETAILS")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = partyName,
                        onValueChange = {
                            partyName = it
                            validationError = null
                        },
                        label = { Text("Party / Contact Name *") },
                        placeholder = { Text("e.g. Ramesh Sharma") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        colors = textFieldColors,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("party_name_input")
                    )
                    OutlinedButton(
                        onClick = {
                            val pickIntent = Intent(
                                Intent.ACTION_PICK,
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                            )
                            runCatching { contactPickerLauncher.launch(pickIntent) }
                        },
                        modifier = Modifier
                            .height(56.dp)
                            .padding(top = 6.dp)
                            .testTag("pick_contact_button"),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, PureBlack),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Contacts,
                            contentDescription = "Pick from phone contacts",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("CONTACTS", style = MaterialTheme.typography.labelMedium)
                    }
                }

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone / WhatsApp Number (Optional)") },
                    placeholder = { Text("${currency.defaultCountryCode} 98XXXXXXXX") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("phone_number_input")
                )

                // 3. Principal Amount & Payment Mode
                SectionHeader("3. PRINCIPAL AMOUNT & PAYMENT MODE")
                OutlinedTextField(
                    value = principalText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) {
                            principalText = input
                            validationError = null
                        }
                    },
                    label = { Text("Principal Amount (${currency.symbol}) *") },
                    prefix = {
                        Text(
                            text = "${currency.symbol} ",
                            style = MaterialTheme.typography.titleMedium,
                            color = PureBlack
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("principal_amount_input")
                )

                Text(
                    text = "PAYMENT MODE",
                    style = MaterialTheme.typography.labelMedium,
                    color = SecondaryInkGrey
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaymentMode.entries.forEach { mode ->
                        val selected = paymentMode == mode
                        MonochromeTogglePill(
                            text = mode.label,
                            selected = selected,
                            onClick = { paymentMode = mode },
                            modifier = Modifier.testTag("payment_mode_${mode.name.lowercase()}")
                        )
                    }
                }

                // 4. Interest Configuration (Optional Toggle)
                HorizontalDivider(color = BorderGrey, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
                        .background(SubtleSurfaceGrey, RoundedCornerShape(4.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "INTEREST APPLICABLE",
                            style = MaterialTheme.typography.titleSmall,
                            color = PureBlack
                        )
                        Text(
                            text = if (isInterestEnabled) "Enabled • Accrues from Start Date" else "No interest (0% flat principal)",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryInkGrey
                        )
                    }
                    Switch(
                        checked = isInterestEnabled,
                        onCheckedChange = { isInterestEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PureWhite,
                            checkedTrackColor = PureBlack,
                            uncheckedThumbColor = PureBlack,
                            uncheckedTrackColor = PureWhite,
                            uncheckedBorderColor = PureBlack
                        ),
                        modifier = Modifier.testTag("interest_applicable_switch")
                    )
                }

                AnimatedVisibility(visible = isInterestEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, PureBlack, RoundedCornerShape(4.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "INTEREST TYPE",
                            style = MaterialTheme.typography.labelMedium,
                            color = SecondaryInkGrey
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            InterestType.entries.forEach { type ->
                                MonochromeTogglePill(
                                    text = type.label,
                                    selected = interestType == type,
                                    onClick = { interestType = type },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("interest_type_${type.name.lowercase()}")
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = interestRateText,
                                onValueChange = { input ->
                                    if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d*$"))) {
                                        interestRateText = input
                                    }
                                },
                                label = { Text("Rate (%)") },
                                suffix = { Text("%") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                colors = textFieldColors,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("interest_rate_input")
                            )

                            Column(
                                modifier = Modifier.weight(1.3f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                InterestRatePeriod.entries.forEach { period ->
                                    MonochromeTogglePill(
                                        text = period.label,
                                        selected = interestRatePeriod == period,
                                        onClick = { interestRatePeriod = period },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("rate_period_${period.name.lowercase()}")
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(visible = interestType == InterestType.COMPOUND) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "COMPOUNDING FREQUENCY",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SecondaryInkGrey
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CompoundFrequency.entries.forEach { freq ->
                                        MonochromeTogglePill(
                                            text = freq.label,
                                            selected = compoundFrequency == freq,
                                            onClick = { compoundFrequency = freq },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("compound_freq_${freq.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                        }

                        // Start Date selector
                        DateSelectorRow(
                            label = "START DATE (INTEREST ACCRUAL)",
                            valueText = InterestCalculator.formatDate(startDateMillis),
                            onSelectClick = { showStartDatePicker = true },
                            onClearClick = null,
                            testTag = "start_date_selector"
                        )

                        // Live Interest Breakdown Preview Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SubtleSurfaceGrey, RoundedCornerShape(4.dp))
                                .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "LIVE BREAKDOWN PREVIEW",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SecondaryInkGrey
                                )
                                Text(
                                    text = previewSummary.subtitleBreakdown,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PureBlack
                                )
                            }
                        }
                    }
                }

                // 5. Expiry / Due Date & Scheduled Reminder
                HorizontalDivider(color = BorderGrey, thickness = 1.dp)
                SectionHeader("5. SETTLEMENT DUE DATE & REMINDER")

                DateSelectorRow(
                    label = "EXPIRY / DUE DATE (OPTIONAL)",
                    valueText = expiryDateMillis?.let { InterestCalculator.formatDate(it) } ?: "Not set (Open-ended)",
                    onSelectClick = { showExpiryDatePicker = true },
                    onClearClick = expiryDateMillis?.let { { expiryDateMillis = null } },
                    testTag = "expiry_date_selector"
                )

                DateSelectorRow(
                    label = "SCHEDULED WHATSAPP REMINDER (OPTIONAL)",
                    valueText = reminderDateMillis?.let { InterestCalculator.formatDateTime(it) } ?: "No reminder scheduled",
                    icon = Icons.Default.NotificationsActive,
                    onSelectClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            showReminderDatePicker = true
                        }
                    },
                    onClearClick = reminderDateMillis?.let { { reminderDateMillis = null } },
                    testTag = "reminder_date_selector"
                )

                // 6. Notes
                SectionHeader("6. NOTES (OPTIONAL)")
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Short Note / Reference") },
                    placeholder = { Text("e.g. Invoice #104 or emergency medical advance") },
                    maxLines = 3,
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notes_input")
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showStartDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = startDateMillis)
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { startDateMillis = it }
                        showStartDatePicker = false
                    }
                ) {
                    Text("CONFIRM", color = PureBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text("CANCEL", color = SecondaryInkGrey)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showExpiryDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = expiryDateMillis ?: (System.currentTimeMillis() + 30L * 86_400_000L)
        )
        DatePickerDialog(
            onDismissRequest = { showExpiryDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { expiryDateMillis = it }
                        showExpiryDatePicker = false
                    }
                ) {
                    Text("CONFIRM", color = PureBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExpiryDatePicker = false }) {
                    Text("CANCEL", color = SecondaryInkGrey)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showReminderDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = reminderDateMillis ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showReminderDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedDate = pickerState.selectedDateMillis ?: System.currentTimeMillis()
                        showReminderDatePicker = false
                        val cal = Calendar.getInstance().apply { timeInMillis = selectedDate }
                        val nowCal = Calendar.getInstance()
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                cal.set(Calendar.MINUTE, minute)
                                cal.set(Calendar.SECOND, 0)
                                reminderDateMillis = cal.timeInMillis
                            },
                            nowCal.get(Calendar.HOUR_OF_DAY),
                            (nowCal.get(Calendar.MINUTE) + 5) % 60,
                            false
                        ).show()
                    }
                ) {
                    Text("NEXT: PICK TIME", color = PureBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReminderDatePicker = false }) {
                    Text("CANCEL", color = SecondaryInkGrey)
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
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
    Box(
        modifier = modifier
            .height(48.dp)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) PureBlack else BorderGrey,
                shape = RoundedCornerShape(4.dp)
            )
            .background(
                color = if (selected) PureBlack else PureWhite,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) PureWhite else PureBlack
        )
    }
}

@Composable
private fun DateSelectorRow(
    label: String,
    valueText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.CalendarToday,
    onSelectClick: () -> Unit,
    onClearClick: (() -> Unit)?,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
            .background(SubtleSurfaceGrey, RoundedCornerShape(4.dp))
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
            IconButton(onClick = onClearClick) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear date",
                    tint = PureBlack
                )
            }
        }
    }
}
