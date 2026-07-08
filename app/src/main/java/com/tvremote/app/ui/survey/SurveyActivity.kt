package com.tvremote.app.ui.survey

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.tvremote.app.BuildConfig
import com.tvremote.app.R
import com.tvremote.app.TvRemoteApp
import com.tvremote.app.ads.AdsHelper.isProVersion
import com.tvremote.app.ads.AdsHelper.obEnable
import com.tvremote.app.ads.interstitialSurvey
import com.tvremote.app.ads.showImmersive
import com.tvremote.app.analytics.AdRevenueBindings
import com.tvremote.app.databinding.ActivitySurveyBinding
import com.tvremote.app.features.fullonboard.FullOnboardActivity
import com.tvremote.app.ui.main.MainActivity
import com.tvremote.app.util.FirebaseLogUtils
import com.tvremote.app.util.GlobalLoader
import com.tvremote.app.util.NetworkUtils
import com.tvremote.app.util.applyLightSystemBars
import com.tvremote.app.util.hideNavigationBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SurveyActivity : AppCompatActivity() {

    private var binding: ActivitySurveyBinding? = null
    private var nativeSurvey: NativeAd? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySurveyBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        hideNavigationBar()
        applyLightSystemBars()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        FirebaseLogUtils.logEvent("survey_view")

        if (TvRemoteApp.isNativeSurveyHf && TvRemoteApp.isNativeSurvey) {
            loadNativeSurveyHf()
        } else if (TvRemoteApp.isNativeSurvey) {
            loadNativeSurvey()
        } else {
            binding?.clbottom?.visibility = View.GONE
        }

        binding?.btnNext?.setOnClickListener { showInterSurvey(this) }

        binding?.clGallery?.setOnClickListener {
            binding?.clGallery?.foreground =
                ContextCompat.getDrawable(this, R.drawable.bg_rectangled_cl_bordered)
            setSelected()
        }
        binding?.clNanoBanana?.setOnClickListener {
            binding?.clNanoBanana?.foreground =
                ContextCompat.getDrawable(this, R.drawable.bg_rectangled_cl_bordered)
            setSelected()
        }
        binding?.photoEnhancer?.setOnClickListener {
            binding?.photoEnhancer?.foreground =
                ContextCompat.getDrawable(this, R.drawable.bg_rectangled_cl_bordered)
            setSelected()
        }
        binding?.clEditPhoto?.setOnClickListener {
            binding?.clEditPhoto?.foreground =
                ContextCompat.getDrawable(this, R.drawable.bg_rectangled_cl_bordered)
            setSelected()
        }
        binding?.clBodyMaker?.setOnClickListener {
            binding?.clBodyMaker?.foreground =
                ContextCompat.getDrawable(this, R.drawable.bg_rectangled_cl_bordered)
            setSelected()
        }
        binding?.clCollage?.setOnClickListener {
            binding?.clCollage?.foreground =
                ContextCompat.getDrawable(this, R.drawable.bg_rectangled_cl_bordered)
            setSelected()
        }
    }

    private fun setSelected() {
        binding?.btnNext?.background = resources.getDrawable(R.drawable.bg_rounded_cl_blue, theme)
    }

    private fun showInterSurvey(currentActivity: FragmentActivity) {
        currentActivity.lifecycleScope.launch {
            try {
                if (isProVersion.value == false) {
                    if (interstitialSurvey != null) {
                        GlobalLoader.show(currentActivity)
                        delay(1000)
                        navigateNext()

                        if (interstitialSurvey != null) {
                            interstitialSurvey?.showImmersive(
                                currentActivity,
                                object : FullScreenContentCallback() {
                                    override fun onAdShowedFullScreenContent() {
                                        currentActivity.lifecycleScope.launch {
                                            delay(1500)
                                            GlobalLoader.hide(this@SurveyActivity)
                                        }
                                    }

                                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                        GlobalLoader.hide(this@SurveyActivity)
                                        interstitialSurvey = null
                                    }

                                    override fun onAdDismissedFullScreenContent() {
                                        GlobalLoader.hide(this@SurveyActivity)
                                        interstitialSurvey = null
                                    }

                                    override fun onAdImpression() {
                                        super.onAdImpression()
                                        interstitialSurvey = null
                                    }
                                },
                            )
                        } else {
                            GlobalLoader.hide(currentActivity)
                        }
                        interstitialSurvey = null
                    } else {
                        interstitialSurvey = null
                        navigateNext()
                    }
                } else {
                    navigateNext()
                }
            } catch (e: Exception) {
                navigateNext()
            }
        }
    }

    private fun navigateNext() {
        val nextActivity = if (obEnable) {
            FullOnboardActivity::class.java
        } else {
            MainActivity::class.java
        }
        startActivity(Intent(this, nextActivity))
        finish()
    }

    private fun loadNativeSurveyHf() {
        if (isProVersion.value == false && NetworkUtils.isOnline(this)) {
            binding?.clbottom?.visibility = View.VISIBLE
            binding?.shimmer?.visibility = View.VISIBLE
            AdLoader.Builder(this, BuildConfig.native_survey_hf)
                .forNativeAd { nativeAd ->
                    nativeSurvey?.destroy()
                    nativeSurvey = nativeAd
                    AdRevenueBindings.bind(nativeAd, BuildConfig.native_survey_hf, adPlacement = "survey_high")
                    showNativeSurveyAd()
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(adError: LoadAdError) = loadNativeSurvey()
                })
                .build()
                .loadAd(AdRequest.Builder().build())
        } else {
            binding?.clbottom?.visibility = View.GONE
        }
    }

    private fun loadNativeSurvey() {
        if (isProVersion.value == false && NetworkUtils.isOnline(this)) {
            binding?.clbottom?.visibility = View.VISIBLE
            AdLoader.Builder(this, BuildConfig.native_survey)
                .forNativeAd { nativeAd ->
                    nativeSurvey?.destroy()
                    nativeSurvey = nativeAd
                    AdRevenueBindings.bind(nativeAd, BuildConfig.native_survey, adPlacement = "survey")
                    showNativeSurveyAd()
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        binding?.clbottom?.visibility = View.GONE
                    }
                })
                .build()
                .loadAd(AdRequest.Builder().build())
        } else {
            binding?.clbottom?.visibility = View.GONE
        }
    }

    private fun showNativeSurveyAd() {
        nativeSurvey?.let { nativeAd ->
            val adView = LayoutInflater.from(this)
                .inflate(R.layout.layout_native_ads, null) as NativeAdView
            populateNativeAdView(nativeAd, adView)
            binding?.nativeAdView?.removeAllViews()
            binding?.nativeAdView?.addView(adView)
            binding?.shimmer?.visibility = View.GONE
            binding?.nativeAdView?.visibility = View.VISIBLE
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

    override fun onDestroy() {
        nativeSurvey?.destroy()
        super.onDestroy()
    }
}
