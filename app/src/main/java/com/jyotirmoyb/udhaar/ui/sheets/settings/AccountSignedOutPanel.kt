package com.jyotirmoyb.udhaar.ui.sheets.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jyotirmoyb.udhaar.R

internal enum class SparkAuthUiMode {
    SIGNUP,
    SIGNIN,
    PASSWORD_RESET
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountSignedOutPanel(
    authMode: SparkAuthUiMode,
    emailInput: String,
    passwordInput: String,
    contactPhoneInput: String,
    isLoading: Boolean,
    onAuthModeChange: (SparkAuthUiMode) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onContactPhoneChange: (String) -> Unit,
    onGoogleSignIn: () -> Unit,
    onSendPasswordReset: () -> Unit,
    onSubmitEmailAuth: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = when (authMode) {
                    SparkAuthUiMode.SIGNUP -> stringResource(R.string.account_sign_up_heading)
                    SparkAuthUiMode.SIGNIN -> stringResource(R.string.account_sign_in_heading)
                    SparkAuthUiMode.PASSWORD_RESET -> stringResource(R.string.account_reset_password_heading)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 8.dp)
            )
            if (authMode != SparkAuthUiMode.PASSWORD_RESET) {
                TextButton(
                    onClick = {
                        onAuthModeChange(
                            if (authMode == SparkAuthUiMode.SIGNUP) SparkAuthUiMode.SIGNIN else SparkAuthUiMode.SIGNUP
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_toggle_auth_mode")
                ) {
                    Text(
                        text = if (authMode == SparkAuthUiMode.SIGNUP) {
                            stringResource(R.string.account_switch_to_signin)
                        } else {
                            stringResource(R.string.account_switch_to_signup)
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onGoogleSignIn,
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("btn_google_credential_signin"),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.account_continue_with_google))
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        when (authMode) {
            SparkAuthUiMode.PASSWORD_RESET -> {
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = onEmailChange,
                    label = { Text(stringResource(R.string.account_email_label)) },
                    placeholder = { Text(stringResource(R.string.account_email_placeholder)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reset_email_input")
                )

                Button(
                    onClick = onSendPasswordReset,
                    enabled = !isLoading && emailInput.contains("@"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_send_password_reset"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.account_send_password_reset_btn))
                }

                TextButton(
                    onClick = { onAuthModeChange(SparkAuthUiMode.SIGNIN) },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .heightIn(min = 48.dp)
                        .testTag("btn_back_to_signin")
                ) {
                    Text(stringResource(R.string.account_back_to_signin_btn))
                }
            }

            SparkAuthUiMode.SIGNUP,
            SparkAuthUiMode.SIGNIN -> {
                OutlinedTextField(
                    value = emailInput,
                    onValueChange = onEmailChange,
                    label = { Text(stringResource(R.string.account_email_label)) },
                    placeholder = { Text(stringResource(R.string.account_email_placeholder)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_email_input")
                )

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = onPasswordChange,
                    label = {
                        Text(
                            if (authMode == SparkAuthUiMode.SIGNUP) {
                                stringResource(R.string.account_password_signup_label)
                            } else {
                                stringResource(R.string.account_password_signin_label)
                            }
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("account_password_input")
                )

                if (authMode == SparkAuthUiMode.SIGNUP) {
                    OutlinedTextField(
                        value = contactPhoneInput,
                        onValueChange = onContactPhoneChange,
                        label = { Text(stringResource(R.string.account_contact_phone_optional_label)) },
                        placeholder = { Text(stringResource(R.string.account_contact_phone_placeholder)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("account_phone_input")
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { onAuthModeChange(SparkAuthUiMode.PASSWORD_RESET) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("btn_forgot_password")
                        ) {
                            Text(
                                stringResource(R.string.account_forgot_password_btn),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Button(
                    onClick = onSubmitEmailAuth,
                    enabled = !isLoading && emailInput.contains("@") && passwordInput.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("btn_submit_email_auth"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        if (authMode == SparkAuthUiMode.SIGNUP) {
                            stringResource(R.string.account_create_and_verify_btn)
                        } else {
                            stringResource(R.string.account_signin_email_btn)
                        }
                    )
                }
            }
        }
    }
}
