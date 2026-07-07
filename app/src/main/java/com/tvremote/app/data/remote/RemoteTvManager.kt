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
    var onAppLaunched: (() -> Unit)? = null

    private var pendingAppLink: String? = null

    private var pendingHost: String = ""
    private var isPairedDevice = false
    /** User wants session kept until explicit disconnect. */
    private var connectionHeld = false
    private var userDisconnectRequested = false
    private val connectionBusy = AtomicBoolean(false)
    private val connectionLoaderVisible = AtomicBoolean(false)
    private val reconnectScheduled = AtomicBoolean(false)

    fun isConnectionBusy(): Boolean = connectionBusy.get()

    fun isConnectionLoaderVisible(): Boolean = connectionLoaderVisible.get()
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
        flushPendingAppLaunch()
    }

    private fun flushPendingAppLaunch() {
        val link = pendingAppLink ?: return
        if (!remoteManager.isSessionReady()) return
        pendingAppLink = null
        sendAppLinkNow(link)
        mainHandler.post { SafeRun.invoke(TAG) { onAppLaunched?.invoke() } }
    }

    private fun sendAppLinkNow(link: String) {
        val normalized = TvChannelLinks.normalize(link)
        AppLogger.d(TAG, "Launching TV app: $normalized")
        remoteManager.send(DeepLink(normalized))
    }

    private fun setConnectionBusy(busy: Boolean, showLoader: Boolean = false) {
        connectionBusy.set(busy)
        if (busy && showLoader) {
            connectionLoaderVisible.set(true)
        }
        busyTimeout?.cancel(false)
        busyTimeout = null
        if (busy) {
            busyTimeout = scheduler.schedule({
                executeSafe {
                    if (!connectionBusy.get()) return@executeSafe
                    if (remoteManager.isSessionReady() || waitingForCode.get()) {
                        clearConnectionBusy()
                        return@executeSafe
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
        connectionLoaderVisible.set(false)
        busyTimeout?.cancel(false)
        busyTimeout = null
        cancelConnectionWatchdog()
        publishConnectionUiState()
    }

    private fun scheduleConnectionWatchdog() {
        cancelConnectionWatchdog()
        connectionWatchdog = scheduler.schedule({
            executeSafe {
                if (!connectionBusy.get()) return@executeSafe
                if (remoteManager.isSessionReady() || waitingForCode.get()) {
                    clearConnectionBusy()
                    return@executeSafe
                }
                if (remoteManager.isConnecting() || remoteManager.isHandshakeInProgress()) {
                    return@executeSafe
                }
                AppLogger.w(TAG, "Connection watchdog timeout (${CONNECTION_TIMEOUT_SEC}s)")
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
        reconnectScheduled.set(false)
    }

    private fun scheduleAutoReconnect(immediate: Boolean = false) {
        if (!shouldMaintainConnection()) return
        if (remoteManager.isSessionReady() || waitingForCode.get()) return
        if (remoteManager.isConnecting() || remoteManager.isHandshakeInProgress()) return
        if (connectionBusy.get()) return
        if (!reconnectScheduled.compareAndSet(false, true)) return
        if (reconnectAttempt >= MAX_AUTO_RECONNECT_ATTEMPTS) {
            AppLogger.w(TAG, "Auto-reconnect attempts exhausted")
            reconnectScheduled.set(false)
            notifyRemoteState("Could not reconnect — tap Reconnect")
            return
        }

        cancelAutoReconnect()
        val delaySec = if (immediate) {
            1L
        } else {
            (AUTO_RECONNECT_BASE_DELAY_SEC * (1L shl reconnectAttempt.coerceAtMost(4)))
                .coerceAtMost(30L)
        }
        val attempt = reconnectAttempt + 1
        AppLogger.d(TAG, "Scheduling auto-reconnect attempt $attempt in ${delaySec}s")

        autoReconnectFuture = scheduler.schedule({
            executeSafe {
                reconnectScheduled.set(false)
                if (!shouldMaintainConnection()) return@executeSafe
                if (remoteManager.isSessionReady() || waitingForCode.get()) return@executeSafe
                if (remoteManager.isConnecting() || remoteManager.isHandshakeInProgress()) return@executeSafe
                reconnectAttempt = attempt
                connectRemoteOnlyInternal(force = false, showLoader = false)
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
                    reconnectScheduled.set(false)
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
                !connectionBusy.get() &&
                !reconnectScheduled.get()
            ) {
                scheduleAutoReconnect(immediate = false)
            }
            publishConnectionUiState()
        }
    }

    /** Refresh UI snapshot only — no reconnect side effects. */
    fun refreshConnectionUi() {
        executeSafe {
            if (remoteManager.isSessionReady()) {
                reconnectAttempt = 0
                reconnectScheduled.set(false)
                cancelAutoReconnect()
                clearConnectionBusy()
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
                        connectRemoteOnlyInternal(force = true, showLoader = true)
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
        publishConnectionUiState()
        if (userDisconnectRequested) return
        if (!connectionHeld) {
            notifyRemoteState("Not connected — pair your TV first")
            return
        }
        if (remoteManager.isSessionReady()) return
        AppLogger.w(TAG, "Remote error — scheduling reconnect: ${error.toDisplayString()}")
        scheduleAutoReconnect(immediate = false)
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
                reconnectScheduled.set(false)
                cancelAutoReconnect()
                clearConnectionBusy()
                notifyRemoteState(remoteManager.currentRemoteState().toDisplayString())
            } else if (!remoteManager.isConnecting() &&
                !remoteManager.isHandshakeInProgress() &&
                !connectionBusy.get() &&
                !reconnectScheduled.get()
            ) {
                scheduleAutoReconnect(immediate = false)
            }
        }
    }

    fun connect(host: String) {
        pendingHost = host.trim()
        if (pendingHost.isEmpty()) return
        userDisconnectRequested = false
        connectionHeld = true
        reconnectAttempt = 0
        reconnectScheduled.set(false)
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
                else -> connectRemoteOnlyInternal(force = false, showLoader = true)
            }
        }
    }

    fun reconnectTo(host: String? = null) {
        host?.trim()?.takeIf { it.isNotEmpty() }?.let { pendingHost = it }
        if (pendingHost.isEmpty()) return
        userDisconnectRequested = false
        connectionHeld = true
        reconnectAttempt = 0
        reconnectScheduled.set(false)
        cancelAutoReconnect()
        executeSafe {
            connectRemoteOnlyInternal(force = true, showLoader = true)
        }
    }

    private fun connectRemoteOnlyInternal(force: Boolean, showLoader: Boolean) {
        if (userDisconnectRequested) return
        if (remoteManager.isSessionReady()) {
            reconnectAttempt = 0
            reconnectScheduled.set(false)
            cancelAutoReconnect()
            clearConnectionBusy()
            notifyRemoteState(remoteManager.currentRemoteState().toDisplayString())
            return
        }
        if (!force && (remoteManager.isConnecting() || remoteManager.isHandshakeInProgress())) {
            if (showLoader) {
                setConnectionBusy(true, showLoader = true)
            }
            scheduleConnectionWatchdog()
            return
        }

        if (force && (remoteManager.isConnecting() || remoteManager.isHandshakeInProgress())) {
            remoteManager.disconnect()
        }

        setConnectionBusy(true, showLoader = showLoader)
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
                isPairedDevice -> connectRemoteOnlyInternal(force = false, showLoader = true)
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

        setConnectionBusy(true, showLoader = true)
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
                    scheduleAutoReconnect(immediate = false)
                } else {
                    notifyRemoteState("Not connected — tap Reconnect")
                }
            } else {
                remoteManager.send(KeyPress(key))
            }
        }
    }

    fun sendText(text: String) {
        executeSafe {
            if (!remoteManager.isSessionReady()) {
                if (shouldMaintainConnection()) {
                    scheduleAutoReconnect(immediate = false)
                } else {
                    notifyRemoteState("Not connected — tap Reconnect")
                }
            } else {
                remoteManager.sendText(text)
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

    fun runNetflix() = launchTvApp(TvChannelLinks.NETFLIX)
    fun runYouTube() = launchTvApp(TvChannelLinks.YOUTUBE)
    fun runPrime() = launchTvApp(TvChannelLinks.PRIME)
    fun runHotstar() = launchTvApp(TvChannelLinks.HOTSTAR)
    fun runAppleTv() = launchTvApp(TvChannelLinks.APPLE_TV)
    fun runDisney() = launchTvApp(TvChannelLinks.DISNEY)

    /**
     * Launch an installed Android TV app via market deep link.
     * Queues launch if socket not ready yet.
     */
    fun launchTvApp(appLink: String): Boolean {
        val link = TvChannelLinks.normalize(appLink)
        if (remoteManager.isSessionReady()) {
            executeSafe { sendAppLinkNow(link) }
            return true
        }
        pendingAppLink = link
        if (shouldMaintainConnection()) {
            scheduleAutoReconnect(immediate = false)
            return false
        }
        pendingAppLink = null
        notifyRemoteState("Not connected — pair your TV first")
        return false
    }

    fun runDeepLink(url: String): Boolean = launchTvApp(url)

    fun disconnectUser() {
        userDisconnectRequested = true
        connectionHeld = false
        reconnectAttempt = 0
        reconnectScheduled.set(false)
        pendingAppLink = null
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
