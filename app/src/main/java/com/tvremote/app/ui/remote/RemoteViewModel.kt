package com.tvremote.app.ui.remote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvremote.app.data.repository.TvRemoteRepository
import com.tvremote.app.data.session.AppSessionMode
import com.tvremote.control.commands.Key
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RemoteViewModel(
    private val repository: TvRemoteRepository,
) : ViewModel() {

    private val _volumeLevel = MutableStateFlow(7)
    val volumeLevel: StateFlow<Int> = _volumeLevel.asStateFlow()

    private val _events = MutableSharedFlow<RemoteEvent>()
    val events = _events.asSharedFlow()

    val connectionStatus: StateFlow<String> = combine(
        repository.remoteState,
        repository.remotePaused,
        repository.sessionMode,
    ) { remote, paused, mode ->
        when {
            paused && mode == AppSessionMode.SCREEN_MIRROR ->
                "Remote paused — screen mirroring active"
            paused && mode == AppSessionMode.CAST ->
                "Remote paused — casting active"
            paused -> "Remote paused while casting"
            else -> remote
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "idle")

    val remotePaused: StateFlow<Boolean> = repository.remotePaused

    init {
        viewModelScope.launch {
            repository.events.collect { event ->
                if (event == TvRemoteRepository.RepositoryEvent.RemotePaused) {
                    _events.emit(RemoteEvent.PausedWhileCasting)
                }
            }
        }
    }

    fun power() = repository.power()
    fun sendKey(key: Key) = repository.sendKey(key)
    fun volUp() {
        _volumeLevel.value = (_volumeLevel.value + 1).coerceAtMost(15)
        repository.volUp()
    }
    fun volDown() {
        _volumeLevel.value = (_volumeLevel.value - 1).coerceAtLeast(0)
        repository.volDown()
    }
    fun channelUp() = repository.channelUp()
    fun channelDown() = repository.channelDown()
    fun mute() = repository.mute()
    fun playPause() = repository.playPause()
    fun rewind() = repository.rewind()
    fun forward() = repository.forward()
    fun voiceSearch() = repository.voiceSearch()
    fun tvInput() = repository.tvInput()
    fun apps() = repository.apps()
    fun runNetflix() = repository.runNetflix()
    fun runYouTube() = repository.runYouTube()
    fun runPrime() = repository.runPrime()
    fun runHotstar() = repository.runHotstar()

    sealed interface RemoteEvent {
        data object PausedWhileCasting : RemoteEvent
    }
}
