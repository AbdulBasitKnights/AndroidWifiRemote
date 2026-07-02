package com.tvremote.app.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import com.tvremote.app.data.model.DiscoveredTv
import com.tvremote.app.util.AppLogger
import com.tvremote.app.util.SafeRun

class TvDiscovery(context: Context) {
    private val nsdManager: NsdManager? = context.getSystemService(NsdManager::class.java)
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
        SafeRun.run(TAG) {
            if (nsdManager == null) {
                notifyError("Network discovery unavailable on this device")
                return@run
            }
            stopScan()
            found.clear()
            resolving.clear()
            discoverNext(0, durationMs)
        }
    }

    fun stopScan() {
        SafeRun.run(TAG) {
            discoveryListener?.let { listener ->
                try {
                    nsdManager?.stopServiceDiscovery(listener)
                } catch (e: Exception) {
                    AppLogger.w(TAG, "stopServiceDiscovery failed", e)
                }
            }
            discoveryListener = null
        }
    }

    private fun discoverNext(index: Int, durationMs: Long) {
        if (nsdManager == null) {
            notifyError("Network discovery unavailable")
            return
        }
        if (index >= serviceTypes.size) {
            mainHandler.postDelayed({
                SafeRun.run(TAG) {
                    stopScan()
                    notifyScanFinished()
                }
            }, durationMs)
            return
        }

        val serviceType = serviceTypes[index]
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                SafeRun.run(TAG) {
                    if (serviceInfo.serviceName.contains("NsdChat")) return@run
                    val key = "${serviceInfo.serviceName}:${serviceInfo.serviceType}"
                    if (key in resolving) return@run
                    resolving.add(key)

                    try {
                        nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                resolving.remove(key)
                            }

                            override fun onServiceResolved(resolved: NsdServiceInfo) {
                                SafeRun.run(TAG) {
                                    resolving.remove(key)
                                    val host = resolved.host?.hostAddress
                                        ?: resolved.hostname
                                        ?: return@run
                                    val tv = DiscoveredTv(
                                        name = cleanServiceName(resolved.serviceName),
                                        host = host,
                                        port = resolved.port,
                                    )
                                    found[host] = tv
                                    mainHandler.post {
                                        SafeRun.invoke(TAG, onDeviceFound, tv)
                                    }
                                }
                            }
                        })
                    } catch (e: Exception) {
                        resolving.remove(key)
                        AppLogger.w(TAG, "resolveService failed", e)
                    }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                discoverNext(index + 1, durationMs)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            mainHandler.postDelayed({
                SafeRun.run(TAG) {
                    stopScan()
                    if (found.isEmpty() && index + 1 < serviceTypes.size) {
                        discoverNext(index + 1, durationMs)
                    } else {
                        notifyScanFinished()
                    }
                }
            }, durationMs)
        } catch (e: Exception) {
            notifyError(e.message ?: "Scan failed")
            discoverNext(index + 1, durationMs)
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

    private fun cleanServiceName(serviceName: String): String {
        return SafeRun.runCatching(TAG, "Android TV") {
            serviceName
                .substringBefore("._androidtvremote2.")
                .substringBefore("._androidtvremote.")
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
