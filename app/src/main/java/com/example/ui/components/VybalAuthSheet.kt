package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.state.AuthMode
import com.example.ui.state.AuthUiState
import com.example.ui.theme.VybalOutline
import com.example.ui.theme.VybalSpacing
import com.example.ui.theme.VybalSurfaceLow
import com.example.ui.theme.VybalTextSecondary
import com.example.ui.theme.VybalViolet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VybalAuthSheet(
    state: AuthUiState,
    onDismiss: () -> Unit,
    onModeChange: (AuthMode) -> Unit,
    onSubmitSignIn: (email: String, password: String) -> Unit,
    onSubmitSignUp: (
        email: String,
        password: String,
        confirmPassword: String,
        username: String,
        displayName: String
    ) -> Unit,
    onSubmitPasswordReset: (email: String) -> Unit
) {
    if (!state.isSheetOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = VybalSurfaceLow,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = VybalSpacing.lg)
                .padding(bottom = VybalSpacing.lg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (state.mode) {
                        AuthMode.SignIn -> stringResource(R.string.auth_required_title)
                        AuthMode.SignUp -> stringResource(R.string.action_create_account)
                        AuthMode.ResetPassword -> stringResource(R.string.auth_reset_title)
                    },
                    style = MaterialTheme.typography.headlineSmall
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("auth_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.cd_close_sheet),
                        tint = VybalTextSecondary
                    )
                }
            }

            Spacer(Modifier.height(VybalSpacing.sm))
            Text(
                text = when (state.mode) {
                    AuthMode.ResetPassword -> stringResource(R.string.auth_reset_body)
                    else -> stringResource(R.string.auth_required_body)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = VybalTextSecondary
            )

            Spacer(Modifier.height(VybalSpacing.lg))

            if (state.mode == AuthMode.SignUp) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.auth_username_label)) },
                    singleLine = true,
                    isError = state.usernameError != null,
                    supportingText = state.usernameError?.let { err -> { Text(err) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VybalViolet,
                        unfocusedBorderColor = VybalOutline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_username_input")
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(stringResource(R.string.auth_display_name_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VybalViolet,
                        unfocusedBorderColor = VybalOutline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_display_name_input")
                )

                Spacer(Modifier.height(10.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.auth_email_label)) },
                singleLine = true,
                isError = state.emailError != null,
                supportingText = state.emailError?.let { err -> { Text(err) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = if (state.mode == AuthMode.ResetPassword) ImeAction.Done else ImeAction.Next
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VybalViolet,
                    unfocusedBorderColor = VybalOutline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_email_input")
            )

            if (state.mode != AuthMode.ResetPassword) {
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.auth_password_label)) },
                    singleLine = true,
                    isError = state.passwordError != null,
                    supportingText = state.passwordError?.let { err -> { Text(err) } },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = null,
                                tint = VybalTextSecondary
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (state.mode == AuthMode.SignUp) ImeAction.Next else ImeAction.Done
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VybalViolet,
                        unfocusedBorderColor = VybalOutline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_password_input")
                )
            }

            if (state.mode == AuthMode.SignUp) {
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(stringResource(R.string.auth_confirm_password_label)) },
                    singleLine = true,
                    isError = state.confirmPasswordError != null,
                    supportingText = state.confirmPasswordError?.let { err -> { Text(err) } },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VybalViolet,
                        unfocusedBorderColor = VybalOutline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_confirm_password_input")
                )
            }

            if (state.mode == AuthMode.SignIn) {
                TextButton(
                    onClick = { onModeChange(AuthMode.ResetPassword) },
                    modifier = Modifier
                        .align(Alignment.End)
                        .testTag("auth_forgot_password_button")
                ) {
                    Text(
                        text = stringResource(R.string.auth_forgot_password),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            if (!state.errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("auth_error_banner")
                )
            }

            if (!state.infoMessage.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.infoMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.testTag("auth_info_banner")
                )
            }

            Spacer(Modifier.height(VybalSpacing.md))

            val isSuperAdminEmail = email.trim().equals(
                com.example.core.config.VybalSchemaContract.PERMANENT_SUPER_ADMIN_EMAIL,
                ignoreCase = true
            )
            if (isSuperAdminEmail && state.mode == AuthMode.SignUp) {
                Text(
                    text = "Permanent Super Admin account — signs in directly without sign-up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.height(8.dp))
            }

            VybalPrimaryButton(
                text = when {
                    isSuperAdminEmail && state.mode != AuthMode.ResetPassword -> stringResource(R.string.action_sign_in)
                    state.mode == AuthMode.SignIn -> stringResource(R.string.action_sign_in)
                    state.mode == AuthMode.SignUp -> stringResource(R.string.action_create_account)
                    else -> stringResource(R.string.auth_send_reset_link)
                },
                onClick = {
                    when {
                        isSuperAdminEmail && state.mode != AuthMode.ResetPassword -> onSubmitSignIn(email, password)
                        state.mode == AuthMode.SignIn -> onSubmitSignIn(email, password)
                        state.mode == AuthMode.SignUp -> onSubmitSignUp(
                            email,
                            password,
                            confirmPassword,
                            username,
                            displayName
                        )
                        else -> onSubmitPasswordReset(email)
                    }
                },
                loading = state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
                testTag = "auth_submit_button"
            )

            Spacer(Modifier.height(VybalSpacing.sm))

            TextButton(
                onClick = {
                    val nextMode = when (state.mode) {
                        AuthMode.SignIn -> AuthMode.SignUp
                        AuthMode.SignUp -> AuthMode.SignIn
                        AuthMode.ResetPassword -> AuthMode.SignIn
                    }
                    onModeChange(nextMode)
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .testTag("auth_toggle_mode_button")
            ) {
                Text(
                    text = when (state.mode) {
                        AuthMode.SignIn -> stringResource(R.string.auth_switch_to_signup)
                        AuthMode.SignUp -> stringResource(R.string.auth_switch_to_signin)
                        AuthMode.ResetPassword -> stringResource(R.string.auth_back_to_signin)
                    },
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}
