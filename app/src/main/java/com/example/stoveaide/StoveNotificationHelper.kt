package com.example.stoveaide

import android.content.Context
import com.example.stoveaide.data.StoveRepository

/**
 * Global helper for stove safety monitoring and push/local notification management.
 * Provides a clean interface for DashboardFragment, ProfileFragment, and Activities.
 */
object StoveNotificationHelper {

    fun stopMonitoring() {
        com.example.stoveaide.utils.StoveNotificationHelper.stopMonitoring()
    }

    fun startRealtimeSpikeMonitor(
        context: Context,
        stoveId: String = StoveRepository.DEFAULT_STOVE_ID
    ) {
        com.example.stoveaide.utils.StoveNotificationHelper.startRealtimeSpikeMonitor(context, stoveId)
    }

    fun initNotificationChannel(context: Context) {
        com.example.stoveaide.utils.StoveNotificationHelper.initNotificationChannel(context)
    }

    fun showSystemNotification(
        context: Context,
        title: String = "Stove Safety Alert",
        message: String = "Are you still using your stove?"
    ) {
        com.example.stoveaide.utils.StoveNotificationHelper.showSystemNotification(context, title, message)
    }

    fun onAppForegrounded() {
        com.example.stoveaide.utils.StoveNotificationHelper.onAppForegrounded()
    }

    fun onAppBackgrounded() {
        com.example.stoveaide.utils.StoveNotificationHelper.onAppBackgrounded()
    }
}
