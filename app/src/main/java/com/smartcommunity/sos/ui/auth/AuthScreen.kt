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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

data class RegisterForm(
    val fullName: String,
    val username: String,
    val email: String,
    val password: String,
    val gender: String,
    val phoneNumber: String,
    val emergencyContactName: String,
    val emergencyContactPhone: String,
    val trustedContactsEnabled: Boolean
)

data class LoginForm(
    val identifier: String,
    val password: String,
    val rememberMe: Boolean
)

@Composable
fun AuthScreen(
    onCreateAccount: suspend (RegisterForm) -> String?,
    onLogin: suspend (LoginForm) -> String?
) {
    val scope = rememberCoroutineScope()
    var isRegisterMode by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }

    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Prefer not to say") }
    var phoneNumber by remember { mutableStateOf("") }
    var emergencyContactName by remember { mutableStateOf("") }
    var emergencyContactPhone by remember { mutableStateOf("") }
    var trustedContactsEnabled by remember { mutableStateOf(true) }

    var loginIdentifier by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(true) }

    var statusText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF232323))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Welcome",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isRegisterMode = true
                            isLoading = false
                            statusText = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sign Up")
                    }
                    OutlinedButton(
                        onClick = {
                            isRegisterMode = false
                            isLoading = false
                            statusText = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Login")
                    }
                }

                Text(
                    text = if (isRegisterMode) {
                        "Create your account with emergency details."
                    } else {
                        "Login using your username or email and password."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isRegisterMode) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = {
                            fullName = it
                            statusText = null
                        },
                        label = { Text("Full Name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            statusText = null
                        },
                        label = { Text("Username") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            statusText = null
                        },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            statusText = null
                        },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = gender,
                        onValueChange = {
                            gender = it
                            statusText = null
                        },
                        label = { Text("Gender (Male/Female/Other/Prefer not to say)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = {
                            phoneNumber = it
                            statusText = null
                        },
                        label = { Text("Phone Number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = emergencyContactName,
                        onValueChange = {
                            emergencyContactName = it
                            statusText = null
                        },
                        label = { Text("Emergency Contact Name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = emergencyContactPhone,
                        onValueChange = {
                            emergencyContactPhone = it
                            statusText = null
                        },
                        label = { Text("Emergency Contact Phone") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = trustedContactsEnabled,
                            onCheckedChange = {
                                trustedContactsEnabled = it
                                statusText = null
                            }
                        )
                        Text("Enable trusted contacts", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Button(
                        onClick = {
                            isLoading = true
                            scope.launch {
                                try {
                                    val error = onCreateAccount(
                                        RegisterForm(
                                            fullName = fullName,
                                            username = username,
                                            email = email,
                                            password = password,
                                            gender = gender,
                                            phoneNumber = phoneNumber,
                                            emergencyContactName = emergencyContactName,
                                            emergencyContactPhone = emergencyContactPhone,
                                            trustedContactsEnabled = trustedContactsEnabled
                                        )
                                    )
                                    statusText = error ?: "Account created. You are now logged in."
                                } catch (_: Exception) {
                                    statusText = "Could not create account. Please try again."
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        Text(if (isLoading) "Creating..." else "Create account")
                    }
                } else {
                    OutlinedTextField(
                        value = loginIdentifier,
                        onValueChange = {
                            loginIdentifier = it
                            statusText = null
                        },
                        label = { Text("Username or email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = loginPassword,
                        onValueChange = {
                            loginPassword = it
                            statusText = null
                        },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = {
                                rememberMe = it
                                statusText = null
                            }
                        )
                        Text("Remember me", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Button(
                        onClick = {
                            isLoading = true
                            scope.launch {
                                try {
                                    val error = onLogin(
                                        LoginForm(
                                            identifier = loginIdentifier,
                                            password = loginPassword,
                                            rememberMe = rememberMe
                                        )
                                    )
                                    statusText = error ?: "Login successful."
                                } catch (_: Exception) {
                                    statusText = "Could not login. Please try again."
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        Text(if (isLoading) "Signing in..." else "Login")
                    }
                }

                statusText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
