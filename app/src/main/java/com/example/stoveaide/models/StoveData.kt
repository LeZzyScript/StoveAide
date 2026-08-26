package com.example.stoveaide.models

data class StoveData(
    val stoveId: String = "STOVE-PH-8842",
    val status: String = "ACTIVE", // ACTIVE, NORMAL, WARNING, EMERGENCY
    val flameDetected: Boolean = true,
    val temperatureCelsius: Float = 38f,
    val gasLevelPpm: Int = 0,
    val activeMinutes: Int = 14,
    val maxAlertMinutes: Int = 20,
    val lpgValveOpen: Boolean = true,
    val lastCheckInTimestamp: Long = System.currentTimeMillis()
)
