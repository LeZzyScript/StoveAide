package com.example.stoveaide.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.stoveaide.views.WelcomeActivity
import com.example.stoveaide.data.FirestoreManager
import com.example.stoveaide.data.LocalAuthManager
import com.example.stoveaide.databinding.FragmentProfileBinding

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentUser = FirestoreManager.auth?.currentUser
        if (currentUser != null) {
            binding.tvUserEmail.text = currentUser.email ?: ""
            FirestoreManager.getUserProfile(currentUser.uid) { profile ->
                if (_binding == null) return@getUserProfile
                if (profile != null) {
                    binding.tvUserName.text = profile.getDisplayName()
                    binding.tvStoveDeviceId.text = profile.stoveDeviceId
                    
                    val initials = when {
                        profile.firstName.isNotBlank() && profile.lastName.isNotBlank() -> {
                            "${profile.firstName.first()}${profile.lastName.first()}".uppercase()
                        }
                        profile.fullName.isNotBlank() -> {
                            profile.fullName.trim().split(" ")
                                .mapNotNull { it.firstOrNull()?.toString() }
                                .take(2).joinToString("").uppercase()
                        }
                        else -> "U"
                    }
                    if (initials.isNotEmpty()) {
                        binding.tvProfileAvatar.text = initials
                    }
                }
            }
        }

        binding.btnLogout.setOnClickListener {
            val appCtx = context?.applicationContext
            try {
                // Stop Firestore listener BEFORE signing out to prevent listener crashes
                com.example.stoveaide.utils.StoveNotificationHelper.stopMonitoring()
                FirestoreManager.auth?.signOut()
                if (appCtx != null) {
                    LocalAuthManager(appCtx).signOut()
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileFragment", "Error signing out", e)
            }
            Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show()
            val intent = Intent(requireActivity(), WelcomeActivity::class.java).apply {
                putExtra("IS_LOGOUT", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            // FLAG_ACTIVITY_CLEAR_TASK already destroys this activity — no need to call finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
