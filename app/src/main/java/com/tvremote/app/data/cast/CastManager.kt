package com.tvremote.app.data.cast

import android.content.Context
import android.net.Uri
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
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

    fun castImage(uri: Uri, title: String = "Photo"): OperationResult = castMedia {
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_PHOTO).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            addImage(WebImage(uri))
        }
        MediaInfo.Builder(uri.toString())
            .setStreamType(MediaInfo.STREAM_TYPE_NONE)
            .setContentType("image/jpeg")
            .setMetadata(metadata)
            .build()
    }

    fun castVideo(uri: Uri, title: String = "Video"): OperationResult = castMedia {
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
        }
        MediaInfo.Builder(uri.toString())
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("video/mp4")
            .setMetadata(metadata)
            .build()
    }

    fun castAudio(uri: Uri, title: String = "Audio"): OperationResult = castMedia {
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, title)
        }
        MediaInfo.Builder(uri.toString())
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("audio/mp3")
            .setMetadata(metadata)
            .build()
    }

    fun castLiveStream(url: String, title: String = "Screen Mirror"): OperationResult = castMedia {
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
        }
        MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType("multipart/x-mixed-replace; boundary=--BoundaryString")
            .setMetadata(metadata)
            .build()
    }

    private fun castMedia(buildMedia: () -> MediaInfo): OperationResult {
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
                    client.load(MediaLoadRequestData.Builder().setMediaInfo(mediaInfo).build())
                    OperationResult.Success
                }
            }
        }
    }

    companion object {
        private const val TAG = "CastManager"
        const val DEFAULT_RECEIVER = CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
    }
}
