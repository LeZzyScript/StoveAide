package com.example.stoveaide.fragments

import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.stoveaide.data.FirestoreManager
import com.example.stoveaide.data.UsageLogger
import com.example.stoveaide.databinding.FragmentDashboardBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set dynamic date
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.ENGLISH)
        binding.tvDate.text = dateFormat.format(Date()).uppercase()

        // Top-right Profile Badge click listener -> navigates to Profile tab
        binding.tvProfileBadge.setOnClickListener {
            (activity as? com.example.stoveaide.views.MainActivity)?.navigateToProfileTab()
        }

        // Fetch User Profile seamlessly across Auth, Firestore, and LocalAuth
        loadUserProfile()

        // Listen to Realtime & Firestore IoT Stove Data
        FirestoreManager.listenToStoveData("STOVE-PH-8842") { stove ->
            // Update usage logger
            UsageLogger.processStoveData(stove)
            if (_binding == null) return@listenToStoveData
            binding.stoveGaugeView.progress = (stove.activeMinutes.toFloat() / stove.maxAlertMinutes.toFloat()) * 100f

            // 1. Flame Guard card
            binding.tvCard1Sub.text = when {
                stove.flameDetected -> "Flame Active"
                stove.lpgValveOpen -> "Flame Off"
                stove.temperatureCelsius > 0f -> "Standby"
                else -> "Standby"
            }

            // 2. Temp Guard card
            val tempQuality = when {
                stove.temperatureCelsius >= 250f -> "High Spike"
                stove.temperatureCelsius >= 120f -> "Cooking"
                stove.temperatureCelsius > 0f -> "Normal"
                else -> "Standby"
            }
            binding.tvCard2Sub.text = "${stove.temperatureCelsius.toInt()}°C • $tempQuality"

            // 3. LPG Detector card
            val gasQuality = when {
                stove.gasLevelPpm >= 400 -> "Leak Detected"
                stove.gasLevelPpm > 150 -> "Normal"
                else -> "Safe"
            }
            binding.tvCard3Sub.text = "${stove.gasLevelPpm} PPM • $gasQuality"

            // Status banner and alert description
            when {
                stove.gasLevelPpm >= 400 -> {
                    binding.tvAlertTag.text = "DANGER: GAS LEAK"
                    binding.tvAlertDesc.text = "High gas levels detected (${stove.gasLevelPpm} PPM). Please turn off your stove and check ventilation."
                }
                stove.temperatureCelsius >= 250f -> {
                    binding.tvAlertTag.text = "ATTENTION NEEDED"
                    binding.tvAlertDesc.text = "High temperature spike detected (${stove.temperatureCelsius.toInt()}°C). Please check your burner."
                }
                stove.lpgValveOpen && !stove.flameDetected && stove.activeMinutes > 0 -> {
                    binding.tvAlertTag.text = "FLAMEOUT DETECTED"
                    binding.tvAlertDesc.text = "Gas valve is open but no flame is detected. Gas may be escaping."
                }
                stove.lpgValveOpen && stove.activeMinutes > 0 -> {
                    binding.tvAlertTag.text = if (stove.activeMinutes >= 14) "ATTENTION NEEDED" else "STOVE ACTIVE"
                    binding.tvAlertDesc.text = "The stove has been on ${stove.activeMinutes} min. You'll get an alert at ${stove.maxAlertMinutes} min if unattended."
                }
                stove.lpgValveOpen -> {
                    binding.tvAlertTag.text = "STOVE READY"
                    binding.tvAlertDesc.text = "Gas valve is OPEN and ready for cooking."
                }
                stove.temperatureCelsius > 0f || stove.flameDetected -> {
                    binding.tvAlertTag.text = "STOVE COOLING"
                    binding.tvAlertDesc.text = "Safety solenoid valve is CLOSED. Burner is cooling down."
                }
                else -> {
                    binding.tvAlertTag.text = "STOVE STANDBY"
                    binding.tvAlertDesc.text = "No stove activity recorded. Turn on your stove or use the dashboard simulator to start monitoring."
                }
            }
        }

        FirestoreManager.listenToRealtimeMinutes(onUpdate = { minutes ->
            if (_binding == null) return@listenToRealtimeMinutes
            binding.tvTimerCount.setText(minutes.toString())
            binding.tvTimerCount.setSelection(binding.tvTimerCount.text.length)
            binding.tvTimerLabel.text = "Cooking duration"
        })

        // Button Listeners
        binding.btnCheckIn.setOnClickListener {
            FirestoreManager.checkInStove("STOVE-PH-8842", currentMinutes = 0)
            Toast.makeText(context, "Timer reset! Stove monitor confirmed.", Toast.LENGTH_SHORT).show()
        }

        binding.btnShutOff.setOnClickListener {
            FirestoreManager.toggleLpgValve("STOVE-PH-8842", openState = false)
            Toast.makeText(context, "Emergency signal sent: LPG Valve Cut Off!", Toast.LENGTH_LONG).show()
        }

        binding.cardEsp32Control.setOnClickListener {
            val intent = android.content.Intent(requireContext(), com.example.stoveaide.views.LedControlActivity::class.java)
            startActivity(intent)
        }

        binding.tvTimerCount.setOnEditorActionListener { _, actionId, event ->
            val submitted = actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER)
            if (submitted) {
                syncTimerInput()
            }
            submitted
        }

        binding.tvTimerCount.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                syncTimerInput()
            }
        }
    }

    private fun syncTimerInput() {
        val minutes = binding.tvTimerCount.text.toString().trim().toIntOrNull()
        if (minutes == null || minutes < 0) {
            Toast.makeText(context, "Please enter a valid number of minutes.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.tvTimerLabel.text = "Syncing..."
        FirestoreManager.updateRealtimeMinutes(minutes) { success ->
            if (_binding == null) return@updateRealtimeMinutes
            if (success) {
                binding.tvTimerLabel.text = "Cooking duration"
                Toast.makeText(context, "Timer set to $minutes min.", Toast.LENGTH_SHORT).show()
            } else {
                binding.tvTimerLabel.text = "Sync failed"
                Toast.makeText(context, "Failed to update timer.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadUserProfile()
    }

    private fun loadUserProfile() {
        val ctx = context ?: return
        FirestoreManager.loadCurrentUserProfile(ctx) { user ->
            if (_binding == null) return@loadCurrentUserProfile
            val firstName = when {
                user.firstName.isNotBlank() -> user.firstName
                user.fullName.isNotBlank() -> user.fullName.split(" ").firstOrNull() ?: "User"
                else -> "User"
            }
            binding.tvWelcomeTitle.text = "Welcome, $firstName"

            val initials = when {
                user.firstName.isNotBlank() && user.lastName.isNotBlank() -> {
                    "${user.firstName.first()}${user.lastName.first()}".uppercase()
                }
                user.fullName.isNotBlank() -> {
                    val parts = user.fullName.trim().split(" ")
                    if (parts.size >= 2) {
                        "${parts[0].firstOrNull() ?: ""}${parts[1].firstOrNull() ?: ""}".uppercase()
                    } else {
                        user.fullName.take(2).uppercase()
                    }
                }
                user.firstName.isNotBlank() -> user.firstName.take(2).uppercase()
                else -> "U"
            }
            if (initials.isNotEmpty()) {
                binding.tvProfileBadge.text = initials
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
