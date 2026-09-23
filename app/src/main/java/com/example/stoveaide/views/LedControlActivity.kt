package com.example.stoveaide.views

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.stoveaide.LoginActivity
import com.example.stoveaide.R
import com.example.stoveaide.data.repository.LedRepositoryImpl
import com.example.stoveaide.databinding.ActivityLedControlBinding
import com.example.stoveaide.models.DeviceOnlineState
import com.example.stoveaide.presenters.ILedControlPresenter
import com.example.stoveaide.presenters.LedControlPresenter

/**
 * MVP View implementation for ESP32 External LED Control.
 * Receives user actions, forwards them to the Presenter, and updates UI based on Presenter instructions.
 */
class LedControlActivity : AppCompatActivity(), ILedControlView {

    private lateinit var binding: ActivityLedControlBinding
    private lateinit var presenter: ILedControlPresenter
    private val deviceId = "STOVE-PH-8842"

    private var currentRequestedState: Boolean? = null
    private var currentActualState: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLedControlBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize MVP Presenter with Model / Repository
        presenter = LedControlPresenter(LedRepositoryImpl())
        presenter.attachView(this)
        presenter.checkAuthentication()

        setupListeners()
        presenter.startMonitoring(deviceId)
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnTurnOn.setOnClickListener {
            presenter.onToggleLed(deviceId, desiredState = true)
        }

        binding.btnTurnOff.setOnClickListener {
            presenter.onToggleLed(deviceId, desiredState = false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.detachView()
    }

    // region MVP View Implementations

    override fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun updateRequestedCommand(targetState: Boolean) {
        currentRequestedState = targetState
        binding.tvRequestedCommand.text = if (targetState) "Target: ON (HIGH)" else "Target: OFF (LOW)"
        checkSyncState()
    }

    override fun updateActualLedStatus(actualState: Boolean, gpioPin: Int) {
        currentActualState = actualState

        if (actualState) {
            // LED is physically ON
            binding.ivBulbIcon.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.led_active_green)
            )
            binding.tvActualStateValue.text = "LED is ON (3.3V)"
            binding.tvActualStateValue.setTextColor(
                ContextCompat.getColor(this, R.color.status_online_text)
            )
            binding.tvConfirmedStatus.text = "Actual: ON (GPIO $gpioPin)"
        } else {
            // LED is physically OFF
            binding.ivBulbIcon.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.led_inactive_gray)
            )
            binding.tvActualStateValue.text = "LED is OFF (0V)"
            binding.tvActualStateValue.setTextColor(
                ContextCompat.getColor(this, R.color.text_primary)
            )
            binding.tvConfirmedStatus.text = "Actual: OFF (GPIO $gpioPin)"
        }

        checkSyncState()
    }

    override fun updateDeviceOnlineStatus(state: DeviceOnlineState, lastSeenSecondsAgo: Long) {
        val lastSeenText = if (lastSeenSecondsAgo >= 0) "${lastSeenSecondsAgo}s ago" else "No signal"

        when (state) {
            DeviceOnlineState.ONLINE -> {
                binding.tvConnectionStatus.text = "ONLINE"
                binding.tvConnectionStatus.setBackgroundResource(R.drawable.bg_status_badge_online)
                binding.tvConnectionStatus.setTextColor(
                    ContextCompat.getColor(this, R.color.status_online_text)
                )
                binding.tvHeartbeatInfo.text = "Last Heartbeat: $lastSeenText • Pin: GPIO 23"
            }
            DeviceOnlineState.UNAVAILABLE -> {
                binding.tvConnectionStatus.text = "UNAVAILABLE"
                binding.tvConnectionStatus.setBackgroundResource(R.drawable.bg_status_badge_warning)
                binding.tvConnectionStatus.setTextColor(
                    ContextCompat.getColor(this, R.color.status_warn_text)
                )
                binding.tvHeartbeatInfo.text = "Delayed Heartbeat: $lastSeenText • Waiting for ESP32"
            }
            DeviceOnlineState.OFFLINE -> {
                binding.tvConnectionStatus.text = "OFFLINE"
                binding.tvConnectionStatus.setBackgroundResource(R.drawable.bg_status_badge_offline)
                binding.tvConnectionStatus.setTextColor(
                    ContextCompat.getColor(this, R.color.status_offline_text)
                )
                binding.tvHeartbeatInfo.text = "Disconnected from Wi-Fi ($lastSeenText) • ESP32 Unreachable"
            }
        }
    }

    override fun setControlsEnabled(enabled: Boolean) {
        binding.btnTurnOn.isEnabled = enabled
        binding.btnTurnOff.isEnabled = enabled
        binding.btnTurnOn.alpha = if (enabled) 1.0f else 0.5f
        binding.btnTurnOff.alpha = if (enabled) 1.0f else 0.5f
    }

    override fun showSuccessMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showErrorMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun navigateToLogin() {
        Toast.makeText(this, "Please log in to control devices.", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // endregion

    private fun checkSyncState() {
        val req = currentRequestedState
        val act = currentActualState

        if (req != null && act != null) {
            if (req == act) {
                binding.tvSyncStatus.text = "✓ State Synchronized & Verified"
                binding.tvSyncStatus.setTextColor(
                    ContextCompat.getColor(this, R.color.rule_valid)
                )
            } else {
                binding.tvSyncStatus.text = "⏳ Syncing command with ESP32..."
                binding.tvSyncStatus.setTextColor(
                    ContextCompat.getColor(this, R.color.brand_terracotta)
                )
            }
        }
    }
}
