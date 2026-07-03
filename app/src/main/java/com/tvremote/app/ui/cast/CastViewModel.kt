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
        val ext = uri.lastPathSegment?.substringAfterLast('.', "")
            ?.takeIf { it.isNotBlank() }
            ?: when {
                isAudio -> "mp3"
                isVideo -> "mp4"
                else -> "jpg"
            }
        val served = castRepository.serveLocalMedia(uri, "cast_media.$ext")
        if (served.url.startsWith("http://127.0.0.1")) {
            return OperationResult.Failure("Could not prepare media for casting")
        }
        val parsed = Uri.parse(served.url)
        return when {
            isAudio -> castRepository.castAudio(parsed, contentType = served.contentType)
            isVideo -> castRepository.castVideo(parsed, contentType = served.contentType)
            else -> castRepository.castImage(parsed, contentType = served.contentType)
        }
    }

    fun castPhoto(uri: Uri, title: String): OperationResult {
        val ext = uri.lastPathSegment?.substringAfterLast('.', "jpg") ?: "jpg"
        val served = castRepository.serveLocalMedia(uri, "cast_photo.$ext")
        if (served.url.startsWith("http://127.0.0.1")) {
            return OperationResult.Failure("Could not prepare photo for casting")
        }
        return castRepository.castImage(
            Uri.parse(served.url),
            title,
            contentType = served.contentType,
        )
    }

    fun castLiveStream(url: String, title: String): OperationResult =
        castRepository.castLiveStream(url, title)

    fun stopLocalServer() = castRepository.stopLocalMediaServer()
}
