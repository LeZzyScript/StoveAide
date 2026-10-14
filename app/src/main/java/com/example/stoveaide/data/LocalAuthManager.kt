package com.example.stoveaide.data

import android.content.Context
import com.example.stoveaide.models.UserProfile
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

    fun setCurrentEmail(email: String) {
        val normalizedEmail = email.trim().lowercase()
        preferences.edit().putString(CURRENT_EMAIL, normalizedEmail).apply()
    }

    fun getCurrentEmail(): String? = preferences.getString(CURRENT_EMAIL, null)

    fun saveUserProfile(profile: UserProfile) {
        preferences.edit()
            .putString(KEY_PROFILE_UID, profile.uid)
            .putString(KEY_PROFILE_FIRST_NAME, profile.firstName)
            .putString(KEY_PROFILE_LAST_NAME, profile.lastName)
            .putString(KEY_PROFILE_FULL_NAME, profile.fullName)
            .putString(KEY_PROFILE_EMAIL, profile.email.ifBlank { getCurrentEmail() ?: "" })
            .putString(KEY_PROFILE_STOVE_ID, profile.stoveDeviceId)
            .apply()
        if (profile.email.isNotBlank()) {
            setCurrentEmail(profile.email)
        }
    }

    fun getUserProfile(): UserProfile? {
        val email = preferences.getString(KEY_PROFILE_EMAIL, null) ?: getCurrentEmail() ?: return null
        val fullName = preferences.getString(KEY_PROFILE_FULL_NAME, "") ?: ""
        val firstName = preferences.getString(KEY_PROFILE_FIRST_NAME, "") ?: ""
        val lastName = preferences.getString(KEY_PROFILE_LAST_NAME, "") ?: ""
        val uid = preferences.getString(KEY_PROFILE_UID, "") ?: ""
        val stoveId = preferences.getString(KEY_PROFILE_STOVE_ID, "STOVE-PH-8842") ?: "STOVE-PH-8842"

        if (fullName.isBlank() && firstName.isBlank() && email.isBlank()) {
            return null
        }

        return UserProfile(
            uid = uid,
            firstName = firstName,
            lastName = lastName,
            fullName = fullName,
            email = email,
            stoveDeviceId = stoveId
        )
    }

    fun updatePassword(email: String, password: String): Boolean {
        val normalizedEmail = email.trim().lowercase()
        preferences.edit().putString(passwordKey(normalizedEmail), hash(password)).apply()
        return true
    }

    fun signOut() {
        preferences.edit()
            .remove(CURRENT_EMAIL)
            .remove(KEY_PROFILE_UID)
            .remove(KEY_PROFILE_FIRST_NAME)
            .remove(KEY_PROFILE_LAST_NAME)
            .remove(KEY_PROFILE_FULL_NAME)
            .remove(KEY_PROFILE_EMAIL)
            .remove(KEY_PROFILE_STOVE_ID)
            .apply()
    }

    fun isSignedIn(): Boolean = preferences.getString(CURRENT_EMAIL, null) != null

    fun hasAccount(email: String): Boolean =
        preferences.contains(passwordKey(email.trim().lowercase()))

    private fun passwordKey(email: String): String = "password_$email"

    private fun hash(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        private const val PREFERENCES = "local_demo_auth"
        private const val CURRENT_EMAIL = "current_email"
        private const val KEY_PROFILE_UID = "profile_uid"
        private const val KEY_PROFILE_FIRST_NAME = "profile_first_name"
        private const val KEY_PROFILE_LAST_NAME = "profile_last_name"
        private const val KEY_PROFILE_FULL_NAME = "profile_full_name"
        private const val KEY_PROFILE_EMAIL = "profile_email"
        private const val KEY_PROFILE_STOVE_ID = "profile_stove_id"
    }
}