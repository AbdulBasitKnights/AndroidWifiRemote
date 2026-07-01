package com.tvremote.app

import android.app.Application
import com.google.android.gms.cast.framework.CastContext
import com.tvremote.app.di.AppContainer

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
