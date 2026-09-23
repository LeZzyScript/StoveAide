package com.example.stoveaide.presenters

import com.example.stoveaide.data.repository.ILedRepository
import com.example.stoveaide.models.DeviceOnlineState
import com.example.stoveaide.views.ILedControlView
import com.google.firebase.firestore.ListenerRegistration

class LedControlPresenter(
    private val repository: ILedRepository
) : ILedControlPresenter {

    private var view: ILedControlView? = null
    private var statusListener: ListenerRegistration? = null
    private var commandListener: ListenerRegistration? = null

    companion object {
        private const val ONLINE_THRESHOLD_S = 12L    // 12 seconds
        private const val UNAVAILABLE_THRESHOLD_S = 30L // 30 seconds
    }

    override fun attachView(view: ILedControlView) {
        this.view = view
    }

    override fun detachView() {
        stopMonitoring()
        this.view = null
    }

    override fun checkAuthentication() {
        val uid = repository.getCurrentUserId()
        if (uid.isNullOrBlank()) {
            view?.navigateToLogin()
        }
    }

    override fun startMonitoring(deviceId: String) {
        view?.showLoading(true)

        // 1. Listen to ESP32 Hardware Feedback & Heartbeat
        statusListener = repository.listenToDeviceStatus(
            deviceId = deviceId,
            onUpdate = { status ->
                view?.showLoading(false)
                
                // Value Manipulation: Compute Online / Offline / Unavailable State
                // ESP32 sends Unix epoch SECONDS (from NTP), so we compare in seconds
                val nowSeconds = System.currentTimeMillis() / 1000L
                val diffS = if (status.lastHeartbeat > 0) nowSeconds - status.lastHeartbeat else Long.MAX_VALUE
                val diffSeconds = if (diffS == Long.MAX_VALUE) -1L else diffS

                val onlineState = when {
                    status.deviceStatus == "OFFLINE" || diffS > UNAVAILABLE_THRESHOLD_S -> DeviceOnlineState.OFFLINE
                    diffS > ONLINE_THRESHOLD_S -> DeviceOnlineState.UNAVAILABLE
                    else -> DeviceOnlineState.ONLINE
                }

                view?.updateDeviceOnlineStatus(onlineState, diffSeconds)
                view?.updateActualLedStatus(status.actualState, status.gpioPin)
                
                // Allow control only if device is known to be online/accessible
                view?.setControlsEnabled(onlineState != DeviceOnlineState.OFFLINE)
            },
            onError = { err ->
                view?.showLoading(false)
                view?.showErrorMessage("Status sync error: $err")
                view?.updateDeviceOnlineStatus(DeviceOnlineState.OFFLINE, -1)
            }
        )

        // 2. Listen to Requested Commands
        commandListener = repository.listenToCommand(
            deviceId = deviceId,
            onUpdate = { cmd ->
                view?.updateRequestedCommand(cmd.targetState)
            },
            onError = { err ->
                view?.showErrorMessage("Command sync error: $err")
            }
        )
    }

    override fun stopMonitoring() {
        statusListener?.remove()
        statusListener = null
        commandListener?.remove()
        commandListener = null
    }

    override fun onToggleLed(deviceId: String, desiredState: Boolean) {
        view?.showLoading(true)
        view?.updateRequestedCommand(desiredState)

        repository.sendLedCommand(deviceId, desiredState) { success, error ->
            view?.showLoading(false)
            if (success) {
                val stateText = if (desiredState) "ON" else "OFF"
                view?.showSuccessMessage("Command sent: Turn $stateText external LED")
            } else {
                view?.showErrorMessage("Failed to send command: ${error ?: "Unknown error"}")
            }
        }
    }
}
