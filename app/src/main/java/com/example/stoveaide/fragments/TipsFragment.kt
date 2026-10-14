package com.example.stoveaide.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.stoveaide.data.FirestoreManager
import com.example.stoveaide.data.StoveRepository
import com.example.stoveaide.databinding.FragmentTipsBinding
import com.example.stoveaide.databinding.ItemNotificationCardBinding
import com.example.stoveaide.models.NotificationItem
import com.example.stoveaide.views.NotificationDetailActivity

class TipsFragment : Fragment() {

    private var _binding: FragmentTipsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTipsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadNotifications()
    }

    private fun loadNotifications() {
        val user = FirestoreManager.auth?.currentUser
        if (user == null) {
            listenToNotifications(StoveRepository.DEFAULT_STOVE_ID)
            return
        }

        FirestoreManager.getUserProfile(user.uid) { profile ->
            val stoveId = profile?.stoveDeviceId?.takeIf { it.isNotBlank() }
                ?: StoveRepository.DEFAULT_STOVE_ID
            listenToNotifications(stoveId)
        }
    }

    private fun listenToNotifications(stoveId: String) {
        StoveRepository.listenToNotifications(stoveId) { items ->
            if (_binding == null) return@listenToNotifications

            binding.containerNotifications.removeAllViews()
            if (items.isEmpty()) {
                binding.layoutEmptyNotifications.visibility = View.VISIBLE
            } else {
                binding.layoutEmptyNotifications.visibility = View.GONE
                items.forEach { notif ->
                    val cardView = createNotificationCard(notif)
                    binding.containerNotifications.addView(cardView)
                }
            }
        }
    }

    private fun createNotificationCard(notif: NotificationItem): View {
        val itemBinding = ItemNotificationCardBinding.inflate(layoutInflater, null, false)
        itemBinding.tvNotifDate.text = notif.dateLabel
        itemBinding.tvNotifTime.text = notif.timeLabel
        itemBinding.tvNotifMessage.text = notif.message

        // Make the card clickable — opens detail screen
        itemBinding.root.isClickable = true
        itemBinding.root.isFocusable = true
        itemBinding.root.setOnClickListener {
            val intent = Intent(requireContext(), NotificationDetailActivity::class.java).apply {
                putExtra(NotificationDetailActivity.EXTRA_NOTIFICATION, notif)
            }
            startActivity(intent)
        }

        return itemBinding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
