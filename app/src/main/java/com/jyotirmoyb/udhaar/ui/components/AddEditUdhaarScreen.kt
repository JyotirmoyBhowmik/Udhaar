package com.jyotirmoyb.udhaar.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jyotirmoyb.udhaar.R
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
import com.jyotirmoyb.udhaar.ui.theme.UdhaarSpacing
import com.jyotirmoyb.udhaar.util.AttachmentManager
import com.jyotirmoyb.udhaar.util.InterestCalculator

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
    onDeleteTransaction: ((UdhaarTransaction) -> Unit)? = null,
    onCancel: () -> Unit
) {
    BackHandler { onCancel() }

    val context = LocalContext.current
    val reducedMotion = rememberReducedMotionEnabled()

    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = cameraTempUri
        if (success && capturedUri != null) {
            AttachmentManager.processCapturedImage(
                context = context,
                sourceUri = capturedUri,
                transactionId = initialTransaction?.id ?: 0L,
                addedBy = "You"
            )?.let { onAddDraftAttachment(it) }
        }
    }

    val launchCameraSafely = {
        try {
            val (uri, _) = AttachmentManager.createCameraTempFileUri(context)
            cameraTempUri = uri
            cameraLauncher.launch(uri)
        } catch (_: Exception) {}
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> if (isGranted) launchCameraSafely() }

    val onCameraClick = {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) launchCameraSafely() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            AttachmentManager.processCapturedImage(
                context = context,
                sourceUri = uri,
                transactionId = initialTransaction?.id ?: 0L,
                addedBy = "You"
            )?.let { onAddDraftAttachment(it) }
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            AttachmentManager.copyDocumentToStorage(
                context = context,
                sourceUri = uri,
                transactionId = initialTransaction?.id ?: 0L,
                addedBy = "You"
            )?.let { onAddDraftAttachment(it) }
        }
    }

    var selectedPartyId by rememberSaveable { mutableStateOf<Long?>(initialTransaction?.partyId ?: preselectedPartyDraft?.partyId) }
    var transactionType by rememberSaveable { mutableStateOf(initialTransaction?.transactionType ?: TransactionType.GIVEN) }
    var currency by rememberSaveable {
        mutableStateOf(
            initialTransaction?.currency
                ?: preselectedPartyDraft?.preferredCurrency?.takeIf { it in enabledCurrencies }
                ?: defaultCurrency
        )
    }
    var partyName by rememberSaveable { mutableStateOf(initialTransaction?.partyName ?: preselectedPartyDraft?.name ?: "") }
    var phoneNumber by rememberSaveable { mutableStateOf(initialTransaction?.phoneNumber ?: preselectedPartyDraft?.phoneNumber ?: "") }
    var principalText by rememberSaveable {
        mutableStateOf(
            initialTransaction?.principalAmountPaise?.let { paise ->
                if (paise % 100L == 0L) (paise / 100L).toString()
                else InterestCalculator.paiseToRupees(paise).toString()
            } ?: ""
        )
    }
    var paymentMode by rememberSaveable { mutableStateOf(initialTransaction?.paymentMode ?: PaymentMode.CASH) }
    var isInterestEnabled by rememberSaveable { mutableStateOf(initialTransaction?.isInterestEnabled ?: false) }
    var interestType by rememberSaveable { mutableStateOf(initialTransaction?.interestType ?: InterestType.SIMPLE) }
    var interestRateText by rememberSaveable {
        mutableStateOf(
            initialTransaction?.interestRate?.takeIf { it > 0.0 }?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            } ?: "2"
        )
    }
    var interestRatePeriod by rememberSaveable { mutableStateOf(initialTransaction?.interestRatePeriod ?: InterestRatePeriod.PER_MONTH) }
    var compoundFrequency by rememberSaveable { mutableStateOf(initialTransaction?.compoundFrequency ?: CompoundFrequency.MONTHLY) }
    var startDateMillis by rememberSaveable { mutableStateOf(initialTransaction?.startDateMillis ?: System.currentTimeMillis()) }
    var expiryDateMillis by rememberSaveable { mutableStateOf<Long?>(initialTransaction?.expiryDateMillis) }
    var reminderDateMillis by rememberSaveable { mutableStateOf<Long?>(initialTransaction?.reminderDateMillis) }
    var notes by rememberSaveable { mutableStateOf(initialTransaction?.notes ?: "") }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showExpiryDatePicker by remember { mutableStateOf(false) }
    var showReminderDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showMoreDetails by rememberSaveable { mutableStateOf(true) }
    val isEditingExisting = initialTransaction != null && initialTransaction.id > 0L

    if (showDeleteConfirmDialog && initialTransaction != null) {
        DeleteTransactionConfirmDialog(
            initialTransaction = initialTransaction,
            onDismiss = { showDeleteConfirmDialog = false },
            onConfirmDelete = {
                showDeleteConfirmDialog = false
                onDeleteTransaction?.invoke(it)
            }
        )
    }

    val plainAmountLabel = if (transactionType == TransactionType.GIVEN) "Amount lent" else "Amount borrowed"

    LaunchedEffect(preselectedPartyDraft) {
        if (preselectedPartyDraft != null && initialTransaction == null) {
            if (preselectedPartyDraft.name.isNotBlank()) {
                partyName = preselectedPartyDraft.name
                validationError = null
            }
            if (preselectedPartyDraft.phoneNumber.isNotBlank()) phoneNumber = preselectedPartyDraft.phoneNumber
            if (preselectedPartyDraft.partyId != null && preselectedPartyDraft.partyId > 0L) selectedPartyId = preselectedPartyDraft.partyId
            if (preselectedPartyDraft.preferredCurrency in enabledCurrencies) currency = preselectedPartyDraft.preferredCurrency
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
                partyName = pickedName
                validationError = null
            }
            if (pickedPhone.isNotBlank()) phoneNumber = pickedPhone
            val matchedParty = savedParties.find {
                it.name.trim().equals(pickedName.trim(), ignoreCase = true) ||
                    (pickedPhone.isNotBlank() && it.phoneNumber.trim().endsWith(pickedPhone.takeLast(10)))
            }
            if (matchedParty != null) selectedPartyId = matchedParty.partyId
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { showReminderDatePicker = true }

    val previewSummary = remember(
        principalText, currency, isInterestEnabled, interestType,
        interestRateText, interestRatePeriod, compoundFrequency, startDateMillis, expiryDateMillis
    ) {
        val principalPaise = InterestCalculator.rupeesStringToPaise(principalText) ?: 0L
        val rateVal = interestRateText.toDoubleOrNull() ?: 0.0
        val tempTx = UdhaarTransaction(
            partyName = partyName.ifBlank { "Party" },
            currency = currency,
            principalAmountPaise = principalPaise,
            isInterestEnabled = isInterestEnabled,
            interestType = interestType,
            interestRate = rateVal,
            interestRatePeriod = interestRatePeriod,
            compoundFrequency = compoundFrequency,
            startDateMillis = startDateMillis,
            expiryDateMillis = expiryDateMillis
        )
        val targetMillis = expiryDateMillis?.takeIf { it > System.currentTimeMillis() } ?: System.currentTimeMillis()
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
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        AppIconButton(
                            onClick = onCancel,
                            testTag = "back_to_dashboard_button",
                            modifier = Modifier.pressScaleFeedback()
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back),
                                tint = PureBlack
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = if (initialTransaction == null) stringResource(R.string.ui_str_new_udhaar_record_8b7ed4) else stringResource(R.string.ui_str_edit_udhaar_record_68199f),
                                style = MaterialTheme.typography.titleLarge,
                                color = PureBlack,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.ui_str_monochrome_ledger_entry_795f44),
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryInkGrey
                            )
                        }
                    }
                    if (isEditingExisting && onDeleteTransaction != null) {
                        AppIconButton(
                            onClick = { showDeleteConfirmDialog = true },
                            testTag = "edit_delete_transaction_top_button",
                            modifier = Modifier.pressScaleFeedback()
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.ui_str_delete_d6f563),
                                tint = MaterialTheme.colorScheme.error
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
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HorizontalDivider(color = BorderGrey, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .padding(horizontal = UdhaarSpacing.Large, vertical = UdhaarSpacing.Medium),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isEditingExisting && onDeleteTransaction != null) {
                        OutlinedButton(
                            onClick = { showDeleteConfirmDialog = true },
                            modifier = Modifier
                                .weight(0.9f)
                                .heightIn(min = 48.dp)
                                .pressScaleFeedback()
                                .testTag("edit_delete_transaction_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.ui_str_delete_d6f563),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .pressScaleFeedback()
                            .testTag("cancel_udhaar_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PureBlack),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PureBlack)
                    ) {
                        Text(stringResource(R.string.ui_str_cancel_1507c4), style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = {
                            val principalPaise = InterestCalculator.rupeesStringToPaise(principalText)
                            if (partyName.trim().isEmpty()) {
                                validationError = "Please enter who this udhaar is with (Step 1)."
                                return@Button
                            }
                            if (principalPaise == null || principalPaise <= 0L) {
                                validationError = "Please enter a valid ${plainAmountLabel.lowercase()} greater than 0 (Step 2)."
                                return@Button
                            }
                            val rate = if (isInterestEnabled) interestRateText.trim().toDoubleOrNull() ?: 0.0 else 0.0
                            val record = (initialTransaction ?: UdhaarTransaction(partyName = "", principalAmountPaise = principalPaise)).copy(
                                partyId = selectedPartyId,
                                transactionType = transactionType,
                                partyName = partyName.trim(),
                                phoneNumber = phoneNumber.trim(),
                                currency = currency,
                                principalAmountPaise = principalPaise,
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
                            .weight(1.4f)
                            .heightIn(min = UdhaarSpacing.PrimaryButtonHeight)
                            .pressScaleFeedback()
                            .testTag("save_udhaar_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PureBlack, contentColor = PureWhite)
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.ui_str_save_udhaar_3990bf), style = MaterialTheme.typography.labelLarge)
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
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 44.dp)
                    .testTag("adaptive_centered_form_container"),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val currentValidationError = validationError
                if (currentValidationError != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, PureBlack, RoundedCornerShape(12.dp))
                            .background(SubtleSurfaceGrey, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = currentValidationError,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = PureBlack
                        )
                    }
                }

                val step1Done = partyName.trim().isNotEmpty()
                val step2Done = (InterestCalculator.rupeesStringToPaise(principalText) ?: 0L) > 0L
                val step3Done = expiryDateMillis != null

                AddEditPartyAndAmountSection(
                    context = context,
                    reducedMotion = reducedMotion,
                    savedParties = savedParties,
                    partyName = partyName,
                    onPartyNameChange = {
                        partyName = it
                        validationError = null
                    },
                    phoneNumber = phoneNumber,
                    onPhoneNumberChange = { phoneNumber = it },
                    onSelectSavedParty = { savedParty ->
                        selectedPartyId = savedParty.partyId.takeIf { it > 0L }
                        partyName = savedParty.name
                        phoneNumber = savedParty.phoneNumber
                        currency = savedParty.preferredCurrency
                        validationError = null
                    },
                    transactionType = transactionType,
                    onTransactionTypeChange = { transactionType = it },
                    enabledCurrencies = enabledCurrencies,
                    currency = currency,
                    onCurrencyChange = { currency = it },
                    principalText = principalText,
                    onPrincipalTextChange = {
                        principalText = it
                        validationError = null
                    },
                    plainAmountLabel = plainAmountLabel,
                    step1Done = step1Done,
                    step2Done = step2Done,
                    step3Done = step3Done,
                    textFieldColors = textFieldColors,
                    contactPickerLauncher = contactPickerLauncher,
                    onOpenInAppContactPicker = onOpenInAppContactPicker
                )

                HorizontalDivider(color = BorderGrey, thickness = 1.dp)

                AddEditTermsAndInterestSection(
                    context = context,
                    reducedMotion = reducedMotion,
                    plainAmountLabel = plainAmountLabel,
                    paymentMode = paymentMode,
                    onPaymentModeChange = { paymentMode = it },
                    isInterestEnabled = isInterestEnabled,
                    onInterestEnabledChange = { isInterestEnabled = it },
                    interestType = interestType,
                    onInterestTypeChange = { interestType = it },
                    interestRateText = interestRateText,
                    onInterestRateTextChange = { interestRateText = it },
                    interestRatePeriod = interestRatePeriod,
                    onInterestRatePeriodChange = { interestRatePeriod = it },
                    compoundFrequency = compoundFrequency,
                    onCompoundFrequencyChange = { compoundFrequency = it },
                    startDateMillis = startDateMillis,
                    onStartDateMillisChange = { startDateMillis = it },
                    expiryDateMillis = expiryDateMillis,
                    onExpiryDateMillisChange = { expiryDateMillis = it },
                    reminderDateMillis = reminderDateMillis,
                    onReminderDateMillisChange = { reminderDateMillis = it },
                    notes = notes,
                    onNotesChange = { notes = it },
                    showMoreDetails = showMoreDetails,
                    onToggleMoreDetails = { showMoreDetails = !showMoreDetails },
                    showStartDatePicker = showStartDatePicker,
                    onShowStartDatePickerChange = { showStartDatePicker = it },
                    showExpiryDatePicker = showExpiryDatePicker,
                    onShowExpiryDatePickerChange = { showExpiryDatePicker = it },
                    showReminderDatePicker = showReminderDatePicker,
                    onShowReminderDatePickerChange = { showReminderDatePicker = it },
                    previewSummary = previewSummary,
                    allCurrentAttachments = draftAttachments + existingAttachments,
                    onCameraClick = onCameraClick,
                    galleryLauncher = galleryLauncher,
                    documentLauncher = documentLauncher,
                    notificationPermissionLauncher = notificationPermissionLauncher,
                    onOpenAttachmentViewer = onOpenAttachmentViewer,
                    onRemoveDraftAttachment = onRemoveDraftAttachment,
                    onDeleteExistingAttachment = onDeleteExistingAttachment,
                    textFieldColors = textFieldColors
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
