package com.tvremote.app.ui.language

import android.content.Intent
import android.os.Bundle
import com.tvremote.app.R
import com.tvremote.app.data.session.AppPreferences
import com.tvremote.app.databinding.ActivityLanguageBinding
import com.tvremote.app.ui.common.BaseActivity
import com.tvremote.app.ui.onboarding.OnboardingActivity
import com.tvremote.app.util.ThemeHelper
import androidx.recyclerview.widget.LinearLayoutManager

class LanguageActivity : BaseActivity() {
    private lateinit var binding: ActivityLanguageBinding
    private lateinit var adapter: LanguageAdapter
    private var selectedLanguage = AppLanguage("en", R.string.lang_english, R.string.lang_english_native, "🇺🇸")

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fromSettings = intent.getBooleanExtra(EXTRA_FROM_SETTINGS, false)
        val prefs = AppPreferences(this)
        selectedLanguage = languages().find { it.code == prefs.selectedLanguage } ?: selectedLanguage

        adapter = LanguageAdapter { lang -> selectedLanguage = lang }
        binding.languageList.layoutManager = LinearLayoutManager(this)
        binding.languageList.adapter = adapter
        adapter.submitList(languages())
        adapter.setSelected(selectedLanguage.code)

        binding.backButton.setOnClickListener { finish() }
        binding.applyButton.setOnClickListener {
            prefs.selectedLanguage = selectedLanguage.code
            if (fromSettings) {
                setResult(RESULT_OK)
                finish()
            } else {
                startActivity(Intent(this, OnboardingActivity::class.java))
                finish()
            }
        }
    }

    private fun languages() = listOf(
        AppLanguage("en", R.string.lang_english, R.string.lang_english_native, "🇺🇸"),
        AppLanguage("es", R.string.lang_spanish, R.string.lang_spanish_native, "🇪🇸"),
        AppLanguage("fr", R.string.lang_french, R.string.lang_french_native, "🇫🇷"),
        AppLanguage("ar", R.string.lang_arabic, R.string.lang_arabic_native, "🇸🇦"),
        AppLanguage("de", R.string.lang_german, R.string.lang_german_native, "🇩🇪"),
        AppLanguage("id", R.string.lang_indonesian, R.string.lang_indonesian_native, "🇮🇩"),
        AppLanguage("ru", R.string.lang_russian, R.string.lang_russian_native, "🇷🇺"),
        AppLanguage("zh", R.string.lang_chinese, R.string.lang_chinese_native, "🇨🇳"),
        AppLanguage("fa", R.string.lang_persian, R.string.lang_persian_native, "🇮🇷"),
        AppLanguage("pt", R.string.lang_portuguese, R.string.lang_portuguese_native, "🇵🇹"),
        AppLanguage("tr", R.string.lang_turkish, R.string.lang_turkish_native, "🇹🇷"),
        AppLanguage("ko", R.string.lang_korean, R.string.lang_korean_native, "🇰🇷"),
        AppLanguage("fil", R.string.lang_filipino, R.string.lang_filipino_native, "🇵🇭"),
    )

    companion object {
        const val EXTRA_FROM_SETTINGS = "from_settings"
    }
}
