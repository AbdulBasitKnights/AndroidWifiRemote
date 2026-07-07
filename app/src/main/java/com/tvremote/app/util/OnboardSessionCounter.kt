package com.tvremote.app.util

import android.content.Context

object OnboardSessionCounter {
    private const val PREFS = "obsession_pref"
    private const val KEY = "session_count"

    fun increment(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY, prefs.getInt(KEY, 0) + 1).apply()
    }

    fun getCounter(context: Context): Int {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 0)
    }
}
