package com.example.stoveaide.data

import com.example.stoveaide.data.FirestoreManager
import com.example.stoveaide.models.StoveUsageLog
import com.example.stoveaide.models.StoveData
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Singleton that logs stove usage from activation (gas valve open) to deactivation (gas valve closed).
 * Call processStoveData() on each stove data update to track state.
 * Tracks dangerous conditions during usage for accurate summary.
 */
object UsageLogger {
    private const val DEFAULT_STOVE_ID = "STOVE-PH-8842"
    private var isLogging = false
    private var startTimeMillis: Long = 0L
    private var stoveId: String = DEFAULT_STOVE_ID
    private var hasSeenDangerousCondition: Boolean = false

    /**
     * Processes a stove data update to detect activation/deactivation and log usage.
     * Should be called whenever new stove data is available (e.g., from a listener).
     * 
     * Activation: lpgValveOpen == true (gas valve open)
     * Deactivation: !lpgValveOpen (gas valve closed)
     */
    fun processStoveData(stove: StoveData) {
        // Activation condition: gas valve open
        val isActivated = stove.lpgValveOpen

        // Deactivation condition: gas valve closed
        val isDeactivated = !stove.lpgValveOpen

        // Track dangerous conditions during usage period
        if (isLogging) {
            updateDangerousConditionTracking(stove)
        }

        if (!isLogging && isActivated) {
            // Start logging
            startLogging()
        } else if (isLogging && isDeactivated) {
            // Stop logging and create usage log
            stopLoggingAndLog()
        }
        // Otherwise, keep current state
    }

    private fun updateDangerousConditionTracking(stove: StoveData) {
        // Check for dangerous conditions that should affect the usage summary
        // Based on DashboardFragment's alert logic:
        val isDangerous = stove.gasLevelPpm >= 400 ||  // Gas leak detected
                          stove.temperatureCelsius >= 250f ||  // High temperature spike
                          (stove.lpgValveOpen && !stove.flameDetected && stove.activeMinutes > 0)  // Flameout detected
        
        if (isDangerous) {
            hasSeenDangerousCondition = true
        }
    }

    private fun startLogging() {
        isLogging = true
        startTimeMillis = System.currentTimeMillis()
        hasSeenDangerousCondition = false  // Reset danger tracking for new usage period
        // Optionally log start for debugging
        // Log.d("UsageLogger", "Usage logging started at $startTimeMillis")
    }

    private fun stopLoggingAndLog() {
        val endTimeMillis = System.currentTimeMillis()
        isLogging = false

        val durationMinutes = ((endTimeMillis - startTimeMillis) / (1000 * 60)).toInt()
        // Remove minimum duration restriction - allow any duration including zero

        val timeFormat = SimpleDateFormat("h:mm aa", Locale.ENGLISH)
        val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
        val startTimeString = timeFormat.format(startTimeMillis)
        val endTimeString = timeFormat.format(endTimeMillis)
        val dateString = dateFormat.format(startTimeMillis)

        // Determine status: if any dangerous condition occurred during usage, mark as Dangerous
        val status = if (hasSeenDangerousCondition) "Dangerous" else "Normal"

        // Generate summary sentence based on whether dangerous conditions were encountered
        val summarySentence = if (status.equals("Dangerous", ignoreCase = true)) {
            "Your stove was turned on at $startTimeString and a warning was sent at $endTimeString."
        } else {
            "Your stove burner was used for $durationMinutes minutes from $startTimeString to $endTimeString at ${status.lowercase()} levels."
        }

        // Create usage log with placeholder sensor values (since we don't have real-time gauge data from StoveData)
        // In a more complete implementation, we would track min/max/average of sensor readings during the usage.
        val log = StoveUsageLog(
            id = "usage_${System.currentTimeMillis()}",
            dateLabel = dateString,
            dateShort = startTimeString, // Just the time part for short display
            section = "Today",
            status = status,
            startTime = startTimeString,
            endTime = endTimeString,
            durationMinutes = durationMinutes,
            summarySentence = summarySentence,
            burnerLevel = 5.0f, // placeholder
            burnerTemperature = 5.0f, // placeholder
            gasTemperature = 4.0f, // placeholder
            flameBehavior = 3.5f, // placeholder
            timestamp = endTimeMillis,
            stoveId = stoveId
        )

        // Save the log
        StoveRepository.addUsageLog(log) { success ->
            if (!success) {
                // Log.e("UsageLogger", "Failed to save usage log")
            }
            // Reset danger tracking for next potential usage
            hasSeenDangerousCondition = false
        }
    }

    /** Resets the logger state (e.g., if needed) */
    fun reset() {
        isLogging = false
        startTimeMillis = 0L
        hasSeenDangerousCondition = false
    }
}