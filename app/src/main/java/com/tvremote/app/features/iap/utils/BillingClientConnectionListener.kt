package com.tvremote.app.features.iap.utils

interface BillingClientConnectionListener {
    fun onConnected(status: Boolean, billingResponseCode: Int)
}