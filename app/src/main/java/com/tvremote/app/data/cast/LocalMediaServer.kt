package com.tvremote.app.data.cast

import android.content.Context
import android.net.Uri
import com.tvremote.app.util.NetworkUtils
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object LocalMediaServer {
    private var server: StaticServer? = null
    private const val PORT = 8081

    fun serve(context: Context, uri: Uri, fileName: String): String {
        val cacheDir = File(context.cacheDir, "cast").apply { mkdirs() }
        val outFile = File(cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }
        stop()
        server = StaticServer(PORT, outFile).also { it.start() }
        val ip = NetworkUtils.getWifiIpv4Address() ?: "127.0.0.1"
        return "http://$ip:$PORT/media"
    }

    fun stop() {
        server?.stop()
        server = null
    }

    private class StaticServer(port: Int, private val file: File) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            if (!file.exists()) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
            }
            val mime = when {
                file.name.endsWith(".mp4", true) -> "video/mp4"
                file.name.endsWith(".mp3", true) -> "audio/mpeg"
                else -> "image/jpeg"
            }
            return newFixedLengthResponse(
                Response.Status.OK,
                mime,
                FileInputStream(file),
                file.length(),
            )
        }
    }
}
