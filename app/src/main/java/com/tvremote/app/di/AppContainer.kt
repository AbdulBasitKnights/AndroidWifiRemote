package com.tvremote.app.di

import android.content.Context
import com.tvremote.app.data.cast.CastManager
import com.tvremote.app.data.discovery.TvDiscovery
import com.tvremote.app.data.remote.RemoteTvManager
import com.tvremote.app.data.repository.CastRepository
import com.tvremote.app.data.repository.TvRemoteRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val remoteManager = RemoteTvManager(appContext)
    val tvDiscovery = TvDiscovery(appContext)
    val castManager = CastManager(appContext)

    val tvRemoteRepository = TvRemoteRepository(remoteManager, tvDiscovery)
    val castRepository = CastRepository(castManager, appContext)
}
