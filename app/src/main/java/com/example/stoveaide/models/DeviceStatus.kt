package com.example.stoveaide.models

/**
 * Represents the feedback state sent back from ESP32 to Firebase.
 */
data class DeviceStatus(
    val actualState: Boolean = false,
    val deviceStatus: String = "OFFLINE", // ONLINE, OFFLINE, UNAVAILABLE
    val lastHeartbeat: Long = 0L,
    val gpioPin: Int = 23,
    val ipAddress: String = ""
)
