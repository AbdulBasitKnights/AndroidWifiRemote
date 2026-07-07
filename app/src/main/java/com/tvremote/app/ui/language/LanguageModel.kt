package com.tvremote.app.ui.language

import android.graphics.drawable.Drawable

data class LanguageModel(
    val id: Int,
    val lang: String,
    var name: String,
    val icon: Drawable? = null,
    var isSelected: Boolean = false,
)
