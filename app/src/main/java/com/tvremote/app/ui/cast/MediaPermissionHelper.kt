package com.tvremote.app.ui.cast

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object MediaPermissionHelper {

    enum class MediaKind { PHOTO, VIDEO }

    fun requiredPermission(kind: MediaKind): String = when (kind) {
        MediaKind.PHOTO -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        MediaKind.VIDEO -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    fun hasPermission(context: Context, kind: MediaKind): Boolean =
        ContextCompat.checkSelfPermission(context, requiredPermission(kind)) ==
            PackageManager.PERMISSION_GRANTED
}
