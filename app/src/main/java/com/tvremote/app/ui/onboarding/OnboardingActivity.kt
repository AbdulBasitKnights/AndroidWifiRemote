package com.tvremote.app.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.tvremote.app.R
import com.tvremote.app.data.session.AppPreferences
import com.tvremote.app.databinding.ActivityOnboardingBinding
import com.tvremote.app.ui.common.BaseActivity
import com.tvremote.app.ui.main.MainActivity
import com.tvremote.app.util.ThemeHelper

class OnboardingActivity : BaseActivity() {
    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var pager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pager = binding.onboardingPager
        pager.adapter = OnboardingPagerAdapter()
        updateDots(0)

        binding.skipButton.setOnClickListener { finishOnboarding() }
        binding.nextButton.setOnClickListener {
            if (pager.currentItem < 2) {
                pager.currentItem += 1
            } else {
                finishOnboarding()
            }
        }

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                binding.nextButton.text = if (position == 2) {
                    getString(R.string.onboarding_get_started)
                } else {
                    getString(R.string.onboarding_next)
                }
            }
        })
    }

    private fun updateDots(position: Int) {
        binding.dot1.alpha = if (position == 0) 1f else 0.3f
        binding.dot2.alpha = if (position == 1) 1f else 0.3f
        binding.dot3.alpha = if (position == 2) 1f else 0.3f
    }

    private fun finishOnboarding() {
        AppPreferences(this).isOnboardingComplete = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private inner class OnboardingPagerAdapter : RecyclerView.Adapter<OnboardingPagerAdapter.PageHolder>() {
        private val pages = listOf(
            Triple(R.drawable.img_dpad_cross, R.string.onboarding_title_1, R.string.onboarding_desc_1),
            Triple(R.drawable.ic_onboarding_cast, R.string.onboarding_title_2, R.string.onboarding_desc_2),
            Triple(R.drawable.ic_onboarding_connect, R.string.onboarding_title_3, R.string.onboarding_desc_3),
        )

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_onboarding_page, parent, false)
            return PageHolder(view)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            holder.bind(pages[position])
        }

        override fun getItemCount() = pages.size

        inner class PageHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val image: ImageView = view.findViewById(R.id.pageImage)
            private val title: TextView = view.findViewById(R.id.pageTitle)
            private val desc: TextView = view.findViewById(R.id.pageDesc)

            fun bind(data: Triple<Int, Int, Int>) {
                image.setImageResource(data.first)
                title.setText(data.second)
                desc.setText(data.third)
            }
        }
    }
}
