package com.smartcommunity.sos.data.auth

import android.content.Context

private const val AUTH_PREFS = "auth_session_prefs"
private const val AUTH_TOKEN_KEY = "access_token"
private const val AUTH_USERNAME_KEY = "current_username"
private const val AUTH_EXPIRES_AT_KEY = "expires_at_epoch_sec"

class AuthSessionStore(context: Context) {
    private val appContext = context.applicationContext

    fun saveSession(session: AuthSession) {
        val nowEpoch = System.currentTimeMillis() / 1000L
        val expiresAt = nowEpoch + session.expiresInSeconds
        appContext.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(AUTH_TOKEN_KEY, session.accessToken)
            .putString(AUTH_USERNAME_KEY, session.username)
            .putLong(AUTH_EXPIRES_AT_KEY, expiresAt)
            .apply()
    }

    fun getUsername(): String? {
        if (isExpired()) {
            clearSession()
            return null
        }
        return appContext.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
            .getString(AUTH_USERNAME_KEY, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun getToken(): String? {
        if (isExpired()) {
            clearSession()
            return null
        }
        return appContext.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
            .getString(AUTH_TOKEN_KEY, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun clearSession() {
        appContext.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(AUTH_TOKEN_KEY)
            .remove(AUTH_USERNAME_KEY)
            .remove(AUTH_EXPIRES_AT_KEY)
            .apply()
    }

    private fun isExpired(): Boolean {
        val prefs = appContext.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
        val expiresAt = prefs.getLong(AUTH_EXPIRES_AT_KEY, 0L)
        if (expiresAt <= 0L) {
            return false
        }
        val nowEpoch = System.currentTimeMillis() / 1000L
        return nowEpoch >= expiresAt
    }
}
