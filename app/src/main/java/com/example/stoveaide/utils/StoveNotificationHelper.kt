package com.example.stoveaide.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.stoveaide.views.MainActivity
import com.example.stoveaide.R
import com.example.stoveaide.data.FirestoreManager
import com.example.stoveaide.data.StoveRepository
import com.example.stoveaide.models.StoveData
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StoveNotificationHelper {

    const val CHANNEL_ID = "stove_safety_alerts_channel"
    private const val CHANNEL_NAME = "Stove Safety Alerts"
    private const val NOTIFICATION_ID = 1001
    private const val TAG = "StoveNotificationHelper"

    private var isListening = false
    private var listenerRegistration: ListenerRegistration? = null
    private var rtdbValueListener: ValueEventListener? = null
    private var activeStoveId: String = ""
    private var lastNotifiedTimestamp = 0L
    private var lastDurationNotifiedTimestamp = 0L
    private var lastDurationNotifiedHours = 0
    private var isAppInForeground = false
    private var lastAppOpenTimestamp = System.currentTimeMillis()
    private var lastRecordedMinutes = -1
    private var lastRecordedTemp = -1f
    private var lastRecordedGas = -1

    fun onAppForegrounded() {
        isAppInForeground = true
        lastAppOpenTimestamp = System.currentTimeMillis()
    }

    fun onAppBackgrounded() {
        isAppInForeground = false
    }

    fun initNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical alerts for stove overheat, gas leaks, and unattended cooking"
                enableVibration(true)
                setShowBadge(true)
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun showSystemNotification(
        context: Context,
        title: String = "Stove Safety Alert",
        message: String = "Are you still using your stove?"
    ) {
        initNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS permission not granted yet
        }
    }

    /**
     * Start real-time Firestore listener that detects spikes and posts system notifications.
     */
    fun startRealtimeSpikeMonitor(context: Context, stoveId: String = StoveRepository.DEFAULT_STOVE_ID) {
        if (isListening) return

        isListening = true
        activeStoveId = stoveId

        // 1. Listen to Realtime Database (instant alerts when web demo or ESP32 reports dangerous values)
        val rtdbListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val stoveData = FirestoreManager.parseStoveDataFromRtdb(snapshot, stoveId)
                    checkForActivitySpikes(context.applicationContext, stoveId, stoveData)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "RTDB spike monitor error: ${error.message}")
            }
        }
        rtdbValueListener = rtdbListener
        FirestoreManager.realtimeDatabase?.getReference("stoves/$stoveId")?.addValueEventListener(rtdbListener)

        // 2. Listen to Firestore
        val db = FirestoreManager.firestore
        if (db != null) {
            listenerRegistration = db.collection("stoves").document(stoveId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Stove alert listener failed", error)
                        return@addSnapshotListener
                    }
                    if (snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    val stoveData = snapshot.toObject(StoveData::class.java) ?: return@addSnapshotListener
                    checkForActivitySpikes(context.applicationContext, stoveId, stoveData)
                }
        }
    }

    /**
     * Stop the spike monitor. Call this on logout to prevent crashes.
     */
    fun stopMonitoring() {
        listenerRegistration?.remove()
        listenerRegistration = null
        if (rtdbValueListener != null && activeStoveId.isNotBlank()) {
            FirestoreManager.realtimeDatabase?.getReference("stoves/$activeStoveId")
                ?.removeEventListener(rtdbValueListener!!)
            rtdbValueListener = null
        }
        activeStoveId = ""
        isListening = false
        lastNotifiedTimestamp = 0L
        lastDurationNotifiedTimestamp = 0L
        lastDurationNotifiedHours = 0
        lastRecordedMinutes = -1
        lastRecordedTemp = -1f
        lastRecordedGas = -1
    }

    private fun checkForActivitySpikes(context: Context, stoveId: String, data: StoveData) {
        val now = System.currentTimeMillis()
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val timeStr = timeFormat.format(Date())

        var triggerAlert = false
        var alertMessage = "Are you still using your stove?"

        // 1. Temperature Spike Check (> 250°C or sudden jump) — Anomaly Alert
        if (data.temperatureCelsius >= 250f && (lastRecordedTemp < 250f || now - lastNotifiedTimestamp > 30000)) {
            triggerAlert = true
            alertMessage = "High heat spike detected (${data.temperatureCelsius.toInt()}°C). Are you still using your stove?"
        }

        // 2. Gas Leak / High PPM Check (> 400 PPM) — Anomaly Alert
        if (data.gasLevelPpm >= 400 && (lastRecordedGas < 400 || now - lastNotifiedTimestamp > 30000)) {
            triggerAlert = true
            alertMessage = "Elevated gas level detected (${data.gasLevelPpm} PPM). Are you still using your stove?"
        }

        // 3. Flameout Check (Valve open but flame not detected) — Anomaly Alert
        if (data.lpgValveOpen && !data.flameDetected && (now - lastNotifiedTimestamp > 30000)) {
            triggerAlert = true
            alertMessage = "Flameout detected with gas open! Are you still using your stove?"
        }

        // 4. Status Danger Check — Anomaly Alert
        if (data.status.equals("DANGEROUS", ignoreCase = true) && !triggerAlert && (now - lastNotifiedTimestamp > 30000)) {
            triggerAlert = true
            alertMessage = "Safety danger detected on your stove! Are you still using your stove?"
        }

        // 5. Activity Duration Check:
        // ONLY notify when activity lasts 2 or more hours (>= 120 minutes),
        // and notify again for every hour while the app is not open.
        val isStoveActive = data.lpgValveOpen || data.flameDetected || data.temperatureCelsius > 60f
        if (data.activeMinutes >= 120 && isStoveActive) {
            val currentHours = data.activeMinutes / 60 // 2, 3, 4, ...
            val oneHourMillis = 3600_000L
            val timeSinceLastDurationNotif = now - lastDurationNotifiedTimestamp

            val isFirstTwoHourAlert = lastDurationNotifiedHours < 2
            val isNewHourReached = currentHours > lastDurationNotifiedHours
            val isHourElapsedClosed = !isAppInForeground && (timeSinceLastDurationNotif >= oneHourMillis)

            if (isFirstTwoHourAlert || isNewHourReached || isHourElapsedClosed) {
                triggerAlert = true
                alertMessage = "Your stove has been active for $currentHours hours (${data.activeMinutes} minutes). Are you still using your stove?"
                lastDurationNotifiedHours = currentHours
                lastDurationNotifiedTimestamp = now
            }
        } else if (data.activeMinutes < 120) {
            // Reset counter when stove activity ends or drops below 2 hours
            lastDurationNotifiedHours = 0
            lastDurationNotifiedTimestamp = 0L
        }

        lastRecordedMinutes = data.activeMinutes
        lastRecordedTemp = data.temperatureCelsius
        lastRecordedGas = data.gasLevelPpm

        if (triggerAlert && (now - lastNotifiedTimestamp > 5000)) {
            lastNotifiedTimestamp = now
            // Fire system notification
            showSystemNotification(context, "Stove Safety Alert", alertMessage)

            // Log to in-app notifications collection
            StoveRepository.addNotification(
                message = alertMessage,
                dateLabel = "Today",
                timeLabel = timeStr,
                stoveId = stoveId
            )
        }
    }
}
