package com.tvremote.app.ads

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.tvremote.app.BuildConfig
import com.tvremote.app.analytics.AdRevenueBindings

var nativeLanguage: NativeAd? = null
var nativeLanguageAlt: NativeAd? = null
var interstitialSurvey: InterstitialAd? = null
var nativeSurvey: NativeAd? = null

fun loadNativeLanguageHigh(activity: FragmentActivity) {
    if (!AdsHelper.shouldShowAds()) return
    val adUnitId = BuildConfig.native_language_high
    AdLoader.Builder(activity, adUnitId)
        .forNativeAd { ad ->
            nativeLanguage?.destroy()
            nativeLanguage = ad
            AdRevenueBindings.bind(ad, adUnitId, adFormat = "native", adPlacement = "language_high")
        }
        .withAdListener(object : AdListener() {
            override fun onAdFailedToLoad(error: LoadAdError) = loadNativeLanguageNormal(activity)
        })
        .build()
        .loadAd(AdRequest.Builder().build())
}

fun loadNativeLanguageNormal(activity: FragmentActivity) {
    if (!AdsHelper.shouldShowAds()) return
    val adUnitId = BuildConfig.native_language
    AdLoader.Builder(activity, adUnitId)
        .forNativeAd { ad ->
            nativeLanguage?.destroy()
            nativeLanguage = ad
            AdRevenueBindings.bind(ad, adUnitId, adFormat = "native", adPlacement = "language")
        }
        .build()
        .loadAd(AdRequest.Builder().build())
}

fun loadNativeLanguageAltHigh(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
    if (!AdsHelper.shouldShowAds()) {
        onResult(false)
        return
    }
    val adUnitId = BuildConfig.native_language_alt_high
    AdLoader.Builder(activity, adUnitId)
        .forNativeAd { ad ->
            nativeLanguageAlt?.destroy()
            nativeLanguageAlt = ad
            AdRevenueBindings.bind(ad, adUnitId, adFormat = "native", adPlacement = "language_alt_high")
            onResult(true)
        }
        .withAdListener(object : AdListener() {
            override fun onAdFailedToLoad(error: LoadAdError) = onResult(false)
        })
        .build()
        .loadAd(AdRequest.Builder().build())
}

fun loadNativeLanguageAltNormal(activity: FragmentActivity, onResult: (Boolean) -> Unit) {
    if (!AdsHelper.shouldShowAds()) {
        onResult(false)
        return
    }
    val adUnitId = BuildConfig.native_language_alt
    AdLoader.Builder(activity, adUnitId)
        .forNativeAd { ad ->
            nativeLanguageAlt?.destroy()
            nativeLanguageAlt = ad
            AdRevenueBindings.bind(ad, adUnitId, adFormat = "native", adPlacement = "language_alt")
            onResult(true)
        }
        .withAdListener(object : AdListener() {
            override fun onAdFailedToLoad(error: LoadAdError) = onResult(false)
        })
        .build()
        .loadAd(AdRequest.Builder().build())
}

fun loadNativeSurveyHigh(activity: FragmentActivity) {
    if (!AdsHelper.shouldShowAds()) return
    val adUnitId = BuildConfig.native_survey_hf
    AdLoader.Builder(activity, adUnitId)
        .forNativeAd { ad ->
            nativeSurvey?.destroy()
            nativeSurvey = ad
            AdRevenueBindings.bind(ad, adUnitId, adFormat = "native", adPlacement = "survey_high")
        }
        .withAdListener(object : AdListener() {
            override fun onAdFailedToLoad(error: LoadAdError) = loadNativeSurveyNormal(activity)
        })
        .build()
        .loadAd(AdRequest.Builder().build())
}

fun loadNativeSurveyNormal(activity: FragmentActivity) {
    if (!AdsHelper.shouldShowAds()) return
    val adUnitId = BuildConfig.native_survey
    AdLoader.Builder(activity, adUnitId)
        .forNativeAd { ad ->
            nativeSurvey?.destroy()
            nativeSurvey = ad
            AdRevenueBindings.bind(ad, adUnitId, adFormat = "native", adPlacement = "survey")
        }
        .build()
        .loadAd(AdRequest.Builder().build())
}

fun loadInterSurveyHigh(context: Context) {
    if (!AdsHelper.shouldShowAds() || interstitialSurvey != null) return
    InterstitialAd.load(
        context,
        BuildConfig.inter_survey_high,
        AdRequest.Builder().build(),
        object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialSurvey = ad
                AdRevenueBindings.bind(ad, adFormat = "interstitial", adPlacement = "survey_high")
            }
            override fun onAdFailedToLoad(error: LoadAdError) = loadInterSurvey(context)
        },
    )
}

fun loadInterSurvey(context: Context) {
    if (!AdsHelper.shouldShowAds() || interstitialSurvey != null) return
    InterstitialAd.load(
        context,
        BuildConfig.inter_survey,
        AdRequest.Builder().build(),
        object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitialSurvey = ad
                AdRevenueBindings.bind(ad, adFormat = "interstitial", adPlacement = "survey")
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                interstitialSurvey = null
            }
        },
    )
}
