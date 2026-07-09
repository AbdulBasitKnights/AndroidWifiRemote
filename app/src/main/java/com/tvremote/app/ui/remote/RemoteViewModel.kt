package com.tvremote.app.ui.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvremote.app.data.model.DiscoveredTv
import com.tvremote.app.data.repository.CastRepository
import com.tvremote.app.data.repository.TvRemoteRepository
import com.tvremote.app.util.VoiceInputHelper
import com.tvremote.control.commands.Key
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class RemoteViewModel(
    private val repository: TvRemoteRepository,
    private val castRepository: CastRepository,
    private val voiceHelper: VoiceInputHelper,
) : ViewModel() {

    private val _volumeLevel = MutableStateFlow(7)
    val volumeLevel: StateFlow<Int> = _volumeLevel.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    val uiState: StateFlow<RemoteUiState> = combine(
        combine(
            repository.isPaired,
            repository.sessionReady,
            repository.connectionLoaderVisible,
            repository.remoteState,
            repository.pairingState,
        ) { paired, sessionReady, loaderVisible, remote, pairing ->
            ConnectionCore(paired, sessionReady, loaderVisible, remote, pairing)
        },
        repository.waitingForCode,
        repository.discoveredTvs,
        combine(repository.isScanning, repository.pairingHost) { scanning, host ->
            ScanHost(scanning, host)
        },
        castRepository.castConnected,
    ) { core, waiting, tvs, scanHost, castConnected ->
        RemoteUiState(
            isPaired = core.paired,
            isSessionReady = core.sessionReady,
            pairingState = core.pairing,
            remoteState = core.remote,
            waitingForCode = waiting,
            discoveredTvs = tvs,
            isScanning = scanHost.scanning,
            pairingHost = scanHost.host,
            savedTvHost = repository.savedTvHost,
            phoneIp = repository.getPhoneIp(),
            showConnectionLoader = core.loaderVisible && !waiting && !core.sessionReady,
            isCastConnected = castConnected,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemoteUiState())

    val events = repository.events

    init {
        voiceHelper.onListeningChanged = { listening -> _isListening.value = listening }
    }

    fun refreshConnectionState() = repository.refreshConnectionUi()

    fun healConnectionIfNeeded() = repository.syncConnectionState()

    fun refreshCastState() = castRepository.refreshCastConnectionState()

    fun startScan() = repository.startScan()
    fun stopScan() = repository.stopScan()
    fun onTvSelected(tv: DiscoveredTv) = repository.onTvSelected(tv.host)
    fun pairManual(host: String) = repository.pairWithTv(host)
    fun reconnect() = repository.reconnect()
    fun disconnect() = repository.disconnectUser()
    fun restartPairing(host: String) = repository.restartPairing(host)
    fun submitPairingCode(code: String) = repository.submitPairingCode(code)
    fun isWaitingForCode() = repository.isWaitingForPairingCode()

    fun power() = repository.power()
    fun sendKey(key: Key) {
        repository.sendKey(key)
    }

    fun sendText(text: String) {
        repository.sendText(text)
    }
    fun volUp() {
        if (repository.isSessionReady()) {
            _volumeLevel.value = (_volumeLevel.value + 1).coerceAtMost(15)
        }
        repository.volUp()
    }
    fun volDown() {
        if (repository.isSessionReady()) {
            _volumeLevel.value = (_volumeLevel.value - 1).coerceAtLeast(0)
        }
        repository.volDown()
    }
    fun channelUp() = repository.channelUp()
    fun channelDown() = repository.channelDown()
    fun mute() = repository.mute()
    fun playPause() = repository.playPause()
    fun rewind() = repository.rewind()
    fun forward() = repository.forward()
    fun tvInput() = repository.tvInput()
    fun apps() = repository.apps()
    fun runNetflix() = launchChannel { repository.runNetflix() }
    fun runYouTube() = launchChannel { repository.runYouTube() }
    fun runPrime() = launchChannel { repository.runPrime() }
    fun runHotstar() = launchChannel { repository.runHotstar() }
    fun runAppleTv() = launchChannel { repository.runAppleTv() }
    fun runDisney() = launchChannel { repository.runDisney() }

    private fun launchChannel(action: () -> Boolean) {
        repository.launchChannel(action)
    }

    fun startVoiceInput(languageCode: String) {
        if (!repository.isSessionReady()) return
        voiceHelper.startListening(languageCode)
    }

    fun stopVoiceInput() = voiceHelper.stopListening()

    private data class ConnectionCore(
        val paired: Boolean,
        val sessionReady: Boolean,
        val loaderVisible: Boolean,
        val remote: String,
        val pairing: String,
    )

    private data class ScanHost(val scanning: Boolean, val host: String?)

    data class RemoteUiState(
        val isPaired: Boolean = false,
        val isSessionReady: Boolean = false,
        val pairingState: String = "idle",
        val remoteState: String = "idle",
        val waitingForCode: Boolean = false,
        val discoveredTvs: List<DiscoveredTv> = emptyList(),
        val isScanning: Boolean = false,
        val pairingHost: String? = null,
        val savedTvHost: String = "",
        val phoneIp: String? = null,
        val showConnectionLoader: Boolean = false,
        val isCastConnected: Boolean = false,
    )
}
