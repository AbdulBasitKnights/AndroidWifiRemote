package com.tvremote.app

import android.content.Context

class AppContainer(context: Context) {
    val remoteManager = RemoteTvManager(context.applicationContext)
    val tvDiscovery = TvDiscovery(context.applicationContext)
    val castManager = CastManager(context.applicationContext)

    var savedTvHost: String = ""
    var isPaired: Boolean = false
}
