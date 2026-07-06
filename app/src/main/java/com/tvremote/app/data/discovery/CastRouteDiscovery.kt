package com.tvremote.app.data.discovery

import android.content.Context
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastDevice
import com.tvremote.app.data.model.DiscoveredTv
import com.tvremote.app.util.AppLogger
import com.tvremote.app.util.SafeRun

object CastRouteDiscovery {
    private const val TAG = "CastRouteDiscovery"

    fun discover(context: Context): List<DiscoveredTv> = SafeRun.runCatching(TAG, emptyList()) {
        val router = MediaRouter.getInstance(context.applicationContext)
        router.routes.mapNotNull { route ->
            if (route.isDefault || !route.isEnabled) return@mapNotNull null
            val name = route.name?.toString()?.trim().orEmpty()
            if (name.isBlank()) return@mapNotNull null

            val castDevice = CastDevice.getFromBundle(route.extras)
            val host = castDevice?.ipAddress?.hostAddress?.takeIf { it.isNotBlank() }
                ?: extractIpv4(route.description?.toString().orEmpty())
            if (host.isNullOrBlank()) {
                AppLogger.d(TAG, "Cast route '$name' has no IP yet")
                return@mapNotNull null
            }
            DiscoveredTv(
                name = name,
                host = host,
                port = castDevice?.servicePort ?: 8009,
            )
        }.distinctBy { it.host }
    }

    private fun extractIpv4(text: String): String? {
        if (text.isBlank()) return null
        return IPV4.find(text)?.value
    }

    private val IPV4 = Regex("""\b(\d{1,3}(?:\.\d{1,3}){3})\b""")
}
