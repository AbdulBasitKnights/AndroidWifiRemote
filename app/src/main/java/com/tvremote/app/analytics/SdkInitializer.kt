package com.tvremote.app.analytics

import android.app.Application
import com.adjust.sdk.Adjust
import com.adjust.sdk.AdjustConfig
import com.adjust.sdk.LogLevel
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tvremote.app.BuildConfig
import com.tvremote.app.R
import com.tvremote.app.util.AppLogger
import com.tvremote.app.util.SafeRun

object SdkInitializer {
    private const val TAG = "SdkInitializer"

    fun init(application: Application) {
        SafeRun.run(TAG) {
            initFirebase(application)
            initFacebook(application)
            initAdjust(application)
            initAdMob(application)
            AnalyticsEvents.init(application)
        }
    }

    private fun initFirebase(application: Application) {
        FirebaseApp.initializeApp(application)
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = true
        FirebaseAnalytics.getInstance(application)
        AppLogger.d(TAG, "Firebase initialized")
    }

    private fun initFacebook(application: Application) {
        if (!FacebookSdk.isInitialized()) {
            FacebookSdk.fullyInitialize()
        }
        AppEventsLogger.activateApp(application)
        AppLogger.d(TAG, "Facebook SDK initialized")
    }

    private fun initAdjust(application: Application) {
        val token = application.getString(R.string.adjust_app_token)
        if (token.isBlank() || token.startsWith("YOUR_")) {
            AppLogger.w(TAG, "Adjust app token missing — skipping Adjust init")
            return
        }

        val environment = when (BuildConfig.ADJUST_ENVIRONMENT) {
            "production" -> AdjustConfig.ENVIRONMENT_PRODUCTION
            else -> AdjustConfig.ENVIRONMENT_SANDBOX
        }
        val config = AdjustConfig(application, token, environment).apply {
            if (BuildConfig.DEBUG) {
                setLogLevel(LogLevel.VERBOSE)
            }
        }
        Adjust.initSdk(config)
        AppLogger.d(TAG, "Adjust initialized ($environment)")
    }

    private fun initAdMob(application: Application) {
        MobileAds.initialize(application) { status ->
            AppLogger.d(TAG, "AdMob initialized: ${status.adapterStatusMap.size} adapters")
        }
    }
}
