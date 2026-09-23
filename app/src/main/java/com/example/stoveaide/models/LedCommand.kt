package com.example.stoveaide.models

/**
 * Represents the command dispatched from the Android application to Firebase.
 */
data class LedCommand(
    val targetState: Boolean = false,
    val lastCommandTimestamp: Long = System.currentTimeMillis(),
    val requestedBy: String = ""
)
