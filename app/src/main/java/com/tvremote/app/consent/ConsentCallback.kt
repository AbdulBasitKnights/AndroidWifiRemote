package com.tvremote.app.consent

interface ConsentCallback {
    fun onAdsLoad(canRequestAd: Boolean) {}
    fun onConsentFormLoaded() {}
    fun onConsentFormDismissed() {}
    fun onPolicyStatus(required: Boolean) {}
}
