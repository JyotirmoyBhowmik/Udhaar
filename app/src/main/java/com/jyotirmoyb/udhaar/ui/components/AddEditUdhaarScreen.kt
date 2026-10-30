package com.jyotirmoyb.udhaar.ui.components

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Surface
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.jyotirmoyb.udhaar.data.Attachment
import com.jyotirmoyb.udhaar.data.CompoundFrequency
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.InterestRatePeriod
import com.jyotirmoyb.udhaar.data.InterestType
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.PaymentMode
import com.jyotirmoyb.udhaar.data.TransactionType
import com.jyotirmoyb.udhaar.data.UdhaarTransaction
import com.jyotirmoyb.udhaar.ui.PreselectedPartyDraft
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.ui.theme.SubtleSurfaceGrey
import com.jyotirmoyb.udhaar.util.AttachmentManager
import com.jyotirmoyb.udhaar.util.InterestCalculator
import java.io.File
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditUdhaarScreen(
    initialTransaction: UdhaarTransaction?,
    preselectedPartyDraft: PreselectedPartyDraft? = null,
    savedParties: List<PartyEntity> = emptyList(),
    enabledCurrencies: List<CurrencyType> = listOf(CurrencyType.INR),
    defaultCurrency: CurrencyType,
    draftAttachments: List<Attachment> = emptyList(),
    existingAttachments: List<Attachment> = emptyList(),
    onEnableCurrency: (CurrencyType) -> Unit = {},
    onAddDraftAttachment: (Attachment) -> Unit = {},
    onRemoveDraftAttachment: (Attachment) -> Unit = {},
    onDeleteExistingAttachment: (Attachment) -> Unit = {},
    onOpenAttachmentViewer: (Attachment) -> Unit = {},
    onOpenInAppContactPicker: () -> Unit = {},
    onSave: (UdhaarTransaction) -> Unit,
    onCancel: () -> Unit
) {
    BackHandler {
        onCancel()
    }

    val context = LocalContext.current

    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && cameraTempUri != null) {
            val processed = AttachmentManager.processCapturedImage(
                context = context,
                sourceUri = cameraTempUri!!,
                transactionId = initialTransaction?.id ?: 0L,
                addedBy = "You"
            )
            if (processed != null) {
                onAddDraftAttachment(processed)
            }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val processed = AttachmentManager.processCapturedImage(
                context = context,
                sourceUri = uri,
                transactionId = initialTransaction?.id ?: 0L,
                addedBy = "You"
            )
            if (processed != null) {
                onAddDraftAttachment(processed)
            }
        }
    }
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val processed = AttachmentManager.copyDocumentToStorage(
                context = context,
                sourceUri = uri,
                transactionId = initialTransaction?.id ?: 0L,
                addedBy = "You"
            )
            if (processed != null) {
                onAddDraftAttachment(processed)
            }
        }
    }

    var selectedPartyId by rememberSaveable {
        mutableStateOf<Long?>(initialTransaction?.partyId ?: preselectedPartyDraft?.partyId)
    }
    var transactionType by rememberSaveable {
        mutableStateOf(initialTransaction?.transactionType ?: TransactionType.GIVEN)
    }
    var currency by rememberSaveable {
        mutableStateOf(
            initialTransaction?.currency
                ?: preselectedPartyDraft?.preferredCurrency?.takeIf { it in enabledCurrencies }
                ?: defaultCurrency
        )
    }
    var partyName by rememberSaveable {
        mutableStateOf(initialTransaction?.partyName ?: preselectedPartyDraft?.name ?: "")
    }
    var phoneNumber by rememberSaveable {
        mutableStateOf(initialTransaction?.phoneNumber ?: preselectedPartyDraft?.phoneNumber ?: "")
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
    var showMoreDetails by rememberSaveable {
        mutableStateOf(
            initialTransaction != null && (
                initialTransaction.isInterestEnabled ||
                    initialTransaction.notes.isNotBlank() ||
                    initialTransaction.reminderDateMillis != null
                )
        )
    }
    val plainAmountLabel = if (transactionType == TransactionType.GIVEN) {
        "Amount lent"
    } else {
        "Amount borrowed"
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val contactUri = result.data?.data ?: return@rememberLauncherForActivityResult
        val extracted = com.jyotirmoyb.udhaar.util.ContactPickerHelper.extractContactInfo(context, contactUri)
        if (extracted != null) {
            val (pickedName, pickedPhone) = extracted
            if (pickedName.isNotBlank()) partyName = pickedName
            if (pickedPhone.isNotBlank()) phoneNumber = pickedPhone
            val matchedParty = savedParties.find {
                it.name.trim().equals(pickedName.trim(), ignoreCase = true) ||
                    (pickedPhone.isNotBlank() && it.phoneNumber.trim().endsWith(pickedPhone.takeLast(10)))
            }
            if (matchedParty != null) {
                selectedPartyId = matchedParty.partyId
            }
            android.widget.Toast.makeText(context, "Imported contact: $pickedName", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(context, "Could not load contact details. Please enter name manually.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        showReminderDatePicker = true
    }

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
                    .statusBarsPadding()
                    .padding(top = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
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
                                color = PureBlack,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                    .imePadding()
            ) {
                HorizontalDivider(color = BorderGrey, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("cancel_udhaar_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PureBlack),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                    ) {
                        Text("CANCEL", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = {
                            val principal = principalText.trim().toDoubleOrNull()
                            if (partyName.trim().isEmpty()) {
                                validationError = "Please enter who this udhaar is with (Step 1)."
                                return@Button
                            }
                            if (principal == null || principal <= 0.0) {
                                validationError = "Please enter a valid ${plainAmountLabel.lowercase()} greater than 0 (Step 2)."
                                return@Button
                            }
                            val rate = if (isInterestEnabled) {
                                interestRateText.trim().toDoubleOrNull() ?: 0.0
                            } else {
                                0.0
                            }

                            val record = (initialTransaction ?: UdhaarTransaction(partyName = "", principalAmount = principal)).copy(
                                partyId = selectedPartyId,
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
                            .height(48.dp)
                            .testTag("save_udhaar_button"),
                        shape = RoundedCornerShape(12.dp),
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
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 44.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (validationError != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, PureBlack, RoundedCornerShape(12.dp))
                            .background(SubtleSurfaceGrey, RoundedCornerShape(12.dp))
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

                // Step progress summary strip
                val step1Done = partyName.trim().isNotEmpty()
                val step2Done = (principalText.trim().toDoubleOrNull() ?: 0.0) > 0.0
                val step3Done = expiryDateMillis != null
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
                        text = if (step1Done) "✓ 1. Who" else "1. Who",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (!step1Done) FontWeight.Bold else FontWeight.SemiBold,
                        color = PureBlack
                    )
                    Text(
                        text = if (step2Done) "✓ 2. How much" else "2. How much",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (step1Done && !step2Done) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (step1Done || step2Done) PureBlack else SecondaryInkGrey
                    )
                    Text(
                        text = if (step3Done) "✓ 3. When" else "3. When (Optional)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (step1Done && step2Done) FontWeight.Bold else FontWeight.Normal,
                        color = if (step1Done && step2Done) PureBlack else SecondaryInkGrey
                    )
                }

                // STEP 1: WHO
                SectionHeader("STEP 1 · WHO IS THIS UDHAAR WITH?")
                val topRecentAndFavorites = remember(savedParties) {
                    savedParties.sortedWith(
                        compareByDescending<PartyEntity> { it.isFavorite }
                            .thenByDescending { it.lastInteractionMillis }
                    ).take(8)
                }
                if (topRecentAndFavorites.isNotEmpty()) {
                    Text(
                        text = "Recent & favourite people",
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
                        items(topRecentAndFavorites, key = { it.partyId }) { savedParty ->
                            val isChosen = partyName.equals(savedParty.name, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(if (isChosen) PureBlack else SubtleSurfaceGrey)
                                    .border(
                                        width = 1.dp,
                                        color = if (isChosen) PureBlack else BorderGrey,
                                        shape = RoundedCornerShape(22.dp)
                                    )
                                    .clickable {
                                        selectedPartyId = savedParty.partyId.takeIf { it > 0L }
                                        partyName = savedParty.name
                                        phoneNumber = savedParty.phoneNumber
                                        currency = savedParty.preferredCurrency
                                    }
                                    .padding(start = 6.dp, end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PersonAvatar(
                                    name = savedParty.name,
                                    photoUri = savedParty.photoUri,
                                    size = 30.dp
                                )
                                Text(
                                    text = if (savedParty.isFavorite) "★ ${savedParty.name}" else savedParty.name,
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
                        onValueChange = {
                            partyName = it
                            validationError = null
                        },
                        label = { Text("Person or business name *") },
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
                            onOpenInAppContactPicker()
                        },
                        modifier = Modifier
                            .height(56.dp)
                            .padding(top = 6.dp)
                            .testTag("pick_contact_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PureBlack),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Contacts,
                            contentDescription = "Pick from phone contacts",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Contacts", style = MaterialTheme.typography.labelMedium)
                    }
                }

                var showPhoneText by rememberSaveable { mutableStateOf(false) }
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = {
                        phoneNumber = it
                        showPhoneText = true
                    },
                    label = { Text("Phone / WhatsApp number (Optional)") },
                    placeholder = { Text("${currency.defaultCountryCode} 98*** ***10") },
                    singleLine = true,
                    visualTransformation = if (showPhoneText) VisualTransformation.None else PhoneMaskVisualTransformation,
                    trailingIcon = if (phoneNumber.isNotBlank()) {
                        {
                            IconButton(onClick = { showPhoneText = !showPhoneText }) {
                                Icon(
                                    imageVector = if (showPhoneText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPhoneText) "Mask phone number" else "Show phone number",
                                    tint = PureBlack
                                )
                            }
                        }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = textFieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("phone_number_input")
                )

                HorizontalDivider(color = BorderGrey, thickness = 1.dp)

                // STEP 2: HOW MUCH
                SectionHeader("STEP 2 · HOW MUCH?")
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
                                onClick = { currency = curr },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("currency_select_${curr.name.lowercase()}")
                            )
                        }
                    }
                }

                // Quick amount chips: 500, 1000, 5000, Custom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("500", "1000", "5000").forEach { quickAmt ->
                        val isQuickSelected = principalText.trim() == quickAmt
                        MonochromeTogglePill(
                            text = "${currency.symbol}$quickAmt",
                            selected = isQuickSelected,
                            onClick = {
                                principalText = quickAmt
                                validationError = null
                            },
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
                                principalText = ""
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
                            principalText = input
                            validationError = null
                        }
                    },
                    label = { Text("$plainAmountLabel (${currency.symbol}) *") },
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

                if (step1Done && step2Done) {
                    Text(
                        text = "Ready to save now — or set a due date below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryInkGrey,
                        fontWeight = FontWeight.Medium
                    )
                }

                HorizontalDivider(color = BorderGrey, thickness = 1.dp)

                // STEP 3: WHEN
                SectionHeader("STEP 3 · WHEN IS IT DUE? (OPTIONAL)")
                val nowMs = remember { System.currentTimeMillis() }
                val oneWeekMs = remember(nowMs) { nowMs + 7L * 86_400_000L }
                val oneMonthMs = remember(nowMs) { nowMs + 30L * 86_400_000L }
                val expDaysFromNow = expiryDateMillis?.let { ((it - nowMs) / 86_400_000L).toInt() }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MonochromeTogglePill(
                        text = "In 1 week",
                        selected = expDaysFromNow in 6..8,
                        onClick = { expiryDateMillis = oneWeekMs },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("due_chip_1_week")
                    )
                    MonochromeTogglePill(
                        text = "In 1 month",
                        selected = expDaysFromNow in 28..31,
                        onClick = { expiryDateMillis = oneMonthMs },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("due_chip_1_month")
                    )
                    MonochromeTogglePill(
                        text = "Custom",
                        selected = expiryDateMillis != null && expDaysFromNow !in 6..8 && expDaysFromNow !in 28..31,
                        onClick = { showExpiryDatePicker = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("due_chip_custom")
                    )
                }

                DateSelectorRow(
                    label = "Due date",
                    valueText = expiryDateMillis?.let { InterestCalculator.formatDate(it) } ?: "No due date (Open-ended)",
                    onSelectClick = { showExpiryDatePicker = true },
                    onClearClick = expiryDateMillis?.let { { expiryDateMillis = null } },
                    testTag = "expiry_date_selector"
                )

                HorizontalDivider(color = BorderGrey, thickness = 1.dp)

                // OPTIONAL MORE DETAILS COLLAPSIBLE SECTION
                val allCurrentAttachments = draftAttachments + existingAttachments
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SubtleSurfaceGrey)
                        .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                        .clickable { showMoreDetails = !showMoreDetails }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("toggle_more_details_button"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "More details (Optional)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = PureBlack
                        )
                        Text(
                            text = "Payment mode, interest, reminder, notes & attachments",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryInkGrey
                        )
                    }
                    Icon(
                        imageVector = if (showMoreDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showMoreDetails) "Collapse more details" else "Expand more details",
                        tint = PureBlack
                    )
                }

                AnimatedVisibility(visible = showMoreDetails) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Payment mode",
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

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                                .background(SubtleSurfaceGrey, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Charge or pay interest",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = PureBlack
                                )
                                Text(
                                    text = if (isInterestEnabled) {
                                        "Enabled • Accrues from start date"
                                    } else {
                                        "Interest-free (only the original ${plainAmountLabel.lowercase()} is due)"
                                    },
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
                                    .border(1.dp, PureBlack, RoundedCornerShape(12.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Interest calculation type",
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
                                Text(
                                    text = if (interestType == InterestType.SIMPLE) {
                                        "Helper: Simple interest is calculated only on the original ${plainAmountLabel.lowercase()}."
                                    } else {
                                        "Helper: Compound interest adds unpaid interest to the balance each period."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryInkGrey,
                                    modifier = Modifier.testTag("interest_type_helper_text")
                                )

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
                                            text = "Compounding frequency",
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

                                DateSelectorRow(
                                    label = "Start date (when interest begins)",
                                    valueText = InterestCalculator.formatDate(startDateMillis),
                                    onSelectClick = { showStartDatePicker = true },
                                    onClearClick = null,
                                    testTag = "start_date_selector"
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SubtleSurfaceGrey, RoundedCornerShape(12.dp))
                                        .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                                        .padding(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "Live summary preview",
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

                        DateSelectorRow(
                            label = "Scheduled reminder (Optional)",
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

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Note or reason (Optional)") },
                            placeholder = { Text("e.g. Shop supplies or medical advance") },
                            maxLines = 3,
                            colors = textFieldColors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("notes_input")
                        )

                        Text(
                            text = "Attachments & bills (${allCurrentAttachments.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = SecondaryInkGrey
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val (uri, _) = AttachmentManager.createCameraTempFileUri(context)
                                    cameraTempUri = uri
                                    cameraLauncher.launch(uri)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("add_attachment_camera_button"),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Take photo with camera", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Camera", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = {
                                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("add_attachment_gallery_button"),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = "Pick image from gallery", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gallery", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = {
                                    documentLauncher.launch(arrayOf("application/pdf", "image/*", "text/*", "*/*"))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("add_attachment_doc_button"),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Icon(Icons.Default.Description, contentDescription = "Attach PDF or document", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Doc/PDF", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        if (allCurrentAttachments.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("add_edit_attachments_row"),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(allCurrentAttachments, key = { it.localPath }) { att ->
                                    val isImg = att.mimeType.startsWith("image/")
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, BorderGrey),
                                        modifier = Modifier
                                            .size(90.dp)
                                            .clickable { onOpenAttachmentViewer(att) }
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize()) {
                                            if (isImg) {
                                                AsyncImage(
                                                    model = File(att.localPath),
                                                    contentDescription = att.fileName,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(6.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.Description,
                                                        contentDescription = att.fileName,
                                                        modifier = Modifier.size(24.dp),
                                                        tint = PureBlack
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        att.fileName,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (att.id > 0L) onDeleteExistingAttachment(att)
                                                    else onRemoveDraftAttachment(att)
                                                },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(24.dp)
                                                    .background(
                                                        PureBlack.copy(alpha = 0.7f),
                                                        RoundedCornerShape(12.dp)
                                                    )
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Remove attachment",
                                                    tint = PureWhite,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

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
            .height(40.dp)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) PureBlack else BorderGrey,
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                color = if (selected) PureBlack else PureWhite,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) PureWhite else PureBlack,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
