package com.example.stoveaide.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.stoveaide.data.StoveRepository
import com.example.stoveaide.databinding.FragmentTipsBinding
import com.example.stoveaide.databinding.ItemNotificationCardBinding
import com.example.stoveaide.models.NotificationItem

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
        StoveRepository.listenToNotifications { items ->
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
        return itemBinding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
