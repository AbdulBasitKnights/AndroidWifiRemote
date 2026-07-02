package com.tvremote.app.data.repository

import android.content.Context
import android.net.Uri
import com.tvremote.app.data.cast.CastManager
import com.tvremote.app.data.cast.LocalMediaServer
import com.tvremote.app.data.session.ConnectionCoordinator
import com.tvremote.app.util.OperationResult

class CastRepository(
    private val castManager: CastManager,
    private val context: Context,
    private val coordinator: ConnectionCoordinator,
) {
    fun initialize() = castManager.initialize()

    fun isConnected(): Boolean = castManager.isConnected()

    fun deviceName(): String? = castManager.deviceName()

    fun isCastingActive(): Boolean = coordinator.isCastingActive()

    fun castImage(uri: Uri, title: String = "Photo"): OperationResult =
        castManager.castImage(uri, title)

    fun castVideo(uri: Uri, title: String = "Video"): OperationResult =
        castManager.castVideo(uri, title)

    fun castAudio(uri: Uri, title: String = "Audio"): OperationResult =
        castManager.castAudio(uri, title)

    fun castLiveStream(url: String, title: String = "Screen Mirror"): OperationResult =
        castManager.castLiveStream(url, title)

    fun serveLocalMedia(uri: Uri, fileName: String): String =
        LocalMediaServer.serve(context, uri, fileName)

    fun stopLocalMediaServer() = LocalMediaServer.stop()
}
