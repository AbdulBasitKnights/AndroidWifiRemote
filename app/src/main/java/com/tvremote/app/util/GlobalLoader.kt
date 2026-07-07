package com.tvremote.app.util

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.tvremote.app.R

object GlobalLoader {
    var isLoaderShowing = false

    fun show(activity: Activity, message: String? = null) {
        isLoaderShowing = true
        val loader = activity.findViewById<View>(R.id.globalLoader) ?: return
        loader.visibility = View.VISIBLE
        message?.let { findLoaderTextView(loader as? ViewGroup)?.text = it }
    }

    fun hide(activity: Activity) {
        isLoaderShowing = false
        activity.findViewById<View>(R.id.globalLoader)?.visibility = View.GONE
        activity.findViewById<ImageView>(R.id.progressImage)?.clearAnimation()
    }

    private fun findLoaderTextView(root: ViewGroup?): TextView? {
        if (root == null) return null
        for (index in 0 until root.childCount) {
            when (val child = root.getChildAt(index)) {
                is TextView -> return child
                is ViewGroup -> findLoaderTextView(child)?.let { return it }
            }
        }
        return null
    }
}
