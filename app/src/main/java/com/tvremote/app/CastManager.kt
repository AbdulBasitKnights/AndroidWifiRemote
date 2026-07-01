package com.tvremote.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage

class CastManager(context: Context) {
    private val appContext = context.applicationContext
    private var castContext: CastContext? = null
    private var sessionListener: SessionManagerListener<CastSession>? = null

    var onSessionChanged: ((CastSession?) -> Unit)? = null

    fun initialize() {
        if (castContext != null) return
        try {
            castContext = CastContext.getSharedInstance(appContext)
            sessionListener = object : SessionManagerListener<CastSession> {
                override fun onSessionStarted(session: CastSession, sessionId: String) {
                    onSessionChanged?.invoke(session)
                }

                override fun onSessionEnded(session: CastSession, error: Int) {
                    onSessionChanged?.invoke(null)
                }

                override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                    onSessionChanged?.invoke(session)
                }

                override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
                override fun onSessionStarting(session: CastSession) = Unit
                override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
                override fun onSessionEnding(session: CastSession) = Unit
                override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
                override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
            }
            castContext?.sessionManager?.addSessionManagerListener(
                sessionListener!!,
                CastSession::class.java,
            )
        } catch (_: Exception) {
            castContext = null
        }
    }

    fun currentSession(): CastSession? = castContext?.sessionManager?.currentCastSession

    fun isConnected(): Boolean = currentSession()?.isConnected == true

    fun deviceName(): String? = currentSession()?.castDevice?.friendlyName

    fun castImage(uri: Uri, title: String = "Photo") {
        val session = currentSession() ?: return
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_PHOTO).apply {
            putString(MediaMetadata.KEY_TITLE, title)
            addImage(WebImage(uri))
        }
        val mediaInfo = MediaInfo.Builder(uri.toString())
            .setStreamType(MediaInfo.STREAM_TYPE_NONE)
            .setContentType("image/jpeg")
            .setMetadata(metadata)
            .build()
        session.remoteMediaClient?.load(MediaLoadRequestData.Builder().setMediaInfo(mediaInfo).build())
    }

    fun castVideo(uri: Uri, title: String = "Video") {
        val session = currentSession() ?: return
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
        }
        val mediaInfo = MediaInfo.Builder(uri.toString())
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("video/mp4")
            .setMetadata(metadata)
            .build()
        session.remoteMediaClient?.load(MediaLoadRequestData.Builder().setMediaInfo(mediaInfo).build())
    }

    fun castAudio(uri: Uri, title: String = "Audio") {
        val session = currentSession() ?: return
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, title)
        }
        val mediaInfo = MediaInfo.Builder(uri.toString())
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("audio/mp3")
            .setMetadata(metadata)
            .build()
        session.remoteMediaClient?.load(MediaLoadRequestData.Builder().setMediaInfo(mediaInfo).build())
    }

    fun castLiveStream(url: String, title: String = "Screen Mirror") {
        val session = currentSession() ?: return
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, title)
        }
        val mediaInfo = MediaInfo.Builder(url)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType("multipart/x-mixed-replace; boundary=--BoundaryString")
            .setMetadata(metadata)
            .build()
        session.remoteMediaClient?.load(MediaLoadRequestData.Builder().setMediaInfo(mediaInfo).build())
    }

    companion object {
        const val DEFAULT_RECEIVER = CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
    }
}
