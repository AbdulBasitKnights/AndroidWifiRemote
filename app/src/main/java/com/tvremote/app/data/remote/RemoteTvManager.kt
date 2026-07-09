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
    /** Pairing credentials saved — persist host/name before remote socket is ready. */
    var onPairingCredentialsSaved: ((String) -> Unit)? = null
    /** Remote TLS session is ready for key presses. */
    var onPaired: ((String) -> Unit)? = null
    var onConnectionUiChanged: (() -> Unit)? = null
    var onAppLaunched: (() -> Unit)? = null
    /** Foreground service notification updates. */
    var onNotificationChanged: ((RemoteConnectionNotification) -> Unit)? = null

    private var pendingAppLink: String? = null

    private var pendingHost: String = ""
    private var pendingTvDisplayName: String = ""
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
    private var connectionMonitor: ScheduledFuture<*>? = null
    private var handshakeWatchdog: ScheduledFuture<*>? = null
    private var recoveryRePairFuture: ScheduledFuture<*>? = null
    private var postPairingConnectFuture: ScheduledFuture<*>? = null
    private var reconnectAttempt = 0
    private var consecutiveConnectFailures = 0
    @Volatile
    private var lastReconnectAtMs = 0L

    private companion object {
        private const val TAG = "RemoteTvManager"
        private const val BUSY_TIMEOUT_SEC = 45L
        private const val LOADER_TIMEOUT_SEC = 22L
        private const val CONNECTION_TIMEOUT_SEC = 45L
        private const val HANDSHAKE_TIMEOUT_SEC = 18L
        private const val AUTO_RECONNECT_BASE_DELAY_SEC = 3L
        private const val CONNECTION_MONITOR_INTERVAL_SEC = 45L
        private const val MIN_RECONNECT_COOLDOWN_MS = 5_000L
        private const val RECONNECT_WINDOW_MS = 30_000L
        private const val POST_PAIRING_CONNECT_DELAY_MS = 800L
    }

    fun isWaitingForPairingCode(): Boolean = waitingForCode.get()

    fun isPaired(): Boolean = isPairedDevice

    fun isConnectionHeld(): Boolean = connectionHeld

    fun isSessionReady(): Boolean = remoteManager.isSessionReady()

    fun currentHost(): String = pendingHost

    fun currentDisplayName(): String = pendingTvDisplayName.ifBlank { pendingHost }

    fun setDisplayName(name: String) {
        if (name.isNotBlank()) {
            pendingTvDisplayName = name.trim()
        }
    }

    fun loadSavedSession(host: String, displayName: String, paired: Boolean) {
        pendingHost = host.trim()
        pendingTvDisplayName = displayName.trim()
        isPairedDevice = paired
        if (paired) {
            connectionHeld = true
            userDisconnectRequested = false
            updateConnectionMonitor()
            publishNotification(
                if (remoteManager.isSessionReady()) {
                    RemoteConnectionStatus.CONNECTED
                } else {
                    RemoteConnectionStatus.RECONNECTING
                },
            )
        }
    }

    fun markPaired(host: String, displayName: String = "") {
        pendingHost = host.trim()
        if (displayName.isNotBlank()) {
            pendingTvDisplayName = displayName.trim()
        }
        isPairedDevice = true
        connectionHeld = true
        userDisconnectRequested = false
        explicitPairingRequested.set(false)
        updateConnectionMonitor()
    }

    fun resetPairingState() {
        isPairedDevice = false
        connectionHeld = false
        pendingTvDisplayName = ""
        cancelAutoReconnect()
        stopConnectionMonitor()
        endRecoverySession()
    }

    private fun shouldMaintainConnection(): Boolean =
        connectionHeld && isPairedDevice && !userDisconnectRequested && pendingHost.isNotEmpty()

    private fun isPairingInProgress(): Boolean =
        waitingForCode.get() || pairingStarted.get() || explicitPairingRequested.get()

    private fun notifyPairingState(message: String) {
        mainHandler.post { SafeRun.invoke(TAG) { pairingStateChanged?.invoke(message) } }
    }

    private fun notifyRemoteState(message: String) {
        mainHandler.post { SafeRun.invoke(TAG) { remoteStateChanged?.invoke(message) } }
    }

    private fun notifyWaitingForCode(waiting: Boolean) {
        mainHandler.post { SafeRun.invoke(TAG) { onWaitingForCodeChanged?.invoke(waiting) } }
    }

    private fun onPairingSucceeded(host: String) {
        isPairedDevice = true
        connectionHeld = true
        userDisconnectRequested = false
        explicitPairingRequested.set(false)
        pairingStarted.set(false)
        waitingForCode.set(false)
        notifyWaitingForCode(false)
        reconnectAttempt = 0
        consecutiveConnectFailures = 0
        cancelAutoReconnect()
        cancelHandshakeWatchdog()
        endRecoverySession()
        updateConnectionMonitor()
        publishNotification(RemoteConnectionStatus.RECONNECTING)
        notifyRemoteState("Pairing complete — connecting remote…")
        mainHandler.post { SafeRun.invoke(TAG) { onPairingCredentialsSaved?.invoke(host) } }
        scheduleRemoteConnectAfterPairing()
    }

    private fun notifySessionReady(host: String) {
        reconnectAttempt = 0
        consecutiveConnectFailures = 0
        cancelAutoReconnect()
        cancelHandshakeWatchdog()
        endRecoverySession()
        clearConnectionBusy()
        updateConnectionMonitor()
        publishNotification(RemoteConnectionStatus.CONNECTED)
        mainHandler.post { SafeRun.invoke(TAG) { onPaired?.invoke(host) } }
        flushPendingAppLaunch()
    }

    private fun scheduleRemoteConnectAfterPairing() {
        postPairingConnectFuture?.cancel(false)
        postPairingConnectFuture = scheduler.schedule({
            executeSafe {
                if (userDisconnectRequested) return@executeSafe
                connectRemoteOnlyInternal(force = true, showLoader = true)
            }
        }, POST_PAIRING_CONNECT_DELAY_MS, TimeUnit.MILLISECONDS)
    }

    private fun cancelPostPairingConnect() {
        postPairingConnectFuture?.cancel(false)
        postPairingConnectFuture = null
    }

    private fun publishNotification(status: RemoteConnectionStatus) {
        val active = when (status) {
            RemoteConnectionStatus.DISCONNECTED -> false
            else -> connectionHeld &&
                pendingHost.isNotEmpty() &&
                (isPairedDevice || isPairingInProgress() || waitingForCode.get())
        }
        val payload = RemoteConnectionNotification(
            active = active,
            host = pendingHost,
            displayName = currentDisplayName(),
            status = status,
        )
        mainHandler.post {
            SafeRun.invoke(TAG) { onNotificationChanged?.invoke(payload) }
        }
    }

    private fun beginRecoverySession() {
        if (recoveryRePairFuture != null && recoveryRePairFuture?.isCancelled == false) return
        publishNotification(RemoteConnectionStatus.RECONNECTING)
        cancelRecoveryRePairTimer()
        recoveryRePairFuture = scheduler.schedule({
            executeSafe {
                if (remoteManager.isSessionReady() || isPairingInProgress() || userDisconnectRequested) {
                    return@executeSafe
                }
                AppLogger.w(TAG, "Reconnect window (${RECONNECT_WINDOW_MS}ms) elapsed — starting re-pair")
                scheduleRePairing()
            }
        }, RECONNECT_WINDOW_MS, TimeUnit.MILLISECONDS)
    }

    private fun endRecoverySession() {
        cancelRecoveryRePairTimer()
        consecutiveConnectFailures = 0
    }

    private fun cancelRecoveryRePairTimer() {
        recoveryRePairFuture?.cancel(false)
        recoveryRePairFuture = null
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
            val timeoutSec = if (showLoader || connectionLoaderVisible.get()) {
                LOADER_TIMEOUT_SEC
            } else {
                BUSY_TIMEOUT_SEC
            }
            busyTimeout = scheduler.schedule({
                executeSafe {
                    if (!connectionBusy.get()) return@executeSafe
                    if (remoteManager.isSessionReady() || waitingForCode.get() || isPairingInProgress()) {
                        clearConnectionBusy()
                        return@executeSafe
                    }
                    AppLogger.w(TAG, "Connection busy timeout (${timeoutSec}s) — clearing loader")
                    handleConnectFailure(
                        reason = "Connection timed out — check TV is on and on same WiFi",
                        suggestRePair = true,
                    )
                }
            }, timeoutSec, TimeUnit.SECONDS)
        }
        publishConnectionUiState()
    }

    private fun clearConnectionBusy() {
        connectionBusy.set(false)
        connectionLoaderVisible.set(false)
        busyTimeout?.cancel(false)
        busyTimeout = null
        cancelConnectionWatchdog()
        cancelHandshakeWatchdog()
        publishConnectionUiState()
    }

    private fun scheduleHandshakeWatchdog() {
        cancelHandshakeWatchdog()
        handshakeWatchdog = scheduler.schedule({
            executeSafe {
                if (remoteManager.isSessionReady() || waitingForCode.get() || isPairingInProgress()) {
                    cancelHandshakeWatchdog()
                    return@executeSafe
                }
                val stillConnecting = remoteManager.isConnecting() ||
                    remoteManager.isHandshakeInProgress() ||
                    connectionBusy.get()
                if (!stillConnecting) return@executeSafe
                AppLogger.w(TAG, "Handshake timeout (${HANDSHAKE_TIMEOUT_SEC}s) — session not ready")
                handleConnectFailure(
                    reason = "Could not establish remote session",
                    suggestRePair = true,
                )
            }
        }, HANDSHAKE_TIMEOUT_SEC, TimeUnit.SECONDS)
    }

    private fun cancelHandshakeWatchdog() {
        handshakeWatchdog?.cancel(false)
        handshakeWatchdog = null
    }

    private fun handleConnectFailure(reason: String, suggestRePair: Boolean) {
        clearConnectionBusy()
        cancelHandshakeWatchdog()
        cancelConnectionWatchdog()
        if (!remoteManager.isSessionReady()) {
            remoteManager.disconnect()
        }
        publishConnectionUiState()
        if (userDisconnectRequested) return
        if (isPairingInProgress()) return
        if (!connectionHeld && !isPairedDevice) {
            notifyRemoteState(reason)
            publishNotification(RemoteConnectionStatus.DISCONNECTED)
            return
        }
        consecutiveConnectFailures++
        notifyRemoteState(reason)
        beginRecoverySession()
        if (connectionHeld || isPairedDevice) {
            scheduleAutoReconnect(immediate = false)
        }
    }

    private fun scheduleRePairing() {
        if (isPairingInProgress() || pendingHost.isEmpty()) return
        endRecoverySession()
        reconnectAttempt = 0
        cancelAutoReconnect()
        reconnectScheduled.set(false)
        executeSafe {
            AppLogger.w(TAG, "Starting re-pair for ${currentDisplayName()} ($pendingHost)")
            userDisconnectRequested = false
            connectionHeld = true
            explicitPairingRequested.set(true)
            pairingStarted.set(false)
            stopConnectionMonitor()
            publishNotification(RemoteConnectionStatus.PAIRING)
            notifyPairingState(
                "Could not reconnect — open TV pairing screen and enter the new code",
            )
            startPairing(force = true)
        }
    }

    private fun TvRemoteError.suggestsRePairing(): Boolean = when (this) {
        is TvRemoteError.ReceiveDataError,
        is TvRemoteError.SendDataError,
        is TvRemoteError.SecretNotSuccess,
        is TvRemoteError.PairingNotSuccess,
        is TvRemoteError.OptionNotSuccess,
        is TvRemoteError.ConfigurationNotSuccess,
        -> true
        else -> false
    }

    private fun scheduleConnectionWatchdog() {
        cancelConnectionWatchdog()
        connectionWatchdog = scheduler.schedule({
            executeSafe {
                if (!connectionBusy.get()) return@executeSafe
                if (remoteManager.isSessionReady() || waitingForCode.get() || isPairingInProgress()) {
                    clearConnectionBusy()
                    return@executeSafe
                }
                if (remoteManager.isConnecting() || remoteManager.isHandshakeInProgress()) {
                    return@executeSafe
                }
                AppLogger.w(TAG, "Connection watchdog timeout (${CONNECTION_TIMEOUT_SEC}s)")
                handleConnectFailure(
                    reason = "Connection timed out — tap Reconnect or wait for pairing code",
                    suggestRePair = true,
                )
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

    private fun stopConnectionMonitor() {
        connectionMonitor?.cancel(false)
        connectionMonitor = null
    }

    private fun updateConnectionMonitor() {
        if (shouldMaintainConnection()) {
            startConnectionMonitor()
        } else {
            stopConnectionMonitor()
        }
    }

    private fun startConnectionMonitor() {
        if (connectionMonitor != null && connectionMonitor?.isCancelled == false) return
        stopConnectionMonitor()
        connectionMonitor = scheduler.scheduleWithFixedDelay({
            executeSafe { monitorConnectionHealth() }
        }, CONNECTION_MONITOR_INTERVAL_SEC, CONNECTION_MONITOR_INTERVAL_SEC, TimeUnit.SECONDS)
    }

    private fun monitorConnectionHealth() {
        if (!shouldMaintainConnection()) {
            stopConnectionMonitor()
            return
        }
        if (remoteManager.isSessionReady()) {
            reconnectAttempt = 0
            reconnectScheduled.set(false)
            cancelAutoReconnect()
            return
        }
        if (waitingForCode.get() ||
            isPairingInProgress() ||
            remoteManager.isConnecting() ||
            remoteManager.isHandshakeInProgress() ||
            connectionBusy.get() ||
            reconnectScheduled.get()
        ) {
            return
        }
        AppLogger.d(TAG, "Connection monitor — session dropped, scheduling reconnect")
        beginRecoverySession()
        scheduleAutoReconnect(immediate = false)
    }

    private fun scheduleAutoReconnect(immediate: Boolean = false) {
        if (!shouldMaintainConnection()) return
        if (isPairingInProgress()) return
        if (remoteManager.isSessionReady() || waitingForCode.get()) return
        if (remoteManager.isConnecting() || remoteManager.isHandshakeInProgress()) return
        if (connectionBusy.get()) return
        if (!reconnectScheduled.compareAndSet(false, true)) return

        val now = System.currentTimeMillis()
        val cooldownRemaining = MIN_RECONNECT_COOLDOWN_MS - (now - lastReconnectAtMs)
        if (!immediate && cooldownRemaining > 0L) {
            AppLogger.d(TAG, "Reconnect cooldown — waiting ${cooldownRemaining}ms")
            scheduleReconnectAfter(cooldownRemaining)
            return
        }

        cancelAutoReconnect()
        val delaySec = if (immediate) {
            1L
        } else {
            (AUTO_RECONNECT_BASE_DELAY_SEC * (1L shl reconnectAttempt.coerceAtMost(4)))
                .coerceAtMost(60L)
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

    private fun scheduleReconnectAfter(delayMs: Long) {
        cancelAutoReconnect()
        autoReconnectFuture = scheduler.schedule({
            executeSafe {
                reconnectScheduled.set(false)
                scheduleAutoReconnect(immediate = false)
            }
        }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun publishConnectionUiState() {
        val status = when {
            userDisconnectRequested || (!connectionHeld && !isPairedDevice) ->
                RemoteConnectionStatus.DISCONNECTED
            waitingForCode.get() || isPairingInProgress() ->
                RemoteConnectionStatus.PAIRING
            remoteManager.isSessionReady() ->
                RemoteConnectionStatus.CONNECTED
            connectionHeld && isPairedDevice && pendingHost.isNotEmpty() ->
                RemoteConnectionStatus.RECONNECTING
            else -> RemoteConnectionStatus.DISCONNECTED
        }
        if (status != RemoteConnectionStatus.DISCONNECTED ||
            (connectionHeld && pendingHost.isNotEmpty())
        ) {
            publishNotification(status)
        }
        mainHandler.post { SafeRun.invoke(TAG) { onConnectionUiChanged?.invoke() } }
    }

    /** Re-sync UI and heal dropped sockets for paired TVs. */
    fun syncConnectionState() {
        updateConnectionMonitor()
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
                    connectionLoaderVisible.get() &&
                    !remoteManager.isConnecting() &&
                    !remoteManager.isHandshakeInProgress() &&
                    !remoteManager.isSessionReady() -> {
                    AppLogger.w(TAG, "Clearing stale loader")
                    clearConnectionBusy()
                }
                connectionBusy.get() &&
                    !remoteManager.isConnecting() &&
                    !remoteManager.isHandshakeInProgress() -> {
                    AppLogger.w(TAG, "Clearing stale connection busy flag")
                    clearConnectionBusy()
                }
            }
            if (shouldMaintainConnection() &&
                !isPairingInProgress() &&
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
                consecutiveConnectFailures = 0
                reconnectScheduled.set(false)
                cancelAutoReconnect()
                clearConnectionBusy()
            } else if (waitingForCode.get()) {
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
                if (!isPairingInProgress()) {
                    scheduleAutoReconnect()
                }
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
                        onPairingSucceeded(pendingHost)
                    }
                    is PairingManager.PairingState.SecretSent -> {
                        notifyPairingState("Submitting pairing code…")
                    }
                    is PairingManager.PairingState.WaitingCode -> {
                        pairingStarted.set(false)
                        explicitPairingRequested.set(false)
                        endRecoverySession()
                        clearConnectionBusy()
                        publishNotification(RemoteConnectionStatus.PAIRING)
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
                        scheduleHandshakeWatchdog()
                        if (!waitingForCode.get() && !isPairedDevice && explicitPairingRequested.get()) {
                            schedulePairingFallback()
                        }
                    }
                    is RemoteManager.RemoteState.FirstConfigSent,
                    is RemoteManager.RemoteState.SecondConfigSent,
                    is RemoteManager.RemoteState.FirstConfigMessageReceived,
                    -> scheduleHandshakeWatchdog()
                    is RemoteManager.RemoteState.Paired -> {
                        reconnectAttempt = 0
                        pairingStarted.set(false)
                        waitingForCode.set(false)
                        explicitPairingRequested.set(false)
                        notifyWaitingForCode(false)
                        cancelPairingFallback()
                        if (!isPairedDevice) {
                            isPairedDevice = true
                            connectionHeld = true
                            mainHandler.post {
                                SafeRun.invoke(TAG) { onPairingCredentialsSaved?.invoke(pendingHost) }
                            }
                        }
                        notifySessionReady(pendingHost)
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
        if (userDisconnectRequested || isPairingInProgress()) return
        if (!connectionHeld && !isPairedDevice) {
            clearConnectionBusy()
            notifyRemoteState("Not connected — pair your TV first")
            publishNotification(RemoteConnectionStatus.DISCONNECTED)
            return
        }
        handleConnectFailure(
            reason = error.toDisplayString(),
            suggestRePair = error.suggestsRePairing(),
        )
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
        updateConnectionMonitor()
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
        updateConnectionMonitor()
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
        if (!isPairedDevice) {
            executeSafe { startPairingForHost() }
            return
        }
        userDisconnectRequested = false
        connectionHeld = true
        reconnectAttempt = 0
        reconnectScheduled.set(false)
        cancelAutoReconnect()
        updateConnectionMonitor()
        publishNotification(RemoteConnectionStatus.RECONNECTING)
        executeSafe {
            connectRemoteOnlyInternal(force = true, showLoader = true)
        }
    }

    private fun startPairingForHost() {
        if (pendingHost.isEmpty()) return
        userDisconnectRequested = false
        connectionHeld = true
        explicitPairingRequested.set(true)
        stopConnectionMonitor()
        cancelAutoReconnect()
        endRecoverySession()
        publishNotification(RemoteConnectionStatus.PAIRING)
        startPairing(force = true)
    }

    private fun connectRemoteOnlyInternal(force: Boolean, showLoader: Boolean) {
        if (userDisconnectRequested) return
        if (isPairingInProgress()) return
        if (remoteManager.isSessionReady()) {
            reconnectAttempt = 0
            reconnectScheduled.set(false)
            cancelAutoReconnect()
            clearConnectionBusy()
            notifyRemoteState(remoteManager.currentRemoteState().toDisplayString())
            return
        }
        if (!force && !showLoader) {
            val sinceLast = System.currentTimeMillis() - lastReconnectAtMs
            if (sinceLast < MIN_RECONNECT_COOLDOWN_MS) {
                AppLogger.d(TAG, "Skipping background reconnect — cooldown active")
                return
            }
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
        if (!isPairingInProgress()) {
            pairingManager.disconnect()
        }
        if (force || remoteManager.currentRemoteState() !is RemoteManager.RemoteState.Idle) {
            remoteManager.disconnect()
        }
        lastReconnectAtMs = System.currentTimeMillis()
        publishNotification(RemoteConnectionStatus.RECONNECTING)
        notifyRemoteState("Connecting to ${currentDisplayName()}…")
        remoteManager.connect(pendingHost)
        scheduleConnectionWatchdog()
        scheduleHandshakeWatchdog()
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
                else -> {
                    userDisconnectRequested = false
                    connectionHeld = true
                    isPairedDevice = false
                    explicitPairingRequested.set(true)
                    stopConnectionMonitor()
                    cancelAutoReconnect()
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
                stopConnectionMonitor()
                cancelAutoReconnect()
                startPairing(force = true)
            }
        }
    }

    private fun startPairing(force: Boolean) {
        if (!force && !explicitPairingRequested.get()) return
        if (!force && !pairingStarted.compareAndSet(false, true)) return
        if (force) pairingStarted.set(true)

        stopConnectionMonitor()
        cancelAutoReconnect()
        reconnectScheduled.set(false)
        setConnectionBusy(true, showLoader = true)
        scheduleConnectionWatchdog()
        cancelPairingFallback()
        remoteManager.disconnect()
        pairingManager.disconnect()
        notifyPairingState("Starting pairing on port ${PairingManager.PAIRING_PORT}…")
        pairingManager.connect(pendingHost, clientName, serviceName)
        scheduleHandshakeWatchdog()
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
                if (shouldMaintainConnection() && !reconnectScheduled.get()) {
                    scheduleAutoReconnect(immediate = false)
                } else if (!shouldMaintainConnection()) {
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
                if (shouldMaintainConnection() && !reconnectScheduled.get()) {
                    scheduleAutoReconnect(immediate = false)
                } else if (!shouldMaintainConnection()) {
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
        isPairedDevice = false
        reconnectAttempt = 0
        reconnectScheduled.set(false)
        pendingAppLink = null
        cancelAutoReconnect()
        cancelPostPairingConnect()
        stopConnectionMonitor()
        endRecoverySession()
        publishNotification(RemoteConnectionStatus.DISCONNECTED)
        clearConnectionBusy()
        executeSafe {
            performDisconnect()
            notifyRemoteState("Disconnected — pair again with TV code")
            notifyPairingState("idle")
        }
    }

    /** App backgrounded — keep socket open; foreground service holds the session. */
    fun onAppBackgrounded() {
        if (shouldMaintainConnection()) {
            publishNotification(
                if (remoteManager.isSessionReady()) {
                    RemoteConnectionStatus.CONNECTED
                } else {
                    RemoteConnectionStatus.RECONNECTING
                },
            )
            updateConnectionMonitor()
        }
    }

    /** Heal session when foreground service starts or app returns. */
    fun maintainBackgroundConnection() {
        if (!shouldMaintainConnection()) return
        updateConnectionMonitor()
        executeSafe {
            if (remoteManager.isSessionReady()) {
                reconnectAttempt = 0
                cancelAutoReconnect()
                return@executeSafe
            }
            if (!remoteManager.isConnecting() &&
                !remoteManager.isHandshakeInProgress() &&
                !connectionBusy.get() &&
                !reconnectScheduled.get()
            ) {
                scheduleAutoReconnect(immediate = false)
            }
        }
    }

    private fun performDisconnect() {
        pairingStarted.set(false)
        explicitPairingRequested.set(false)
        waitingForCode.set(false)
        notifyWaitingForCode(false)
        cancelPairingFallback()
        cancelHandshakeWatchdog()
        cancelRecoveryRePairTimer()
        cancelPostPairingConnect()
        remoteManager.disconnect()
        pairingManager.disconnect()
    }
}
