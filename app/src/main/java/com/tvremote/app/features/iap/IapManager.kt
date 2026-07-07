package com.tvremote.app.features.iap

import android.content.Context
import com.tvremote.app.features.iap.utils.IapConnector

object IapManager {
    private var iapConnector: IapConnector? = null

    const val skuKeyWeek = "weekly_pro"
    //licenseKey
    fun getIapConnector(context: Context): IapConnector {
        return if (iapConnector != null) {
            iapConnector as IapConnector
        } else {
            val nonConsumablesList = listOf("lifetime")
            val consumablesList = listOf("base", "moderate", "quite")
            val subsList = listOf(skuKeyWeek)
            IapConnector(
                context = context,
                nonConsumableKeys = nonConsumablesList,
                consumableKeys = consumablesList,
                subscriptionKeys = subsList,
                key = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApKYjB+N1j0TpaA3yDN4yMP2BgVTSiVtLYS208xk/7ALP3aNGVBvcuXrb/ODj5M5fsEWmLG0Gc3TykaUsP24meMXN9bEGIwOtC7wFi1RpT/IRnK62CXcEpD8IRiOhP4TUujrBpUiAukLYScV46M3GhfxBxrt26sdeJWbqTHfso75p/4ubQbPAJl9vSg/W7zQMW7U/hAPuKWjwzNjvrXmYSXg5wUkX+e8yn2WV2Hc3VTjdGfvPuuh9rtUETfdoUlWgIOker0y//eZjF5VTsg8QxXgPlj9ptn4AFpbellAG42MHVPsOAOV76m1FNRPMMEPlxp1LyJW8jljrG/H9ZiYm3wIDAQAB",
                enableLogging = true
            )
        }
    }
}