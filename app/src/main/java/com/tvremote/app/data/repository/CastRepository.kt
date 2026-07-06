package com.tvremote.app.data.repository

import android.content.Context
import android.net.Uri
import com.tvremote.app.data.cast.CastManager
import com.tvremote.app.data.cast.LocalMediaServer
import com.tvremote.app.data.session.ConnectionCoordinator
import com.tvremote.app.util.OperationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CastRepository(
    private val castManager: CastManager,
    private val context: Context,
    private val coordinator: ConnectionCoordinator,
) {
    private val _castConnected = MutableStateFlow(false)
    val castConnected: StateFlow<Boolean> = _castConnected.asStateFlow()

    fun initialize() {
        castManager.onSessionChanged = { session ->
            val connected = session?.isConnected == true
            _castConnected.value = connected
            if (connected) {
                coordinator.onCastSessionStarted()
            } else {
                coordinator.onCastSessionEnded()
            }
        }
        castManager.initialize()
        _castConnected.value = castManager.isConnected()
    }

    fun isConnected(): Boolean = castManager.isConnected()

    fun refreshCastConnectionState() {
        _castConnected.value = castManager.isConnected()
    }

    fun deviceName(): String? = castManager.deviceName()

    fun isCastingActive(): Boolean = coordinator.isCastingActive()

    fun castImage(uri: Uri, title: String = "Photo", contentType: String = "image/jpeg"): OperationResult =
        castManager.castImage(uri, title, contentType)

    fun castVideo(uri: Uri, title: String = "Video", contentType: String = "video/mp4"): OperationResult =
        castManager.castVideo(uri, title, contentType)

    fun castAudio(uri: Uri, title: String = "Audio", contentType: String = "audio/mpeg"): OperationResult =
        castManager.castAudio(uri, title, contentType)

    fun startMirrorPlayback(url: String, title: String = "Screen Mirror"): OperationResult =
        castManager.startMirrorPlayback(url, title)

    fun castLiveStream(url: String, title: String = "Screen Mirror"): OperationResult =
        startMirrorPlayback(url, title)

    fun queueMirrorSegment(url: String, title: String = "Screen Mirror"): OperationResult =
        castManager.queueMirrorSegment(url, title)

    fun serveLocalMedia(uri: Uri, fileName: String): LocalMediaServer.ServedMedia =
        LocalMediaServer.serve(context, uri, fileName)

    fun stopLocalMediaServer() = LocalMediaServer.stop()
}
