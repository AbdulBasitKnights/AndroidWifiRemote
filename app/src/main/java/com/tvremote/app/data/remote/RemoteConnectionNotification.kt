package com.tvremote.app.data.remote

enum class RemoteConnectionStatus {
    CONNECTED,
    RECONNECTING,
    PAIRING,
    DISCONNECTED,
}

data class RemoteConnectionNotification(
    val active: Boolean,
    val host: String,
    val displayName: String,
    val status: RemoteConnectionStatus,
) {
    fun label(): String = displayName.ifBlank { host }
}
