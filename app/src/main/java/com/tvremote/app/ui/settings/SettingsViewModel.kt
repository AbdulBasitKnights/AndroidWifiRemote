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
        repository.pairingHost,
    ) { flows, host ->
        SettingsUiState(
            phoneIp = repository.getPhoneIp(),
            pairingState = flows.pairingState,
            remoteState = flows.remoteState,
            waitingForCode = flows.waitingForCode,
            discoveredTvs = flows.discoveredTvs,
            isScanning = flows.isScanning,
            pairingHost = host,
            savedTvHost = repository.savedTvHost,
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
                    is TvRemoteRepository.RepositoryEvent.Error ->
                        _events.emit(SettingsEvent.Error(event.message))
                }
            }
        }
    }

    fun startScan() = repository.startScan()

    fun onTvSelected(tv: DiscoveredTv) = repository.pairWithTv(tv.host)

    fun pairManual(host: String) = repository.pairWithTv(host)

    fun restartPairing(host: String) = repository.restartPairing(host)

    fun submitPairingCode(code: String): Boolean = repository.submitPairingCode(code)

    fun isWaitingForCode(): Boolean = repository.isWaitingForPairingCode()

    fun stopScan() = repository.stopScan()

    sealed interface SettingsEvent {
        data object PairingStarted : SettingsEvent
        data object UseCompletePairing : SettingsEvent
        data object SelectTvFirst : SettingsEvent
        data object InvalidIp : SettingsEvent
        data class Error(val message: String) : SettingsEvent
    }
}
