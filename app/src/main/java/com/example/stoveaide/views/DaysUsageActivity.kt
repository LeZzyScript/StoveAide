package com.example.stoveaide.views

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.stoveaide.R
import com.example.stoveaide.databinding.ActivityDaysUsageBinding
import com.example.stoveaide.data.StoveRepository
import com.example.stoveaide.models.StoveUsageLog

open class DaysUsageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDaysUsageBinding

    companion object {
        const val EXTRA_LOG = "extra_log"
        const val EXTRA_LOG_ID = "extra_log_id"
        const val EXTRA_DATE = "extra_date"
    }

    private var currentLogId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDaysUsageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnDelete.setOnClickListener {
            val log = intent.getSerializableExtra(EXTRA_LOG) as? StoveUsageLog
            val targetId = log?.id?.ifBlank { null } ?: currentLogId.ifBlank { null }
            if (!targetId.isNullOrBlank()) {
                showDeleteConfirmationDialog {
                    deleteUsageLog(targetId)
                }
            } else {
                Toast.makeText(this, "Unable to delete: invalid log", Toast.LENGTH_SHORT).show()
            }
        }

        val passedLog = intent.getSerializableExtra(EXTRA_LOG) as? StoveUsageLog
        val passedLogId = intent.getStringExtra(EXTRA_LOG_ID)
            ?: intent.getStringExtra("log_id")
            ?: intent.getStringExtra("logId")
        val passedDate = intent.getStringExtra(EXTRA_DATE)
            ?: intent.getStringExtra("date")

        if (passedLog != null) {
            currentLogId = passedLog.id
            bindData(passedLog)
        } else if (!passedLogId.isNullOrBlank()) {
            currentLogId = passedLogId
            // Fetch log from repository if available
            StoveRepository.listenToUsageLogs { logs ->
                val found = logs.firstOrNull { it.id == passedLogId }
                runOnUiThread {
                    if (found != null) {
                        bindData(found)
                    } else {
                        bindData(createFallbackLog(passedDate ?: "Today", passedLogId))
                    }
                }
            }
        } else if (!passedDate.isNullOrBlank()) {
            // Find by date
            StoveRepository.listenToUsageLogs { logs ->
                val found = logs.firstOrNull { 
                    it.dateLabel.contains(passedDate, ignoreCase = true) ||
                    it.dateShort.contains(passedDate, ignoreCase = true) 
                }
                runOnUiThread {
                    if (found != null) {
                        currentLogId = found.id
                        bindData(found)
                    } else {
                        bindData(createFallbackLog(passedDate, ""))
                    }
                }
            }
        } else {
            bindData(createFallbackLog("September 17", ""))
        }
    }

    private fun createFallbackLog(date: String, logId: String): StoveUsageLog {
        return StoveUsageLog(
            id = logId,
            dateShort = date,
            dateLabel = date,
            durationMinutes = 18,
            startTime = "1:12 PM",
            endTime = "1:30 PM",
            summarySentence = "Your stove burner was used for 18 minutes from 1:12 PM to 1:30 PM at moderately safe levels",
            burnerLevel = 5.5f,
            burnerTemperature = 5.5f,
            gasTemperature = 4.5f,
            flameBehavior = 3.5f,
            stoveId = StoveRepository.DEFAULT_STOVE_ID
        )
    }

    private fun showDeleteConfirmationDialog(onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Delete Entry")
            .setMessage("Are you sure you want to delete this entry? This action cannot be undone.")
            .setPositiveButton("Delete") { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun deleteUsageLog(logId: String) {
        StoveRepository.deleteUsageLog(logId) { success ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "Log deleted successfully", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Failed to delete log", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun bindData(log: StoveUsageLog) {
        binding.tvCardDate.text = log.dateShort.ifBlank { log.dateLabel.substringBefore(",") }
        
        val defaultSummary = if (log.status.equals("Dangerous", ignoreCase = true)) {
            "Your stove was turned on at ${log.startTime} and a warning was sent at ${log.endTime}."
        } else {
            "Your stove burner was used for ${log.durationMinutes} minutes from ${log.startTime} to ${log.endTime} at ${log.status.lowercase()} levels"
        }
        binding.tvSummarySentence.text = if (log.summarySentence.isNotBlank()) {
            log.summarySentence
        } else {
            defaultSummary
        }

        val cyanColor = ContextCompat.getColor(this, R.color.bar_cyan)
        val tealColor = ContextCompat.getColor(this, R.color.bar_teal)
        val greenColor = ContextCompat.getColor(this, R.color.bar_green)

        binding.gaugeBurnerLevel.setProgress(log.burnerLevel, cyanColor)
        binding.gaugeBurnerTemp.setProgress(log.burnerTemperature, cyanColor)
        binding.gaugeGasTemp.setProgress(log.gasTemperature, tealColor)
        binding.gaugeFlameBehavior.setProgress(log.flameBehavior, greenColor)
    }
}
