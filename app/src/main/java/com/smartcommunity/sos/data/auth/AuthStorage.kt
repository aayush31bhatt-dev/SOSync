package com.smartcommunity.sos.data.auth

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

private const val AUTH_DB_NAME = "auth.db"
private const val AUTH_DB_VERSION = 1
private const val SESSION_PREFS = "auth_session_prefs"
private const val SESSION_USERNAME_KEY = "current_username"

data class AuthUser(
    val id: Long,
    val username: String
)

class AuthStorage(context: Context) {
    private val appContext = context.applicationContext
    private val dbHelper = AuthDatabaseHelper(appContext)

    fun hasAnyUser(): Boolean {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT 1 FROM users LIMIT 1", null)
        return cursor.use { it.moveToFirst() }
    }

    fun createUser(username: String): Boolean {
        val normalized = normalizeUsername(username)
        if (normalized.isEmpty()) {
            return false
        }

        val db = dbHelper.writableDatabase
        return try {
            db.execSQL(
                "INSERT INTO users(username) VALUES (?)",
                arrayOf(normalized)
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    fun findUserByUsername(username: String): AuthUser? {
        val normalized = normalizeUsername(username)
        if (normalized.isEmpty()) {
            return null
        }

        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, username FROM users WHERE username = ? LIMIT 1",
            arrayOf(normalized)
        )

        return cursor.use {
            if (!it.moveToFirst()) {
                null
            } else {
                AuthUser(
                    id = it.getLong(0),
                    username = it.getString(1)
                )
            }
        }
    }

    fun setCurrentUsername(username: String) {
        val normalized = normalizeUsername(username)
        appContext
            .getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(SESSION_USERNAME_KEY, normalized)
            .apply()
    }

    fun getCurrentUsername(): String? {
        return appContext
            .getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
            .getString(SESSION_USERNAME_KEY, null)
            ?.takeIf { it.isNotBlank() }
    }

    private fun normalizeUsername(username: String): String {
        return username.trim().lowercase()
    }
}

private class AuthDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    AUTH_DB_NAME,
    null,
    AUTH_DB_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == newVersion) {
            return
        }
        db.execSQL("DROP TABLE IF EXISTS users")
        onCreate(db)
    }
}
