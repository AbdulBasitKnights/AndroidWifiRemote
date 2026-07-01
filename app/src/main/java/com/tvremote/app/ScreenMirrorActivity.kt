package com.tvremote.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import com.tvremote.app.databinding.ActivityScreenMirrorBinding

class ScreenMirrorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScreenMirrorBinding
    private lateinit var container: AppContainer
    private var mirroring = false

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) {
            Toast.makeText(this, R.string.mirror_permission_denied, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        ScreenCastService.start(this, result.resultCode, result.data!!)
    }

    private val mirrorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ScreenCastService.ACTION_MIRROR_STARTED -> {
                    mirroring = true
                    val url = intent.getStringExtra(ScreenCastService.EXTRA_STREAM_URL).orEmpty()
                    container.castManager.castLiveStream(url, getString(R.string.mirror_title))
                    updateUi()
                    Toast.makeText(
                        this@ScreenMirrorActivity,
                        getString(R.string.mirror_active, container.castManager.deviceName() ?: "TV"),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                ScreenCastService.ACTION_MIRROR_STOPPED -> {
                    mirroring = false
                    updateUi()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScreenMirrorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        container = (application as TvRemoteApp).container
        container.castManager.initialize()

        binding.backButton.setOnClickListener { finish() }
        binding.mirrorRouteButton.routeSelector = MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO)
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
            .build()

        container.castManager.onSessionChanged = { session ->
            runOnUiThread {
                binding.selectedDeviceLabel.text = session?.castDevice?.friendlyName
                    ?: getString(R.string.cast_select_device)
            }
        }
        binding.selectedDeviceLabel.text = container.castManager.deviceName()
            ?: getString(R.string.cast_select_device)

        binding.startMirrorButton.setOnClickListener { startMirrorFlow() }
        binding.stopMirrorButton.setOnClickListener {
            ScreenCastService.stop(this)
            mirroring = false
            updateUi()
        }

        updateUi()
    }

    private fun startMirrorFlow() {
        if (!container.castManager.isConnected()) {
            Toast.makeText(this, R.string.cast_select_device, Toast.LENGTH_SHORT).show()
            binding.mirrorRouteButton.performClick()
            return
        }
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun updateUi() {
        binding.startMirrorButton.isVisible = !mirroring
        binding.stopMirrorButton.isVisible = mirroring
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(ScreenCastService.ACTION_MIRROR_STARTED)
            addAction(ScreenCastService.ACTION_MIRROR_STOPPED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mirrorReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(mirrorReceiver, filter)
        }
    }

    override fun onStop() {
        unregisterReceiver(mirrorReceiver)
        super.onStop()
    }
}
