package com.tvremote.app.util

import com.tvremote.app.analytics.AnalyticsEvents

object FirebaseLogUtils {
    fun logEvent(eventName: String, param: String = "") {
        val params = if (param.isBlank()) emptyMap() else mapOf("detail" to param)
        AnalyticsEvents.logEvent(eventName, params)
    }
}
