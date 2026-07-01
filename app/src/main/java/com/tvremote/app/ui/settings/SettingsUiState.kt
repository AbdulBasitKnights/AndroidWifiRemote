package com.tvremote.app.ui.settings

data class SettingsUiState(
    val phoneIp: String? = null,
    val pairingState: String = "idle",
    val remoteState: String = "idle",
    val waitingForCode: Boolean = false,
    val discoveredTvs: List<com.tvremote.app.data.model.DiscoveredTv> = emptyList(),
    val isScanning: Boolean = false,
    val pairingHost: String? = null,
    val savedTvHost: String = "",
)
