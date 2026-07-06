package com.tvremote.app.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tvremote.app.di.AppContainer
import com.tvremote.app.ui.cast.CastViewModel
import com.tvremote.app.ui.remote.RemoteViewModel
import com.tvremote.app.ui.settings.SettingsViewModel

class AppViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel() as T
            modelClass.isAssignableFrom(RemoteViewModel::class.java) ->
                RemoteViewModel(
                    container.tvRemoteRepository,
                    container.castRepository,
                    container.voiceInputHelper,
                ) as T
            modelClass.isAssignableFrom(CastViewModel::class.java) ->
                CastViewModel(container.castRepository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
