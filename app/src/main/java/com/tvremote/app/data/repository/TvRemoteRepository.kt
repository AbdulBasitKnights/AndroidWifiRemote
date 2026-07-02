package com.tvremote.app.data.repository

import com.tvremote.app.data.discovery.TvDiscovery
import com.tvremote.app.data.model.DiscoveredTv
import com.tvremote.app.data.remote.RemoteTvManager
import com.tvremote.app.data.session.AppSessionMode
import com.tvremote.app.data.session.ConnectionCoordinator
import com.tvremote.app.util.NetworkUtils
import com.tvremote.app.util.SafeRun
import com.tvremote.control.commands.Key
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TvRemoteRepository(
    private val remoteManager: RemoteTvManager,
    private val tvDiscovery: TvDiscovery,
    private val coordinator: ConnectionCoordinator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val discoveredMap = linkedMapOf<String, DiscoveredTv>()

    private val _pairingState = MutableStateFlow("idle")
    val pairingState: StateFlow<String> = _pairingState.asStateFlow()

    private val _remoteState = MutableStateFlow("idle")
    val remoteState: StateFlow<String> = _remoteState.asStateFlow()

    private val _waitingForCode = MutableStateFlow(false)
    val waitingForCode: StateFlow<Boolean> = _waitingForCode.asStateFlow()

    private val _discoveredTvs = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val discoveredTvs: StateFlow<List<DiscoveredTv>> = _discoveredTvs.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _pairingHost = MutableStateFlow<String?>(null)
    val pairingHost: StateFlow<String?> = _pairingHost.asStateFlow()

    private val _isPaired = MutableStateFlow(coordinator.isPaired())
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()

    val sessionMode: StateFlow<AppSessionMode> = coordinator.sessionMode
    val remotePaused: StateFlow<Boolean> = coordinator.remotePaused

    private val _events = MutableSharedFlow<RepositoryEvent>()
    val events: SharedFlow<RepositoryEvent> = _events.asSharedFlow()

    val savedTvHost: String
        get() = coordinator.savedHost().ifEmpty { remoteManager.currentHost() }

    init {
        coordinator.loadSavedSession()

        remoteManager.onPaired = { host ->
            SafeRun.run("TvRemoteRepository") {
                coordinator.onRemotePaired(host)
                _isPaired.value = true
                _pairingHost.value = null
            }
        }

        remoteManager.pairingStateChanged = { _pairingState.value = it }
        remoteManager.remoteStateChanged = { state ->
            SafeRun.run("TvRemoteRepository") {
                _remoteState.value = state
                if (state.contains("Paired", ignoreCase = true)) {
                    _isPaired.value = true
                    _pairingHost.value = null
                }
            }
        }
        remoteManager.onWaitingForCodeChanged = { waiting ->
            SafeRun.run("TvRemoteRepository") {
                _waitingForCode.value = waiting
                if (!waiting) _pairingHost.value = null
            }
        }
        tvDiscovery.onDeviceFound = { tv ->
            SafeRun.run("TvRemoteRepository") {
                discoveredMap[tv.host] = tv
                _discoveredTvs.value = discoveredMap.values.toList()
            }
        }
        tvDiscovery.onScanFinished = { devices ->
            SafeRun.run("TvRemoteRepository") {
                devices.forEach { discoveredMap[it.host] = it }
                _discoveredTvs.value = discoveredMap.values.toList()
                _isScanning.value = false
            }
        }
        tvDiscovery.onError = { message ->
            SafeRun.run("TvRemoteRepository") {
                _isScanning.value = false
                scope.launch { _events.emit(RepositoryEvent.Error(message)) }
            }
        }
    }

    fun getPhoneIp(): String? = NetworkUtils.getWifiIpv4Address()

    fun isWaitingForPairingCode(): Boolean = remoteManager.isWaitingForPairingCode()

    fun isCastingActive(): Boolean = coordinator.isCastingActive()

    fun startScan() {
        discoveredMap.clear()
        _discoveredTvs.value = emptyList()
        _isScanning.value = true
        tvDiscovery.startScan()
    }

    fun stopScan() = tvDiscovery.stopScan()

    fun onTvSelected(host: String) {
        if (remoteManager.isWaitingForPairingCode()) {
            scope.launch { _events.emit(RepositoryEvent.UseCompletePairing) }
            return
        }
        if (!validateHost(host)) return

        if (coordinator.isPairedWith(host)) {
            reconnect(host)
        } else {
            pairWithTv(host)
        }
    }

    fun pairWithTv(host: String) {
        if (coordinator.isCastingActive()) {
            scope.launch { _events.emit(RepositoryEvent.CastingActive) }
            return
        }
        if (remoteManager.isWaitingForPairingCode()) {
            scope.launch { _events.emit(RepositoryEvent.UseCompletePairing) }
            return
        }
        if (!validateHost(host)) return
        _pairingHost.value = host
        remoteManager.pairOnly(host)
        scope.launch { _events.emit(RepositoryEvent.PairingStarted) }
    }

    fun reconnect(host: String? = null) {
        val target = host?.trim().orEmpty().ifEmpty { savedTvHost }
        if (target.isBlank()) {
            scope.launch { _events.emit(RepositoryEvent.SelectTvFirst) }
            return
        }
        if (coordinator.isCastingActive()) {
            scope.launch { _events.emit(RepositoryEvent.CastingActive) }
            return
        }
        if (!validateHost(target)) return
        _pairingHost.value = null
        remoteManager.connect(target)
        scope.launch { _events.emit(RepositoryEvent.Reconnecting) }
    }

    fun restartPairing(host: String) {
        if (coordinator.isCastingActive()) {
            scope.launch { _events.emit(RepositoryEvent.CastingActive) }
            return
        }
        if (host.isBlank()) {
            scope.launch { _events.emit(RepositoryEvent.SelectTvFirst) }
            return
        }
        _pairingHost.value = host
        coordinator.resetPairing()
        _isPaired.value = false
        remoteManager.restartPairing(host)
    }

    fun submitPairingCode(code: String): Boolean = remoteManager.submitPairingCode(code)

    fun validateHost(host: String): Boolean {
        if (host.isBlank()) {
            scope.launch { _events.emit(RepositoryEvent.SelectTvFirst) }
            return false
        }
        if (!NetworkUtils.isConnectableHost(host)) {
            scope.launch { _events.emit(RepositoryEvent.InvalidIp) }
            return false
        }
        return true
    }

    fun sendKey(key: Key) {
        if (coordinator.remotePaused.value) {
            scope.launch { _events.emit(RepositoryEvent.RemotePaused) }
            return
        }
        remoteManager.sendKey(key)
    }

    fun power() = guardedRemote { remoteManager.power() }
    fun volUp() = guardedRemote { remoteManager.volUp() }
    fun volDown() = guardedRemote { remoteManager.volDown() }
    fun channelUp() = guardedRemote { remoteManager.channelUp() }
    fun channelDown() = guardedRemote { remoteManager.channelDown() }
    fun mute() = guardedRemote { remoteManager.mute() }
    fun playPause() = guardedRemote { remoteManager.playPause() }
    fun rewind() = guardedRemote { remoteManager.rewind() }
    fun forward() = guardedRemote { remoteManager.forward() }
    fun voiceSearch() = guardedRemote { remoteManager.voiceSearch() }
    fun tvInput() = guardedRemote { remoteManager.tvInput() }
    fun apps() = guardedRemote { remoteManager.apps() }
    fun runNetflix() = guardedRemote { remoteManager.runNetflix() }
    fun runYouTube() = guardedRemote { remoteManager.runYouTube() }
    fun runPrime() = guardedRemote { remoteManager.runPrime() }
    fun runHotstar() = guardedRemote { remoteManager.runHotstar() }

    private inline fun guardedRemote(action: () -> Unit) {
        if (coordinator.remotePaused.value) {
            scope.launch { _events.emit(RepositoryEvent.RemotePaused) }
            return
        }
        action()
    }

    sealed interface RepositoryEvent {
        data object PairingStarted : RepositoryEvent
        data object UseCompletePairing : RepositoryEvent
        data object SelectTvFirst : RepositoryEvent
        data object InvalidIp : RepositoryEvent
        data object Reconnecting : RepositoryEvent
        data object CastingActive : RepositoryEvent
        data object RemotePaused : RepositoryEvent
        data class Error(val message: String) : RepositoryEvent
    }
}
