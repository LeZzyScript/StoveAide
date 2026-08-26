package com.example.stoveaide.models

data class UserProfile(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val stoveDeviceId: String = "STOVE-PH-8842",
    val createdAt: Long = System.currentTimeMillis()
)
