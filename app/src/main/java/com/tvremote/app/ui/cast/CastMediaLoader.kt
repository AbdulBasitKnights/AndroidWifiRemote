package com.tvremote.app.ui.cast

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.tvremote.app.util.SafeRun

object CastMediaLoader {

    private const val DEFAULT_LIMIT = 240

    fun loadPhotos(context: Context, limit: Int = DEFAULT_LIMIT): List<CastMediaItem> =
        SafeRun.runCatching("CastMediaLoader", emptyList()) {
            queryImages(context, limit)
        }

    fun loadVideos(context: Context, limit: Int = DEFAULT_LIMIT): List<CastMediaItem> =
        SafeRun.runCatching("CastMediaLoader", emptyList()) {
            queryVideos(context, limit)
        }

    private fun queryImages(context: Context, limit: Int): List<CastMediaItem> {
        val items = mutableListOf<CastMediaItem>()
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sort,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext() && items.size < limit) {
                val id = cursor.getLong(idCol)
                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString(),
                )
                items.add(CastMediaItem(uri = uri, isVideo = false))
            }
        }
        return items
    }

    private fun queryVideos(context: Context, limit: Int): List<CastMediaItem> {
        val items = mutableListOf<CastMediaItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
        )
        val sort = "${MediaStore.Video.Media.DATE_ADDED} DESC"
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sort,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (cursor.moveToNext() && items.size < limit) {
                val id = cursor.getLong(idCol)
                val duration = cursor.getLong(durationCol)
                val uri = Uri.withAppendedPath(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id.toString(),
                )
                items.add(CastMediaItem(uri = uri, durationMs = duration, isVideo = true))
            }
        }
        return items
    }
}
