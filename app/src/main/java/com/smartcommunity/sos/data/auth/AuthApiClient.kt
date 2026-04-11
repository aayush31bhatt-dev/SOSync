package com.smartcommunity.sos.data.auth

import android.os.Build
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val AUTH_BASE_URL = if (
    Build.FINGERPRINT.contains("generic", ignoreCase = true) ||
    Build.MODEL.contains("Emulator", ignoreCase = true)
) {
    "http://10.0.2.2:8001"
} else {
    "http://127.0.0.1:8001"
}
private const val AUTH_TIMEOUT_MS = 10_000

data class RegisterInput(
    val fullName: String,
    val username: String,
    val email: String,
    val password: String,
    val gender: String,
    val phoneNumber: String,
    val emergencyContactName: String,
    val emergencyContactPhone: String,
    val trustedContactsEnabled: Boolean = true
)

data class LoginInput(
    val identifier: String,
    val password: String,
    val rememberMe: Boolean
)

data class UpdateProfileInput(
    val name: String,
    val gender: String,
    val phoneNumber: String,
    val emergencyContactName: String,
    val emergencyContactPhone: String,
    val trustedContactsEnabled: Boolean
)

data class AuthSession(
    val accessToken: String,
    val username: String,
    val expiresInSeconds: Int
)

data class UserProfile(
    val name: String,
    val username: String,
    val email: String,
    val gender: String,
    val phoneNumber: String,
    val emergencyContactName: String,
    val emergencyContactPhone: String,
    val trustedContactsEnabled: Boolean
)

data class AuthApiResult<T>(
    val data: T? = null,
    val error: String? = null
)

class AuthApiClient {
    suspend fun register(input: RegisterInput): AuthApiResult<AuthSession> = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("name", input.fullName.trim())
            .put("username", input.username.trim().lowercase())
            .put("email", input.email.trim().lowercase())
            .put("password", input.password)
            .put("gender", input.gender)
            .put("phone_number", input.phoneNumber.trim())
            .put("emergency_contact_name", input.emergencyContactName.trim())
            .put("emergency_contact_phone", input.emergencyContactPhone.trim())
            .put("trusted_contacts_enabled", input.trustedContactsEnabled)

