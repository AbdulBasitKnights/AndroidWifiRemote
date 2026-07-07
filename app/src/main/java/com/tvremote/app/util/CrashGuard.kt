package com.tvremote.app.util

import android.app.Application
import com.google.firebase.crashlytics.FirebaseCrashlytics

object CrashGuard {
    fun install(application: Application) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.e("CrashGuard", "Uncaught on ${thread.name}", throwable)
            runCatching {
                FirebaseCrashlytics.getInstance().recordException(throwable)
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
