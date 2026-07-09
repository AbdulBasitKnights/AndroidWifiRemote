package com.tvremote.app.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.tvremote.app.BuildConfig
import com.tvremote.app.R
import com.tvremote.app.TvRemoteApp
import com.tvremote.app.ads.AdsHelper
import com.tvremote.app.analytics.AdRevenueBindings
import com.tvremote.app.data.FlowPreferences
import com.tvremote.app.data.IS_LANGUAGE
import com.tvremote.app.data.IS_LANGUAGE_SPLASH
import com.tvremote.app.data.IS_ONBOARD
import com.tvremote.app.databinding.ActivitySplashBinding
import com.tvremote.app.features.fullonboard.FullOnboardActivity
import com.tvremote.app.features.iap.IAPActivity
import com.tvremote.app.features.iap.IapManager
import com.tvremote.app.features.iap.ProSubscriptionChecker
import com.tvremote.app.features.iap.utils.DataWrappers
import com.tvremote.app.features.iap.utils.SubscriptionServiceListener
import com.tvremote.app.ui.common.BaseActivity
import com.tvremote.app.ui.language.LanguageActivity
import com.tvremote.app.ui.main.MainActivity
import com.tvremote.app.ui.survey.SurveyActivity
import com.tvremote.app.util.AppLogger
import com.tvremote.app.util.FirebaseLogUtils
import com.tvremote.app.util.GlobalLoader
import com.tvremote.app.util.NetworkUtils
import com.tvremote.app.ads.AdsHelper.langNative1Enabled
import com.tvremote.app.ads.AdsHelper.langNativeHigh1Enabled
import com.tvremote.app.ads.loadNativeLanguageHigh
import com.tvremote.app.ads.loadNativeLanguageNormal
import com.tvremote.app.consent.ConsentCallback
import com.tvremote.app.consent.ConsentController
import com.tvremote.app.util.OnboardSessionCounter
import com.tvremote.app.util.applyLightSystemBars
import com.tvremote.app.util.hideNavigationBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity() {

    private var binding: ActivitySplashBinding? = null
    private var nativeSplash: NativeAd? = null
    private var interstitialAd: InterstitialAd? = null
    private var hasNavigated = false
    private var interShowStarted = false
    private var splashWaitTimedOut = false
    private var splashInterState = SplashInterState.DISABLED

    private val splashWaitTimer = object : CountDownTimer(90_000, 1_000) {
        override fun onTick(millisUntilFinished: Long) = Unit
        override fun onFinish() {
            splashWaitTimedOut = true
            if (splashInterState == SplashInterState.LOADED) {
                splashInterState = SplashInterState.DONE
                interstitialAd = null
            }
            navigateNext()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        applyLightSystemBars()
        hideNavigationBar()
        FirebaseLogUtils.logEvent("splash_view")

        FlowPreferences.readDataStoreValue(IS_ONBOARD, false) { isOnboard = !this }
        FlowPreferences.readDataStoreValue(IS_LANGUAGE_SPLASH, false) { isLanguage = !this }
        performNavigation()
      /*  lifecycleScope.launch {
            val isPro = ProSubscriptionChecker.check(applicationContext)
            AdsHelper.updateProVersion(isPro)
            initIapRestore()
            showProgressBar(5_000)
            if (isPro) binding?.containAds?.visibility = View.INVISIBLE
            delay(2_000)

            if (!NetworkUtils.isOnline(this@SplashActivity)) {
                binding?.shimmer?.visibility = View.GONE
                binding?.clAd?.visibility = View.GONE
                splashInterState = SplashInterState.DISABLED
                navigateNext()
                return@launch
            }

            initConsent()
        }*/
    }

    private fun initConsent() {
        ConsentController(this).apply {
            initConsent(CONSENT_TEST_DEVICE_ID, object : ConsentCallback {
                override fun onAdsLoad(canRequestAd: Boolean) {
                    MobileAds.initialize(this@SplashActivity)
                    TvRemoteApp.isConfigFetched.observe(this@SplashActivity) { fetched ->
                        if (!fetched || binding == null) return@observe
                        handleRemoteConfigReady()
                    }
                }

                override fun onConsentFormLoaded() {
                    showConsentForm()
                }
            })
        }
    }

    private fun handleRemoteConfigReady() {
        val session = TvRemoteApp.onboardingSession
        if (session == 2 || session == 3) {
            val count = OnboardSessionCounter.getCounter(this)
            if (count <= session) {
                isOnboard = true
                FlowPreferences.writeDataStoreValue(IS_ONBOARD, false)
            }
        }

        if (AdsHelper.shouldShowAds()) {
            binding?.containAds?.visibility = View.VISIBLE
            if (TvRemoteApp.isNativeSplashHf && TvRemoteApp.isNativeSplash) loadNativeSplashHigh()
            else if (TvRemoteApp.isNativeSplash) loadNativeSplashNormal()
            else binding?.clAd?.visibility = View.GONE

            if (TvRemoteApp.isInterSplashHf && TvRemoteApp.isInterSplash) {
                splashInterState = SplashInterState.LOADING
                splashWaitTimer.start()
                loadInterSplashHigh()
            } else if (TvRemoteApp.isInterSplash) {
                splashInterState = SplashInterState.LOADING
                splashWaitTimer.start()
                loadInterSplashNormal()
            } else {
                splashInterState = SplashInterState.DISABLED
                navigateNext()
            }

            if (isOnboard) {
                if (langNativeHigh1Enabled && langNative1Enabled) {
                    loadNativeLanguageHigh(this@SplashActivity)
                } else if (langNative1Enabled) {
                    loadNativeLanguageNormal(this@SplashActivity)
                }
            }
        } else {
            binding?.containAds?.visibility = View.INVISIBLE
            splashInterState = SplashInterState.DISABLED
            navigateNext()
        }
    }

    private fun navigateNext() {
        if (hasNavigated) return
        if (splashInterState == SplashInterState.LOADING || splashInterState == SplashInterState.SHOWING) return
        if (splashInterState == SplashInterState.LOADED && !splashWaitTimedOut) return
        performNavigation()
    }

    private fun performNavigation() {
        if (hasNavigated) return
        hasNavigated = true
        splashWaitTimer.cancel()

        /*val next = when {
            TvRemoteApp.showProFromSplash && AdsHelper.isProVersion.value != true ->
                Intent(this, IAPActivity::class.java)
            isOnboard && isLanguage ->
                Intent(this, LanguageActivity::class.java)
            isOnboard && TvRemoteApp.surveyEnable ->
                Intent(this, SurveyActivity::class.java)
            isOnboard ->
                Intent(this, FullOnboardActivity::class.java)
            else ->
                Intent(this, MainActivity::class.java)
        }*/
        val intent=Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun showProgressBar(duration: Long) {
        binding?.loadingBar?.progress = 0
        lifecycleScope.launch {
            val steps = 100
            val delayPerStep = duration / steps
            for (i in 1..steps) {
                binding?.loadingBar?.progress = i
                delay(delayPerStep)
            }
        }
    }

    private fun loadNativeSplashHigh() {
        if (!AdsHelper.shouldShowAds() || !NetworkUtils.isOnline(this)) return
        binding?.shimmer?.visibility = View.VISIBLE
        binding?.clAd?.visibility = View.VISIBLE
        AdLoader.Builder(this, BuildConfig.native_splash_high)
            .forNativeAd { ad ->
                nativeSplash?.destroy()
                nativeSplash = ad
                showNativeSplash()
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) = loadNativeSplashNormal()
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    private fun loadNativeSplashNormal() {
        if (!AdsHelper.shouldShowAds() || !NetworkUtils.isOnline(this)) return
        AdLoader.Builder(this, BuildConfig.native_splash)
            .forNativeAd { ad ->
                nativeSplash?.destroy()
                nativeSplash = ad
                showNativeSplash()
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    binding?.clAd?.visibility = View.GONE
                }
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    private fun showNativeSplash() {
        nativeSplash?.let { nativeAd ->
            val adView = LayoutInflater.from(this)
                .inflate(R.layout.layout_native_ads_without_mediaview, null) as NativeAdView
            populateNativeBannerAdView(nativeAd, adView)
            binding?.nativeAdView?.removeAllViews()
            binding?.nativeAdView?.addView(adView)
            binding?.shimmer?.visibility = View.GONE
            binding?.nativeAdView?.visibility = View.VISIBLE
        }
    }

    private fun populateNativeBannerAdView(nativeAd: NativeAd, adView: NativeAdView) {
        adView.headlineView = adView.findViewById(R.id.ad_headline)
        adView.bodyView = adView.findViewById(R.id.ad_body)
        adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
        adView.iconView = adView.findViewById(R.id.ad_app_icon)
        (adView.headlineView as? TextView)?.text = nativeAd.headline
        (adView.bodyView as? TextView)?.text = nativeAd.body
        (adView.callToActionView as? AppCompatButton)?.text = nativeAd.callToAction
        (adView.iconView as? ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
        adView.setNativeAd(nativeAd)
    }

    private fun loadInterSplashHigh() {
        InterstitialAd.load(
            this,
            BuildConfig.inter_splash_high,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    AdRevenueBindings.bind(ad, adFormat = "interstitial", adPlacement = "splash_high")
                    onSplashInterLoaded(ad)
                }
                override fun onAdFailedToLoad(error: LoadAdError) = loadInterSplashNormal()
            },
        )
    }

    private fun loadInterSplashNormal() {
        InterstitialAd.load(
            this,
            BuildConfig.inter_splash,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    AdRevenueBindings.bind(ad, adFormat = "interstitial", adPlacement = "splash")
                    onSplashInterLoaded(ad)
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    splashInterState = SplashInterState.FAILED
                    splashWaitTimer.cancel()
                    navigateNext()
                }
            },
        )
    }

    private fun onSplashInterLoaded(ad: InterstitialAd) {
        if (hasNavigated || splashWaitTimedOut) return
        interstitialAd = ad
        splashInterState = SplashInterState.LOADED
        showInterSplashIfReady()
    }

    private fun showInterSplashIfReady() {
        if (interShowStarted || hasNavigated || isFinishing) return
        val ad = interstitialAd ?: return
        if (!AdsHelper.shouldShowAds()) {
            onSplashInterFinished()
            return
        }
        interShowStarted = true
        splashInterState = SplashInterState.SHOWING
        GlobalLoader.show(this)
        lifecycleScope.launch {
            delay(500)
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    GlobalLoader.hide(this@SplashActivity)
                    onSplashInterFinished()
                }
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    GlobalLoader.hide(this@SplashActivity)
                    onSplashInterFinished()
                }
            }
            ad.show(this@SplashActivity)
        }
    }

    private fun onSplashInterFinished() {
        splashInterState = SplashInterState.DONE
        splashWaitTimer.cancel()
        interstitialAd = null
        navigateNext()
    }

    private fun initIapRestore() {
        val connector = IapManager.getIapConnector(applicationContext)
        connector.addSubscriptionListener(object : SubscriptionServiceListener {
            override fun onSubscriptionRestored(purchaseInfo: DataWrappers.PurchaseInfo) {
                AdsHelper.updateProVersion(purchaseInfo.sku == IapManager.skuKeyWeek)
            }
            override fun onSubscriptionPurchased(purchaseInfo: DataWrappers.PurchaseInfo) {
                AdsHelper.updateProVersion(purchaseInfo.sku == IapManager.skuKeyWeek)
            }
            override fun onPricesUpdated(iapKeyPrices: Map<String, List<DataWrappers.ProductDetails>>) = Unit
        })
    }

    override fun onDestroy() {
        splashWaitTimer.cancel()
        nativeSplash?.destroy()
        super.onDestroy()
    }

    companion object {
        var isOnboard = false
        var isLanguage = false

        private enum class SplashInterState {
            DISABLED, LOADING, LOADED, SHOWING, FAILED, DONE,
        }

        private const val CONSENT_TEST_DEVICE_ID = "3E0C66FC65F12F1C83DC8561646DF22B"
    }
}
