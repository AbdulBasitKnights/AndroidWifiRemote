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
    /** User wants connection held until explicit disconnect or app close. */
    private var connectionHeld = false
    private var userDisconnectRequested = false
    /** Only true after loadSavedSession — one cold-start connect, no retry loops. */
    private var autoConnectAllowed = false
    private val connectionBusy = AtomicBoolean(false)

    fun isConnectionBusy(): Boolean = connectionBusy.get()
    private val clientName = "Android TV Remote"
    private val serviceName = "atvremote"

    private var pairingFallback: ScheduledFuture<*>? = null

    fun isWaitingForPairingCode(): Boolean = waitingForCode.get()

    fun isPaired(): Boolean = isPairedDevice

    fun isConnectionHeld(): Boolean = connectionHeld

    fun isSessionReady(): Boolean = remoteManager.isSessionReady()

    fun currentHost(): String = pendingHost

    fun loadSavedSession(host: String, paired: Boolean) {
        pendingHost = host.trim()
        isPairedDevice = paired
        if (paired) {
            connectionHeld = true
            userDisconnectRequested = false
            autoConnectAllowed = true
        }
    }

    fun markPaired(host: String) {
        pendingHost = host.trim()
        isPairedDevice = true
        connectionHeld = true
        userDisconnectRequested = false
        explicitPairingRequested.set(false)
    }

    fun resetPairingState() {
        isPairedDevice = false
        connectionHeld = false
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
        connectionHeld = true
        userDisconnectRequested = false
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
                waitingForCode.set(state is PairingManager.PairingState.WaitingCode)
                notifyWaitingForCode(waitingForCode.get())
                notifyPairingState(state.toDisplayString())

                when (state) {
                    is PairingManager.PairingState.SuccessPaired -> {
                        pairingStarted.set(false)
                        waitingForCode.set(false)
                        notifyWaitingForCode(false)
                        notifyPaired(pendingHost)
                        pairingManager.disconnect()
                        connectRemoteOnlyInternal(force = true)
                    }
                    is PairingManager.PairingState.SecretSent -> {
                        notifyPairingState("Submitting pairing code…")
                    }
                    is PairingManager.PairingState.WaitingCode -> {
                        connectionBusy.set(false)
                    }
                    is PairingManager.PairingState.Error -> {
                        connectionBusy.set(false)
                        waitingForCode.set(false)
                        notifyWaitingForCode(false)
                        pairingStarted.set(false)
                        explicitPairingRequested.set(false)
                    }
                    else -> Unit
                }
            }
        }

        remoteManager.stateChanged = { state ->
            SafeRun.run(TAG) {
                notifyRemoteState(state.toDisplayString())
                when (state) {
                    is RemoteManager.RemoteState.Connected -> {
                        if (!waitingForCode.get() && !isPairedDevice && explicitPairingRequested.get()) {
                            schedulePairingFallback()
                        }
                    }
                    is RemoteManager.RemoteState.Paired -> {
                        autoConnectAllowed = false
                        connectionBusy.set(false)
                        pairingStarted.set(false)
                        waitingForCode.set(false)
                        explicitPairingRequested.set(false)
                        notifyWaitingForCode(false)
                        cancelPairingFallback()
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

    private fun handleRemoteError(error: TvRemoteError) {
        autoConnectAllowed = false
        connectionBusy.set(false)
        if (userDisconnectRequested) return
        if (!connectionHeld) {
            notifyRemoteState("Not connected — pair your TV in Settings")
            return
        }
        AppLogger.w(TAG, "Remote error (no auto-reconnect): ${error.toDisplayString()}")
        notifyRemoteState("Connection interrupted — tap Reconnect Remote in Settings")
    }

    private fun schedulePairingFallback() {
        if (isPairedDevice || !explicitPairingRequested.get()) return
        cancelPairingFallback()
        pairingFallback = scheduler.schedule({
            SafeRun.run(TAG) {
                if (!isPairedDevice && explicitPairingRequested.get() && !waitingForCode.get()) {
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

    /** Open connection once after app launch with saved session. Never auto-retry after errors. */
    fun ensureConnected() {
        if (!autoConnectAllowed || userDisconnectRequested || pendingHost.isEmpty() || !connectionHeld) return
        executeSafe {
            if (remoteManager.isSessionReady()) {
                autoConnectAllowed = false
                notifyRemoteState(remoteManager.currentRemoteState().toDisplayString())
            } else if (!remoteManager.isConnecting() && !remoteManager.isHandshakeInProgress()) {
                connectRemoteOnlyInternal(force = false)
            }
        }
    }

    fun connect(host: String) {
        pendingHost = host.trim()
        if (pendingHost.isEmpty()) return
        userDisconnectRequested = false
        connectionHeld = true
        executeSafe {
            when {
                waitingForCode.get() -> notifyPairingState(
                    "Code already on TV — enter it below and tap Complete Pairing",
                )
                remoteManager.isSessionReady() -> {
                    connectionBusy.set(false)
                    notifyRemoteState(remoteManager.currentRemoteState().toDisplayString())
                }
                else -> connectRemoteOnlyInternal(force = false)
            }
        }
    }

    /** Force socket reconnect — use after user disconnect or connection drop. */
    fun reconnectTo(host: String? = null) {
        host?.trim()?.takeIf { it.isNotEmpty() }?.let { pendingHost = it }
        if (pendingHost.isEmpty()) return
        userDisconnectRequested = false
        connectionHeld = true
        autoConnectAllowed = false
        executeSafe {
            connectRemoteOnlyInternal(force = true)
        }
    }

    private fun connectRemoteOnlyInternal(force: Boolean) {
        if (userDisconnectRequested) return
        if (!force && remoteManager.isSessionReady()) {
            connectionBusy.set(false)
            notifyRemoteState(remoteManager.currentRemoteState().toDisplayString())
            return
        }
        if (remoteManager.isConnecting() || (!force && remoteManager.isHandshakeInProgress())) return

        connectionBusy.set(true)
        pairingStarted.set(false)
        explicitPairingRequested.set(false)
        cancelPairingFallback()
        pairingManager.disconnect()
        if (force || !remoteManager.isSessionReady()) {
            remoteManager.disconnect()
        }
        notifyRemoteState("Connecting to $pendingHost…")
        remoteManager.connect(pendingHost)
    }

    fun pairOnly(host: String? = null) {
        executeSafe {
            host?.trim()?.takeIf { it.isNotEmpty() }?.let { pendingHost = it }
            when {
                pendingHost.isEmpty() -> Unit
                waitingForCode.get() -> notifyPairingState(
                    "Already waiting for code — enter it and tap Complete Pairing",
                )
                isPairedDevice && remoteManager.isSessionReady() -> notifyRemoteState(
                    remoteManager.currentRemoteState().toDisplayString(),
                )
                isPairedDevice -> connectRemoteOnlyInternal(force = false)
                else -> {
                    userDisconnectRequested = false
                    connectionHeld = true
                    explicitPairingRequested.set(true)
                    startPairing(force = true)
                }
            }
        }
    }

    fun restartPairing(host: String? = null) {
        executeSafe {
            host?.trim()?.takeIf { it.isNotEmpty() }?.let { pendingHost = it }
            if (pendingHost.isNotEmpty()) {
                waitingForCode.set(false)
                notifyWaitingForCode(false)
                explicitPairingRequested.set(true)
                isPairedDevice = false
                connectionHeld = true
                userDisconnectRequested = false
                startPairing(force = true)
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
        if (!force && !explicitPairingRequested.get()) return
        if (!force && !pairingStarted.compareAndSet(false, true)) return
        if (force) pairingStarted.set(true)

        connectionBusy.set(true)
        cancelPairingFallback()
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

    fun sendKey(key: Key) {
        executeSafe {
            if (!remoteManager.isSessionReady()) {
                notifyRemoteState("Not connected — tap Reconnect Remote in Settings")
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

    fun runSearchQuery(encodedQuery: String) = runDeepLink("https://www.google.com/search?q=$encodedQuery")
    fun tvInput() = sendKey(Key.KEYCODE_TV_INPUT)
    fun apps() = sendKey(Key.KEYCODE_APP_SWITCH)

    fun runNetflix() = runDeepLink("https://www.netflix.com/title")
    fun runYouTube() = runDeepLink("https://www.youtube.com")
    fun runPrime() = runDeepLink("https://app.primevideo.com")
    fun runHotstar() = runDeepLink("https://www.hotstar.com")

    private fun runDeepLink(url: String) {
        executeSafe {
            if (!remoteManager.isSessionReady()) {
                notifyRemoteState("Not connected — tap Reconnect Remote in Settings")
            } else {
                remoteManager.send(DeepLink(url))
            }
        }
    }

    /** User explicitly disconnects — only path to drop connection besides app close. */
    fun disconnectUser() {
        userDisconnectRequested = true
        connectionHeld = false
        autoConnectAllowed = false
        connectionBusy.set(false)
        executeSafe {
            performDisconnect()
            notifyRemoteState("Disconnected")
            notifyPairingState("idle")
        }
    }

    fun disconnectForAppClose() {
        userDisconnectRequested = true
        connectionHeld = false
        autoConnectAllowed = false
        executeSafe { performDisconnect() }
    }

    private fun performDisconnect() {
        pairingStarted.set(false)
        explicitPairingRequested.set(false)
        waitingForCode.set(false)
        notifyWaitingForCode(false)
        cancelPairingFallback()
        remoteManager.disconnect()
        pairingManager.disconnect()
    }

    companion object {
        private const val TAG = "RemoteTvManager"
    }
}
