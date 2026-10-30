package com.jyotirmoyb.udhaar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jyotirmoyb.udhaar.R
import com.jyotirmoyb.udhaar.ui.theme.BorderGrey
import com.jyotirmoyb.udhaar.ui.theme.MutedChipGrey
import com.jyotirmoyb.udhaar.ui.theme.PureBlack
import com.jyotirmoyb.udhaar.ui.theme.PureWhite
import com.jyotirmoyb.udhaar.ui.theme.SecondaryInkGrey
import com.jyotirmoyb.udhaar.ui.theme.SubtleSurfaceGrey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingModal(
    onFinish: (pin: String, upiId: String, name: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var step by remember { mutableIntStateOf(1) }

    var payeeName by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = { /* Force completion of onboarding once */ },
        sheetState = sheetState,
        containerColor = PureWhite,
        contentColor = PureBlack,
        dragHandle = null,
        contentWindowInsets = { WindowInsets.navigationBars.union(WindowInsets.ime) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .fillMaxHeight(0.95f)
                .align(Alignment.CenterHorizontally)
                .navigationBarsPadding()
                .imePadding()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
                .testTag("onboarding_modal")
        ) {
            // Step Progress Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STEP $step OF 4",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryInkGrey,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..4).forEach { i ->
                        Box(
                            modifier = Modifier
                                .size(if (i == step) 20.dp else 8.dp, 8.dp)
                                .background(
                                    if (i <= step) PureBlack else BorderGrey,
                                    CircleShape
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (step) {
                1 -> OnboardingPageContent(
                    icon = Icons.Default.Payments,
                    title = stringResource(R.string.onboarding_title_1),
                    description = stringResource(R.string.onboarding_desc_1),
                    features = listOf(
                        "Dual Currency: Record in Indian Rupee (₹) & Nepalese Rupee (रू)",
                        "Precise Interest: Simple or Compound with monthly/annual periods",
                        "Smart Partial Payments: Real-time net due balance calculation"
                    )
                )
                2 -> OnboardingPageContent(
                    icon = Icons.Default.VerifiedUser,
                    title = stringResource(R.string.onboarding_title_2),
                    description = stringResource(R.string.onboarding_desc_2),
                    features = listOf(
                        "Two-Party Verification: Share links/QR codes for counterparty review",
                        "Tamper-Proof Audit: Every change has a cryptographic SHA-256 hash",
                        "Direct In-App Chat: Propose due-date extensions with one tap"
                    )
                )
                3 -> OnboardingPageContent(
                    icon = Icons.Default.CloudDone,
                    title = stringResource(R.string.onboarding_title_3),
                    description = stringResource(R.string.onboarding_desc_3),
                    features = listOf(
                        "Google Drive Sync: Hidden app data folder with zero third-party access",
                        "Client-Side AES-256-GCM: Decrypted only with your master passphrase",
                        "Offline Attachments: Attach UPI screenshots, camera photos & receipts"
                    )
                )
                4 -> {
                    Text(
                        text = stringResource(R.string.onboarding_setup_pin_title).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = PureBlack,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.onboarding_setup_pin_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryInkGrey
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = payeeName,
                        onValueChange = { payeeName = it },
                        label = { Text("Your Name or Business Name") },
                        placeholder = { Text("e.g. Ramesh Kumar") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PureBlack,
                            unfocusedBorderColor = BorderGrey
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_name_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = upiId,
                        onValueChange = { upiId = it },
                        label = { Text("UPI ID (Optional for fast QR settlement)") },
                        placeholder = { Text("e.g. yourname@okhdfcbank") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PureBlack,
                            unfocusedBorderColor = BorderGrey
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_upi_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) pin = it },
                        label = { Text("4-Digit Security PIN (Optional)") },
                        placeholder = { Text("••••") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PureBlack,
                            unfocusedBorderColor = BorderGrey
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_pin_input")
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            // Navigation Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step > 1) {
                    OutlinedButton(
                        onClick = { step-- },
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("onboarding_back_button")
                            .semantics { contentDescription = "Go back to previous onboarding slide" }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = PureBlack)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("BACK", color = PureBlack)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (step < 4) {
                    Button(
                        onClick = { step++ },
                        colors = ButtonDefaults.buttonColors(containerColor = PureBlack, contentColor = PureWhite),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("onboarding_next_button")
                            .semantics { contentDescription = "Next onboarding slide" }
                    ) {
                        Text("NEXT", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                } else {
                    Button(
                        onClick = { onFinish(pin, upiId, payeeName) },
                        colors = ButtonDefaults.buttonColors(containerColor = PureBlack, contentColor = PureWhite),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("onboarding_finish_button")
                            .semantics { contentDescription = "Complete setup and enter Udhaar" }
                    ) {
                        Text(stringResource(R.string.onboarding_get_started).uppercase(), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    icon: ImageVector,
    title: String,
    description: String,
    features: List<String>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(PureBlack, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PureWhite,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            color = PureBlack,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryInkGrey,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            features.forEach { feat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SubtleSurfaceGrey, RoundedCornerShape(4.dp))
                        .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = PureBlack,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = feat,
                        style = MaterialTheme.typography.bodySmall,
                        color = PureBlack,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
