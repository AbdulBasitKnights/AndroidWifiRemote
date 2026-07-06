package com.tvremote.app.data.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.tvremote.app.R
import com.tvremote.app.util.AppLogger
import com.tvremote.app.util.NetworkUtils
import com.tvremote.app.util.OperationResult
import com.tvremote.app.util.SafeRun
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures screen via MediaProjection, encodes 3s H.264 MP4 clips,
 * serves them over HTTP and queues them to the connected Cast device.
 */
class ScreenCastService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var httpServer: MirrorHttpServer? = null
    private val running = AtomicBoolean(false)
    private var castPlaybackStarted = false
    private var segmentCounter = 0
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var mirrorDir: File
    private var recordWidth = 0
    private var recordHeight = 0
    private var recordDensity = 0

    private val castManager by lazy {
        CastManager(applicationContext).also { it.initialize() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        SafeRun.run(TAG) {
            when (intent?.action) {
                ACTION_STOP -> {
                    stopMirror()
                    stopSelf()
                }
                ACTION_START -> {
                    val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                    val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(EXTRA_RESULT_DATA)
                    }
                    if (data == null) {
                        stopSelf()
                        return@run
                    }
                    try {
                        startForegroundCompat(getString(R.string.mirror_notification_title))
                        startMirror(resultCode, data)
                    } catch (e: Exception) {
                        AppLogger.e(TAG, "Failed to start mirror", e)
                        stopMirror()
                        sendStoppedBroadcast()
                        stopSelf()
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundCompat(title: String) {
        val notification = buildNotification(title)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startMirror(resultCode: Int, data: Intent) {
        if (running.get()) return
        running.set(true)
        castPlaybackStarted = false
        segmentCounter = 0

        mirrorDir = File(cacheDir, "mirror_segments").apply {
            deleteRecursively()
            mkdirs()
        }

        val metrics = resources.displayMetrics
        val scaled = scaleForCast(metrics.widthPixels, metrics.heightPixels)
        recordWidth = scaled.first
        recordHeight = scaled.second
        recordDensity = metrics.densityDpi.coerceAtLeast(1)

        val projectionManager = getSystemService(MediaProjectionManager::class.java)
            ?: throw IllegalStateException("MediaProjection unavailable")
        projection = projectionManager.getMediaProjection(resultCode, data)
            ?: throw IllegalStateException("MediaProjection permission invalid")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    handler.post { stopMirror(); stopSelf() }
                }
            }, handler)
        }

        httpServer = MirrorHttpServer(PORT, mirrorDir).also {
            try {
                it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            } catch (e: Exception) {
                AppLogger.e(TAG, "HTTP server start failed", e)
                throw e
            }
        }

        recordNextSegment()
    }

    private fun scaleForCast(width: Int, height: Int): Pair<Int, Int> {
        val maxWidth = 1280
        var w = width.coerceAtLeast(2)
        var h = height.coerceAtLeast(2)
        if (w > maxWidth) {
            h = (h * maxWidth / w.toFloat()).toInt().coerceAtLeast(2)
            w = maxWidth
        }
        if (w % 2 != 0) w--
        if (h % 2 != 0) h--
        return w to h
    }

    private fun recordNextSegment() {
        if (!running.get()) return

        val segmentName = "seg_$segmentCounter.mp4"
        val outputFile = File(mirrorDir, segmentName)
        if (outputFile.exists()) outputFile.delete()

        val recorder = createMediaRecorder(outputFile)
        mediaRecorder = recorder

        virtualDisplay?.release()
        virtualDisplay = projection?.createVirtualDisplay(
            "ScreenCast",
            recordWidth,
            recordHeight,
            recordDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            recorder.surface,
            null,
            handler,
        )

        try {
            recorder.start()
        } catch (e: Exception) {
            AppLogger.e(TAG, "MediaRecorder start failed", e)
            stopMirror()
            sendStoppedBroadcast()
            stopSelf()
            return
        }

        handler.postDelayed({ finishCurrentSegment(outputFile, recorder, segmentName) }, SEGMENT_DURATION_MS)
    }

    private fun finishCurrentSegment(file: File, recorder: MediaRecorder, segmentName: String) {
        if (!running.get()) {
            releaseRecorder(recorder)
            return
        }

        releaseRecorder(recorder)
        mediaRecorder = null

        if (file.exists() && file.length() > 1024) {
            publishSegmentToCast(segmentName)
            segmentCounter++
            trimOldSegments()
        } else {
            AppLogger.w(TAG, "Segment empty or too small: ${file.length()} bytes")
            file.delete()
        }

        recordNextSegment()
    }

    private fun publishSegmentToCast(segmentName: String) {
        val url = segmentUrl(segmentName)

        castManager.initialize()
        if (!castPlaybackStarted) {
            when (val result = castManager.startMirrorPlayback(url)) {
                is OperationResult.Failure -> {
                    AppLogger.e(TAG, "Cast mirror start failed: ${result.message}")
                    handler.post {
                        stopMirror()
                        sendStoppedBroadcast()
                        stopSelf()
                    }
                    return
                }
                is OperationResult.Success -> {
                    castPlaybackStarted = true
                    AppLogger.d(TAG, "Cast mirror started: $url")
                    sendMirrorBroadcast(ACTION_MIRROR_STARTED) {
                        putExtra(EXTRA_STREAM_URL, url)
                    }
                }
            }
        } else {
            castManager.queueMirrorSegment(url)
        }
    }

    private fun trimOldSegments() {
        val files = mirrorDir.listFiles()?.filter { it.extension == "mp4" }?.sortedBy { it.name } ?: return
        if (files.size <= MAX_STORED_SEGMENTS) return
        files.take(files.size - MAX_STORED_SEGMENTS).forEach { it.delete() }
    }

    private fun releaseRecorder(recorder: MediaRecorder) {
        try {
            recorder.stop()
        } catch (e: Exception) {
            AppLogger.w(TAG, "MediaRecorder stop failed", e)
        }
        try {
            recorder.release()
        } catch (e: Exception) {
            AppLogger.w(TAG, "MediaRecorder release failed", e)
        }
    }

    private fun createMediaRecorder(outputFile: File): MediaRecorder {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        recorder.setVideoSize(recordWidth, recordHeight)
        recorder.setVideoFrameRate(15)
        recorder.setVideoEncodingBitRate(2_000_000)
        recorder.setOutputFile(outputFile.absolutePath)
        recorder.prepare()
        return recorder
    }

    private fun segmentUrl(name: String): String {
        val ip = NetworkUtils.getWifiIpv4Address() ?: "127.0.0.1"
        return "http://$ip:$PORT/mirror/$name"
    }

    private fun stopMirror() {
        running.set(false)
        castPlaybackStarted = false
        handler.removeCallbacksAndMessages(null)
        SafeRun.run(TAG) {
            try {
                mediaRecorder?.let { releaseRecorder(it) }
            } catch (_: Exception) {
            }
            mediaRecorder = null
            httpServer?.stop()
            httpServer = null
            virtualDisplay?.release()
            virtualDisplay = null
            projection?.stop()
            projection = null
            if (::mirrorDir.isInitialized) {
                mirrorDir.deleteRecursively()
            }
        }
    }

    private fun sendStoppedBroadcast() {
        SafeRun.run(TAG) {
            sendMirrorBroadcast(ACTION_MIRROR_STOPPED)
        }
    }

    private fun sendMirrorBroadcast(action: String, configure: Intent.() -> Unit = {}) {
        sendBroadcast(
            Intent(action).apply {
                setPackage(packageName)
                configure()
            },
        )
    }

    private fun buildNotification(title: String): Notification {
        createChannel()
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainLauncherIntent(),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(getString(R.string.mirror_notification_text))
            .setSmallIcon(R.drawable.ic_screen_mirror)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.mirror_title),
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun mainLauncherIntent(): Intent {
        return packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent().setClassName(packageName, MAIN_ACTIVITY_CLASS)
    }

    override fun onDestroy() {
        stopMirror()
        sendStoppedBroadcast()
        super.onDestroy()
    }

    private class MirrorHttpServer(
        port: Int,
        private val rootDir: File,
    ) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            return SafeRun.runCatching(TAG, errorResponse()) {
                val uri = session.uri.orEmpty()
                if (uri.startsWith("/mirror/")) {
                    val name = uri.removePrefix("/mirror/").trim('/')
                    val file = File(rootDir, name)
                    if (name.isEmpty() || !file.exists() || !file.isFile) {
                        newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
                    } else {
                        serveFile(session, file)
                    }
                } else {
                    newFixedLengthResponse(Response.Status.OK, "text/plain", "ScreenCast active")
                }
            }
        }

        private fun serveFile(session: IHTTPSession, file: File): Response {
            val total = file.length()
            val range = session.headers["range"] ?: session.headers["Range"]
            if (range != null && range.startsWith("bytes=")) {
                val parts = range.removePrefix("bytes=").split("-")
                val start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                val end = parts.getOrNull(1)?.toLongOrNull() ?: (total - 1)
                val safeEnd = end.coerceAtMost(total - 1)
                val length = safeEnd - start + 1
                val stream = FileInputStream(file)
                stream.skip(start)
                return newFixedLengthResponse(
                    Response.Status.PARTIAL_CONTENT,
                    "video/mp4",
                    stream,
                    length,
                ).apply {
                    addHeader("Content-Range", "bytes $start-$safeEnd/$total")
                    addHeader("Accept-Ranges", "bytes")
                    addHeader("Cache-Control", "no-cache")
                    addHeader("Access-Control-Allow-Origin", "*")
                }
            }
            return newFixedLengthResponse(
                Response.Status.OK,
                "video/mp4",
                FileInputStream(file),
                total,
            ).apply {
                addHeader("Accept-Ranges", "bytes")
                addHeader("Cache-Control", "no-cache")
                addHeader("Access-Control-Allow-Origin", "*")
            }
        }

        private fun errorResponse(): Response =
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Mirror error")

        companion object {
            private const val TAG = "MirrorHttpServer"
        }
    }

    companion object {
        private const val TAG = "ScreenCastService"
        const val ACTION_START = "com.tvremote.app.action.START_MIRROR"
        const val ACTION_STOP = "com.tvremote.app.action.STOP_MIRROR"
        const val ACTION_MIRROR_STARTED = "com.tvremote.app.action.MIRROR_STARTED"
        const val ACTION_MIRROR_STOPPED = "com.tvremote.app.action.MIRROR_STOPPED"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_STREAM_URL = "stream_url"
        const val PORT = 8080
        private const val CHANNEL_ID = "screen_cast"
        private const val NOTIFICATION_ID = 1001
        private const val SEGMENT_DURATION_MS = 3_000L
        private const val MAX_STORED_SEGMENTS = 6
        private const val MAIN_ACTIVITY_CLASS = "com.tvremote.app.ui.main.MainActivity"

        fun streamUrl(): String {
            val ip = NetworkUtils.getWifiIpv4Address() ?: "127.0.0.1"
            return "http://$ip:$PORT/mirror/seg_0.mp4"
        }

        fun start(context: Context, resultCode: Int, data: Intent) {
            SafeRun.run(TAG) {
                val intent = Intent(context, ScreenCastService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_RESULT_CODE, resultCode)
                    putExtra(EXTRA_RESULT_DATA, data)
                }
                context.startForegroundService(intent)
            }
        }

        fun stop(context: Context) {
            SafeRun.run(TAG) {
                context.startService(
                    Intent(context, ScreenCastService::class.java).apply { action = ACTION_STOP },
                )
            }
        }
    }
}
