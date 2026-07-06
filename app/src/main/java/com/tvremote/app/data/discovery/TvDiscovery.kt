package com.tvremote.app.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.tvremote.app.data.model.DiscoveredTv
import com.tvremote.app.util.AppLogger
import com.tvremote.app.util.SafeRun
import java.util.concurrent.Executors

class TvDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val nsdManager: NsdManager? = appContext.getSystemService(NsdManager::class.java)
    private val wifiManager: WifiManager? = appContext.getSystemService(WifiManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()
    private val serviceCallbacks = mutableListOf<NsdManager.ServiceInfoCallback>()
    private var multicastLock: WifiManager.MulticastLock? = null
    private val resolving = mutableSetOf<String>()
    private val found = linkedMapOf<String, DiscoveredTv>()

    var onDeviceFound: ((DiscoveredTv) -> Unit)? = null
    var onScanFinished: ((List<DiscoveredTv>) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private val serviceTypes = listOf(
        "_androidtvremote2._tcp.",
        "_androidtvremote._tcp.",
        "_googlecast._tcp.",
    )

    fun startScan(durationMs: Long = 15_000) {
        SafeRun.run(TAG) {
            if (nsdManager == null) {
                mergeCastRoutes()
                notifyScanFinished()
                notifyError("Network discovery unavailable on this device")
                return@run
            }
            stopScan()
            found.clear()
            resolving.clear()
            mergeCastRoutes()
            acquireMulticastLock()

            serviceTypes.forEach { type ->
                startDiscoveryForType(type)
            }

            mainHandler.postDelayed({
                SafeRun.run(TAG) {
                    stopScan()
                    mergeCastRoutes()
                    notifyScanFinished()
                }
            }, durationMs)
        }
    }

    fun stopScan() {
        SafeRun.run(TAG) {
            mainHandler.removeCallbacksAndMessages(null)
            serviceCallbacks.toList().forEach { callback ->
                try {
                    nsdManager?.unregisterServiceInfoCallback(callback)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "unregisterServiceInfoCallback failed", e)
                }
            }
            serviceCallbacks.clear()

            discoveryListeners.toList().forEach { listener ->
                try {
                    nsdManager?.stopServiceDiscovery(listener)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "stopServiceDiscovery failed", e)
                }
            }
            discoveryListeners.clear()
            resolving.clear()
            releaseMulticastLock()
        }
    }

    private fun mergeCastRoutes() {
        CastRouteDiscovery.discover(appContext).forEach { tv ->
            if (tv.host !in found) {
                found[tv.host] = tv
                mainHandler.post { SafeRun.invoke(TAG, onDeviceFound, tv) }
            }
        }
    }

    private fun startDiscoveryForType(serviceType: String) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                SafeRun.run(TAG) {
                    if (serviceInfo.serviceName.contains("NsdChat")) return@run
                    resolveService(serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, errorCode: Int) {
                AppLogger.w(TAG, "Discovery failed for $type code=$errorCode")
            }
            override fun onStopDiscoveryFailed(type: String, errorCode: Int) = Unit
        }
        discoveryListeners.add(listener)
        try {
            nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            AppLogger.w(TAG, "discoverServices failed for $serviceType", e)
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        val key = "${serviceInfo.serviceName}:${serviceInfo.serviceType}"
        if (key in resolving) return
        resolving.add(key)

        val immediateHost = hostFrom(serviceInfo)
        if (!immediateHost.isNullOrBlank()) {
            publishResolved(serviceInfo, immediateHost, key)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            resolveServiceApi34(serviceInfo, key)
        } else {
            resolveServiceLegacy(serviceInfo, key)
        }
    }

    private fun resolveServiceLegacy(serviceInfo: NsdServiceInfo, key: String) {
        try {
            nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    resolving.remove(key)
                    AppLogger.w(TAG, "resolve failed ${info.serviceName} code=$errorCode")
                }

                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val host = hostFrom(resolved)
                    if (host.isNullOrBlank()) {
                        resolving.remove(key)
                        return
                    }
                    publishResolved(resolved, host, key)
                }
            })
        } catch (e: Exception) {
            resolving.remove(key)
            AppLogger.w(TAG, "resolveService failed", e)
        }
    }

    private fun resolveServiceApi34(serviceInfo: NsdServiceInfo, key: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            resolving.remove(key)
            return
        }
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceUpdated(info: NsdServiceInfo) {
                val host = hostFrom(info)
                if (!host.isNullOrBlank()) {
                    publishResolved(info, host, key)
                }
                unregisterCallback(this)
            }

            override fun onServiceLost() {
                resolving.remove(key)
                unregisterCallback(this)
            }

            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                resolving.remove(key)
                AppLogger.w(TAG, "ServiceInfoCallback failed code=$errorCode for ${serviceInfo.serviceName}")
                unregisterCallback(this)
                resolveServiceLegacy(serviceInfo, key)
            }

            override fun onServiceInfoCallbackUnregistered() {
                resolving.remove(key)
            }
        }
        serviceCallbacks.add(callback)
        try {
            nsdManager?.registerServiceInfoCallback(serviceInfo, executor, callback)
        } catch (e: Exception) {
            serviceCallbacks.remove(callback)
            resolving.remove(key)
            AppLogger.w(TAG, "registerServiceInfoCallback failed", e)
            resolveServiceLegacy(serviceInfo, key)
        }
    }

    private fun unregisterCallback(callback: NsdManager.ServiceInfoCallback) {
        try {
            nsdManager?.unregisterServiceInfoCallback(callback)
        } catch (e: Exception) {
            AppLogger.w(TAG, "unregisterServiceInfoCallback failed", e)
        }
        serviceCallbacks.remove(callback)
    }

    private fun publishResolved(info: NsdServiceInfo, host: String, key: String) {
        SafeRun.run(TAG) {
            resolving.remove(key)
            val tv = DiscoveredTv(
                name = cleanServiceName(info.serviceName, info.serviceType),
                host = host,
                port = info.port,
            )
            found[host] = tv
            mainHandler.post { SafeRun.invoke(TAG, onDeviceFound, tv) }
        }
    }

    private fun hostFrom(info: NsdServiceInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.hostAddresses.firstOrNull()?.hostAddress?.takeIf { it.isNotBlank() }?.let { return it }
        }
        info.host?.hostAddress?.takeIf { it.isNotBlank() }?.let { return it }
        return info.hostname?.takeIf { it.isNotBlank() }
    }

    private fun acquireMulticastLock() {
        SafeRun.run(TAG) {
            if (multicastLock?.isHeld == true) return@run
            multicastLock = wifiManager?.createMulticastLock("tvremote_nsd")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseMulticastLock() {
        SafeRun.run(TAG) {
            multicastLock?.let { lock ->
                if (lock.isHeld) {
                    try {
                        lock.release()
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "multicast lock release failed", e)
                    }
                }
            }
            multicastLock = null
        }
    }

    private fun notifyScanFinished() {
        mainHandler.post {
            SafeRun.invoke(TAG, onScanFinished, found.values.toList())
        }
    }

    private fun notifyError(message: String) {
        mainHandler.post {
            SafeRun.invoke(TAG, onError, message)
        }
    }

    private fun cleanServiceName(serviceName: String, serviceType: String): String {
        return SafeRun.runCatching(TAG, "Android TV") {
            serviceName
                .substringBefore("._androidtvremote2.")
                .substringBefore("._androidtvremote.")
                .substringBefore("._googlecast.")
                .replace('-', ' ')
                .replace('_', ' ')
                .trim()
                .ifBlank { "Android TV" }
        }
    }

    companion object {
        private const val TAG = "TvDiscovery"
    }
}
