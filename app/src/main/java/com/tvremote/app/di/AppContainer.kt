package com.tvremote.app.di

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.tvremote.app.data.cast.CastManager
import com.tvremote.app.data.cast.ScreenCastService
import com.tvremote.app.data.discovery.TvDiscovery
import com.tvremote.app.data.remote.RemoteTvManager
import com.tvremote.app.data.repository.CastRepository
import com.tvremote.app.data.repository.TvRemoteRepository
import com.tvremote.app.data.session.AppPreferences
import com.tvremote.app.data.session.ConnectionCoordinator
import com.tvremote.app.data.session.PairingStore
import com.tvremote.app.util.SafeRun
import com.tvremote.app.util.VoiceInputHelper

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appPreferences = AppPreferences(appContext)
    private val pairingStore = PairingStore(appContext)
    val remoteManager = RemoteTvManager(appContext)
    val tvDiscovery = TvDiscovery(appContext)
    val castManager = CastManager(appContext)
    val voiceInputHelper = VoiceInputHelper(appContext, remoteManager)

    val connectionCoordinator = ConnectionCoordinator(remoteManager, pairingStore)

    val tvRemoteRepository = TvRemoteRepository(remoteManager, tvDiscovery, connectionCoordinator)
    val castRepository = CastRepository(castManager, appContext, connectionCoordinator)

    private val mirrorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            SafeRun.run(TAG) {
                when (intent?.action) {
                    ScreenCastService.ACTION_MIRROR_STARTED -> connectionCoordinator.onScreenMirrorStarted()
                    ScreenCastService.ACTION_MIRROR_STOPPED -> connectionCoordinator.onScreenMirrorStopped()
                }
            }
        }
    }

    init {
        SafeRun.run(TAG) {
            castManager.onSessionChanged = { session ->
                SafeRun.run(TAG) {
                    if (session != null) {
                        connectionCoordinator.onCastSessionStarted()
                    } else {
                        connectionCoordinator.onCastSessionEnded()
                    }
                }
            }

            val filter = IntentFilter().apply {
                addAction(ScreenCastService.ACTION_MIRROR_STARTED)
                addAction(ScreenCastService.ACTION_MIRROR_STOPPED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(mirrorReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                appContext.registerReceiver(mirrorReceiver, filter)
            }
        }
    }

    companion object {
        private const val TAG = "AppContainer"
    }
}
