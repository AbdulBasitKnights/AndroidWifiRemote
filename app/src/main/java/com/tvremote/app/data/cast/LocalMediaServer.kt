package com.tvremote.app.data.cast

import android.content.Context
import android.net.Uri
import com.tvremote.app.util.AppLogger
import com.tvremote.app.util.NetworkUtils
import com.tvremote.app.util.SafeRun
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object LocalMediaServer {
    private var server: StaticServer? = null
    private const val PORT = 8081
    private const val TAG = "LocalMediaServer"

    fun serve(context: Context, uri: Uri, fileName: String): String {
        return SafeRun.runCatching(TAG, "http://127.0.0.1:$PORT/media") {
            val cacheDir = File(context.cacheDir, "cast").apply { mkdirs() }
            val outFile = File(cacheDir, fileName)
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot read selected media")
            input.use { stream ->
                FileOutputStream(outFile).use { output -> stream.copyTo(output) }
            }
            stop()
            server = StaticServer(PORT, outFile).also {
                try {
                    it.start()
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to start media server", e)
                    throw e
                }
            }
            val ip = NetworkUtils.getWifiIpv4Address() ?: "127.0.0.1"
            "http://$ip:$PORT/media"
        }
    }

    fun stop() {
        SafeRun.run(TAG) {
            server?.stop()
            server = null
        }
    }

    private class StaticServer(port: Int, private val file: File) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            return SafeRun.runCatching(TAG, internalErrorResponse()) {
                if (!file.exists()) {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
                } else {
                    val mime = when {
                        file.name.endsWith(".mp4", true) -> "video/mp4"
                        file.name.endsWith(".mp3", true) -> "audio/mpeg"
                        else -> "image/jpeg"
                    }
                    newFixedLengthResponse(
                        Response.Status.OK,
                        mime,
                        FileInputStream(file),
                        file.length(),
                    )
                }
            }
        }

        private fun internalErrorResponse(): Response =
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Server error")
    }
}
