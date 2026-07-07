package com.tvremote.app.ads

import android.app.Activity
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.interstitial.InterstitialAd

fun InterstitialAd.showImmersive(
    activity: Activity,
    contentCallback: FullScreenContentCallback? = null,
) {
    fullScreenContentCallback = contentCallback
    setImmersiveMode(true)
    show(activity)
}
