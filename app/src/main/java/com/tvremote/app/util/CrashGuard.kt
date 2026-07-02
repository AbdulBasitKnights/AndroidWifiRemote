package com.tvremote.app.util

import android.app.Application

object CrashGuard {
    fun install(application: Application) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.e("CrashGuard", "Uncaught on ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
