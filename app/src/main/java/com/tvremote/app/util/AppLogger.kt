package com.tvremote.app.util

import android.util.Log

object AppLogger {
    private const val PREFIX = "TvRemote"

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e("$PREFIX:$tag", message, throwable)
        } else {
            Log.e("$PREFIX:$tag", message)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w("$PREFIX:$tag", message, throwable)
        } else {
            Log.w("$PREFIX:$tag", message)
        }
    }

    fun d(tag: String, message: String) {
        Log.d("$PREFIX:$tag", message)
    }
}
