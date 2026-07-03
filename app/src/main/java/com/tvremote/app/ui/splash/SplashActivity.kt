package com.tvremote.app.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.tvremote.app.databinding.ActivitySplashBinding
import com.tvremote.app.ui.common.BaseActivity
import com.tvremote.app.ui.language.LanguageActivity
import com.tvremote.app.ui.main.MainActivity
import com.tvremote.app.data.session.AppPreferences
import com.tvremote.app.util.ThemeHelper

@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity() {
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        val binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        handler.postDelayed({ navigateNext() }, SPLASH_DELAY_MS)
    }

    private fun navigateNext() {
        if (isFinishing) return
        val prefs = AppPreferences(this)
        val intent = if (prefs.isOnboardingComplete) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, LanguageActivity::class.java)
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        private const val SPLASH_DELAY_MS = 2200L
    }
}
