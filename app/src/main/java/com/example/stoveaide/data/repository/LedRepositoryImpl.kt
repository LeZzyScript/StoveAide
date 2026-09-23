package com.example.stoveaide.data.repository

import android.util.Log
import com.example.stoveaide.models.DeviceStatus
import com.example.stoveaide.models.LedCommand
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

class LedRepositoryImpl(
    private val auth: FirebaseAuth? = try { FirebaseAuth.getInstance() } catch (e: Exception) { null },
    private val rtdb: FirebaseDatabase? = try { 
        FirebaseDatabase.getInstance("https://stoveaide-default-rtdb.asia-southeast1.firebasedatabase.app") 
    } catch (e: Exception) { 
        try { FirebaseDatabase.getInstance() } catch (e2: Exception) { null } 
    },
    private val firestore: FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (e: Exception) { null }
) : ILedRepository {

    companion object {
        private const val TAG = "LedRepository"
        private const val PATH_DEVICES = "devices"
    }

    override fun getCurrentUserId(): String? {
        return auth?.currentUser?.uid
    }

    override fun sendLedCommand(
        deviceId: String,
        targetState: Boolean,
        onResult: (Boolean, String?) -> Unit
    ) {
        val uid = getCurrentUserId() ?: "anonymous"
        val timestamp = System.currentTimeMillis()

        // 1. Write to Realtime Database (Primary for ESP32)
        if (rtdb != null) {
            val cmdRef = rtdb.getReference(PATH_DEVICES).child(deviceId).child("command")
            val commandMap = mapOf(
                "targetState" to targetState,
                "lastCommandTimestamp" to timestamp,
                "requestedBy" to uid
            )

            cmdRef.setValue(commandMap)
                .addOnSuccessListener {
                    Log.d(TAG, "RTDB LED command dispatched: targetState=$targetState")
                    onResult(true, null)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "RTDB error sending command", e)
                    // Fallback to Firestore
                    sendToFirestore(deviceId, targetState, uid, timestamp, onResult)
                }
        } else {
            sendToFirestore(deviceId, targetState, uid, timestamp, onResult)
        }
    }

    private fun sendToFirestore(
        deviceId: String,
        targetState: Boolean,
        uid: String,
        timestamp: Long,
        onResult: (Boolean, String?) -> Unit
    ) {
        val db = firestore
        if (db == null) {
            Log.w(TAG, "Firebase unavailable; running in local mode.")
            onResult(true, null)
            return
        }

        val commandPayload = mapOf(
            "command" to mapOf(
                "targetState" to targetState,
                "lastCommandTimestamp" to timestamp,
                "requestedBy" to uid
            )
        )

        db.collection(PATH_DEVICES)
            .document(deviceId)
            .set(commandPayload, SetOptions.merge())
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.localizedMessage) }
    }

    override fun listenToDeviceStatus(
        deviceId: String,
        onUpdate: (DeviceStatus) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration? {
        // Listen via Realtime Database
        if (rtdb != null) {
            val statusRef = rtdb.getReference(PATH_DEVICES).child(deviceId).child("status")
            statusRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val actualState = snapshot.child("actualState").getValue(Boolean::class.java) ?: false
                        val devStatus = snapshot.child("deviceStatus").getValue(String::class.java) ?: "OFFLINE"
                        val lastHeartbeat = snapshot.child("lastHeartbeat").getValue(Long::class.java) ?: 0L
                        val gpioPin = snapshot.child("gpioPin").getValue(Int::class.java) ?: 23
                        val ipAddress = snapshot.child("ipAddress").getValue(String::class.java) ?: ""

                        onUpdate(DeviceStatus(
                            actualState = actualState,
                            deviceStatus = devStatus,
                            lastHeartbeat = lastHeartbeat,
                            gpioPin = gpioPin,
                            ipAddress = ipAddress
                        ))
                    } else {
                        onUpdate(DeviceStatus(actualState = false, deviceStatus = "OFFLINE", lastHeartbeat = 0L))
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    onError(error.message)
                }
            })
        }

        // Also register Firestore listener as companion
        val db = firestore ?: return null
        return db.collection(PATH_DEVICES).document(deviceId).addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null && snapshot.exists() && rtdb == null) {
                val statusMap = snapshot.get("status") as? Map<*, *>
                if (statusMap != null) {
                    val actualState = statusMap["actualState"] as? Boolean ?: false
                    val devStatus = statusMap["deviceStatus"] as? String ?: "OFFLINE"
                    val lastHeartbeat = (statusMap["lastHeartbeat"] as? Number)?.toLong() ?: 0L
                    val gpioPin = (statusMap["gpioPin"] as? Number)?.toInt() ?: 23
                    val ipAddress = statusMap["ipAddress"] as? String ?: ""

                    onUpdate(DeviceStatus(
                        actualState = actualState,
                        deviceStatus = devStatus,
                        lastHeartbeat = lastHeartbeat,
                        gpioPin = gpioPin,
                        ipAddress = ipAddress
                    ))
                }
            }
        }
    }

    override fun listenToCommand(
        deviceId: String,
        onUpdate: (LedCommand) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration? {
        if (rtdb != null) {
            val cmdRef = rtdb.getReference(PATH_DEVICES).child(deviceId).child("command")
            cmdRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val targetState = snapshot.child("targetState").getValue(Boolean::class.java) ?: false
                        val lastTs = snapshot.child("lastCommandTimestamp").getValue(Long::class.java) ?: 0L
                        val requestedBy = snapshot.child("requestedBy").getValue(String::class.java) ?: ""

                        onUpdate(LedCommand(targetState, lastTs, requestedBy))
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    onError(error.message)
                }
            })
        }
        return null
    }
}
