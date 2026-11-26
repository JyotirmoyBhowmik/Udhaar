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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.data.PartyEntity
import com.jyotirmoyb.udhaar.data.UserSettings
import com.jyotirmoyb.udhaar.ui.icons.UdhaarIcons
import com.jyotirmoyb.udhaar.ui.theme.IbmPlexMonoFamily
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.util.InterestCalculator
import com.jyotirmoyb.udhaar.util.UpiQrGenerator
import com.jyotirmoyb.udhaar.util.VerificationCryptoHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareMyPaymentDetailsSheet(
    settings: UserSettings,
    targetParty: PartyEntity? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val myName = settings.payeeName.ifBlank { stringResource(R.string.udhaar_default_user_name) }

    val profileLink = remember(settings, myName) {
        VerificationCryptoHelper.buildPaymentProfileLink(
            VerificationCryptoHelper.SharedPaymentProfilePayload(
                userId = settings.cloudUserId,
                displayName = myName,
                phoneNumber = settings.phoneNumber,
                upiId = settings.upiId,
                photoUrl = settings.profilePhotoUri
            )
        )
    }
    val qrBitmap = remember(profileLink) {
        UpiQrGenerator.generateQrBitmap(profileLink, sizePx = 560)
    }

    val shareText = remember(settings, profileLink, targetParty, myName) {
        buildString {
            append("*Udhaar Payment Details*\n")
            if (targetParty != null) {
                append("Namaste ${targetParty.name},\n\n")
            } else {
                append("Namaste,\n\n")
            }
            append("• Name: *$myName*\n")
            if (settings.phoneNumber.isNotBlank()) {
                append("• Phone: ${InterestCalculator.maskPhoneNumber(settings.phoneNumber)}\n")
            }
            if (settings.upiId.isNotBlank()) {
                append("• UPI ID: `${InterestCalculator.maskUpiId(settings.upiId)}`\n")
            }
            append("\n👇 *Tap link to save UPI ID in Udhaar:*\n")
            append(profileLink)
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ui_str_share_my_payment_details_c9cc51),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (targetParty != null) {
                            stringResource(R.string.ui_fmt_sharing_with_565a7a, targetParty.name)
                        } else {
                            stringResource(R.string.ui_str_qr_app_link_for_instant_a4b214)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 14.dp, bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = myName.take(2).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = myName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (settings.phoneNumber.isNotBlank()) {
                                    stringResource(R.string.ui_fmt_phone_812cdd, InterestCalculator.maskPhoneNumber(settings.phoneNumber))
                                } else {
                                    stringResource(R.string.ui_str_phone_not_set_in_settings_d8a427)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (settings.upiId.isNotBlank()) {
                                    stringResource(R.string.ui_fmt_upi_2971f0, InterestCalculator.maskUpiId(settings.upiId))
                                } else {
                                    stringResource(R.string.ui_str_upi_id_not_set_in_c3a0bf)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = IbmPlexMonoFamily,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(205.dp)
                            .border(2.dp, PureBlack, RoundedCornerShape(12.dp))
                            .background(PureWhite, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.cd_payment_profile_qr),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val phoneDigits = targetParty?.phoneNumber?.filter { it.isDigit() }.orEmpty()
                        val encoded = Uri.encode(shareText)
                        val waUrl = if (phoneDigits.length >= 10) {
                            val formatted = if (phoneDigits.length == 10) "91$phoneDigits" else phoneDigits
                            "https://wa.me/$formatted?text=$encoded"
                        } else {
                            "https://wa.me/?text=$encoded"
                        }
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)))
                        }.onFailure {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(
                                Intent.createChooser(sendIntent, context.getString(R.string.chooser_share_payment_profile))
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("share_payment_profile_whatsapp_btn"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_str_share_via_whatsapp_b0968d), style = MaterialTheme.typography.labelLarge)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(context.getString(R.string.share_clip_payment_profile), profileLink)
                            )
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_payment_profile_copied),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(UdhaarIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.ui_str_copy_link_1ccac4), style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(
                                Intent.createChooser(sendIntent, context.getString(R.string.chooser_share_payment_details))
                            )
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.ui_str_other_app_f796ab), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestPartyPaymentDetailsSheet(
    party: PartyEntity,
    settings: UserSettings,
    onDismiss: () -> Unit,
    onOpenReceivedProfileLink: (String) -> Unit
) {
    val context = LocalContext.current
    var pastedProfileLink by remember { mutableStateOf("") }
    val myName = settings.payeeName.ifBlank { stringResource(R.string.udhaar_default_user_name) }

    val requestLink = "https://udhaar.jyotirmoyb.com/u/request-upi?from=${Uri.encode(myName)}&party=${Uri.encode(party.name)}"
    val qrBitmap = remember(requestLink) {
        UpiQrGenerator.generateQrBitmap(requestLink, sizePx = 500)
    }

    val requestMessage = remember(party, myName) {
        buildString {
            append("*Udhaar Payment Details Request*\n")
            append("Namaste ${party.name},\n\n")
            append("*$myName* requested your UPI ID on Udhaar for quick settlement.\n\n")
            append("👇 *Tap to share your UPI ID or reply here:*\n")
            append(requestLink)
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ui_str_request_payment_details_d31a36),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.ui_fmt_ask_to_share_their_upi_ee3730, party.name),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cd_close),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 14.dp, bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (qrBitmap != null) {
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .border(2.dp, PureBlack, RoundedCornerShape(12.dp))
                            .background(PureWhite, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.cd_request_payment_qr),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val phoneDigits = party.phoneNumber.filter { it.isDigit() }
                        val encoded = Uri.encode(requestMessage)
                        val waUrl = if (phoneDigits.length >= 10) {
                            val formatted = if (phoneDigits.length == 10) "91$phoneDigits" else phoneDigits
                            "https://wa.me/$formatted?text=$encoded"
                        } else {
                            "https://wa.me/?text=$encoded"
                        }
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)))
                        }.onFailure {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, requestMessage)
                            }
                            context.startActivity(
                                Intent.createChooser(sendIntent, context.getString(R.string.chooser_request_payment_details))
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("send_request_payment_whatsapp_btn"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.ui_str_request_via_whatsapp_11cb38), style = MaterialTheme.typography.labelLarge)
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.ui_fmt_already_received_s_payment_link_7c4c6f, party.name),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pastedProfileLink,
                    onValueChange = { pastedProfileLink = it },
                    label = { Text(stringResource(R.string.ui_str_paste_https_jyotirmoyb_com_u_906892)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("paste_party_profile_link_input")
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = {
                        if (pastedProfileLink.isNotBlank()) {
                            onOpenReceivedProfileLink(pastedProfileLink.trim())
                        }
                    },
                    enabled = pastedProfileLink.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("preview_shared_profile_btn"),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.ui_str_preview_accept_payment_details_be315a))
                }
            }
        }
    }
}
