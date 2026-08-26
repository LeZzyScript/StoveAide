package com.example.stoveaide.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.stoveaide.data.FirestoreManager
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

        // Fetch User Profile from Firestore or Auth
        val uid = FirestoreManager.auth?.currentUser?.uid
        if (uid != null) {
            FirestoreManager.getUserProfile(uid) { user ->
                if (user != null) {
                    val firstName = user.fullName.split(" ").firstOrNull() ?: "John"
                    binding.tvWelcomeTitle.text = "Welcome, $firstName"
                    
                    // Initials for avatar circle badge
                    val parts = user.fullName.trim().split(" ")
                    val initials = if (parts.size >= 2) {
                        "${parts[0].firstOrNull() ?: ""}${parts[1].firstOrNull() ?: ""}"
                    } else {
                        user.fullName.take(2)
                    }.uppercase()
                    if (initials.isNotEmpty()) {
                        binding.tvProfileBadge.text = initials
                    }
                }
            }
        }

        // Listen to Firestore IoT Stove Data
        FirestoreManager.listenToStoveData("STOVE-PH-8842") { stove ->
            binding.stoveGaugeView.progress = (stove.activeMinutes.toFloat() / stove.maxAlertMinutes.toFloat()) * 100f
            binding.tvTimerCount.text = "${stove.activeMinutes} min"
            
            if (stove.lpgValveOpen) {
                binding.tvCard1Sub.text = if (stove.flameDetected) "Flame Active" else "Flame Off"
                binding.tvCard2Sub.text = "${stove.temperatureCelsius.toInt()}°C • Normal"
                binding.tvCard3Sub.text = "${stove.gasLevelPpm} PPM • Safe"
                binding.tvAlertTag.text = "LONGER THAN USUAL"
                binding.tvAlertDesc.text = "The stove has been on ${stove.activeMinutes} min with no check-in. You'll get an alert at ${stove.maxAlertMinutes} min if it's still active."
            } else {
                binding.tvCard1Sub.text = "Valve Shut"
                binding.tvAlertTag.text = "LPG CUTOFF ACTIVE"
                binding.tvAlertDesc.text = "Safety solenoid valve is CLOSED. Gas supply isolated."
            }
        }

        // Button Listeners
        binding.btnCheckIn.setOnClickListener {
            FirestoreManager.checkInStove("STOVE-PH-8842", currentMinutes = 0)
            Toast.makeText(context, "Timer reset! Stove monitor confirmed.", Toast.LENGTH_SHORT).show()
        }

        binding.btnShutOff.setOnClickListener {
            FirestoreManager.toggleLpgValve("STOVE-PH-8842", openState = false)
            Toast.makeText(context, "Emergency signal sent: LPG Valve Cut Off!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
