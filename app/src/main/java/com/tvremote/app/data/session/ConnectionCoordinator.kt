package com.tvremote.app.data.session

import com.tvremote.app.data.remote.RemoteTvManager
import com.tvremote.app.util.SafeRun
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppSessionMode {
    REMOTE,
    CAST,
    SCREEN_MIRROR,
}

class ConnectionCoordinator(
    private val remoteManager: RemoteTvManager,
    private val pairingStore: PairingStore,
) {
    private var castActive = false
    private var mirrorActive = false

    private val _sessionMode = MutableStateFlow(AppSessionMode.REMOTE)
    val sessionMode: StateFlow<AppSessionMode> = _sessionMode.asStateFlow()

    private val _remotePaused = MutableStateFlow(false)
    val remotePaused: StateFlow<Boolean> = _remotePaused.asStateFlow()

    fun onCastSessionStarted() = SafeRun.run(TAG) {
        castActive = true
        applyPauseState()
    }

    fun onCastSessionEnded() = SafeRun.run(TAG) {
        castActive = false
        applyPauseState()
    }

    fun onScreenMirrorStarted() = SafeRun.run(TAG) {
        mirrorActive = true
        applyPauseState()
    }

    fun onScreenMirrorStopped() = SafeRun.run(TAG) {
        mirrorActive = false
        applyPauseState()
    }

    fun isCastingActive(): Boolean = castActive || mirrorActive

    private fun applyPauseState() {
        SafeRun.run(TAG) {
            val shouldPause = castActive || mirrorActive
            _remotePaused.value = shouldPause
            _sessionMode.value = when {
                mirrorActive -> AppSessionMode.SCREEN_MIRROR
                castActive -> AppSessionMode.CAST
                else -> AppSessionMode.REMOTE
            }
            if (shouldPause) {
                remoteManager.pauseForCast()
            } else {
                remoteManager.resumeAfterCast()
            }
        }
    }

    fun onRemotePaired(host: String) = SafeRun.run(TAG) {
        pairingStore.markPaired(host)
        remoteManager.markPaired(host)
    }

    fun loadSavedSession() = SafeRun.run(TAG) {
        val host = pairingStore.savedHost()
        if (host.isNotEmpty() && pairingStore.isPaired()) {
            remoteManager.loadSavedSession(host, paired = true)
        }
    }

    fun isPairedWith(host: String): Boolean = pairingStore.isPairedWith(host)

    fun isPaired(): Boolean = pairingStore.isPaired()

    fun savedHost(): String = pairingStore.savedHost()

    fun resetPairing() = SafeRun.run(TAG) {
        pairingStore.clear()
        remoteManager.resetPairingState()
    }

    companion object {
        private const val TAG = "ConnectionCoordinator"
    }
}
