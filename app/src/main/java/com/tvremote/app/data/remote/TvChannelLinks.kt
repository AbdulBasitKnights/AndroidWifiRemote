package com.tvremote.app.data.remote

/**
 * Android TV app launch URLs for the remote v2 deep-link command.
 * android-app:// scheme opens the installed TV app directly.
 */
object TvChannelLinks {
    const val YOUTUBE = "android-app://com.google.android.youtube.tv"
    const val NETFLIX = "android-app://com.netflix.ninja"
    const val PRIME = "android-app://com.amazon.amazonvideo.livingroom"
    const val DISNEY = "android-app://com.disney.disneyplus"
    const val APPLE_TV = "android-app://com.apple.atve.androidtv.appletv"
    const val HOTSTAR = "android-app://in.startv.hotstar"
}
