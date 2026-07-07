package com.tvremote.app.data.remote

/**
 * Android TV app launch links for RemoteAppLinkLaunchRequest (protocol v2).
 *
 * Installed apps are opened with `market://launch?id=<package>` — same format used by
 * Google TV / Home Assistant androidtvremote2. Plain `android-app://` does not work on
 * most devices.
 */
object TvChannelLinks {

    const val PKG_YOUTUBE = "com.google.android.youtube.tv"
    const val PKG_NETFLIX = "com.netflix.ninja"
    const val PKG_PRIME = "com.amazon.amazonvideo.livingroom"
    const val PKG_DISNEY = "com.disney.disneyplus"
    const val PKG_APPLE_TV = "com.apple.atve.androidtv.appletv"
    const val PKG_HOTSTAR_IN = "in.startv.hotstar"
    const val PKG_HOTSTAR_GLOBAL = "com.disney.hotstar"

    val YOUTUBE: String = marketLaunch(PKG_YOUTUBE)
    val NETFLIX: String = marketLaunch(PKG_NETFLIX)
    val PRIME: String = marketLaunch(PKG_PRIME)
    val DISNEY: String = marketLaunch(PKG_DISNEY)
    val APPLE_TV: String = marketLaunch(PKG_APPLE_TV)
    val HOTSTAR: String = marketLaunch(PKG_HOTSTAR_IN)

    /** Alternate package / URL if primary market link fails on some TVs. */
    fun fallbacksFor(primaryLink: String): List<String> {
        return when (primaryLink) {
            YOUTUBE -> listOf(
                marketLaunch(PKG_YOUTUBE),
                "https://www.youtube.com",
                "vnd.youtube.launch://",
            )
            NETFLIX -> listOf(
                marketLaunch(PKG_NETFLIX),
                "https://www.netflix.com/browse",
                "netflix://",
            )
            PRIME -> listOf(
                marketLaunch(PKG_PRIME),
                "https://app.primevideo.com",
            )
            DISNEY -> listOf(
                marketLaunch(PKG_DISNEY),
                "https://www.disneyplus.com",
            )
            APPLE_TV -> listOf(
                marketLaunch(PKG_APPLE_TV),
                marketLaunch("com.apple.tv"),
            )
            HOTSTAR -> listOf(
                marketLaunch(PKG_HOTSTAR_IN),
                marketLaunch(PKG_HOTSTAR_GLOBAL),
                "https://www.hotstar.com",
            )
            else -> listOf(primaryLink)
        }.distinct()
    }

    fun marketLaunch(packageId: String): String = "market://launch?id=$packageId"

    fun normalize(linkOrPackage: String): String {
        val trimmed = linkOrPackage.trim()
        if (trimmed.contains("://")) return trimmed
        return marketLaunch(trimmed)
    }
}
