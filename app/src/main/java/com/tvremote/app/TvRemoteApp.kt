package com.tvremote.app

import android.app.Application
import com.google.android.gms.cast.framework.CastContext
import com.tvremote.app.di.AppContainer
import com.tvremote.app.util.AppLogger
import com.tvremote.app.util.CrashGuard
import com.tvremote.app.util.SafeRun

class TvRemoteApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
        container = AppContainer(this)
        SafeRun.run(TAG) {
            try {
                CastContext.getSharedInstance(this)
            } catch (e: Exception) {
                AppLogger.w(TAG, "CastContext init deferred", e)
            }
        }
    }

    companion object {
        private const val TAG = "TvRemoteApp"
    }
}
