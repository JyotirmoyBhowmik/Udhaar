package com.jyotirmoyb.udhaar.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.CurrencyType
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.ui.QrModalState
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.UpiQrGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpiQrCodeModal(
    qrState: QrModalState,
    userSettings: UserSettings,
    onSaveUpiSettings: (upiId: String, payeeName: String, defaultCurrency: CurrencyType) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isPayToParty = qrState.isPayToPartyMode && !qrState.customUpiId.isNullOrBlank()

    var upiIdInput by rememberSaveable(qrState) {
        mutableStateOf(
            if (isPayToParty) qrState.customUpiId.orEmpty() else userSettings.upiId
        )
    }
    var payeeNameInput by rememberSaveable(qrState) {
        mutableStateOf(
            if (isPayToParty) qrState.partyName.orEmpty() else userSettings.payeeName
        )
    }
    var amountText by rememberSaveable(qrState) {
        mutableStateOf(
            qrState.initialAmountInr?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else String.format(java.util.Locale.US, "%.2f", it)
            } ?: ""
        )
    }

    val parsedAmount = amountText.trim().toDoubleOrNull()
    val effectiveUpiId = upiIdInput.trim()
    val effectivePayeeName = payeeNameInput.trim().ifBlank { "Udhaar Payee" }

    val upiUri = remember(effectiveUpiId, effectivePayeeName, parsedAmount, qrState.partyName, isPayToParty) {
        if (effectiveUpiId.isBlank()) {
            ""
        } else {
            val note = if (isPayToParty) {
                "Udhaar repayment to ${qrState.partyName ?: effectivePayeeName}"
            } else {
                qrState.partyName?.let { "Udhaar settlement - $it" } ?: "Udhaar Payment"
            }
            UpiQrGenerator.buildUpiUri(
                upiId = effectiveUpiId,
                payeeName = effectivePayeeName,
                amountInr = parsedAmount,
                transactionNote = note
            )
        }
    }

    val qrBitmap = remember(upiUri) {
        if (upiUri.isBlank()) null else UpiQrGenerator.generateQrBitmap(upiUri, sizePx = 560)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = { WindowInsets.navigationBars.union(WindowInsets.ime) }
    ) {
        AnimatedSheetContent(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .align(Alignment.CenterHorizontally)
                .imePadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
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
                            text = if (isPayToParty) stringResource(R.string.ui_str_pay_party_via_upi_81f63b) else stringResource(R.string.ui_str_receive_via_upi_qr_640ed3),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val settleTargetName = qrState.partyName?.uppercase() ?: stringResource(R.string.ui_party_caps)
                        Text(
                            text = if (isPayToParty) {
                                stringResource(R.string.ui_fmt_settle_directly_3d3daa, settleTargetName)
                            } else {
                                qrState.partyName?.let { stringResource(R.string.ui_fmt_for_inr_669ce5, it.uppercase()) } ?: stringResource(R.string.ui_str_global_merchant_personal_qr_7d572f)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_qr_modal_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    var showQrUpiId by rememberSaveable { mutableStateOf(false) }
                    OutlinedTextField(
                        value = upiIdInput,
                        onValueChange = {
                            showQrUpiId = true
                            upiIdInput = it
                        },
                        label = { Text(if (isPayToParty) stringResource(R.string.ui_str_party_upi_id_vpa_9c2dfd) else stringResource(R.string.ui_str_your_upi_id_vpa_47fda1)) },
                        placeholder = { Text(stringResource(R.string.ui_str_e_g_yourname_okaxis_223c45)) },
                        singleLine = true,
                        visualTransformation = if (showQrUpiId || upiIdInput.isBlank()) {
                            VisualTransformation.None
                        } else {
                            UpiMaskVisualTransformation
                        },
                        trailingIcon = {
                            if (upiIdInput.isNotBlank()) {
                                IconButton(onClick = { showQrUpiId = !showQrUpiId }) {
                                    Icon(
                                        imageVector = if (showQrUpiId) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showQrUpiId) "Hide UPI ID" else "Show UPI ID"
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("qr_upi_id_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = payeeNameInput,
                            onValueChange = { payeeNameInput = it },
                            label = { Text(stringResource(R.string.ui_str_payee_name_cf3058)) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("qr_payee_name_input")
                        )
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = {
                                if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) {
                                    amountText = it
                                }
                            },
                            label = { Text(stringResource(R.string.ui_str_amount_4ecd3f)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("qr_amount_input")
                        )
                    }

                    if (!isPayToParty && (upiIdInput.trim() != userSettings.upiId || payeeNameInput.trim() != userSettings.payeeName)) {
                        OutlinedButton(
                            onClick = {
                                onSaveUpiSettings(
                                    upiIdInput.trim(),
                                    payeeNameInput.trim(),
                                    userSettings.defaultCurrency
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("save_upi_from_qr_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Text(stringResource(R.string.ui_str_save_as_default_upi_profile_a5e997), style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (qrBitmap != null) {
                        Box(
                            modifier = Modifier
                                .size(210.dp)
                                .border(2.dp, PureBlack, RoundedCornerShape(8.dp))
                                .background(PureWhite, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Generated UPI QR Code",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("upi_qr_bitmap_image")
                            )
                        }
                        Text(
                            text = stringResource(R.string.ui_fmt_vpa_251b59, InterestCalculator.maskUpiId(effectiveUpiId)),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.share_clip_upi_link), upiUri))
                                    Toast.makeText(context, context.getString(R.string.toast_upi_link_copied), Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .pressScaleFeedback()
                                    .testTag("copy_upi_uri_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.ui_str_copy_link_1ccac4), style = MaterialTheme.typography.labelMedium)
                            }

                            Button(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
                                        context.startActivity(intent)
                                    }.onFailure {
                                        Toast.makeText(context, context.getString(R.string.toast_no_upi_app), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(48.dp)
                                    .pressScaleFeedback()
                                    .testTag("launch_upi_app_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.ui_str_open_upi_app_2ab19b), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.ui_str_enter_a_valid_upi_id_cdd444),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
