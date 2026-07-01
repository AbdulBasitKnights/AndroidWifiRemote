package com.tvremote.app

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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class RemoteTvManager(context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val certManager = CertManager(context)
    private val cryptoManager = CryptoManager()
    private val tlsManager: TlsManager
    private val pairingManager: PairingManager
    private val remoteManager: RemoteManager
    private val pairingStarted = AtomicBoolean(false)
    private val waitingForCode = AtomicBoolean(false)

    var pairingStateChanged: ((String) -> Unit)? = null
    var remoteStateChanged: ((String) -> Unit)? = null
    var onWaitingForCodeChanged: ((Boolean) -> Unit)? = null

    private var pendingHost: String = ""
    private val clientName = "Android TV Remote"
    private val serviceName = "atvremote"

    fun isWaitingForPairingCode(): Boolean = waitingForCode.get()

    private fun notifyPairingState(message: String) {
        mainHandler.post { pairingStateChanged?.invoke(message) }
    }

    private fun notifyRemoteState(message: String) {
        mainHandler.post { remoteStateChanged?.invoke(message) }
    }

    private fun notifyWaitingForCode(waiting: Boolean) {
        mainHandler.post { onWaitingForCodeChanged?.invoke(waiting) }
    }

    init {
        certManager.ensureCertificates(clientName)

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
            waitingForCode.set(state is PairingManager.PairingState.WaitingCode)
            notifyWaitingForCode(waitingForCode.get())
            notifyPairingState(state.toDisplayString())

            when (state) {
                is PairingManager.PairingState.SuccessPaired -> {
                    pairingStarted.set(false)
                    waitingForCode.set(false)
                    notifyWaitingForCode(false)
                    remoteManager.disconnect()
                    remoteManager.connect(pendingHost)
                }
                is PairingManager.PairingState.SecretSent -> {
                    notifyPairingState("Submitting pairing code…")
                }
                is PairingManager.PairingState.Error -> {
                    waitingForCode.set(false)
                    notifyWaitingForCode(false)
                    pairingStarted.set(false)
                }
                else -> Unit
            }
        }

        remoteManager.stateChanged = { state ->
            notifyRemoteState(state.toDisplayString())
            when (state) {
                is RemoteManager.RemoteState.Connected -> {
                    if (!waitingForCode.get()) {
                        schedulePairingFallback()
                    }
                }
                is RemoteManager.RemoteState.Paired -> {
                    pairingStarted.set(false)
                    waitingForCode.set(false)
                    notifyWaitingForCode(false)
                    cancelPairingFallback()
                }
                is RemoteManager.RemoteState.Error -> {
                    cancelPairingFallback()
                    if (!pairingStarted.get() && !waitingForCode.get()) {
                        startPairingIfNeeded(state.error)
                    }
                }
                else -> Unit
            }
        }
    }

    private var pairingFallback: java.util.concurrent.ScheduledFuture<*>? = null
    private val scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()

    private fun schedulePairingFallback() {
        cancelPairingFallback()
        pairingFallback = scheduler.schedule({
            if (!pairingStarted.get() && !waitingForCode.get()) {
                notifyPairingState("Remote connected but not paired — starting pairing…")
                startPairing(force = true)
            }
        }, 8, java.util.concurrent.TimeUnit.SECONDS)
    }

    private fun cancelPairingFallback() {
        pairingFallback?.cancel(false)
        pairingFallback = null
    }

    fun connect(host: String) {
        pendingHost = host.trim()
        if (pendingHost.isEmpty()) return

        executor.execute {
            if (waitingForCode.get()) {
                notifyPairingState(
                    "Code already on TV — enter it below and tap Complete Pairing (do not tap Pair again)",
                )
                return@execute
            }
            pairingStarted.set(false)
            cancelPairingFallback()
            remoteManager.disconnect()
            pairingManager.disconnect()
            remoteManager.connect(pendingHost)
        }
    }

    fun pairOnly(host: String? = null) {
        executor.execute {
            host?.trim()?.takeIf { it.isNotEmpty() }?.let { pendingHost = it }
            if (pendingHost.isEmpty()) return@execute

            if (waitingForCode.get()) {
                notifyPairingState(
                    "Already waiting for code — enter it and tap Complete Pairing",
                )
                return@execute
            }
            startPairing(force = true)
        }
    }

    /** Restart pairing only when user explicitly asks (new code on TV). */
    fun restartPairing(host: String? = null) {
        executor.execute {
            host?.trim()?.takeIf { it.isNotEmpty() }?.let { pendingHost = it }
            if (pendingHost.isEmpty()) return@execute
            waitingForCode.set(false)
            notifyWaitingForCode(false)
            startPairing(force = true)
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
        if (needsPairing) {
            startPairing(force = false)
        }
    }

    private fun startPairing(force: Boolean) {
        if (!force && !pairingStarted.compareAndSet(false, true)) return
        if (force) pairingStarted.set(true)

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
        executor.execute {
            pairingManager.sendSecret(normalized)
        }
        return true
    }

    fun sendCode(code: String) = submitPairingCode(code)

    fun sendKey(key: Key) {
        executor.execute {
            remoteManager.send(KeyPress(key))
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
        executor.execute {
            remoteManager.send(DeepLink(url))
        }
    }

    fun disconnect() {
        executor.execute {
            pairingStarted.set(false)
            waitingForCode.set(false)
            notifyWaitingForCode(false)
            cancelPairingFallback()
            remoteManager.disconnect()
            pairingManager.disconnect()
        }
    }
}
