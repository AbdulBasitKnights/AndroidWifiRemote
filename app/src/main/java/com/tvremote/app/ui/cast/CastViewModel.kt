package com.tvremote.app.ui.cast

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.tvremote.app.data.repository.CastRepository

class CastViewModel(
    private val castRepository: CastRepository,
) : ViewModel() {

    fun initialize() = castRepository.initialize()

    fun isConnected(): Boolean = castRepository.isConnected()

    fun deviceName(): String? = castRepository.deviceName()

    fun castMedia(uri: Uri, isVideo: Boolean, isAudio: Boolean = false) {
        val ext = when {
            isAudio -> "mp3"
            isVideo -> "mp4"
            else -> "jpg"
        }
        val url = castRepository.serveLocalMedia(uri, "cast_media.$ext")
        val parsed = Uri.parse(url)
        when {
            isAudio -> castRepository.castAudio(parsed)
            isVideo -> castRepository.castVideo(parsed)
            else -> castRepository.castImage(parsed)
        }
    }

    fun castPhoto(uri: Uri, title: String) {
        val url = castRepository.serveLocalMedia(uri, "cast_photo.jpg")
        castRepository.castImage(Uri.parse(url), title)
    }

    fun castLiveStream(url: String, title: String) = castRepository.castLiveStream(url, title)

    fun stopLocalServer() = castRepository.stopLocalMediaServer()
}
