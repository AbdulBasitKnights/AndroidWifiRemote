package com.tvremote.app.ads

import androidx.lifecycle.MutableLiveData

object AdsHelper {
    val isProVersion = MutableLiveData<Boolean?>(false)

    fun updateProVersion(isPro: Boolean) {
        isProVersion.postValue(isPro)
    }

    fun shouldShowAds(): Boolean = isProVersion.value != true

    // Language
    var languageButtonDelay: Int = 3
    var languageButtonStyle: Int = 1
    var langNative1Enabled: Boolean = true
    var langNativeHigh1Enabled: Boolean = true
    var langNative2Enabled: Boolean = true
    var langNativeHigh2Enabled: Boolean = true

    // Onboarding
    var obEnable: Boolean = true
}
