package com.tvremote.app.util

import android.os.Handler
import android.os.Looper

object SafeRun {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun run(tag: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            AppLogger.e(tag, "Unhandled error", e)
        }
    }

    fun <T> runCatching(tag: String, default: T, block: () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            AppLogger.e(tag, "Operation failed", e)
            default
        }
    }

    fun runOnMain(tag: String, block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            run(tag, block)
        } else {
            mainHandler.post { run(tag, block) }
        }
    }

    fun invoke(tag: String, callback: (() -> Unit)?) {
        if (callback == null) return
        run(tag, callback)
    }

    fun <T> invoke(tag: String, callback: ((T) -> Unit)?, value: T) {
        if (callback == null) return
        run(tag) { callback(value) }
    }
}
