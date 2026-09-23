package com.example.stoveaide.views

import com.example.stoveaide.models.DeviceOnlineState

/**
 * MVP View contract for ESP32 LED Control.
 */
interface ILedControlView {
    fun showLoading(isLoading: Boolean)
    fun updateRequestedCommand(targetState: Boolean)
    fun updateActualLedStatus(actualState: Boolean, gpioPin: Int)
    fun updateDeviceOnlineStatus(state: DeviceOnlineState, lastSeenSecondsAgo: Long)
    fun showSuccessMessage(message: String)
    fun showErrorMessage(message: String)
    fun setControlsEnabled(enabled: Boolean)
    fun navigateToLogin()
}
