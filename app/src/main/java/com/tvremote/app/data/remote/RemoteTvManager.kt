package com.tvremote.app.data.remote

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.tvremote.control.CertManager
import com.tvremote.control.CryptoManager
import com.tvremote.control.PairingManager
import com.tvremote.control.RemoteManager
import com.tvremote.control.TlsManager
import com.tvremote.control.commands.DeepLink
import com.tvremote.control.commands.Key
import com.tvremote.control.commands.KeyPress
import com.tvremote.control.misc.DefaultLogger
import com.tvremote.control.misc.TvRemoteError
import com.tvremote.control.network.command.DeviceInfo
import com.tvremote.app.util.AppLogger
import com.tvremote.app.util.SafeRun
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RemoteTvManager(context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val certManager = CertManager(context)
    private val cryptoManager = CryptoManager()
    private val tlsManager: TlsManager
    private val pairingManager: PairingManager
    private val remoteManager: RemoteManager
    private val pairingStarted = AtomicBoolean(false)
    private val waitingForCode = AtomicBoolean(false)
    private val explicitPairingRequested = AtomicBoolean(false)

    var pairingStateChanged: ((String) -> Unit)? = null
    var remoteStateChanged: ((String) -> Unit)? = null
    var onWaitingForCodeChanged: ((Boolean) -> Unit)? = null
    var onPaired: ((String) -> Unit)? = null

    private var pendingHost: String = ""
    private var isPairedDevice = false
    private var pausedForCast = false
    private val clientName = "Android TV Remote"
    private val serviceName = "atvremote"

    private var pairingFallback: ScheduledFuture<*>? = null
    private var reconnectTask: ScheduledFuture<*>? = null

    fun isWaitingForPairingCode(): Boolean = waitingForCode.get()

    fun isPaired(): Boolean = isPairedDevice

    fun isPausedForCast(): Boolean = pausedForCast

    fun currentHost(): String = pendingHost

    fun loadSavedSession(host: String, paired: Boolean) {
        pendingHost = host.trim()
        isPairedDevice = paired
    }

    fun markPaired(host: String) {
        pendingHost = host.trim()
        isPairedDevice = true
        explicitPairingRequested.set(false)
    }

    fun resetPairingState() {
        isPairedDevice = false
    }

    private fun notifyPairingState(message: String) {
        mainHandler.post { SafeRun.invoke(TAG) { pairingStateChanged?.invoke(message) } }
    }

    private fun notifyRemoteState(message: String) {
        mainHandler.post { SafeRun.invoke(TAG) { remoteStateChanged?.invoke(message) } }
    }

    private fun notifyWaitingForCode(waiting: Boolean) {
        mainHandler.post { SafeRun.invoke(TAG) { onWaitingForCodeChanged?.invoke(waiting) } }
    }

    private fun notifyPaired(host: String) {
        isPairedDevice = true
        explicitPairingRequested.set(false)
        mainHandler.post { SafeRun.invoke(TAG) { onPaired?.invoke(host) } }
    }

    private fun executeSafe(block: () -> Unit) {
        executor.execute {
            try {
                block()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Remote task failed", e)
                notifyRemoteState("Error: ${e.message ?: "Unknown error"}")
            }
        }
    }

    init {
        SafeRun.run(TAG) {
            certManager.ensureCertificates(clientName)
        }

        cryptoManager.clientPublicKeyProvider = {
            certManager.getClientPublicKey()
        }

        tlsManager = TlsManager(keyStoreProvider = {
            certManager.loadKeyStore()
        })

        tlsManager.onServerCertificate = { certificate ->
            cryptoManager.serverPublicKeyProvider = {
                certManager.getServerPublicKey(certificate)
            }
        }

        pairingManager = PairingManager(tlsManager, cryptoManager, DefaultLogger())
        remoteManager = RemoteManager(
            tlsManager,
            DeviceInfo("client", clientName, "1.0.0", "android_tv_remote", "1"),
            DefaultLogger(),
        )

        pairingManager.stateChanged = { state ->
            SafeRun.run(TAG) {
                if (!pausedForCast) {
                    waitingForCode.set(state is PairingManager.PairingState.WaitingCode)
                    notifyWaitingForCode(waitingForCode.get())
                    notifyPairingState(state.toDisplayString())

                    when (state) {
                        is PairingManager.PairingState.SuccessPaired -> {
                            pairingStarted.set(false)
                            waitingForCode.set(false)
                            notifyWaitingForCode(false)
                            notifyPaired(pendingHost)
                            remoteManager.disconnect()
                            connectRemoteOnlyInternal()
                        }
                        is PairingManager.PairingState.SecretSent -> {
                            notifyPairingState("Submitting pairing code…")
                        }
                        is PairingManager.PairingState.Error -> {
                            waitingForCode.set(false)
                            notifyWaitingForCode(false)
                            pairingStarted.set(false)
                            explicitPairingRequested.set(false)
                        }
                        else -> Unit
                    }
                }
            }
        }

        remoteManager.stateChanged = { state ->
            SafeRun.run(TAG) {
                if (!pausedForCast || state is RemoteManager.RemoteState.Paired) {
                    notifyRemoteState(state.toDisplayString())
                    when (state) {
                        is RemoteManager.RemoteState.Connected -> {
                            if (!waitingForCode.get() && !isPairedDevice && explicitPairingRequested.get()) {
                                schedulePairingFallback()
                            }
                        }
                        is RemoteManager.RemoteState.Paired -> {
                            pairingStarted.set(false)
                            waitingForCode.set(false)
                            explicitPairingRequested.set(false)
                            notifyWaitingForCode(false)
                            cancelPairingFallback()
                            cancelReconnect()
                            notifyPaired(pendingHost)
                        }
                        is RemoteManager.RemoteState.Error -> {
                            cancelPairingFallback()
                            handleRemoteError(state.error)
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun handleRemoteError(error: TvRemoteError) {
        if (pausedForCast) return

        if (isPairedDevice) {
            notifyRemoteState("Connection lost — reconnecting…")
            scheduleReconnect()
            return
        }

        if (explicitPairingRequested.get()) {
            startPairingIfNeeded(error)
        } else {
            notifyRemoteState("Not connected — tap TV in Settings to pair or reconnect")
        }
    }

    private fun schedulePairingFallback() {
        if (pausedForCast || isPairedDevice || !explicitPairingRequested.get()) return
        cancelPairingFallback()
        pairingFallback = scheduler.schedule({
            SafeRun.run(TAG) {
                if (!pausedForCast && !isPairedDevice && explicitPairingRequested.get() && !waitingForCode.get()) {
                    notifyPairingState("Remote connected but not paired — starting pairing…")
                    startPairing(force = true)
                }
            }
        }, 8, TimeUnit.SECONDS)
    }

    private fun cancelPairingFallback() {
        pairingFallback?.cancel(false)
        pairingFallback = null
    }

    private fun scheduleReconnect() {
        if (pausedForCast || pendingHost.isEmpty() || !isPairedDevice) return
        cancelReconnect()
        reconnectTask = scheduler.schedule({
            SafeRun.run(TAG) {
                if (!pausedForCast && isPairedDevice && pendingHost.isNotEmpty()) {
                    connectRemoteOnlyInternal()
                }
            }
        }, 3, TimeUnit.SECONDS)
    }

    private fun cancelReconnect() {
        reconnectTask?.cancel(false)
        reconnectTask = null
    }

    fun connect(host: String) {
        pendingHost = host.trim()
        if (pendingHost.isEmpty()) return
        executeSafe {
            when {
                pausedForCast -> notifyRemoteState("Remote paused while casting")
                waitingForCode.get() -> notifyPairingState(
                    "Code already on TV — enter it below and tap Complete Pairing",
                )
                else -> connectRemoteOnlyInternal()
            }
        }
    }

    private fun connectRemoteOnlyInternal() {
        if (pausedForCast) return
        pairingStarted.set(false)
        explicitPairingRequested.set(false)
        cancelPairingFallback()
        cancelReconnect()
        pairingManager.disconnect()
        remoteManager.disconnect()
        notifyRemoteState("Connecting to $pendingHost…")
        remoteManager.connect(pendingHost)
    }

    fun pairOnly(host: String? = null) {
        executeSafe {
            when {
                pausedForCast -> notifyPairingState("Finish or stop casting before pairing")
                else -> {
                    host?.trim()?.takeIf { it.isNotEmpty() }?.let { pendingHost = it }
                    when {
                        pendingHost.isEmpty() -> Unit
                        waitingForCode.get() -> notifyPairingState(
                            "Already waiting for code — enter it and tap Complete Pairing",
                        )
                        isPairedDevice -> {
                            notifyPairingState("Already paired — reconnecting remote instead")
                            connectRemoteOnlyInternal()
                        }
                        else -> {
                            explicitPairingRequested.set(true)
                            startPairing(force = true)
                        }
                    }
                }
            }
        }
    }

    fun restartPairing(host: String? = null) {
        executeSafe {
            when {
                pausedForCast -> notifyPairingState("Stop casting before pairing again")
                else -> {
                    host?.trim()?.takeIf { it.isNotEmpty() }?.let { pendingHost = it }
                    if (pendingHost.isNotEmpty()) {
                        waitingForCode.set(false)
                        notifyWaitingForCode(false)
                        explicitPairingRequested.set(true)
                        isPairedDevice = false
                        startPairing(force = true)
                    }
                }
            }
        }
    }

    private fun startPairingIfNeeded(error: TvRemoteError) {
        val needsPairing = when (error) {
            is TvRemoteError.ConnectionWaitingError,
            is TvRemoteError.ConnectionFailed,
            is TvRemoteError.ReceiveDataError,
            is TvRemoteError.SendDataError,
            -> true
            else -> false
        }
        if (needsPairing && explicitPairingRequested.get() && !isPairedDevice) {
            startPairing(force = false)
        }
    }

    private fun startPairing(force: Boolean) {
        if (pausedForCast) return
        if (!force && !explicitPairingRequested.get()) return
        if (!force && !pairingStarted.compareAndSet(false, true)) return
        if (force) pairingStarted.set(true)

        cancelPairingFallback()
        cancelReconnect()
        remoteManager.disconnect()
        pairingManager.disconnect()
        notifyPairingState("Starting pairing on port ${PairingManager.PAIRING_PORT}…")
        pairingManager.connect(pendingHost, clientName, serviceName)
    }

    fun submitPairingCode(code: String): Boolean {
        val normalized = code.trim().uppercase()
        if (normalized.length != 6) {
            notifyPairingState("Enter the full 6-character code from your TV")
            return false
        }
        if (!waitingForCode.get()) {
            notifyPairingState("Select your TV and wait for the code on screen")
            return false
        }
        executeSafe {
            pairingManager.sendSecret(normalized)
        }
        return true
    }

    fun pauseForCast() {
        pausedForCast = true
        executeSafe {
            pairingStarted.set(false)
            explicitPairingRequested.set(false)
            waitingForCode.set(false)
            notifyWaitingForCode(false)
            cancelPairingFallback()
            cancelReconnect()
            remoteManager.disconnect()
            pairingManager.disconnect()
            notifyRemoteState("Paused while casting")
            notifyPairingState("idle")
        }
    }

    fun resumeAfterCast() {
        pausedForCast = false
        executeSafe {
            if (pendingHost.isNotEmpty()) {
                if (isPairedDevice) {
                    notifyRemoteState("Resuming remote after cast…")
                    connectRemoteOnlyInternal()
                } else {
                    notifyRemoteState("idle")
                }
            }
        }
    }

    fun sendKey(key: Key) {
        executeSafe {
            if (pausedForCast) {
                notifyRemoteState("Remote paused while casting")
            } else {
                remoteManager.send(KeyPress(key))
            }
        }
    }

    fun volUp() = sendKey(Key.KEYCODE_VOLUME_UP)
    fun volDown() = sendKey(Key.KEYCODE_VOLUME_DOWN)
    fun channelUp() = sendKey(Key.KEYCODE_CHANNEL_UP)
    fun channelDown() = sendKey(Key.KEYCODE_CHANNEL_DOWN)
    fun power() = sendKey(Key.KEYCODE_POWER)
    fun mute() = sendKey(Key.KEYCODE_MUTE)
    fun playPause() = sendKey(Key.KEYCODE_MEDIA_PLAY_PAUSE)
    fun rewind() = sendKey(Key.KEYCODE_MEDIA_REWIND)
    fun forward() = sendKey(Key.KEYCODE_MEDIA_FAST_FORWARD)
    fun voiceSearch() = sendKey(Key.KEYCODE_SEARCH)
    fun tvInput() = sendKey(Key.KEYCODE_TV_INPUT)
    fun apps() = sendKey(Key.KEYCODE_APP_SWITCH)

    fun runNetflix() = runDeepLink("https://www.netflix.com/title")
    fun runYouTube() = runDeepLink("https://www.youtube.com")
    fun runPrime() = runDeepLink("https://app.primevideo.com")
    fun runHotstar() = runDeepLink("https://www.hotstar.com")

    private fun runDeepLink(url: String) {
        executeSafe {
            if (pausedForCast) {
                notifyRemoteState("Remote paused while casting")
            } else {
                remoteManager.send(DeepLink(url))
            }
        }
    }

    fun disconnect() {
        executeSafe {
            pairingStarted.set(false)
            explicitPairingRequested.set(false)
            waitingForCode.set(false)
            notifyWaitingForCode(false)
            cancelPairingFallback()
            cancelReconnect()
            remoteManager.disconnect()
            pairingManager.disconnect()
        }
    }

    companion object {
        private const val TAG = "RemoteTvManager"
    }
}
