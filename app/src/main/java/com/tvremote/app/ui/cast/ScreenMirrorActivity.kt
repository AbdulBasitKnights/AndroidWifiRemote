package com.tvremote.app.ui.cast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import com.tvremote.app.R
import com.tvremote.app.TvRemoteApp
import com.tvremote.app.data.cast.ScreenCastService
import com.tvremote.app.util.OperationResult
import com.tvremote.app.util.SafeRun
import com.tvremote.app.databinding.ActivityScreenMirrorBinding
import com.tvremote.app.ui.common.AppViewModelFactory

class ScreenMirrorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScreenMirrorBinding
    private var mirroring = false

    private val viewModel: CastViewModel by viewModels {
        AppViewModelFactory((application as TvRemoteApp).container)
    }

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
                    when (val result = viewModel.castLiveStream(url, getString(R.string.mirror_title))) {
                        is com.tvremote.app.util.OperationResult.Success -> {
                            updateUi()
                            Toast.makeText(
                                this@ScreenMirrorActivity,
                                getString(R.string.mirror_active, viewModel.deviceName() ?: "TV"),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        is com.tvremote.app.util.OperationResult.Failure -> {
                            mirroring = false
                            ScreenCastService.stop(this@ScreenMirrorActivity)
                            updateUi()
                            Toast.makeText(
                                this@ScreenMirrorActivity,
                                getString(R.string.cast_failed, result.message),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
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

        viewModel.initialize()
        binding.backButton.setOnClickListener { finish() }
        binding.mirrorRouteButton.routeSelector = MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO)
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
            .build()
        binding.selectedDeviceLabel.text = viewModel.deviceName()
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
        if (!viewModel.isConnected()) {
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

    override fun onResume() {
        super.onResume()
        binding.selectedDeviceLabel.text = viewModel.deviceName()
            ?: getString(R.string.cast_select_device)
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
        SafeRun.run(TAG) {
            try {
                unregisterReceiver(mirrorReceiver)
            } catch (_: IllegalArgumentException) {
            }
        }
        super.onStop()
    }

    companion object {
        private const val TAG = "ScreenMirrorActivity"
    }
}
