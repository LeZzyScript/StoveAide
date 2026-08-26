package com.example.stoveaide.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.stoveaide.WelcomeActivity
import com.example.stoveaide.data.FirestoreManager
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
            binding.tvUserEmail.text = currentUser.email ?: "juan@example.com"
            FirestoreManager.getUserProfile(currentUser.uid) { profile ->
                if (profile != null) {
                    binding.tvUserName.text = profile.fullName
                    binding.tvStoveDeviceId.text = profile.stoveDeviceId
                    
                    val initials = profile.fullName.trim().split(" ")
                        .mapNotNull { it.firstOrNull()?.toString() }
                        .take(2).joinToString("").uppercase()
                    if (initials.isNotEmpty()) {
                        binding.tvProfileAvatar.text = initials
                    }
                }
            }
        }

        binding.btnLogout.setOnClickListener {
            FirestoreManager.auth?.signOut()
            Toast.makeText(context, "Logged out successfully", Toast.LENGTH_SHORT).show()
            val intent = Intent(requireActivity(), WelcomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
