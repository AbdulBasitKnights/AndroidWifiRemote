package com.tvremote.app.data.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tvremote.app.TvRemoteApp
import com.tvremote.app.util.SafeRun

class RemoteDisconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != RemoteConnectionService.ACTION_DISCONNECT) return
        SafeRun.run(TAG) {
            (context.applicationContext as? TvRemoteApp)
                ?.container
                ?.tvRemoteRepository
                ?.disconnectUser()
            RemoteConnectionService.stop(context)
        }
    }

    companion object {
        private const val TAG = "RemoteDisconnectReceiver"
    }
}
