package com.example.stoveaide.models

import java.io.Serializable

data class StoveUsageLog(
    val id: String = "",
    val dateLabel: String = "",
    val dateShort: String = "",
    val section: String = "Today",
    val status: String = "Safe", // "Safe", "Normal", "Dangerous"
    val startTime: String = "",
    val endTime: String = "",
    val durationMinutes: Int = 0,
    val summarySentence: String = "",
    val burnerLevel: Float = 5.0f,
    val burnerTemperature: Float = 5.0f,
    val gasTemperature: Float = 4.0f,
    val flameBehavior: Float = 3.0f,
    val timestamp: Long = System.currentTimeMillis(),
    val stoveId: String = ""
) : Serializable
