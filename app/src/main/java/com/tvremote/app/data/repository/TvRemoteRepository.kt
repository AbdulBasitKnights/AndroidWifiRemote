package com.tvremote.app.data.repository

import android.content.Context
import com.tvremote.app.data.discovery.CastRouteDiscovery
import com.tvremote.app.data.discovery.TvDiscovery
import com.tvremote.app.data.model.DiscoveredTv
import com.tvremote.app.data.remote.RemoteConnectionService
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
    private val appContext: Context,
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

    private val _sessionReady = MutableStateFlow(false)
    val sessionReady: StateFlow<Boolean> = _sessionReady.asStateFlow()

    private val _connectionLoaderVisible = MutableStateFlow(false)
    val connectionLoaderVisible: StateFlow<Boolean> = _connectionLoaderVisible.asStateFlow()

    val sessionMode: StateFlow<AppSessionMode> = coordinator.sessionMode

    private val _events = MutableSharedFlow<RepositoryEvent>()
    val events: SharedFlow<RepositoryEvent> = _events.asSharedFlow()

    val savedTvHost: String
        get() = coordinator.savedHost().ifEmpty { remoteManager.currentHost() }

    val savedTvName: String
        get() = coordinator.savedTvName()
            .ifEmpty { resolveDisplayName(savedTvHost) }
            .ifEmpty { remoteManager.currentDisplayName() }

    init {
        coordinator.loadSavedSession()
        refreshConnectionSnapshot()
        ensureConnected()

        remoteManager.onPairingCredentialsSaved = { host ->
            SafeRun.run("TvRemoteRepository") {
                val name = resolveDisplayName(host)
                coordinator.onRemotePaired(host, name)
                _isPaired.value = true
                _pairingHost.value = null
                refreshConnectionSnapshot()
            }
        }

        remoteManager.onPaired = {
            SafeRun.run("TvRemoteRepository") {
                refreshConnectionSnapshot()
            }
        }

        remoteManager.onConnectionUiChanged = { refreshConnectionSnapshot() }

        remoteManager.onNotificationChanged = { payload ->
            SafeRun.run("TvRemoteRepository") {
                RemoteConnectionService.sync(appContext, payload)
            }
        }

        remoteManager.onAppLaunched = {
            scope.launch { _events.emit(RepositoryEvent.ChannelLaunched) }
        }

        remoteManager.pairingStateChanged = { _pairingState.value = it }
        remoteManager.remoteStateChanged = { state ->
            SafeRun.run("TvRemoteRepository") {
                _remoteState.value = state
                refreshConnectionSnapshot()
                if (state.contains("Paired", ignoreCase = true)) {
                    _isPaired.value = true
                    _pairingHost.value = null
                }
            }
        }
        remoteManager.onWaitingForCodeChanged = { waiting ->
            SafeRun.run("TvRemoteRepository") {
                _waitingForCode.value = waiting
                if (waiting) {
                    _pairingHost.value = remoteManager.currentHost()
                    scope.launch { _events.emit(RepositoryEvent.PairingStarted) }
                } else {
                    _pairingHost.value = null
                }
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

    fun ensureConnected() {
        if (coordinator.isPaired()) {
            remoteManager.ensureConnected()
        }
        refreshConnectionSnapshot()
    }

    fun syncConnectionState() {
        remoteManager.syncConnectionState()
        refreshConnectionSnapshot()
    }

    fun maintainBackgroundConnection() {
        remoteManager.maintainBackgroundConnection()
        refreshConnectionSnapshot()
    }

    fun onAppBackgrounded() {
        remoteManager.onAppBackgrounded()
    }

    fun refreshConnectionUi() {
        remoteManager.refreshConnectionUi()
        refreshConnectionSnapshot()
    }

    private fun refreshConnectionSnapshot() {
        val ready = remoteManager.isSessionReady()
        _sessionReady.value = ready
        _connectionLoaderVisible.value = if (ready) {
            false
        } else {
            remoteManager.isConnectionLoaderVisible()
        }
        if (ready) {
            _isPaired.value = coordinator.isPaired() || remoteManager.isPaired()
        }
    }

    fun disconnectUser() {
        remoteManager.disconnectUser()
        coordinator.onUserDisconnected()
        refreshConnectionSnapshot()
        _isPaired.value = false
        scope.launch { _events.emit(RepositoryEvent.Disconnected) }
    }

    fun disconnectForAppClose() {
        remoteManager.onAppBackgrounded()
    }

    fun isSessionReady(): Boolean {
        val ready = remoteManager.isSessionReady()
        if (ready != _sessionReady.value) {
            _sessionReady.value = ready
        }
        return ready
    }

    fun getPhoneIp(): String? = NetworkUtils.getWifiIpv4Address()

    fun isWaitingForPairingCode(): Boolean = remoteManager.isWaitingForPairingCode()

    fun isCastingActive(): Boolean = coordinator.isCastingActive()

    fun startScan() {
        discoveredMap.clear()
        seedKnownDevices()
        _discoveredTvs.value = discoveredMap.values.toList()
        _isScanning.value = true
        tvDiscovery.startScan()
    }

    private fun seedKnownDevices() {
        val saved = savedTvHost
        if (saved.isNotBlank() && coordinator.isPaired()) {
            discoveredMap[saved] = DiscoveredTv(
                name = savedTvName.ifBlank { "Paired TV" },
                host = saved,
                port = 6466,
            )
        }
        CastRouteDiscovery.discover(appContext).forEach { tv ->
            discoveredMap[tv.host] = tv
        }
    }

    fun stopScan() = tvDiscovery.stopScan()

    fun onTvSelected(host: String) {
        if (remoteManager.isWaitingForPairingCode()) {
            scope.launch { _events.emit(RepositoryEvent.UseCompletePairing) }
            return
        }
        if (!validateHost(host)) return
        remoteManager.setDisplayName(resolveDisplayName(host))

        if (coordinator.isPairedWith(host)) {
            if (!remoteManager.isSessionReady()) {
                reconnect(host)
            } else {
                refreshConnectionSnapshot()
            }
        } else {
            pairWithTv(host)
        }
    }

    fun pairWithTv(host: String) {
        if (remoteManager.isWaitingForPairingCode()) {
            scope.launch { _events.emit(RepositoryEvent.UseCompletePairing) }
            return
        }
        if (!validateHost(host)) return
        remoteManager.setDisplayName(resolveDisplayName(host))
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
        if (!validateHost(target)) return
        remoteManager.setDisplayName(resolveDisplayName(target))
        if (!coordinator.isPaired()) {
            pairWithTv(target)
            return
        }
        _pairingHost.value = null
        remoteManager.reconnectTo(target)
        refreshConnectionSnapshot()
        scope.launch { _events.emit(RepositoryEvent.Reconnecting) }
    }

    private fun resolveDisplayName(host: String): String {
        if (host.isBlank()) return ""
        return discoveredMap[host]?.name
            ?.takeIf { it.isNotBlank() }
            ?: coordinator.savedTvName().takeIf { coordinator.isPairedWith(host) }.orEmpty()
    }

    fun isConnectionBusy(): Boolean = _connectionLoaderVisible.value

    fun restartPairing(host: String) {
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

    fun sendKey(key: Key) = remoteManager.sendKey(key)

    fun sendText(text: String) = remoteManager.sendText(text)

    fun power() = remoteManager.power()
    fun volUp() = remoteManager.volUp()
    fun volDown() = remoteManager.volDown()
    fun channelUp() = remoteManager.channelUp()
    fun channelDown() = remoteManager.channelDown()
    fun mute() = remoteManager.mute()
    fun playPause() = remoteManager.playPause()
    fun rewind() = remoteManager.rewind()
    fun forward() = remoteManager.forward()
    fun voiceSearch() = remoteManager.voiceSearch()
    fun tvInput() = remoteManager.tvInput()
    fun apps() = remoteManager.apps()
    fun runNetflix() = remoteManager.runNetflix()
    fun runYouTube() = remoteManager.runYouTube()
    fun runPrime() = remoteManager.runPrime()
    fun runHotstar() = remoteManager.runHotstar()
    fun runAppleTv() = remoteManager.runAppleTv()
    fun runDisney() = remoteManager.runDisney()

    fun launchChannel(action: () -> Boolean) {
        val launched = action()
        if (launched) {
            scope.launch { _events.emit(RepositoryEvent.ChannelLaunched) }
            return
        }
        if (coordinator.isPaired()) {
            scope.launch { _events.emit(RepositoryEvent.ChannelLaunching) }
        } else {
            scope.launch { _events.emit(RepositoryEvent.ChannelNotConnected) }
        }
    }

    sealed interface RepositoryEvent {
        data object PairingStarted : RepositoryEvent
        data object UseCompletePairing : RepositoryEvent
        data object SelectTvFirst : RepositoryEvent
        data object InvalidIp : RepositoryEvent
        data object Reconnecting : RepositoryEvent
        data object Disconnected : RepositoryEvent
        data object ChannelNotConnected : RepositoryEvent
        data object ChannelLaunching : RepositoryEvent
        data object ChannelLaunched : RepositoryEvent
        data class Error(val message: String) : RepositoryEvent
    }
}
