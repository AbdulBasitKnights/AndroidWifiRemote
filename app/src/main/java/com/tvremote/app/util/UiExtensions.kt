package com.tvremote.app.util

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tvremote.app.R

fun AppCompatActivity.applyLightSystemBars() {
    val color = ContextCompat.getColor(this, R.color.bgColor)
    window.statusBarColor = color
    window.navigationBarColor = color
    WindowInsetsControllerCompat(window, window.decorView).apply {
        isAppearanceLightStatusBars = true
        isAppearanceLightNavigationBars = true
    }
}

fun Activity.hideNavigationBar() {
    WindowInsetsControllerCompat(window, window.decorView).apply {
        hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

fun View.show() {
    visibility = View.VISIBLE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}

fun Activity.openPrivacyPolicy() {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_policy_url))))
}

fun Activity.openTermsOfUse() {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.terms_of_service_url))))
}

fun AppCompatActivity.changeStatusBarColor(@ColorRes colorRes: Int, darkIcons: Boolean = false) {
    val color = ContextCompat.getColor(this, colorRes)
    window.statusBarColor = color
    window.navigationBarColor = color
    WindowInsetsControllerCompat(window, window.decorView).apply {
        isAppearanceLightStatusBars = darkIcons
        isAppearanceLightNavigationBars = darkIcons
    }
}
