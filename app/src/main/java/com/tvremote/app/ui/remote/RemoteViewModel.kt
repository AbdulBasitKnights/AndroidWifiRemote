package com.tvremote.app.ui.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvremote.app.data.model.DiscoveredTv
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
    private val voiceHelper: VoiceInputHelper,
) : ViewModel() {

    private val _volumeLevel = MutableStateFlow(7)
    val volumeLevel: StateFlow<Int> = _volumeLevel.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    val uiState: StateFlow<RemoteUiState> = combine(
        combine(
            repository.isPaired,
            repository.remoteState,
            repository.pairingState,
            repository.waitingForCode,
            repository.discoveredTvs,
        ) { paired, remote, pairing, waiting, tvs ->
            Core(paired, remote, pairing, waiting, tvs)
        },
        combine(repository.isScanning, repository.pairingHost) { scanning, host ->
            ScanHost(scanning, host)
        },
    ) { core, extra ->
        val ready = repository.isSessionReady()
        val busy = repository.isConnectionBusy()
        RemoteUiState(
            isPaired = core.paired,
            isSessionReady = ready,
            pairingState = core.pairing,
            remoteState = core.remote,
            waitingForCode = core.waiting,
            discoveredTvs = core.tvs,
            isScanning = extra.scanning,
            pairingHost = extra.host,
            savedTvHost = repository.savedTvHost,
            phoneIp = repository.getPhoneIp(),
            showConnectionLoader = busy && !core.waiting,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemoteUiState())

    val events = repository.events

    init {
        voiceHelper.onListeningChanged = { listening -> _isListening.value = listening }
    }

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
        if (!repository.isSessionReady()) return
        repository.sendKey(key)
    }
    fun volUp() {
        if (!repository.isSessionReady()) return
        _volumeLevel.value = (_volumeLevel.value + 1).coerceAtMost(15)
        repository.volUp()
    }
    fun volDown() {
        if (!repository.isSessionReady()) return
        _volumeLevel.value = (_volumeLevel.value - 1).coerceAtLeast(0)
        repository.volDown()
    }
    fun channelUp() = guarded { repository.channelUp() }
    fun channelDown() = guarded { repository.channelDown() }
    fun mute() = guarded { repository.mute() }
    fun playPause() = guarded { repository.playPause() }
    fun rewind() = guarded { repository.rewind() }
    fun forward() = guarded { repository.forward() }
    fun tvInput() = guarded { repository.tvInput() }
    fun apps() = guarded { repository.apps() }
    fun runNetflix() = guarded { repository.runNetflix() }
    fun runYouTube() = guarded { repository.runYouTube() }
    fun runPrime() = guarded { repository.runPrime() }
    fun runHotstar() = guarded { repository.runHotstar() }

    fun startVoiceInput(languageCode: String) {
        if (!repository.isSessionReady()) return
        voiceHelper.startListening(languageCode)
    }

    fun stopVoiceInput() = voiceHelper.stopListening()

    private inline fun guarded(block: () -> Unit) {
        if (repository.isSessionReady()) block()
    }

    private data class Core(
        val paired: Boolean,
        val remote: String,
        val pairing: String,
        val waiting: Boolean,
        val tvs: List<DiscoveredTv>,
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
    )
}
