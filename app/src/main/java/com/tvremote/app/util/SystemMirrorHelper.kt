package com.tvremote.app.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.tvremote.app.R

object SystemMirrorHelper {

    /**
     * Opens the best available system screen for Cast / wireless display / mirroring.
     * Returns true if a settings screen was launched.
     */
    fun openMirrorSettings(context: Context): Boolean {
        val candidates = listOf(
            Intent(Settings.ACTION_CAST_SETTINGS),
            Intent("android.settings.CAST_SETTINGS"),
            Intent("android.settings.WIRELESS_DISPLAY_SETTINGS"),
            Intent(Settings.ACTION_WIFI_SETTINGS),
            Intent(Settings.ACTION_DISPLAY_SETTINGS),
        )
        val pm = context.packageManager
        for (intent in candidates) {
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            }
        }
        return false
    }

    fun fallbackMessage(context: Context): String =
        context.getString(R.string.mirror_system_settings_unavailable)
}
