package com.example.stoveaide.models

import java.io.Serializable

data class NotificationItem(
    val id: String = "",
    val dateLabel: String = "Today",
    val fullDate: String = "",
    val timeLabel: String = "7:32 AM",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
