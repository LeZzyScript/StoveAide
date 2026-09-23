package com.example.stoveaide.presenters

import com.example.stoveaide.views.ILedControlView

/**
 * MVP Presenter contract for ESP32 LED Control.
 */
interface ILedControlPresenter {
    fun attachView(view: ILedControlView)
    fun detachView()
    fun startMonitoring(deviceId: String)
    fun stopMonitoring()
    fun onToggleLed(deviceId: String, desiredState: Boolean)
    fun checkAuthentication()
}
