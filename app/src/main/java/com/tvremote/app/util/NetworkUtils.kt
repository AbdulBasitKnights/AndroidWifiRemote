package com.tvremote.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.regex.Pattern

object NetworkUtils {
    private val IPV4_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$",
    )

    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isValidIpv4(host: String): Boolean = IPV4_PATTERN.matcher(host).matches()

    fun isConnectableHost(host: String): Boolean {
        if (host.isBlank()) return false
        if (isValidIpv4(host)) return true
        return host.matches(Regex("^[a-zA-Z0-9.-]+$"))
    }

    fun getWifiIpv4Address(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .map { it.hostAddress }
                .firstOrNull { address ->
                    address != null && !address.startsWith("169.254.")
                }
        } catch (_: Exception) {
            null
        }
    }
}
