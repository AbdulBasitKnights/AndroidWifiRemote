package com.tvremote.app.analytics

import android.content.Context
import android.os.Bundle
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustAdRevenue
import com.adjust.sdk.AdjustEvent
import com.facebook.appevents.AppEventsConstants
import com.facebook.appevents.AppEventsLogger
import com.google.firebase.analytics.FirebaseAnalytics
import com.tvremote.app.util.AppLogger
import com.tvremote.app.util.SafeRun

/**
 * Unified analytics facade for Firebase Analytics, Adjust, and Meta App Events.
 */
object AnalyticsEvents {
    private const val TAG = "AnalyticsEvents"
    private const val AD_PLATFORM_ADMOB = "admob"
    private const val AD_PLATFORM_ADMOB_SDK = "admob_sdk"

    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var metaLogger: AppEventsLogger? = null

    fun init(context: Context) {
        firebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)
        metaLogger = AppEventsLogger.newLogger(context.applicationContext)
    }

    /**
     * Logs a custom event to Firebase Analytics and Meta App Events.
     * Pass [adjustEventToken] to also send the event to Adjust.
     */
    fun logEvent(
        eventName: String,
        params: Map<String, Any?> = emptyMap(),
        adjustEventToken: String? = null,
    ) {
        SafeRun.run(TAG) {
            logFirebaseEvent(eventName, params)
            logMetaEvent(eventName, params)
            adjustEventToken?.let { token ->
                if (token.isNotBlank() && !token.startsWith("YOUR_")) {
                    logAdjustEvent(token, params)
                }
            }
        }
    }

    /**
     * Logs ad impression revenue to Firebase, Adjust, and Meta.
     *
     * Call from AdMob [com.google.android.gms.ads.OnPaidEventListener] or via
     * [AdRevenueBindings] helpers on loaded ad objects.
     */
    fun logAdImpressionRevenue(
        revenueMicros: Long,
        currency: String,
        adUnitId: String,
        adFormat: String,
        adSourceName: String? = null,
        adPlacement: String? = null,
        precision: Int? = null,
    ) {
        val revenue = revenueMicros / 1_000_000.0
        if (revenue <= 0.0 || currency.isBlank()) {
            AppLogger.w(TAG, "Skipped ad revenue: revenue=$revenue currency=$currency")
            return
        }

        SafeRun.run(TAG) {
            logFirebaseAdImpression(
                revenue = revenue,
                currency = currency,
                adUnitId = adUnitId,
                adFormat = adFormat,
                adSourceName = adSourceName,
                precision = precision,
            )
            logAdjustAdRevenue(
                revenue = revenue,
                currency = currency,
                adUnitId = adUnitId,
                adSourceName = adSourceName,
                adPlacement = adPlacement ?: adFormat,
            )
            logMetaAdImpression(
                revenue = revenue,
                currency = currency,
                adUnitId = adUnitId,
                adFormat = adFormat,
                adSourceName = adSourceName,
            )
            AppLogger.d(TAG, "Ad impression revenue logged: $revenue $currency ($adFormat)")
        }
    }

    private fun logFirebaseEvent(eventName: String, params: Map<String, Any?>) {
        val bundle = params.toAnalyticsBundle()
        firebaseAnalytics?.logEvent(eventName, bundle)
    }

    private fun logFirebaseAdImpression(
        revenue: Double,
        currency: String,
        adUnitId: String,
        adFormat: String,
        adSourceName: String?,
        precision: Int?,
    ) {
        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.AD_PLATFORM, AD_PLATFORM_ADMOB)
            putString(FirebaseAnalytics.Param.AD_FORMAT, adFormat)
            putString(FirebaseAnalytics.Param.AD_UNIT_NAME, adUnitId)
            putDouble(FirebaseAnalytics.Param.VALUE, revenue)
            putString(FirebaseAnalytics.Param.CURRENCY, currency)
            adSourceName?.let { putString(FirebaseAnalytics.Param.AD_SOURCE, it) }
            precision?.let { putInt("precision", it) }
        }
        firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, bundle)
    }

    private fun logAdjustAdRevenue(
        revenue: Double,
        currency: String,
        adUnitId: String,
        adSourceName: String?,
        adPlacement: String,
    ) {
        val adRevenue = AdjustAdRevenue(AD_PLATFORM_ADMOB_SDK).apply {
            setRevenue(revenue, currency)
            setAdRevenueUnit(adUnitId)
            setAdRevenuePlacement(adPlacement)
            adSourceName?.let { setAdRevenueNetwork(it) }
        }
        Adjust.trackAdRevenue(adRevenue)
    }

    private fun logMetaAdImpression(
        revenue: Double,
        currency: String,
        adUnitId: String,
        adFormat: String,
        adSourceName: String?,
    ) {
        val params = Bundle().apply {
            putString(AppEventsConstants.EVENT_PARAM_CURRENCY, currency)
            putString("ad_platform", AD_PLATFORM_ADMOB)
            putString("ad_format", adFormat)
            putString("ad_unit_id", adUnitId)
            adSourceName?.let { putString("ad_source", it) }
        }
        metaLogger?.logEvent(AppEventsConstants.EVENT_NAME_AD_IMPRESSION, revenue, params)
    }

    private fun logMetaEvent(eventName: String, params: Map<String, Any?>) {
        val bundle = params.toAnalyticsBundle()
        metaLogger?.logEvent(eventName, bundle)
    }

    private fun logAdjustEvent(eventToken: String, params: Map<String, Any?>) {
        val event = AdjustEvent(eventToken)
        params.forEach { (key, value) ->
            value?.toString()?.let { event.addCallbackParameter(key, it) }
        }
        Adjust.trackEvent(event)
    }

    private fun Map<String, Any?>.toAnalyticsBundle(): Bundle {
        return Bundle().apply {
            forEach { (key, value) ->
                when (value) {
                    null -> Unit
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Double -> putDouble(key, value)
                    is Float -> putFloat(key, value)
                    is Boolean -> putBoolean(key, value)
                    else -> putString(key, value.toString())
                }
            }
        }
    }
}
