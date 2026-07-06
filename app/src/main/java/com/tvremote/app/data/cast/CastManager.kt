package com.tvremote.app.data.cast

import android.content.Context
import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import com.tvremote.app.util.AppLogger
import com.tvremote.app.util.OperationResult
import com.tvremote.app.util.SafeRun

class CastManager(context: Context) {
    private val appContext = context.applicationContext
    private var castContext: CastContext? = null
    private var sessionListener: SessionManagerListener<CastSession>? = null

    var onSessionChanged: ((CastSession?) -> Unit)? = null

    fun initialize() {
        if (castContext != null) return
        SafeRun.run(TAG) {
            castContext = CastContext.getSharedInstance(appContext)
            sessionListener = object : SessionManagerListener<CastSession> {
                override fun onSessionStarted(session: CastSession, sessionId: String) {
                    notifySessionChanged(session)
                }

                override fun onSessionEnded(session: CastSession, error: Int) {
                    notifySessionChanged(null)
                }

                override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                    notifySessionChanged(session)
                }

                override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
                override fun onSessionStarting(session: CastSession) = Unit
                override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
                override fun onSessionEnding(session: CastSession) = Unit
                override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
                override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
            }
            val listener = sessionListener ?: return@run
            castContext?.sessionManager?.addSessionManagerListener(listener, CastSession::class.java)
        }
    }

    private fun notifySessionChanged(session: CastSession?) {
        SafeRun.invoke(TAG, onSessionChanged, session)
    }

    fun currentSession(): CastSession? = SafeRun.runCatching(TAG, null) {
        castContext?.sessionManager?.currentCastSession
    }

    fun isConnected(): Boolean = SafeRun.runCatching(TAG, false) {
        currentSession()?.isConnected == true
    }

    fun deviceName(): String? = SafeRun.runCatching(TAG, null) {
        currentSession()?.castDevice?.friendlyName
    }

    fun castImage(uri: Uri, title: String = "Photo", contentType: String = "image/jpeg"): OperationResult =
        castMedia(contentType, MediaInfo.STREAM_TYPE_NONE) {
            val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_PHOTO).apply {
                putString(MediaMetadata.KEY_TITLE, title)
                addImage(WebImage(uri))
            }
            MediaInfo.Builder(uri.toString())
                .setStreamType(MediaInfo.STREAM_TYPE_NONE)
                .setContentType(contentType)
                .setMetadata(metadata)
                .build()
        }

    fun castVideo(uri: Uri, title: String = "Video", contentType: String = "video/mp4"): OperationResult =
        castMedia(contentType, MediaInfo.STREAM_TYPE_BUFFERED) {
            val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                putString(MediaMetadata.KEY_TITLE, title)
            }
            MediaInfo.Builder(uri.toString())
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(contentType)
                .setMetadata(metadata)
                .build()
        }

    fun castAudio(uri: Uri, title: String = "Audio", contentType: String = "audio/mpeg"): OperationResult =
        castMedia(contentType, MediaInfo.STREAM_TYPE_BUFFERED) {
            val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                putString(MediaMetadata.KEY_TITLE, title)
            }
            MediaInfo.Builder(uri.toString())
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(contentType)
                .setMetadata(metadata)
                .build()
        }

    fun startMirrorPlayback(url: String, title: String = "Screen Mirror"): OperationResult = castMedia(
        "video/mp4",
        MediaInfo.STREAM_TYPE_BUFFERED,
    ) {
        buildMirrorMediaInfo(url, title)
    }

    fun queueMirrorSegment(url: String, title: String = "Screen Mirror"): OperationResult {
        return SafeRun.runCatching(TAG, OperationResult.Failure("Cast queue failed")) {
            val session = currentSession()
                ?: return@runCatching OperationResult.Failure("No Cast device connected")
            val client = session.remoteMediaClient
                ?: return@runCatching OperationResult.Failure("Cast media client unavailable")
            val item = MediaQueueItem.Builder(buildMirrorMediaInfo(url, title))
                .setAutoplay(true)
                .build()
            client.queueInsertItems(arrayOf(item), 0, null)
            OperationResult.Success
        }
    }

    private fun buildMirrorMediaInfo(url: String, title: String): MediaInfo {
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
        }
        return MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("video/mp4")
            .setMetadata(metadata)
            .build()
    }

    @Deprecated("Use startMirrorPlayback for screen mirror")
    fun castLiveStream(url: String, title: String = "Screen Mirror"): OperationResult =
        startMirrorPlayback(url, title)

    private fun castMedia(
        contentType: String,
        streamTypeHint: Int,
        buildMedia: () -> MediaInfo,
    ): OperationResult {
        return SafeRun.runCatching(TAG, OperationResult.Failure("Cast failed")) {
            val session = currentSession()
            if (session == null) {
                OperationResult.Failure("No Cast device connected")
            } else {
                val client = session.remoteMediaClient
                if (client == null) {
                    OperationResult.Failure("Cast media client unavailable")
                } else {
                    val mediaInfo = buildMedia()
                    AppLogger.d(TAG, "Loading media: ${mediaInfo.contentId} ($contentType)")
                    val pending = client.load(
                        MediaLoadRequestData.Builder()
                            .setMediaInfo(mediaInfo)
                            .setAutoplay(true)
                            .build(),
                    )
                    pending.setResultCallback { result ->
                        if (result.status.isSuccess) {
                            AppLogger.d(TAG, "Cast load success: ${mediaInfo.contentId}")
                        } else {
                            AppLogger.e(
                                TAG,
                                "Cast load failed: ${result.status.statusMessage} code=${result.status.statusCode}",
                            )
                        }
                    }
                    OperationResult.Success
                }
            }
        }
    }

    companion object {
        private const val TAG = "CastManager"
        const val DEFAULT_RECEIVER = com.google.android.gms.cast.CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
    }
}
