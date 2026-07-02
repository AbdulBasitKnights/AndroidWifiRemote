package com.tvremote.app.data.session

import android.content.Context
import com.tvremote.app.util.SafeRun

class PairingStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun savedHost(): String = SafeRun.runCatching(TAG, "") {
        prefs.getString(KEY_HOST, "").orEmpty()
    }

    fun isPaired(): Boolean = SafeRun.runCatching(TAG, false) {
        prefs.getBoolean(KEY_PAIRED, false)
    }

    fun isPairedWith(host: String): Boolean =
        isPaired() && savedHost().equals(host.trim(), ignoreCase = false)

    fun markPaired(host: String) {
        SafeRun.run(TAG) {
            prefs.edit()
                .putString(KEY_HOST, host.trim())
                .putBoolean(KEY_PAIRED, true)
                .apply()
        }
    }

    fun clear() {
        SafeRun.run(TAG) {
            prefs.edit().clear().apply()
        }
    }

    companion object {
        private const val TAG = "PairingStore"
        private const val PREFS_NAME = "tv_remote_session"
        private const val KEY_HOST = "paired_host"
        private const val KEY_PAIRED = "is_paired"
    }
}
