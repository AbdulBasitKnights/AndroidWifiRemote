package com.tvremote.app

import android.app.Application
import com.google.android.gms.cast.framework.CastContext

class TvRemoteApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        try {
            CastContext.getSharedInstance(this)
        } catch (_: Exception) {
        }
    }
}
