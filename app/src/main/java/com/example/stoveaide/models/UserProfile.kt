package com.example.stoveaide.models

data class UserProfile(
    val uid: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val fullName: String = "",
    val email: String = "",
    val stoveDeviceId: String = "STOVE-PH-8842",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getDisplayName(): String {
        return when {
            fullName.isNotBlank() -> fullName
            firstName.isNotBlank() || lastName.isNotBlank() -> "$firstName $lastName".trim()
            else -> "User"
        }
    }
}

