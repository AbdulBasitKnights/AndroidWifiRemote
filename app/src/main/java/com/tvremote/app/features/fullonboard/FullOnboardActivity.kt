package com.tvremote.app.features.fullonboard

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.tvremote.app.R
import com.tvremote.app.data.FlowPreferences
import com.tvremote.app.data.IS_ONBOARD
import com.tvremote.app.databinding.ActivityFullOnboardBinding
import com.tvremote.app.features.fullonboard.widget.BeforeAfterCompareView
import com.tvremote.app.ui.main.MainActivity
import com.tvremote.app.util.FirebaseLogUtils
import com.tvremote.app.util.hideNavigationBar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FullOnboardActivity : AppCompatActivity(), FullOnboardHost {

    private lateinit var binding: ActivityFullOnboardBinding
    private val pagerAdapter by lazy { FullOnboardPagerAdapter(this) }

    private var currentPage = 0
    private var segmentProgress = 0f
    private var isTimerPaused = false
    private var segmentJob: Job? = null
    private var isProgrammaticPageChange = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false

        binding = ActivityFullOnboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideNavigationBar()

        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.offscreenPageLimit = 1
        binding.viewPager.registerOnPageChangeCallback(pageChangeCallback)

        binding.btnBack.setOnClickListener { goPrevious() }
        binding.btnContinue.setOnClickListener { goNext(manual = true) }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (currentPage > 0) goPrevious() else finishOnboarding()
                }
            },
        )

        bindPageUi(currentPage)
        startSegmentTimer()
        binding.viewPager.post { playCompareHintForPage(currentPage) }
        FirebaseLogUtils.logEvent("full_onboard_view", "page_1")
    }

    override fun onCompareTouchChanged(isTouching: Boolean) {
        isTimerPaused = isTouching
    }

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            val wasSwipe = !isProgrammaticPageChange && position != currentPage
            stopCompareHintForPage(currentPage)
            currentPage = position
            segmentProgress = 0f
            bindPageUi(position)
            updateProgressUi()
            restartSegmentTimer()
            playCompareHintForPage(position)
            if (wasSwipe) FirebaseLogUtils.logEvent("full_onboard_swipe", "page_${position + 1}")
            isProgrammaticPageChange = false
        }
    }

    private fun playCompareHintForPage(page: Int) {
        if (FullOnboardContent.pages.getOrNull(page) !is FullOnboardPage.Compare) return
        binding.viewPager.post { findCompareViewOnPage(page)?.startHintAnimation() }
    }

    private fun stopCompareHintForPage(page: Int) {
        binding.viewPager.post { findCompareViewOnPage(page)?.stopHintAnimation() }
    }

    private fun findCompareViewOnPage(page: Int): BeforeAfterCompareView? {
        val recyclerView = binding.viewPager.getChildAt(0) as? RecyclerView ?: return null
        val holder = recyclerView.findViewHolderForAdapterPosition(page) ?: return null
        return holder.itemView.findViewById(R.id.compareView)
    }

    private fun startSegmentTimer() {
        segmentJob?.cancel()
        segmentJob = lifecycleScope.launch {
            var elapsed = 0L
            val duration = FullOnboardContent.SEGMENT_DURATION_MS
            while (isActive && elapsed < duration) {
                if (!isTimerPaused) {
                    delay(TIMER_TICK_MS)
                    elapsed += TIMER_TICK_MS
                    segmentProgress = elapsed.toFloat() / duration.toFloat()
                    updateProgressUi()
                } else {
                    delay(TIMER_TICK_MS)
                }
            }
            if (isActive) {
                segmentProgress = 1f
                updateProgressUi()
                if (currentPage < FullOnboardContent.PAGE_COUNT - 1) goNext(manual = false)
            }
        }
    }

    private fun restartSegmentTimer() {
        segmentProgress = 0f
        updateProgressUi()
        startSegmentTimer()
    }

    override fun onPause() {
        super.onPause()
        isTimerPaused = true
    }

    private fun updateProgressUi() {
        binding.segmentedProgress.currentPage = currentPage
        binding.segmentedProgress.segmentProgress = segmentProgress
    }

    private fun bindPageUi(page: Int) {
        val item = FullOnboardContent.pages.getOrNull(page) ?: return
        binding.tvTitle.setText(item.titleRes)
        binding.tvDescription.setText(item.descriptionRes)
        if (page == 0) {
            binding.btnBack.setBackgroundResource(R.drawable.disable_back)
            binding.btnBack.isEnabled = false
        } else {
            binding.btnBack.isEnabled = true
            binding.btnBack.setBackgroundResource(R.drawable.enable_back)
        }
        binding.btnContinue.setText(R.string.full_onboard_continue)
    }

    private fun goNext(manual: Boolean) {
        if (manual) FirebaseLogUtils.logEvent("full_onboard_continue", "page_${currentPage + 1}")
        if (currentPage >= FullOnboardContent.PAGE_COUNT - 1) {
            finishOnboarding()
            return
        }
        isProgrammaticPageChange = true
        binding.viewPager.setCurrentItem(currentPage + 1, true)
    }

    private fun goPrevious() {
        if (currentPage <= 0) return
        FirebaseLogUtils.logEvent("full_onboard_back", "page_${currentPage + 1}")
        isProgrammaticPageChange = true
        binding.viewPager.setCurrentItem(currentPage - 1, true)
    }

    private fun finishOnboarding() {
        segmentJob?.cancel()
        FlowPreferences.writeDataStoreValue(IS_ONBOARD, true)
        FirebaseLogUtils.logEvent("full_onboard_complete", "")
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
        )
        finish()
    }

    override fun onResume() {
        super.onResume()
        isTimerPaused = false
        hideNavigationBar()
    }

    override fun onDestroy() {
        binding.viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        segmentJob?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TIMER_TICK_MS = 16L
    }
}
