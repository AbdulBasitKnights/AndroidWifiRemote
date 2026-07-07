package com.tvremote.app.ui.language

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.tvremote.app.R
import com.tvremote.app.TvRemoteApp
import com.tvremote.app.ads.AdsHelper.isProVersion
import com.tvremote.app.ads.AdsHelper.langNative2Enabled
import com.tvremote.app.ads.AdsHelper.langNativeHigh2Enabled
import com.tvremote.app.ads.AdsHelper.languageButtonDelay
import com.tvremote.app.ads.AdsHelper.languageButtonStyle
import com.tvremote.app.ads.AdsHelper.obEnable
import com.tvremote.app.ads.loadInterSurvey
import com.tvremote.app.ads.loadInterSurveyHigh
import com.tvremote.app.ads.loadNativeLanguageAltHigh
import com.tvremote.app.ads.loadNativeLanguageAltNormal
import com.tvremote.app.ads.nativeLanguage
import com.tvremote.app.ads.nativeLanguageAlt
import com.tvremote.app.data.FlowPreferences
import com.tvremote.app.data.IS_LANGUAGE
import com.tvremote.app.data.IS_LANGUAGE_SPLASH
import com.tvremote.app.data.IS_ONBOARD
import com.tvremote.app.data.session.AppPreferences
import com.tvremote.app.databinding.ActivityLanguageBinding
import com.tvremote.app.features.fullonboard.FullOnboardActivity
import com.tvremote.app.ui.main.MainActivity
import com.tvremote.app.ui.survey.SurveyActivity
import com.tvremote.app.util.FirebaseLogUtils
import com.tvremote.app.util.applyLightSystemBars
import com.tvremote.app.util.hideNavigationBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class LanguageActivity : AppCompatActivity(), LanguageSelectionAdapter.LanguageSelectionClickListener {

    private var binding: ActivityLanguageBinding? = null
    private var selectedLanguage = "none"
    private var isOnboard = false
    private var isShow = false
    private var adapter: LanguageSelectionAdapter? = null
    private val fromSettings: Boolean
        get() = intent.getBooleanExtra(EXTRA_FROM_SETTINGS, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setLocate(this)
        enableEdgeToEdge()
        binding = ActivityLanguageBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        hideNavigationBar()
        applyLightSystemBars()
        FirebaseLogUtils.logEvent("language_view")

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding?.rvLanguage?.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(this)

        lifecycleScope.launch {
            if (nativeLanguage != null) {
                showNativeLanguage(this@LanguageActivity)
            } else {
                delay(2000)
                if (nativeLanguage != null) {
                    showNativeLanguage(this@LanguageActivity)
                } else {
                    binding?.clbottom?.visibility = View.GONE
                }
            }
        }

        FlowPreferences.readDataStoreValue(IS_ONBOARD, false) {
            isOnboard = !this
        }
        FlowPreferences.writeDataStoreValue(IS_LANGUAGE_SPLASH, true)

        if (fromSettings) {
            binding?.back?.visibility = View.VISIBLE
        }
        binding?.back?.setOnClickListener { finish() }

        doneButtonDisableStyle()
        setLanguageRv(this)

        if (langNativeHigh2Enabled && langNative2Enabled) {
            loadNativeLanguageAltHigh(this) { onLoaded ->
                if (!onLoaded) {
                    loadNativeLanguageAltNormal(this) { }
                }
            }
        } else if (langNative2Enabled) {
            loadNativeLanguageAltNormal(this) { }
        }

        if (isProVersion.value == false) {
            lifecycleScope.launch {
                delay(3000)
                binding?.langLoading?.visibility = View.GONE
            }
        } else {
            binding?.langLoading?.visibility = View.GONE
        }

        binding?.btnDone?.setOnClickListener { onDoneClicked() }
        binding?.icBtnDone?.setOnClickListener { onDoneClicked() }
    }

    private fun onDoneClicked() {
        if (selectedLanguage == "none") {
            Toast.makeText(this, getString(R.string.plz_select_language), Toast.LENGTH_SHORT).show()
            return
        }

        if (!isOnboard || fromSettings) {
            FlowPreferences.writeDataStoreValue(IS_LANGUAGE, selectedLanguage)
        }
        AppPreferences(this).selectedLanguage = selectedLanguage

        if (isOnboard) {
            binding?.back?.visibility = View.GONE
        }

        if (fromSettings) {
            setResult(RESULT_OK)
            finish()
            return
        }

        navigateNext()
    }

    private fun navigateNext() {
        FirebaseLogUtils.logEvent("language_click_next")

        if (isOnboard) {
            if (TvRemoteApp.isInterSurveyHf && TvRemoteApp.isInterSurvey && TvRemoteApp.surveyEnable) {
                loadInterSurveyHigh(this)
            } else if (TvRemoteApp.isInterSurvey && TvRemoteApp.surveyEnable) {
                loadInterSurvey(this)
            }

            val nextActivity = if (TvRemoteApp.surveyEnable) {
                SurveyActivity::class.java
            } else if (obEnable) {
                FullOnboardActivity::class.java
            } else {
                MainActivity::class.java
            }
            startActivity(Intent(this, nextActivity))
            finish()
        } else {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun setLanguageRv(activity: FragmentActivity) {
        val languageList = arrayListOf(
            LanguageModel(2, "ja", "Japanese", ContextCompat.getDrawable(activity, R.drawable.japan_svg), false),
            LanguageModel(3, "hi", "Hindi", ContextCompat.getDrawable(activity, R.drawable.india_svg), false),
            LanguageModel(4, "in", "Indonesian", ContextCompat.getDrawable(activity, R.drawable.indo_svg), false),
            LanguageModel(1, "en", "English (Auto)", ContextCompat.getDrawable(activity, R.drawable.eng_svg), false),
            LanguageModel(5, "es", "Spanish", ContextCompat.getDrawable(activity, R.drawable.eng_svg), false),
            LanguageModel(6, "de", "German", ContextCompat.getDrawable(activity, R.drawable.eng_svg), false),
            LanguageModel(7, "it", "Italian", ContextCompat.getDrawable(activity, R.drawable.eng_svg), false),
            LanguageModel(8, "pt", "Portuguese", ContextCompat.getDrawable(activity, R.drawable.eng_svg), false),
            LanguageModel(9, "ko", "Korean", ContextCompat.getDrawable(activity, R.drawable.japan_svg), false),
        )
        adapter = LanguageSelectionAdapter(languageList, this)
        binding?.rvLanguage?.adapter = adapter
    }

    override fun onLanguageClick(language: LanguageModel?) {
        mSelectedLanguage = language?.lang ?: "en"

        if (!isShow) {
            loadOB1Ads()
            showNativeLanguageAlt(this)
            isShow = true
            binding?.progressBar?.visibility = View.VISIBLE
            binding?.btnDone?.visibility = View.INVISIBLE
            binding?.icBtnDone?.visibility = View.INVISIBLE
            lifecycleScope.launch {
                delay(1000L * languageButtonDelay)
                doneButtonStyle()
            }
        }
        selectedLanguage = language?.lang ?: "en"
    }

    private fun doneButtonDisableStyle() {
        when (languageButtonStyle) {
            1 -> {
                binding?.btnDone?.apply {
                    background = ContextCompat.getDrawable(this@LanguageActivity, R.drawable.bg_unselected_btn)
                    setTextColor(ContextCompat.getColor(this@LanguageActivity, R.color.white))
                    visibility = View.VISIBLE
                }
            }
            2 -> {
                binding?.icBtnDone?.apply {
                    setImageDrawable(ContextCompat.getDrawable(this@LanguageActivity, R.drawable.arrow_next))
                    visibility = View.VISIBLE
                }
            }
            3 -> {
                binding?.btnDone?.apply {
                    setTextColor(ContextCompat.getColor(this@LanguageActivity, R.color.unselected_color))
                    visibility = View.VISIBLE
                }
            }
            else -> {
                binding?.btnDone?.apply {
                    background = ContextCompat.getDrawable(this@LanguageActivity, R.drawable.bg_unselected_btn)
                    setTextColor(ContextCompat.getColor(this@LanguageActivity, R.color.white))
                    visibility = View.VISIBLE
                }
            }
        }
    }

    private fun doneButtonStyle() {
        binding?.progressBar?.visibility = View.GONE
        when (languageButtonStyle) {
            1 -> {
                binding?.btnDone?.apply {
                    background = ContextCompat.getDrawable(this@LanguageActivity, R.drawable.bg_selected_btn)
                    setTextColor(ContextCompat.getColor(this@LanguageActivity, R.color.white))
                    visibility = View.VISIBLE
                }
            }
            2 -> {
                binding?.icBtnDone?.apply {
                    setImageDrawable(ContextCompat.getDrawable(this@LanguageActivity, R.drawable.arrow_next_enable))
                    visibility = View.VISIBLE
                }
            }
            3 -> {
                binding?.btnDone?.apply {
                    setTextColor(ContextCompat.getColor(this@LanguageActivity, R.color.primaryColor))
                    visibility = View.VISIBLE
                }
            }
            else -> {
                binding?.btnDone?.apply {
                    background = ContextCompat.getDrawable(this@LanguageActivity, R.drawable.bg_selected_btn)
                    setTextColor(ContextCompat.getColor(this@LanguageActivity, R.color.white))
                    visibility = View.VISIBLE
                }
            }
        }
    }

    private fun setLocate(activity: Activity) {
        var lang = Locale.getDefault().language
        FlowPreferences.readDataStoreValue(IS_LANGUAGE, "") {
            val savedLang = this
            lang = if (savedLang.isBlank()) {
                val supported = listOf("ja", "es", "in", "hi")
                if (lang in supported) lang else "en"
            } else {
                savedLang
            }
            val locale = Locale.forLanguageTag(lang)
            Locale.setDefault(locale)
            val config = Configuration()
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
        }
    }

    private fun showNativeLanguage(activity: FragmentActivity) {
        if (isProVersion.value == true) return
        nativeLanguage?.let { ad ->
            val adView = LayoutInflater.from(activity)
                .inflate(R.layout.layout_native_ads, null) as NativeAdView
            populateNativeAdView(ad, adView)
            binding?.clbottom?.visibility = View.VISIBLE
            binding?.nativeAdView?.removeAllViews()
            binding?.nativeAdView?.addView(adView)
            binding?.nativeAdView?.visibility = View.VISIBLE
            binding?.shimmer?.visibility = View.GONE
        }
    }

    private fun showNativeLanguageAlt(activity: FragmentActivity) {
        if (isProVersion.value == true) return
        nativeLanguageAlt?.let { ad ->
            val adView = LayoutInflater.from(activity)
                .inflate(R.layout.layout_native_ads, null) as NativeAdView
            populateNativeAdView(ad, adView)
            binding?.clbottom?.visibility = View.VISIBLE
            binding?.nativeAdView?.removeAllViews()
            binding?.nativeAdView?.addView(adView)
            binding?.nativeAdView?.visibility = View.VISIBLE
            binding?.shimmer?.visibility = View.GONE
        }
    }

    private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.mediaView = adView.findViewById(R.id.ad_media)
        (adView.headlineView as? TextView)?.text = nativeAd.headline
        (adView.bodyView as? TextView)?.text = nativeAd.body
        (adView.callToActionView as? AppCompatButton)?.text = nativeAd.callToAction
        adView.mediaView?.mediaContent = nativeAd.mediaContent
        adView.setNativeAd(nativeAd)
    }

    private fun loadOB1Ads() = Unit

    companion object {
        var mSelectedLanguage = "none"
        const val EXTRA_FROM_SETTINGS = "from_settings"
    }
}
