package com.tvremote.app.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import com.tvremote.app.data.model.DiscoveredTv

class TvDiscovery(context: Context) {
    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val resolving = mutableSetOf<String>()
    private val found = linkedMapOf<String, DiscoveredTv>()

    var onDeviceFound: ((DiscoveredTv) -> Unit)? = null
    var onScanFinished: ((List<DiscoveredTv>) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private val serviceTypes = listOf(
        "_androidtvremote2._tcp.",
        "_androidtvremote._tcp.",
    )

    fun startScan(durationMs: Long = 12_000) {
        stopScan()
        found.clear()
        resolving.clear()
        discoverNext(0, durationMs)
    }

    fun stopScan() {
        discoveryListener?.let { listener ->
            try {
                nsdManager?.stopServiceDiscovery(listener)
            } catch (_: Exception) {
            }
        }
        discoveryListener = null
    }

    private fun discoverNext(index: Int, durationMs: Long) {
        if (index >= serviceTypes.size) {
            mainHandler.postDelayed({
                stopScan()
                onScanFinished?.invoke(found.values.toList())
            }, durationMs)
            return
        }

        val serviceType = serviceTypes[index]
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceName.contains("NsdChat")) return
                val key = "${serviceInfo.serviceName}:${serviceInfo.serviceType}"
                if (key in resolving) return
                resolving.add(key)

                nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        resolving.remove(key)
                    }

                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        resolving.remove(key)
                        val host = resolved.host?.hostAddress
                            ?: resolved.hostname
                            ?: return
                        val tv = DiscoveredTv(
                            name = cleanServiceName(resolved.serviceName),
                            host = host,
                            port = resolved.port,
                        )
                        found[host] = tv
                        mainHandler.post { onDeviceFound?.invoke(tv) }
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                discoverNext(index + 1, durationMs)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        try {
            nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            mainHandler.postDelayed({
                stopScan()
                if (found.isEmpty() && index + 1 < serviceTypes.size) {
                    discoverNext(index + 1, durationMs)
                } else {
                    onScanFinished?.invoke(found.values.toList())
                }
            }, durationMs)
        } catch (e: Exception) {
            onError?.invoke(e.message ?: "Scan failed")
            discoverNext(index + 1, durationMs)
        }
    }

    private fun cleanServiceName(serviceName: String): String {
        return serviceName
            .substringBefore("._androidtvremote2.")
            .substringBefore("._androidtvremote.")
            .replace('-', ' ')
            .replace('_', ' ')
            .trim()
            .ifBlank { "Android TV" }
    }
}
