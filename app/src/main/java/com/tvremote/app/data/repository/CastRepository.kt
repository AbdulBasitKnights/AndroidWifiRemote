package com.tvremote.app.data.repository

import android.content.Context
import android.net.Uri
import com.tvremote.app.data.cast.CastManager
import com.tvremote.app.data.cast.LocalMediaServer
import com.google.android.gms.cast.framework.CastSession

class CastRepository(
    private val castManager: CastManager,
    private val context: Context,
) {
    fun initialize() = castManager.initialize()

    fun isConnected(): Boolean = castManager.isConnected()

    fun deviceName(): String? = castManager.deviceName()

    var onSessionChanged: ((CastSession?) -> Unit)?
        get() = castManager.onSessionChanged
        set(value) { castManager.onSessionChanged = value }

    fun castImage(uri: Uri, title: String = "Photo") = castManager.castImage(uri, title)

    fun castVideo(uri: Uri, title: String = "Video") = castManager.castVideo(uri, title)

    fun castAudio(uri: Uri, title: String = "Audio") = castManager.castAudio(uri, title)

    fun castLiveStream(url: String, title: String = "Screen Mirror") =
        castManager.castLiveStream(url, title)

    fun serveLocalMedia(uri: Uri, fileName: String): String =
        LocalMediaServer.serve(context, uri, fileName)

    fun stopLocalMediaServer() = LocalMediaServer.stop()
}
