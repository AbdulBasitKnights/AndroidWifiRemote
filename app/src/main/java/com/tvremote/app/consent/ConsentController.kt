package com.tvremote.app.consent

import android.app.Activity
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentForm
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.tvremote.app.BuildConfig

class ConsentController(private val activity: Activity) {

    private var consentInformation: ConsentInformation? = null
    private var consentCallback: ConsentCallback? = null
    private var consentForm: ConsentForm? = null

    val canRequestAds: Boolean get() = consentInformation?.canRequestAds() == true

    fun initConsent(
        deviceId: String,
        callback: ConsentCallback?,
    ) {
        consentCallback = callback

        val debugSettings = ConsentDebugSettings.Builder(activity)
            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
            .addTestDeviceHashedId(deviceId)
            .build()

        val params = if (BuildConfig.DEBUG) {
            ConsentRequestParameters.Builder()
                .setConsentDebugSettings(debugSettings)
                .build()
        } else {
            ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .build()
        }

        consentInformation = UserMessagingPlatform.getConsentInformation(activity).also { info ->
            info.requestConsentInfoUpdate(
                activity,
                params,
                {
                    val isPolicyRequired =
                        info.privacyOptionsRequirementStatus ==
                            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                    consentCallback?.onPolicyStatus(isPolicyRequired)

                    when (info.consentStatus) {
                        ConsentInformation.ConsentStatus.REQUIRED -> loadConsentForm()
                        ConsentInformation.ConsentStatus.OBTAINED,
                        ConsentInformation.ConsentStatus.NOT_REQUIRED,
                        -> consentCallback?.onAdsLoad(canRequestAds)
                        else -> consentCallback?.onAdsLoad(canRequestAds)
                    }
                },
                { error ->
                    Log.e(TAG, "Consent init failed: ${error.message}")
                    consentCallback?.onAdsLoad(canRequestAds)
                },
            )
        }
    }

    private fun loadConsentForm() {
        UserMessagingPlatform.loadConsentForm(
            activity,
            { form ->
                consentForm = form
                consentCallback?.onConsentFormLoaded()
            },
            { formError ->
                Log.e(TAG, "Consent form load failed: ${formError.message}")
                consentCallback?.onAdsLoad(canRequestAds)
            },
        )
    }

    fun showConsentForm() {
        val form = consentForm
        if (form == null) {
            consentCallback?.onAdsLoad(canRequestAds)
            return
        }
        form.show(activity) { formError ->
            consentCallback?.onConsentFormDismissed()
            consentCallback?.onAdsLoad(canRequestAds)
            if (formError != null) {
                Log.e(TAG, "Consent form show failed: ${formError.message}")
            }
        }
    }

    companion object {
        private const val TAG = "ConsentController"
    }
}