        val response = postJsonWithFallback(
            baseUrl = AUTH_BASE_URL,
            candidatePaths = listOf("/register", "/api/register"),
            payload = payload
        )
        response.fold(
            onSuccess = { body ->
                AuthApiResult(data = parseAuthSession(body))
            },
            onFailure = { err ->
                val message = err.message ?: "Registration failed."
                AuthApiResult(
                    error = if (message.contains("Not Found", ignoreCase = true)) {
                        "Auth endpoint not found. Start latest backend on port 8001."
                    } else {
                        message
                    }
                )
            }
        )
    }

    suspend fun login(input: LoginInput): AuthApiResult<AuthSession> = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("identifier", input.identifier.trim().lowercase())
            .put("password", input.password)
            .put("remember_me", input.rememberMe)

        val response = postJsonWithFallback(
            baseUrl = AUTH_BASE_URL,
            candidatePaths = listOf("/login", "/api/login"),
            payload = payload
        )
        response.fold(
            onSuccess = { body ->
                AuthApiResult(data = parseAuthSession(body))
            },
            onFailure = { err ->
                AuthApiResult(error = err.message ?: "Login failed.")
            }
        )
    }

    suspend fun logout(token: String): Boolean = withContext(Dispatchers.IO) {
        val connection = (URL("$AUTH_BASE_URL/logout").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = AUTH_TIMEOUT_MS
            readTimeout = AUTH_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $token")
        }

        return@withContext try {
            connection.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }

    suspend fun forgotPassword(email: String): AuthApiResult<Unit> = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("email", email.trim().lowercase())
        val response = postJson("$AUTH_BASE_URL/forgot-password", payload)
        response.fold(
            onSuccess = { AuthApiResult(data = Unit) },
            onFailure = { err -> AuthApiResult(error = err.message ?: "Failed to request password reset.") }
        )
    }

    suspend fun resetPassword(token: String, newPassword: String): AuthApiResult<Unit> = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("token", token.trim())
            .put("new_password", newPassword)
        val response = postJson("$AUTH_BASE_URL/reset-password", payload)
        response.fold(
            onSuccess = { AuthApiResult(data = Unit) },
            onFailure = { err -> AuthApiResult(error = err.message ?: "Failed to reset password.") }
        )
    }

    suspend fun getMyProfile(token: String): AuthApiResult<UserProfile> = withContext(Dispatchers.IO) {
        val connection = (URL("$AUTH_BASE_URL/me").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = AUTH_TIMEOUT_MS
            readTimeout = AUTH_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $token")
        }

        return@withContext try {
            val responseCode = connection.responseCode
            val bodyStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val bodyText = bodyStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) {
                val detail = runCatching { JSONObject(bodyText).optString("detail") }.getOrNull()
                AuthApiResult(error = detail?.takeIf { it.isNotBlank() } ?: "Failed to load profile.")
            } else {
                AuthApiResult(data = parseUserProfile(JSONObject(bodyText)))
            }
        } catch (exc: Exception) {
            AuthApiResult(error = exc.message ?: "Failed to load profile.")
        } finally {
            connection.disconnect()
        }
    }

    suspend fun updateProfile(token: String, input: UpdateProfileInput): AuthApiResult<UserProfile> = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("name", input.name.trim())
            .put("gender", input.gender.trim())
            .put("phone_number", input.phoneNumber.trim())
            .put("emergency_contact_name", input.emergencyContactName.trim())
            .put("emergency_contact_phone", input.emergencyContactPhone.trim())
            .put("trusted_contacts_enabled", input.trustedContactsEnabled)

        val response = putJson("$AUTH_BASE_URL/update-profile", payload, token)
        response.fold(
            onSuccess = { body -> AuthApiResult(data = parseUserProfile(body)) },
            onFailure = { err -> AuthApiResult(error = err.message ?: "Failed to update profile.") }
        )
    }

    suspend fun deleteAccount(token: String, password: String): AuthApiResult<Unit> = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("password", password)
        val response = deleteJson("$AUTH_BASE_URL/delete-account", payload, token)
        response.fold(
            onSuccess = { AuthApiResult(data = Unit) },
            onFailure = { err -> AuthApiResult(error = err.message ?: "Failed to delete account.") }
        )
    }

    private fun parseAuthSession(body: JSONObject): AuthSession {
        val user = body.optJSONObject("user") ?: JSONObject()
        return AuthSession(
            accessToken = body.optString("access_token"),
            username = user.optString("username"),
            expiresInSeconds = body.optInt("expires_in", 0)
        )
    }

    private fun parseUserProfile(body: JSONObject): UserProfile {
        return UserProfile(
            name = body.optString("name"),
            username = body.optString("username"),
            email = body.optString("email"),
            gender = body.optString("gender"),
            phoneNumber = body.optString("phone_number"),
            emergencyContactName = body.optString("emergency_contact_name"),
            emergencyContactPhone = body.optString("emergency_contact_phone"),
            trustedContactsEnabled = body.optBoolean("trusted_contacts_enabled", true)
        )
    }

    private fun postJson(url: String, payload: JSONObject): Result<JSONObject> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = AUTH_TIMEOUT_MS
            readTimeout = AUTH_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }

        return try {
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(payload.toString())
            }

            val responseCode = connection.responseCode
            val bodyStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val bodyText = bodyStream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (responseCode !in 200..299) {
                val detail = runCatching { JSONObject(bodyText).optString("detail") }.getOrNull()
                Result.failure(IOException(detail?.takeIf { it.isNotBlank() } ?: "Request failed (HTTP $responseCode)."))
            } else {
                Result.success(JSONObject(bodyText))
            }
        } catch (exc: Exception) {
            Result.failure(exc)
        } finally {
            connection.disconnect()
        }
    }

    private fun postJsonWithFallback(
        baseUrl: String,
        candidatePaths: List<String>,
        payload: JSONObject
    ): Result<JSONObject> {
        var lastFailure: Throwable? = null
        for (path in candidatePaths) {
            val result = postJson("$baseUrl$path", payload)
            if (result.isSuccess) {
                return result
            }

            val failure = result.exceptionOrNull()
            lastFailure = failure
            val isNotFound = failure?.message?.contains("Not Found", ignoreCase = true) == true
            if (!isNotFound) {
                return result
            }
        }

        return Result.failure(lastFailure ?: IOException("Request failed."))
    }

    private fun putJson(url: String, payload: JSONObject, token: String): Result<JSONObject> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = AUTH_TIMEOUT_MS
            readTimeout = AUTH_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            doOutput = true
        }

        return try {
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(payload.toString())
            }

            val responseCode = connection.responseCode
            val bodyStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val bodyText = bodyStream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (responseCode !in 200..299) {
                val detail = runCatching { JSONObject(bodyText).optString("detail") }.getOrNull()
                Result.failure(IOException(detail?.takeIf { it.isNotBlank() } ?: "Request failed (HTTP $responseCode)."))
            } else {
                Result.success(JSONObject(bodyText))
            }
        } catch (exc: Exception) {
            Result.failure(exc)
        } finally {
            connection.disconnect()
        }
    }

    private fun deleteJson(url: String, payload: JSONObject, token: String): Result<JSONObject> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = AUTH_TIMEOUT_MS
            readTimeout = AUTH_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            doOutput = true
        }

        return try {
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(payload.toString())
            }

            val responseCode = connection.responseCode
            val bodyStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val bodyText = bodyStream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (responseCode !in 200..299) {
                val detail = runCatching { JSONObject(bodyText).optString("detail") }.getOrNull()
                Result.failure(IOException(detail?.takeIf { it.isNotBlank() } ?: "Request failed (HTTP $responseCode)."))
            } else {
                Result.success(JSONObject(bodyText.ifBlank { "{}" }))
            }
        } catch (exc: Exception) {
            Result.failure(exc)
        } finally {
            connection.disconnect()
        }
    }
}
