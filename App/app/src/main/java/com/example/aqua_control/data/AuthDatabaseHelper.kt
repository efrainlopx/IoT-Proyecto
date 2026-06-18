package com.example.aqua_control.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.aqua_control.config.ProjectConfig
import java.security.MessageDigest

class AuthDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        insertUser(db, ProjectConfig.DEFAULT_LOGIN_USER, ProjectConfig.DEFAULT_LOGIN_PASSWORD)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS users")
        onCreate(db)
    }

    fun validateUser(username: String, password: String): Boolean {
        val normalizedUser = username.trim()
        if (normalizedUser.isBlank() || password.isBlank()) return false

        readableDatabase.rawQuery(
            "SELECT 1 FROM users WHERE username = ? AND password_hash = ? LIMIT 1",
            arrayOf(normalizedUser, hashPassword(password)),
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    fun createUser(username: String, password: String): AuthResult {
        val normalizedUser = username.trim()
        if (normalizedUser.length < 3) return AuthResult.Invalid("Usuario minimo de 3 caracteres")
        if (password.length < 6) return AuthResult.Invalid("Contrasena minima de 6 caracteres")

        return try {
            insertUser(writableDatabase, normalizedUser, password)
            AuthResult.Success
        } catch (exception: Exception) {
            AuthResult.Invalid("No se pudo crear el usuario. Puede que ya exista.")
        }
    }

    private fun insertUser(db: SQLiteDatabase, username: String, password: String) {
        val values = ContentValues().apply {
            put("username", username.trim())
            put("password_hash", hashPassword(password))
            put("created_at", System.currentTimeMillis())
        }
        db.insertOrThrow("users", null, values)
    }

    private fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    companion object {
        private const val DATABASE_NAME = "aquacontrol_auth.db"
        private const val DATABASE_VERSION = 1
    }
}

sealed class AuthResult {
    data object Success : AuthResult()
    data class Invalid(val message: String) : AuthResult()
}
