package com.example.stoveaide.data.repository

import com.example.stoveaide.models.DeviceStatus
import com.example.stoveaide.models.LedCommand
import com.google.firebase.firestore.ListenerRegistration

/**
 * Interface defining operations for Firebase IoT LED commands & feedback monitoring.
 */
interface ILedRepository {
    fun getCurrentUserId(): String?
    fun sendLedCommand(deviceId: String, targetState: Boolean, onResult: (Boolean, String?) -> Unit)
    fun listenToDeviceStatus(deviceId: String, onUpdate: (DeviceStatus) -> Unit, onError: (String) -> Unit): ListenerRegistration?
    fun listenToCommand(deviceId: String, onUpdate: (LedCommand) -> Unit, onError: (String) -> Unit): ListenerRegistration?
}
