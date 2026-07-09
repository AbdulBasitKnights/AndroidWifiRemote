package com.tvremote.app.ui.cast

import android.net.Uri

data class CastMediaItem(
    val uri: Uri,
    val durationMs: Long = 0L,
    val isVideo: Boolean = false,
)
