package com.example.stoveaide

import android.os.Bundle
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.stoveaide.databinding.ActivityMainBinding
import com.example.stoveaide.fragments.DashboardFragment
import com.example.stoveaide.fragments.HistoryFragment
import com.example.stoveaide.fragments.ProfileFragment
import com.example.stoveaide.fragments.TipsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.fragmentContainer.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        // Set default fragment
        loadFragment(DashboardFragment(), 0)

        // Setup bottom tab listeners
        binding.tabHome.setOnClickListener { loadFragment(DashboardFragment(), 0) }
        binding.tabHistory.setOnClickListener { loadFragment(HistoryFragment(), 1) }
        binding.tabTips.setOnClickListener { loadFragment(TipsFragment(), 2) }
        binding.tabProfile.setOnClickListener { loadFragment(ProfileFragment(), 3) }
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
}