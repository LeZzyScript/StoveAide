package com.example.stoveaide.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.stoveaide.views.DaysUsageActivity
import com.example.stoveaide.R
import com.example.stoveaide.data.FirestoreManager
import com.example.stoveaide.data.StoveRepository
import com.example.stoveaide.databinding.FragmentHistoryBinding
import com.example.stoveaide.databinding.ItemStoveCalendarLogBinding
import com.example.stoveaide.models.StoveUsageLog

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadCalendarLogs()
    }

    private fun loadCalendarLogs() {
        val user = FirestoreManager.auth?.currentUser
        if (user == null) {
            listenToUsageLogs(StoveRepository.DEFAULT_STOVE_ID)
            return
        }

        FirestoreManager.getUserProfile(user.uid) { profile ->
            listenToUsageLogs(profile?.stoveDeviceId?.takeIf { it.isNotBlank() }
                ?: StoveRepository.DEFAULT_STOVE_ID)
        }
    }

    private fun listenToUsageLogs(stoveId: String) {
        StoveRepository.listenToUsageLogs(stoveId) { logs ->
            if (_binding == null) return@listenToUsageLogs

            binding.containerTodayLogs.removeAllViews()
            binding.containerLastWeekLogs.removeAllViews()

            val todayLogs = logs.filter { it.section.equals("Today", ignoreCase = true) }
            val pastLogs = logs.filter { !it.section.equals("Today", ignoreCase = true) }

            if (logs.isEmpty()) {
                binding.layoutEmptyHistory.visibility = View.VISIBLE
                binding.tvSectionToday.visibility = View.GONE
                binding.tvSectionLastWeek.visibility = View.GONE
                binding.dividerSections.visibility = View.GONE
            } else {
                binding.layoutEmptyHistory.visibility = View.GONE

                // Populate Today Section
                if (todayLogs.isEmpty()) {
                    binding.tvSectionToday.visibility = View.GONE
                } else {
                    binding.tvSectionToday.visibility = View.VISIBLE
                    todayLogs.forEach { log ->
                        binding.containerTodayLogs.addView(createLogCardView(log))
                    }
                }

                // Populate Last Week / Past Section
                if (pastLogs.isEmpty()) {
                    binding.tvSectionLastWeek.visibility = View.GONE
                    binding.dividerSections.visibility = View.GONE
                } else {
                    binding.tvSectionLastWeek.visibility = View.VISIBLE
                    binding.dividerSections.visibility = View.VISIBLE
                    pastLogs.forEach { log ->
                        binding.containerLastWeekLogs.addView(createLogCardView(log))
                    }
                }
            }
        }
    }

    private fun createLogCardView(log: StoveUsageLog): View {
        val itemBinding = ItemStoveCalendarLogBinding.inflate(layoutInflater, null, false)

        itemBinding.tvDate.text = log.dateLabel
        itemBinding.tvStatus.text = log.status

        val statusColor = when (log.status.lowercase()) {
            "safe" -> ContextCompat.getColor(requireContext(), R.color.status_safe_green)
            "dangerous" -> ContextCompat.getColor(requireContext(), R.color.status_dangerous_red)
            else -> ContextCompat.getColor(requireContext(), R.color.status_normal_blue)
        }
        itemBinding.tvStatus.setTextColor(statusColor)

        itemBinding.tvUsageTime.text =
            "Usage Time: ${log.startTime} - ${log.endTime} (${log.durationMinutes} minutes)"

        itemBinding.root.setOnClickListener {
            val intent = Intent(requireContext(), DaysUsageActivity::class.java).apply {
                putExtra(DaysUsageActivity.EXTRA_LOG, log)
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
