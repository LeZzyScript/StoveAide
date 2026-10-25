package com.example.stoveaide.views

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.stoveaide.R
import com.example.stoveaide.data.FirestoreManager
import com.example.stoveaide.databinding.ActivityMainBinding
import com.example.stoveaide.fragments.DashboardFragment
import com.example.stoveaide.fragments.HistoryFragment
import com.example.stoveaide.fragments.ProfileFragment
import com.example.stoveaide.fragments.TipsFragment
import com.example.stoveaide.StoveNotificationHelper
import com.example.stoveaide.data.StoveRepository
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.SetOptions

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.fragmentContainer.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        // Create stove_alerts NotificationChannel (required for Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("stove_alerts", "Stove Alerts", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        // Initialize local notification channel & request permission
        StoveNotificationHelper.initNotificationChannel(this)
        requestNotificationPermission()

        // Sync FCM token to current user profile in Firestore
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                val uid = FirestoreManager.auth?.currentUser?.uid
                if (!token.isNullOrBlank() && !uid.isNullOrBlank()) {
                    FirestoreManager.firestore?.collection("users")?.document(uid)
                        ?.set(mapOf("fcmToken" to token), SetOptions.merge())
                }
            }
        }
        val user = FirestoreManager.auth?.currentUser
        if (user != null) {
            FirestoreManager.getUserProfile(user.uid) { profile ->
                val stoveId = profile?.stoveDeviceId?.takeIf { it.isNotBlank() }
                    ?: StoveRepository.DEFAULT_STOVE_ID
                StoveNotificationHelper.startRealtimeSpikeMonitor(applicationContext, stoveId)
            }
        } else {
            StoveNotificationHelper.startRealtimeSpikeMonitor(
                applicationContext,
                StoveRepository.DEFAULT_STOVE_ID
            )
        }

        // Set default fragment
        loadFragment(DashboardFragment(), 0)

        // Setup bottom tab listeners
        binding.tabHome.setOnClickListener { loadFragment(DashboardFragment(), 0) }
        binding.tabHistory.setOnClickListener { loadFragment(HistoryFragment(), 1) }
        binding.tabTips.setOnClickListener { loadFragment(TipsFragment(), 2) }
        binding.tabProfile.setOnClickListener { loadFragment(ProfileFragment(), 3) }
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    fun navigateToProfileTab() {
        loadFragment(ProfileFragment(), 3)
    }

    private fun loadFragment(fragment: Fragment, tabIndex: Int) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()

        updateNavSelection(tabIndex)
    }

    private fun updateNavSelection(selectedIndex: Int) {
        val navItems = listOf(
            binding.ivNavHome to binding.tabHome,
            binding.ivNavHistory to binding.tabHistory,
            binding.ivNavTips to binding.tabTips,
            binding.ivNavProfile to binding.tabProfile
        )

        navItems.forEachIndexed { index, (imageView, frameLayout) ->
            if (index == selectedIndex) {
                frameLayout.setBackgroundResource(R.drawable.bg_nav_active_pill)
                imageView.setColorFilter(getColor(R.color.brand_terracotta))
            } else {
                frameLayout.background = null
                imageView.setColorFilter(getColor(R.color.text_muted))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        StoveNotificationHelper.onAppForegrounded()
    }

    override fun onPause() {
        super.onPause()
        StoveNotificationHelper.onAppBackgrounded()
    }
}
