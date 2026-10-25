package com.example.stoveaide.views

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.stoveaide.databinding.ActivityNotificationDetailBinding
import com.example.stoveaide.data.StoveRepository
import com.example.stoveaide.models.NotificationItem

class NotificationDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationDetailBinding

    companion object {
        const val EXTRA_NOTIFICATION = "extra_notification"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        binding.btnDelete.setOnClickListener {
            val notif = intent.getSerializableExtra(EXTRA_NOTIFICATION) as? NotificationItem
            if (notif != null && notif.id.isNotBlank()) {
                showDeleteConfirmationDialog {
                    deleteNotification(notif.id)
                }
            } else {
                Toast.makeText(this, "Unable to delete: invalid notification", Toast.LENGTH_SHORT).show()
            }
        }

        val notif = intent.getSerializableExtra(EXTRA_NOTIFICATION) as? NotificationItem
            ?: NotificationItem(
                dateLabel = "Today",
                timeLabel = "—",
                message = "No details available."
            )

        bindData(notif)
    }

    private fun showDeleteConfirmationDialog(onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Delete Notification")
            .setMessage("Are you sure you want to delete this notification? This action cannot be undone.")
            .setPositiveButton("Delete") { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteNotification(notifId: String) {
        StoveRepository.deleteNotification(notifId) { success ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "Notification deleted successfully", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Failed to delete notification", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun bindData(notif: NotificationItem) {
        // Use fullDate if stored, else derive from timestamp, else use dateLabel
        val displayDate = when {
            notif.fullDate.isNotBlank() -> notif.fullDate
            notif.dateLabel.equals("Today", ignoreCase = true) -> {
                val sdf = java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.ENGLISH)
                sdf.format(java.util.Date(notif.timestamp))
            }
            else -> notif.dateLabel
        }
        binding.tvDetailFullDate.text = displayDate
        binding.tvDetailTime.text = notif.timeLabel
        binding.tvDetailMessage.text = notif.message
    }
}
