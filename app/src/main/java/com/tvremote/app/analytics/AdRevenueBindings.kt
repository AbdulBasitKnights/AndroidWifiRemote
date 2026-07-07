package com.tvremote.app.analytics

import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd

/**
 * Attaches AdMob paid-event listeners that forward impression revenue to [AnalyticsEvents].
 */
object AdRevenueBindings {
    fun bind(
        ad: InterstitialAd,
        adFormat: String = "interstitial",
        adPlacement: String? = null,
    ) {
        ad.onPaidEventListener = paidListener(ad.adUnitId, adFormat, adPlacement, ad.responseInfo)
    }

    fun bind(
        ad: RewardedAd,
        adFormat: String = "rewarded",
        adPlacement: String? = null,
    ) {
        ad.onPaidEventListener = paidListener(ad.adUnitId, adFormat, adPlacement, ad.responseInfo)
    }

    fun bind(
        ad: RewardedInterstitialAd,
        adFormat: String = "rewarded_interstitial",
        adPlacement: String? = null,
    ) {
        ad.onPaidEventListener = paidListener(ad.adUnitId, adFormat, adPlacement, ad.responseInfo)
    }

    fun bind(
        ad: AppOpenAd,
        adFormat: String = "app_open",
        adPlacement: String? = null,
    ) {
        ad.onPaidEventListener = paidListener(ad.adUnitId, adFormat, adPlacement, ad.responseInfo)
    }

    fun bind(
        ad: NativeAd,
        adUnitId: String,
        adFormat: String = "native",
        adPlacement: String? = null,
    ) {
        ad.setOnPaidEventListener(paidListener(adUnitId, adFormat, adPlacement, ad.responseInfo))
    }

    fun bind(
        adView: AdView,
        adFormat: String = "banner",
        adPlacement: String? = null,
    ) {
        adView.onPaidEventListener = paidListener(adView.adUnitId, adFormat, adPlacement, adView.responseInfo)
    }

    private fun paidListener(
        adUnitId: String,
        adFormat: String,
        adPlacement: String?,
        responseInfoProvider: () -> com.google.android.gms.ads.ResponseInfo?,
    ): OnPaidEventListener {
        return OnPaidEventListener { adValue ->
            val loadedAdapter = responseInfoProvider()?.loadedAdapterResponseInfo
            AnalyticsEvents.logAdImpressionRevenue(
                revenueMicros = adValue.valueMicros,
                currency = adValue.currencyCode,
                adUnitId = adUnitId,
                adFormat = adFormat,
                adSourceName = loadedAdapter?.adSourceName,
                adPlacement = adPlacement,
                precision = adValue.precisionType,
            )
        }
    }

    private fun paidListener(
        adUnitId: String,
        adFormat: String,
        adPlacement: String?,
        responseInfo: com.google.android.gms.ads.ResponseInfo?,
    ): OnPaidEventListener {
        return paidListener(adUnitId, adFormat, adPlacement) { responseInfo }
    }
}
