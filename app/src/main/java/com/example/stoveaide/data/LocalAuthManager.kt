package com.example.stoveaide.data

import android.content.Context
import java.security.MessageDigest

class LocalAuthManager(context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun register(email: String, password: String): Boolean {
        val normalizedEmail = email.trim().lowercase()
        if (preferences.contains(passwordKey(normalizedEmail))) return false
        preferences.edit()
            .putString(passwordKey(normalizedEmail), hash(password))
            .apply()
        return true
    }

    fun authenticate(email: String, password: String): Boolean {
        val normalizedEmail = email.trim().lowercase()
        val storedPassword = preferences.getString(passwordKey(normalizedEmail), null)
        val valid = storedPassword != null && storedPassword == hash(password)
        if (valid) {
            preferences.edit().putString(CURRENT_EMAIL, normalizedEmail).apply()
        }
        return valid
    }

    fun updatePassword(email: String, password: String): Boolean {
        val normalizedEmail = email.trim().lowercase()
        preferences.edit().putString(passwordKey(normalizedEmail), hash(password)).apply()
        return true
    }

    fun signOut() {
        preferences.edit().remove(CURRENT_EMAIL).apply()
    }

    fun isSignedIn(): Boolean = preferences.getString(CURRENT_EMAIL, null) != null

    private fun passwordKey(email: String): String = "password_$email"

    private fun hash(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        private const val PREFERENCES = "local_demo_auth"
        private const val CURRENT_EMAIL = "current_email"
    }
}