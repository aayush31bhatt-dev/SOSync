package com.smartcommunity.sos.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.smartcommunity.sos.data.auth.AuthApiClient
import com.smartcommunity.sos.data.auth.AuthSessionStore
import com.smartcommunity.sos.data.auth.UpdateProfileInput
import kotlinx.coroutines.launch

@Composable
fun AccountSettingsScreen(
    currentUsername: String,
    onSignedOut: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val authApiClient = remember { AuthApiClient() }
    val authSessionStore = remember { AuthSessionStore(context) }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Prefer not to say") }
    var phone by remember { mutableStateOf("") }
    var emergencyName by remember { mutableStateOf("") }
    var emergencyPhone by remember { mutableStateOf("") }
    var trustedContactsEnabled by remember { mutableStateOf(true) }

    var forgotEmail by remember { mutableStateOf("") }
    var resetToken by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var deletePassword by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val token = authSessionStore.getToken() ?: return@LaunchedEffect
        val result = authApiClient.getMyProfile(token)
        result.data?.let { profile ->
            name = profile.name
            email = profile.email
            gender = profile.gender
            phone = profile.phoneNumber
            emergencyName = profile.emergencyContactName
            emergencyPhone = profile.emergencyContactPhone
            trustedContactsEnabled = profile.trustedContactsEnabled
            forgotEmail = profile.email
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Account",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Signed in as $currentUsername",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = gender,
                    onValueChange = { gender = it },
                    label = { Text("Gender") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                OutlinedTextField(
                    value = emergencyName,
                    onValueChange = { emergencyName = it },
                    label = { Text("Emergency Contact Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = emergencyPhone,
                    onValueChange = { emergencyPhone = it },
                    label = { Text("Emergency Contact Phone") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = trustedContactsEnabled,
                        onCheckedChange = { trustedContactsEnabled = it }
                    )
                    Text("Trusted contacts enabled")
                }

                Button(
                    onClick = {
                        val token = authSessionStore.getToken()
                        if (token.isNullOrBlank()) {
                            statusText = "Session expired. Please login again."
                            authSessionStore.clearSession()
                            onSignedOut()
                        } else {
                            loading = true
                            scope.launch {
                                val result = authApiClient.updateProfile(
                                    token,
                                    UpdateProfileInput(
                                        name = name,
                                        gender = gender,
                                        phoneNumber = phone,
                                        emergencyContactName = emergencyName,
                                        emergencyContactPhone = emergencyPhone,
                                        trustedContactsEnabled = trustedContactsEnabled
                                    )
                                )
                                statusText = result.error ?: "Profile updated successfully."
                                loading = false
                            }
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (loading) "Updating..." else "Update Profile")
                }
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Reset Password", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = resetToken,
                    onValueChange = { resetToken = it },
                    label = { Text("Reset Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                OutlinedTextField(
                    value = confirmNewPassword,
                    onValueChange = { confirmNewPassword = it },
                    label = { Text("Confirm New Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Button(
                    onClick = {
                        when {
                            resetToken.isBlank() -> {
                                statusText = "Reset token is required."
                            }

                            newPassword.length < 8 -> {
                                statusText = "New password must be at least 8 characters."
                            }

                            newPassword != confirmNewPassword -> {
                                statusText = "New password and confirm password do not match."
                            }

                            else -> {
                                loading = true
                                scope.launch {
                                    val result = authApiClient.resetPassword(
                                        token = resetToken,
                                        newPassword = newPassword
                                    )
                                    statusText = result.error ?: "Password reset successful."
                                    loading = false
                                }
                            }
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset Password")
                }
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Forgot Password", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = forgotEmail,
                    onValueChange = { forgotEmail = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                Button(
                    onClick = {
                        loading = true
                        scope.launch {
                            val result = authApiClient.forgotPassword(forgotEmail)
                            statusText = result.error ?: "Reset link sent if email exists."
                            loading = false
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send Reset Link")
                }
            }
        }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Session", style = MaterialTheme.typography.titleLarge)
                Button(
                    onClick = {
                        loading = true
                        scope.launch {
                            val token = authSessionStore.getToken()
                            if (!token.isNullOrBlank()) {
                                authApiClient.logout(token)
                            }
                            authSessionStore.clearSession()
                            loading = false
                            onSignedOut()
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Logout")
                }

                OutlinedTextField(
                    value = deletePassword,
                    onValueChange = { deletePassword = it },
                    label = { Text("Confirm Password to Delete Account") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                Button(
                    onClick = {
                        val token = authSessionStore.getToken()
                        if (token.isNullOrBlank()) {
                            statusText = "Session expired. Please login again."
                            authSessionStore.clearSession()
                            onSignedOut()
                        } else {
                            loading = true
                            scope.launch {
                                val result = authApiClient.deleteAccount(token, deletePassword)
                                if (result.error == null) {
                                    authSessionStore.clearSession()
                                    statusText = "Account deleted."
                                    loading = false
                                    onSignedOut()
                                } else {
                                    statusText = result.error
                                    loading = false
                                }
                            }
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete Account")
                }
            }
        }

        statusText?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
