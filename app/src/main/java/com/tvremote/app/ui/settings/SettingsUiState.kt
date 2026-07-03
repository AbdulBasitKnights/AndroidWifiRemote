package com.tvremote.app.ui.settings

import com.tvremote.app.data.model.DiscoveredTv
import com.tvremote.app.data.session.AppSessionMode

data class SettingsUiState(
    val phoneIp: String? = null,
    val pairingState: String = "idle",
    val remoteState: String = "idle",
    val waitingForCode: Boolean = false,
    val discoveredTvs: List<DiscoveredTv> = emptyList(),
    val isScanning: Boolean = false,
    val pairingHost: String? = null,
    val savedTvHost: String = "",
    val isPaired: Boolean = false,
    val sessionMode: AppSessionMode = AppSessionMode.REMOTE,
    val isSessionReady: Boolean = false,
    val showPairingLoader: Boolean = false,
)
