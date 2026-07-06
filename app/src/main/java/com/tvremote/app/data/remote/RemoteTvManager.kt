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
    var onConnectionUiChanged: (() -> Unit)? = null

    private var pendingHost: String = ""
    private var isPairedDevice = false
    /** User wants session kept until explicit disconnect. */
    private var connectionHeld = false
    private var userDisconnectRequested = false
    private val connectionBusy = AtomicBoolean(false)

    fun isConnectionBusy(): Boolean = connectionBusy.get()
    private val clientName = "Android TV Remote"
    private val serviceName = "atvremote"

    private var pairingFallback: ScheduledFuture<*>? = null
    private var busyTimeout: ScheduledFuture<*>? = null
    private var connectionWatchdog: ScheduledFuture<*>? = null
    private var autoReconnectFuture: ScheduledFuture<*>? = null
    private var reconnectAttempt = 0

    private companion object {
        private const val TAG = "RemoteTvManager"
        private const val BUSY_TIMEOUT_SEC = 45L
        private const val CONNECTION_TIMEOUT_SEC = 45L
        private const val MAX_AUTO_RECONNECT_ATTEMPTS = 6
        private const val AUTO_RECONNECT_BASE_DELAY_SEC = 2L
    }

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
        cancelAutoReconnect()
    }

    private fun shouldMaintainConnection(): Boolean =
        connectionHeld && isPairedDevice && !userDisconnectRequested && pendingHost.isNotEmpty()

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
        reconnectAttempt = 0
        cancelAutoReconnect()
        clearConnectionBusy()
        mainHandler.post { SafeRun.invoke(TAG) { onPaired?.invoke(host) } }
    }

    private fun setConnectionBusy(busy: Boolean) {
        connectionBusy.set(busy)
        busyTimeout?.cancel(false)
        busyTimeout = null
        if (busy) {
            busyTimeout = scheduler.schedule({
                SafeRun.run(TAG) {
                    if (!connectionBusy.get()) return@run
                    if (remoteManager.isSessionReady() || waitingForCode.get()) {
                        clearConnectionBusy()
                        return@run
                    }
                    AppLogger.w(TAG, "Connection busy timeout — clearing loader")
                    clearConnectionBusy()
                    notifyRemoteState("Connection timed out — tap Reconnect")
                    scheduleAutoReconnect()
                }
            }, BUSY_TIMEOUT_SEC, TimeUnit.SECONDS)
        }
        publishConnectionUiState()
    }

    private fun clearConnectionBusy() {
        connectionBusy.set(false)
        busyTimeout?.cancel(false)
        busyTimeout = null
        cancelConnectionWatchdog()
        publishConnectionUiState()
    }

    private fun scheduleConnectionWatchdog() {
        cancelConnectionWatchdog()
        connectionWatchdog = scheduler.schedule({
            SafeRun.run(TAG) {
                if (!connectionBusy.get()) return@run
                if (remoteManager.isSessionReady() || waitingForCode.get()) {
                    clearConnectionBusy()
                    return@run
                }
                AppLogger.w(TAG, "Connection watchdog timeout (${CONNECTION_TIMEOUT_SEC}s)")
                remoteManager.disconnect()
                clearConnectionBusy()
                notifyRemoteState("Connection timed out — tap Reconnect")
                scheduleAutoReconnect()
            }
        }, CONNECTION_TIMEOUT_SEC, TimeUnit.SECONDS)
    }

    private fun cancelConnectionWatchdog() {
        connectionWatchdog?.cancel(false)
        connectionWatchdog = null
    }

    private fun cancelAutoReconnect() {
        autoReconnectFuture?.cancel(false)
        autoReconnectFuture = null
    }

    private fun scheduleAutoReconnect(immediate: Boolean = false) {
        if (!shouldMaintainConnection()) return
        if (remoteManager.isSessionReady() || waitingForCode.get()) return
        if (remoteManager.isConnecting() || remoteManager.isHandshakeInProgress()) return
        if (reconnectAttempt >= MAX_AUTO_RECONNECT_ATTEMPTS) {
            AppLogger.w(TAG, "Auto-reconnect attempts exhausted")
            notifyRemoteState("Could not reconnect — tap Reconnect")
            reconnectAttempt = 0
            return
        }

        cancelAutoReconnect()
        val delaySec = if (immediate) {
            0L
        } else {
            (AUTO_RECONNECT_BASE_DELAY_SEC * (1L shl reconnectAttempt.coerceAtMost(4)))
                .coerceAtMost(30L)
        }
        val attempt = reconnectAttempt + 1
        AppLogger.d(TAG, "Scheduling auto-reconnect attempt $attempt in ${delaySec}s")

        autoReconnectFuture = scheduler.schedule({
            SafeRun.run(TAG) {
                if (!shouldMaintainConnection()) return@run
                if (remoteManager.isSessionReady() || waitingForCode.get()) return@run
                if (remoteManager.isConnecting() || remoteManager.isHandshakeInProgress()) return@run
                reconnectAttempt = attempt
                notifyRemoteState("Reconnecting to $pendingHost… (attempt $attempt)")
                connectRemoteOnlyInternal(force = true)
            }
        }, delaySec, TimeUnit.SECONDS)
    }

    private fun publishConnectionUiState() {
        mainHandler.post { SafeRun.invoke(TAG) { onConnectionUiChanged?.invoke() } }
    }

    /** Re-sync UI and heal dropped sockets for paired TVs. */
    fun syncConnectionState() {
        executeSafe {
            when {
                userDisconnectRequested -> Unit
                remoteManager.isSessionReady() -> {
                    reconnectAttempt = 0
                    cancelAutoReconnect()
                    clearConnectionBusy()
                    notifyRemoteState(remoteManager.currentRemoteState().toDisplayString())
                }
                waitingForCode.get() -> clearConnectionBusy()
                connectionBusy.get() &&
                    !remoteManager.isConnecting() &&
                    !remoteManager.isHandshakeInProgress() -> {
                    AppLogger.w(TAG, "Clearing stale connection busy flag")
                    clearConnectionBusy()
                }
            }
            if (shouldMaintainConnection() &&
                !remoteManager.isSessionReady() &&
                !waitingForCode.get() &&
                !remoteManager.isConnecting() &&
                !remoteManager.isHandshakeInProgress() &&
                !connectionBusy.get()
            ) {
                scheduleAutoReconnect(immediate = true)
            }
            publishConnectionUiState()
        }
    }

    private fun executeSafe(block: () -> Unit) {
        executor.execute {
            try {
                block()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Remote task failed", e)
                notifyRemoteState("Error: ${e.message ?: "Unknown error"}")
                scheduleAutoReconnect()
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
                        clearConnectionBusy()
                    }
                    is PairingManager.PairingState.Error -> {
                        clearConnectionBusy()
                        waitingForCode.set(false)
                        notifyWaitingForCode(false)
                        pairingStarted.set(false)
                        explicitPairingRequested.set(false)
                    }
                    else -> Unit
                }
                publishConnectionUiState()
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
                        notifyPaired(pendingHost)
                        pairingStarted.set(false)
                        waitingForCode.set(false)
                        explicitPairingRequested.set(false)
                        notifyWaitingForCode(false)
                        cancelPairingFallback()
                    }
                    is RemoteManager.RemoteState.Error -> {
                        cancelPairingFallback()
                        handleRemoteError(state.error)
                    }
                    else -> Unit
                }
                publishConnectionUiState()
            }
        }
    }

    private fun handleRemoteError(error: TvRemoteError) {
        clearConnectionBusy()
        remoteManager.disconnect()
        publishConnectionUiState()
        if (userDisconnectRequested) return
        if (!connectionHeld) {
            notifyRemoteState("Not connected — pair your TV first")
            return
        }
        AppLogger.w(TAG, "Remote error — scheduling reconnect: ${error.toDisplayString()}")
        notifyRemoteState("Connection lost — reconnecting…")
        scheduleAutoReconnect(immediate = true)
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

    /** Connect or reconnect when app opens / returns to foreground. */
    fun ensureConnected() {
        if (!shouldMaintainConnection()) return
        executeSafe {
            if (remoteManager.isSessionReady()) {
                reconnectAttempt = 0
                cancelAutoReconnect()
                clearConnectionBusy()
                notifyRemoteState(remoteManager.currentRemoteState().toDisplayString())
            } else if (!remoteManager.isConnecting() && !remoteManager.isHandshakeInProgress()) {
                scheduleAutoReconnect(immediate = true)
            }
        }
    }

    fun connect(host: String) {
        pendingHost = host.trim()
        if (pendingHost.isEmpty()) return
        userDisconnectRequested = false
        connectionHeld = true
        reconnectAttempt = 0
        cancelAutoReconnect()
        executeSafe {
            when {
                waitingForCode.get() -> notifyPairingState(
                    "Code already on TV — enter it below and tap Complete Pairing",
                )
                remoteManager.isSessionReady() -> {
                    clearConnectionBusy()
                    notifyRemoteState(remoteManager.currentRemoteState().toDisplayString())
                }
                else -> connectRemoteOnlyInternal(force = false)
            }
        }
    }

    fun reconnectTo(host: String? = null) {
        host?.trim()?.takeIf { it.isNotEmpty() }?.let { pendingHost = it }
        if (pendingHost.isEmpty()) return
        userDisconnectRequested = false
        connectionHeld = true
        reconnectAttempt = 0
        cancelAutoReconnect()
        executeSafe {
            connectRemoteOnlyInternal(force = true)
        }
    }

    private fun connectRemoteOnlyInternal(force: Boolean) {
        if (userDisconnectRequested) return
        if (remoteManager.isSessionReady()) {
            reconnectAttempt = 0
            cancelAutoReconnect()
            clearConnectionBusy()
            notifyRemoteState(remoteManager.currentRemoteState().toDisplayString())
            return
        }
        if (!force && (remoteManager.isConnecting() || remoteManager.isHandshakeInProgress())) {
            scheduleConnectionWatchdog()
            return
        }

        if (force && (remoteManager.isConnecting() || remoteManager.isHandshakeInProgress())) {
            remoteManager.disconnect()
        }

        setConnectionBusy(true)
        pairingStarted.set(false)
        explicitPairingRequested.set(false)
        cancelPairingFallback()
        pairingManager.disconnect()
        if (force || !remoteManager.isSessionReady()) {
            remoteManager.disconnect()
        }
        notifyRemoteState("Connecting to $pendingHost…")
        remoteManager.connect(pendingHost)
        scheduleConnectionWatchdog()
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
                cancelAutoReconnect()
                startPairing(force = true)
            }
        }
    }

    private fun startPairing(force: Boolean) {
        if (!force && !explicitPairingRequested.get()) return
        if (!force && !pairingStarted.compareAndSet(false, true)) return
        if (force) pairingStarted.set(true)

        setConnectionBusy(true)
        scheduleConnectionWatchdog()
        cancelPairingFallback()
        cancelAutoReconnect()
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
                if (shouldMaintainConnection()) {
                    notifyRemoteState("Reconnecting…")
                    scheduleAutoReconnect(immediate = true)
                } else {
                    notifyRemoteState("Not connected — tap Reconnect")
                }
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

    fun runNetflix() = runDeepLink(TvChannelLinks.NETFLIX)
    fun runYouTube() = runDeepLink(TvChannelLinks.YOUTUBE)
    fun runPrime() = runDeepLink(TvChannelLinks.PRIME)
    fun runHotstar() = runDeepLink(TvChannelLinks.HOTSTAR)
    fun runAppleTv() = runDeepLink(TvChannelLinks.APPLE_TV)
    fun runDisney() = runDeepLink(TvChannelLinks.DISNEY)

    fun runDeepLink(url: String): Boolean {
        if (!remoteManager.isSessionReady()) {
            if (shouldMaintainConnection()) {
                scheduleAutoReconnect(immediate = true)
            }
            notifyRemoteState("Not connected — reconnecting…")
            return false
        }
        executeSafe { remoteManager.send(DeepLink(url)) }
        return true
    }

    fun disconnectUser() {
        userDisconnectRequested = true
        connectionHeld = false
        reconnectAttempt = 0
        cancelAutoReconnect()
        clearConnectionBusy()
        executeSafe {
            performDisconnect()
            notifyRemoteState("Disconnected")
            notifyPairingState("idle")
        }
    }

    /** Close sockets when app leaves; keep paired session so we reconnect on return. */
    fun disconnectForAppClose() {
        cancelAutoReconnect()
        clearConnectionBusy()
        executeSafe {
            performDisconnect()
            if (isPairedDevice) {
                userDisconnectRequested = false
                connectionHeld = true
            }
        }
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
}
