package com.tvremote.app.ui.cast

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.tvremote.app.data.repository.CastRepository
import com.tvremote.app.util.OperationResult

class CastViewModel(
    private val castRepository: CastRepository,
) : ViewModel() {

    fun initialize() = castRepository.initialize()

    fun isConnected(): Boolean = castRepository.isConnected()

    fun deviceName(): String? = castRepository.deviceName()

    fun castMedia(uri: Uri, isVideo: Boolean, isAudio: Boolean = false): OperationResult {
        val ext = when {
            isAudio -> "mp3"
            isVideo -> "mp4"
            else -> "jpg"
        }
        val url = castRepository.serveLocalMedia(uri, "cast_media.$ext")
        if (url.startsWith("http://127.0.0.1")) {
            return OperationResult.Failure("Could not prepare media for casting")
        }
        val parsed = Uri.parse(url)
        return when {
            isAudio -> castRepository.castAudio(parsed)
            isVideo -> castRepository.castVideo(parsed)
            else -> castRepository.castImage(parsed)
        }
    }

    fun castPhoto(uri: Uri, title: String): OperationResult {
        val url = castRepository.serveLocalMedia(uri, "cast_photo.jpg")
        if (url.startsWith("http://127.0.0.1")) {
            return OperationResult.Failure("Could not prepare photo for casting")
        }
        return castRepository.castImage(Uri.parse(url), title)
    }

    fun castLiveStream(url: String, title: String): OperationResult =
        castRepository.castLiveStream(url, title)

    fun stopLocalServer() = castRepository.stopLocalMediaServer()
}
