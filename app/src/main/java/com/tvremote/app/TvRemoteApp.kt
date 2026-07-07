package com.tvremote.app

import android.app.Application
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.cast.framework.CastContext
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.tvremote.app.analytics.SdkInitializer
import com.tvremote.app.data.FlowPreferences
import com.tvremote.app.di.AppContainer
import com.tvremote.app.util.AppLogger
import com.tvremote.app.util.CrashGuard
import com.tvremote.app.util.OnboardSessionCounter
import com.tvremote.app.util.SafeRun
import com.tvremote.app.util.ThemeHelper

class TvRemoteApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        context = this
        ThemeHelper.applySavedTheme(this)
        FlowPreferences.init(this)
        SdkInitializer.init(this)
        CrashGuard.install(this)
        container = AppContainer(this)
        fetchRemoteConfig()
        SafeRun.run(TAG) {
            try {
                CastContext.getSharedInstance(this)
            } catch (e: Exception) {
                AppLogger.w(TAG, "CastContext init deferred", e)
            }
        }
    }

    private fun fetchRemoteConfig() {
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3600
            },
        )
        remoteConfig.setDefaultsAsync(
            mapOf(
                "show_pro_from_splash" to true,
                "time_show_sub_close_btn" to 3L,
                "survey_enable" to true,
                "native_splash" to true,
                "native_splash_high" to true,
                "inter_splash" to true,
                "inter_splash_high" to true,
                "native_survey" to true,
                "native_survey_hf" to true,
                "inter_survey" to true,
                "inter_survey_high" to true,
                "ob_session" to 1L,
            ),
        )
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                showProFromSplash = remoteConfig.getBoolean("show_pro_from_splash")
                showTimeSubScreenX = remoteConfig.getLong("time_show_sub_close_btn")
                surveyEnable = remoteConfig.getBoolean("survey_enable")
                isNativeSplash = remoteConfig.getBoolean("native_splash")
                isNativeSplashHf = remoteConfig.getBoolean("native_splash_high")
                isInterSplash = remoteConfig.getBoolean("inter_splash")
                isInterSplashHf = remoteConfig.getBoolean("inter_splash_high")
                onboardingSession = remoteConfig.getLong("ob_session").toInt()
                isNativeSurvey = remoteConfig.getBoolean("native_survey")
                isNativeSurveyHf = remoteConfig.getBoolean("native_survey_hf")
                isInterSurvey = remoteConfig.getBoolean("inter_survey")
                isInterSurveyHf = remoteConfig.getBoolean("inter_survey_high")
            }
            isConfigFetched.postValue(true)
        }
    }

    companion object {
        private const val TAG = "TvRemoteApp"

        lateinit var context: Application
            private set

        val isConfigFetched = MutableLiveData(false)
        var onboardingSession = 1
        var showTimeSubScreenX = 3L
        var showProFromSplash = true
        var surveyEnable = true
        var isNativeSplash = true
        var isNativeSplashHf = true
        var isInterSplash = true
        var isInterSplashHf = true
        var isNativeSurvey = true
        var isNativeSurveyHf = true
        var isInterSurvey = true
        var isInterSurveyHf = true
    }
}
