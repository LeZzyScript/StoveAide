package com.example.stoveaide.models

data class StoveData(
    val stoveId: String = "STOVE-PH-8842",
    val status: String = "STANDBY", // STANDBY, NORMAL, SAFE, DANGEROUS, EMERGENCY_SHUTOFF
    val flameDetected: Boolean = false,
    val temperatureCelsius: Float = 0f,
    val gasLevelPpm: Int = 0,
    val activeMinutes: Int = 0,
    val maxAlertMinutes: Int = 20,
    val lpgValveOpen: Boolean = false,
    val lastCheckInTimestamp: Long = 0L
)
