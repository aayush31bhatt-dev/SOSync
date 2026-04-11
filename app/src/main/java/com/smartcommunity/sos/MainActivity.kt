package com.smartcommunity.sos

import android.os.Bundle
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.WindowCompat
import com.smartcommunity.sos.data.auth.AuthApiClient
import com.smartcommunity.sos.data.auth.AuthSessionStore
import com.smartcommunity.sos.data.auth.LoginInput
import com.smartcommunity.sos.data.auth.RegisterInput
import com.smartcommunity.sos.ui.auth.LoginForm
import com.smartcommunity.sos.ui.auth.RegisterForm
import com.smartcommunity.sos.ui.auth.AuthScreen
import com.smartcommunity.sos.ui.navigation.SafetyAppShell
import com.smartcommunity.sos.ui.theme.SmartCommunitySOSTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        setContent {
            SmartCommunitySOSApp()
        }
    }
}

@Composable
fun SmartCommunitySOSApp() {
    SmartCommunitySOSTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val authApiClient = remember { AuthApiClient() }
            val authSessionStore = remember { AuthSessionStore(context) }

            var currentUsername by remember { mutableStateOf(authSessionStore.getUsername()) }

            if (currentUsername.isNullOrBlank()) {
                AuthScreen(
                    onCreateAccount = { form: RegisterForm ->
                        if (!isValidUsername(form.username.trim().lowercase())) {
                            "Username must be 3-20 characters: letters, numbers, underscore, or dot."
                        } else {
                            val response = authApiClient.register(
                                RegisterInput(
                                    fullName = form.fullName,
                                    username = form.username,
                                    email = form.email,
                                    password = form.password,
                                    gender = form.gender,
                                    phoneNumber = form.phoneNumber,
                                    emergencyContactName = form.emergencyContactName,
                                    emergencyContactPhone = form.emergencyContactPhone,
                                    trustedContactsEnabled = form.trustedContactsEnabled
                                )
                            )

                            if (response.data != null) {
                                authSessionStore.saveSession(response.data)
                                currentUsername = response.data.username
                                null
                            } else {
                                response.error ?: "Failed to create account."
                            }
                        }
                    },
                    onLogin = { form: LoginForm ->
                        if (form.identifier.isBlank() || form.password.isBlank()) {
                            "Username or email and password are required."
                        } else {
                            val response = authApiClient.login(
                                LoginInput(
                                    identifier = form.identifier,
                                    password = form.password,
                                    rememberMe = form.rememberMe
                                )
                            )

                            if (response.data != null) {
                                authSessionStore.saveSession(response.data)
                                currentUsername = response.data.username
                                null
                            } else {
                                response.error ?: "Login failed."
                            }
                        }
                    }
                )
            } else {
                SafetyAppShell(
                    currentUsername = currentUsername.orEmpty(),
                    onSignedOut = {
                        authSessionStore.clearSession()
                        currentUsername = null
                    }
                )
            }
        }
    }
}

private fun isValidUsername(username: String): Boolean {
    val usernameRegex = Regex("^[a-z0-9._]{3,20}$")
    return usernameRegex.matches(username)
}

@Preview(showBackground = true)
@Composable
private fun SmartCommunitySOSAppPreview() {
    SmartCommunitySOSApp()
}


