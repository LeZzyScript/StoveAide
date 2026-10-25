package com.example.stoveaide.data

import android.util.Log
import com.example.stoveaide.models.NotificationItem
import com.example.stoveaide.models.StoveUsageLog
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.Query

object StoveRepository {
    private const val TAG = "StoveRepository"
    const val DEFAULT_STOVE_ID = "STOVE-PH-8842"

    private val rtdbUsageLogs = mutableMapOf<String, StoveUsageLog>()
    private val firestoreUsageLogs = mutableMapOf<String, StoveUsageLog>()

    private val rtdbNotifications = mutableMapOf<String, NotificationItem>()
    private val firestoreNotifications = mutableMapOf<String, NotificationItem>()

    private val userNotifications = mutableMapOf<String, NotificationItem>()
    private val topLevelNotifications = mutableMapOf<String, NotificationItem>()

    fun listenToUsageLogs(callback: (List<StoveUsageLog>) -> Unit) {
        listenToUsageLogs(DEFAULT_STOVE_ID, callback)
    }

    fun listenToUsageLogs(
        stoveId: String = DEFAULT_STOVE_ID,
        onUpdate: (List<StoveUsageLog>) -> Unit
    ) {
        fun emitMergedLogs() {
            val merged = (firestoreUsageLogs + rtdbUsageLogs).values.toList()
                .sortedByDescending { it.timestamp }
            onUpdate(merged)
        }

        // 1. Listen to Realtime Database (instant sync from Web Demo and ESP32)
        FirestoreManager.realtimeDatabase?.getReference("stoves/$stoveId/usage_logs")
            ?.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    rtdbUsageLogs.clear()
                    for (child in snapshot.children) {
                        val log = parseUsageLogFromRtdb(child)
                        if (log.id.isNotBlank()) {
                            rtdbUsageLogs[log.id] = log
                        }
                    }
                    emitMergedLogs()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "RTDB listenToUsageLogs error: ${error.message}")
                }
            })

        // 2. Listen to Firestore stoves/{stoveId}/usage_logs
        val db = FirestoreManager.firestore
        if (db != null) {
            db.collection("stoves").document(stoveId).collection("usage_logs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Firestore listenToUsageLogs (usage_logs) error: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        for (doc in snapshot.documents) {
                            val log = doc.toObject(StoveUsageLog::class.java)
                            if (log != null && log.id.isNotBlank()) {
                                firestoreUsageLogs[log.id] = log
                            }
                        }
                        emitMergedLogs()
                    }
                }

            // Also listen to stoves/{stoveId}/usageLogs (camelCase collection alias)
            db.collection("stoves").document(stoveId).collection("usageLogs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        for (doc in snapshot.documents) {
                            val log = doc.toObject(StoveUsageLog::class.java)
                            if (log != null && log.id.isNotBlank()) {
                                firestoreUsageLogs[log.id] = log
                            }
                        }
                        emitMergedLogs()
                    }
                }
        }
    }

    fun listenToNotifications(callback: (List<NotificationItem>) -> Unit) {
        listenToNotifications(DEFAULT_STOVE_ID, callback)
    }

    fun listenToNotifications(
        stoveId: String = DEFAULT_STOVE_ID,
        onUpdate: (List<NotificationItem>) -> Unit
    ) {
        fun emitMergedNotifications() {
            val merged = (firestoreNotifications + rtdbNotifications + userNotifications + topLevelNotifications)
                .values.toList()
                .sortedByDescending { it.timestamp }
            onUpdate(merged)
        }

        // 1. Listen to Realtime Database (instant sync from Web Demo and ESP32)
        FirestoreManager.realtimeDatabase?.getReference("stoves/$stoveId/notifications")
            ?.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    rtdbNotifications.clear()
                    for (child in snapshot.children) {
                        val notif = parseNotificationFromRtdb(child)
                        if (notif.id.isNotBlank()) {
                            rtdbNotifications[notif.id] = notif
                        }
                    }
                    emitMergedNotifications()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "RTDB listenToNotifications error: ${error.message}")
                }
            })

        val db = FirestoreManager.firestore
        if (db != null) {
            // 2. Listen to Firestore stoves/{stoveId}/notifications
            db.collection("stoves").document(stoveId).collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Firestore listenToNotifications error: ${error.message}")
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        for (doc in snapshot.documents) {
                            val notif = doc.toObject(NotificationItem::class.java)
                            if (notif != null && notif.id.isNotBlank()) {
                                firestoreNotifications[notif.id] = notif
                            }
                        }
                        emitMergedNotifications()
                    }
                }

            // 3. Listen to users/{uid}/notifications if user is logged in
            val currentUid = FirestoreManager.auth?.currentUser?.uid
            if (!currentUid.isNullOrBlank()) {
                db.collection("users").document(currentUid).collection("notifications")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshot, error ->
                        if (error == null && snapshot != null) {
                            for (doc in snapshot.documents) {
                                val notif = doc.toObject(NotificationItem::class.java)
                                if (notif != null && notif.id.isNotBlank()) {
                                    userNotifications[notif.id] = notif
                                }
                            }
                            emitMergedNotifications()
                        }
                    }
            }

            // 4. Also listen to top-level notifications collection
            db.collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error == null && snapshot != null) {
                        for (doc in snapshot.documents) {
                            val notif = doc.toObject(NotificationItem::class.java)
                            if (notif != null && notif.id.isNotBlank()) {
                                topLevelNotifications[notif.id] = notif
                            }
                        }
                        emitMergedNotifications()
                    }
                }
        }
    }

    fun addNotification(
        message: String,
        dateLabel: String = "Today",
        timeLabel: String = "Now",
        stoveId: String = DEFAULT_STOVE_ID,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val fullDate = java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.ENGLISH)
            .format(java.util.Date())
        val notif = NotificationItem(
            id = "notif_${System.currentTimeMillis()}",
            dateLabel = dateLabel,
            fullDate = fullDate,
            timeLabel = timeLabel,
            message = message,
            timestamp = System.currentTimeMillis()
        )

        // Write to Realtime Database
        FirestoreManager.realtimeDatabase?.getReference("stoves/$stoveId/notifications/${notif.id}")
            ?.setValue(notif)

        // Write to Firestore
        val db = FirestoreManager.firestore
        if (db != null) {
            db.collection("stoves").document(stoveId).collection("notifications")
                .document(notif.id)
                .set(notif)
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { onComplete(false) }
        } else {
            onComplete(true)
        }
    }

    fun deleteUsageLog(
        logId: String,
        stoveId: String = DEFAULT_STOVE_ID,
        onComplete: (Boolean) -> Unit = {}
    ) {
        // Delete from Realtime Database
        FirestoreManager.realtimeDatabase?.getReference("stoves/$stoveId/usage_logs/$logId")
            ?.removeValue()

        // Delete from Firestore
        val db = FirestoreManager.firestore
        if (db != null) {
            db.collection("stoves").document(stoveId).collection("usage_logs")
                .document(logId)
                .delete()
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { onComplete(false) }
        } else {
            onComplete(true)
        }
    }

    fun deleteNotification(
        notifId: String,
        stoveId: String = DEFAULT_STOVE_ID,
        onComplete: (Boolean) -> Unit = {}
    ) {
        // Delete from Realtime Database
        FirestoreManager.realtimeDatabase?.getReference("stoves/$stoveId/notifications/$notifId")
            ?.removeValue()

        // Delete from Firestore
        val db = FirestoreManager.firestore
        if (db != null) {
            db.collection("stoves").document(stoveId).collection("notifications")
                .document(notifId)
                .delete()
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { onComplete(false) }
        } else {
            onComplete(true)
        }
    }

    fun addUsageLog(
        log: StoveUsageLog,
        onComplete: (Boolean) -> Unit = {}
    ) {
        // Write to Realtime Database
        FirestoreManager.realtimeDatabase?.getReference("stoves/${log.stoveId ?: DEFAULT_STOVE_ID}/usage_logs/${log.id}")
            ?.setValue(log)

        // Write to Firestore
        val db = FirestoreManager.firestore
        if (db != null) {
            val stoveId = log.stoveId ?: DEFAULT_STOVE_ID
            db.collection("stoves").document(stoveId).collection("usage_logs")
                .document(log.id)
                .set(log)
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { onComplete(false) }
        } else {
            onComplete(true)
        }
    }

    private fun parseUsageLogFromRtdb(child: DataSnapshot): StoveUsageLog {
        val id = child.child("id").getValue(String::class.java) ?: child.key ?: ""
        val dateLabel = child.child("dateLabel").getValue(String::class.java) ?: ""
        val dateShort = child.child("dateShort").getValue(String::class.java) ?: ""
        val section = child.child("section").getValue(String::class.java) ?: "Today"
        val status = child.child("status").getValue(String::class.java) ?: "Safe"
        val startTime = child.child("startTime").getValue(String::class.java) ?: ""
        val endTime = child.child("endTime").getValue(String::class.java) ?: ""
        val durationMinutes = (child.child("durationMinutes").value as? Number)?.toInt() ?: 0
        val summarySentence = child.child("summarySentence").getValue(String::class.java) ?: ""
        val burnerLevel = (child.child("burnerLevel").value as? Number)?.toFloat() ?: 5.0f
        val burnerTemp = (child.child("burnerTemperature").value as? Number)?.toFloat() ?: 5.0f
        val gasTemp = (child.child("gasTemperature").value as? Number)?.toFloat() ?: 4.0f
        val flameScore = (child.child("flameBehavior").value as? Number)?.toFloat() ?: 3.0f
        val timestamp = (child.child("timestamp").value as? Number)?.toLong() ?: System.currentTimeMillis()
        val stoveId = child.child("stoveId").getValue(String::class.java) ?: DEFAULT_STOVE_ID

        return StoveUsageLog(
            id = id,
            dateLabel = dateLabel,
            dateShort = dateShort,
            section = section,
            status = status,
            startTime = startTime,
            endTime = endTime,
            durationMinutes = durationMinutes,
            summarySentence = summarySentence,
            burnerLevel = burnerLevel,
            burnerTemperature = burnerTemp,
            gasTemperature = gasTemp,
            flameBehavior = flameScore,
            timestamp = timestamp,
            stoveId = stoveId
        )
    }

    private fun parseNotificationFromRtdb(child: DataSnapshot): NotificationItem {
        val id = child.child("id").getValue(String::class.java) ?: child.key ?: ""
        val dateLabel = child.child("dateLabel").getValue(String::class.java) ?: "Today"
        val fullDate = child.child("fullDate").getValue(String::class.java) ?: ""
        val timeLabel = child.child("timeLabel").getValue(String::class.java) ?: "Now"
        val message = child.child("message").getValue(String::class.java) ?: ""
        val timestamp = (child.child("timestamp").value as? Number)?.toLong() ?: System.currentTimeMillis()

        return NotificationItem(
            id = id,
            dateLabel = dateLabel,
            fullDate = fullDate,
            timeLabel = timeLabel,
            message = message,
            timestamp = timestamp
        )
    }
}
