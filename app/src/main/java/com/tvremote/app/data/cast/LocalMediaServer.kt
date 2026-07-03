package com.tvremote.app.data.cast

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.tvremote.app.util.AppLogger
import com.tvremote.app.util.NetworkUtils
import com.tvremote.app.util.SafeRun
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

object LocalMediaServer {
    private var server: StaticServer? = null
    private const val PORT = 8081
    private const val TAG = "LocalMediaServer"
    private val fileMap = ConcurrentHashMap<String, File>()

    data class ServedMedia(val url: String, val contentType: String)

    fun serve(context: Context, uri: Uri, fileName: String): ServedMedia {
        return SafeRun.runCatching(TAG, ServedMedia("http://127.0.0.1:$PORT/media", "image/jpeg")) {
            val token = "${System.currentTimeMillis()}_${uri.hashCode()}"
            val ext = fileName.substringAfterLast('.', "jpg")
            val safeName = "$token.$ext"
            val cacheDir = File(context.cacheDir, "cast").apply { mkdirs() }
            val outFile = File(cacheDir, safeName)
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot read selected media")
            input.use { stream ->
                FileOutputStream(outFile).use { output -> stream.copyTo(output) }
            }
            val mime = mimeForFile(outFile, ext)
            fileMap[token] = outFile
            ensureServerRunning()
            val ip = NetworkUtils.getWifiIpv4Address() ?: "127.0.0.1"
            ServedMedia("http://$ip:$PORT/media/$token", mime)
        }
    }

    private fun ensureServerRunning() {
        if (server == null) {
            server = StaticServer(PORT, fileMap).also {
                try {
                    it.start()
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to start media server", e)
                    throw e
                }
            }
        }
    }

    private fun mimeForFile(file: File, ext: String): String {
        val fromExt = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
        if (!fromExt.isNullOrBlank()) return fromExt
        return when {
            file.name.endsWith(".png", true) -> "image/png"
            file.name.endsWith(".webp", true) -> "image/webp"
            file.name.endsWith(".gif", true) -> "image/gif"
            file.name.endsWith(".mp4", true) -> "video/mp4"
            file.name.endsWith(".webm", true) -> "video/webm"
            file.name.endsWith(".mkv", true) -> "video/x-matroska"
            file.name.endsWith(".mp3", true) -> "audio/mpeg"
            file.name.endsWith(".wav", true) -> "audio/wav"
            file.name.endsWith(".m4a", true) -> "audio/mp4"
            else -> "image/jpeg"
        }
    }

    fun stop() {
        SafeRun.run(TAG) {
            server?.stop()
            server = null
            fileMap.clear()
        }
    }

    private class StaticServer(
        port: Int,
        private val files: ConcurrentHashMap<String, File>,
    ) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            return SafeRun.runCatching(TAG, errorResponse()) {
                val token = session.uri.removePrefix("/media/").trim('/')
                val file = files[token]
                if (token.isEmpty() || file == null || !file.exists()) {
                    newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
                } else {
                    val ext = file.extension
                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
                        ?: "application/octet-stream"
                    newFixedLengthResponse(
                        Response.Status.OK,
                        mime,
                        FileInputStream(file),
                        file.length(),
                    ).apply {
                        addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                        addHeader("Pragma", "no-cache")
                    }
                }
            }
        }

        private fun errorResponse(): Response =
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Server error")
    }
}
