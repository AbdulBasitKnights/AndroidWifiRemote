package com.tvremote.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ScreenCastService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var httpServer: MjpegServer? = null
    private val running = AtomicBoolean(false)
    private val latestFrame = AtomicReference<ByteArray?>(null)
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMirror()
                stopSelf()
            }
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                if (data == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.mirror_notification_title)))
                startMirror(resultCode, data)
            }
        }
        return START_STICKY
    }

    private fun startMirror(resultCode: Int, data: Intent) {
        if (running.get()) return
        running.set(true)

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        projection = projectionManager.getMediaProjection(resultCode, data)
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection?.createVirtualDisplay(
            "ScreenCast",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler,
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride,
                    height,
                    Bitmap.Config.ARGB_8888,
                )
                bitmap.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                bitmap.recycle()
                val stream = ByteArrayOutputStream()
                cropped.compress(Bitmap.CompressFormat.JPEG, 60, stream)
                cropped.recycle()
                latestFrame.set(stream.toByteArray())
            } finally {
                image.close()
            }
        }, handler)

        httpServer = MjpegServer(PORT, latestFrame).also { it.start() }
        sendBroadcast(Intent(ACTION_MIRROR_STARTED).putExtra(EXTRA_STREAM_URL, streamUrl()))
    }

    private fun stopMirror() {
        running.set(false)
        httpServer?.stop()
        httpServer = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projection?.stop()
        projection = null
        sendBroadcast(Intent(ACTION_MIRROR_STOPPED))
    }

    private fun buildNotification(title: String): Notification {
        createChannel()
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
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
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopMirror()
        super.onDestroy()
    }

    private class MjpegServer(
        port: Int,
        private val frameRef: AtomicReference<ByteArray?>,
    ) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            if (session.uri == "/stream.mjpg") {
                val output = PipedOutputStream()
                val input = PipedInputStream(output, 256 * 1024)
                Thread {
                    try {
                        while (!Thread.interrupted()) {
                            val frame = frameRef.get() ?: continue
                            val header = (
                                "--BoundaryString\r\n" +
                                    "Content-Type: image/jpeg\r\n" +
                                    "Content-Length: ${frame.size}\r\n\r\n"
                                ).toByteArray()
                            output.write(header)
                            output.write(frame)
                            output.write("\r\n".toByteArray())
                            output.flush()
                            Thread.sleep(100)
                        }
                    } catch (_: Exception) {
                    } finally {
                        try {
                            output.close()
                        } catch (_: Exception) {
                        }
                    }
                }.start()
                return newChunkedResponse(
                    Response.Status.OK,
                    "multipart/x-mixed-replace; boundary=--BoundaryString",
                    input,
                )
            }
            return newFixedLengthResponse(Response.Status.OK, "text/plain", "ScreenCast active")
        }
    }

    companion object {
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

        fun streamUrl(): String = "http://${NetworkUtils.getWifiIpv4Address() ?: "127.0.0.1"}:$PORT/stream.mjpg"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCastService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ScreenCastService::class.java).apply { action = ACTION_STOP },
            )
        }
    }
}
