package com.tvremote.app.util

import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

object EdgeToEdgeHelper {

    fun configureSystemBars(activity: AppCompatActivity) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val lightIcons = !ThemeHelper.isDarkMode(activity)
        controller.isAppearanceLightStatusBars = lightIcons
        controller.isAppearanceLightNavigationBars = lightIcons
    }

    fun applyContentInsets(root: View) {
        val initialPadding = Insets(
            left = root.paddingLeft,
            top = root.paddingTop,
            right = root.paddingRight,
            bottom = root.paddingBottom,
        )
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialPadding.left + bars.left,
                initialPadding.top + bars.top,
                initialPadding.right + bars.right,
                initialPadding.bottom + bars.bottom,
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(root)
    }

    fun applyInsetsToContentRoot(activity: AppCompatActivity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val root = content.getChildAt(0) ?: return
        applyContentInsets(root)
    }

    private data class Insets(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    )
}
