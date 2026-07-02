package com.tvremote.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvremote.app.data.model.DiscoveredTv
import com.tvremote.app.data.repository.TvRemoteRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: TvRemoteRepository,
) : ViewModel() {

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events = _events.asSharedFlow()

    private data class CoreFlows(
        val pairingState: String,
        val remoteState: String,
        val waitingForCode: Boolean,
        val discoveredTvs: List<DiscoveredTv>,
        val isScanning: Boolean,
    )

    val uiState = combine(
        combine(
            repository.pairingState,
            repository.remoteState,
            repository.waitingForCode,
            repository.discoveredTvs,
            repository.isScanning,
        ) { pairing, remote, waiting, tvs, scanning ->
            CoreFlows(pairing, remote, waiting, tvs, scanning)
        },
        combine(repository.isPaired, repository.remotePaused) { paired, paused ->
            paired to paused
        },
        repository.pairingHost,
        repository.sessionMode,
    ) { core, session, host, mode ->
        SettingsUiState(
            phoneIp = repository.getPhoneIp(),
            pairingState = core.pairingState,
            remoteState = core.remoteState,
            waitingForCode = core.waitingForCode,
            discoveredTvs = core.discoveredTvs,
            isScanning = core.isScanning,
            pairingHost = host,
            savedTvHost = repository.savedTvHost,
            isPaired = session.first,
            remotePaused = session.second,
            sessionMode = mode,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch {
            repository.events.collect { event ->
                when (event) {
                    TvRemoteRepository.RepositoryEvent.PairingStarted ->
                        _events.emit(SettingsEvent.PairingStarted)
                    TvRemoteRepository.RepositoryEvent.UseCompletePairing ->
                        _events.emit(SettingsEvent.UseCompletePairing)
                    TvRemoteRepository.RepositoryEvent.SelectTvFirst ->
                        _events.emit(SettingsEvent.SelectTvFirst)
                    TvRemoteRepository.RepositoryEvent.InvalidIp ->
                        _events.emit(SettingsEvent.InvalidIp)
                    TvRemoteRepository.RepositoryEvent.Reconnecting ->
                        _events.emit(SettingsEvent.Reconnecting)
                    TvRemoteRepository.RepositoryEvent.CastingActive ->
                        _events.emit(SettingsEvent.CastingActive)
                    TvRemoteRepository.RepositoryEvent.RemotePaused ->
                        _events.emit(SettingsEvent.RemotePaused)
                    is TvRemoteRepository.RepositoryEvent.Error ->
                        _events.emit(SettingsEvent.Error(event.message))
                }
            }
        }
    }

    fun startScan() = repository.startScan()

    fun onTvSelected(tv: DiscoveredTv) = repository.onTvSelected(tv.host)

    fun pairManual(host: String) = repository.pairWithTv(host)

    fun reconnect() = repository.reconnect()

    fun restartPairing(host: String) = repository.restartPairing(host)

    fun submitPairingCode(code: String): Boolean = repository.submitPairingCode(code)

    fun isWaitingForCode(): Boolean = repository.isWaitingForPairingCode()

    fun stopScan() = repository.stopScan()

    sealed interface SettingsEvent {
        data object PairingStarted : SettingsEvent
        data object UseCompletePairing : SettingsEvent
        data object SelectTvFirst : SettingsEvent
        data object InvalidIp : SettingsEvent
        data object Reconnecting : SettingsEvent
        data object CastingActive : SettingsEvent
        data object RemotePaused : SettingsEvent
        data class Error(val message: String) : SettingsEvent
    }
}
