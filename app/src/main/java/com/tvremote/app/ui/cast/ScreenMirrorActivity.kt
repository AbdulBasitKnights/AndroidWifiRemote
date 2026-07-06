package com.tvremote.app.ui.cast

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.tvremote.app.ui.common.BaseActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import com.tvremote.app.R
import com.tvremote.app.TvRemoteApp
import com.tvremote.app.data.cast.ScreenCastService
import com.tvremote.app.databinding.ActivityScreenMirrorBinding
import com.tvremote.app.ui.common.AppViewModelFactory
import com.tvremote.app.ui.common.ConnectionLoader
import com.tvremote.app.util.NetworkUtils
import com.tvremote.app.util.SafeRun

class ScreenMirrorActivity : BaseActivity() {
    private lateinit var binding: ActivityScreenMirrorBinding
    private var mirroring = false
    private var pendingProjectionStart = false
    private var mirrorLoaderVisible = false

    private val viewModel: CastViewModel by viewModels {
        AppViewModelFactory((application as TvRemoteApp).container)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            requestScreenCapture()
        } else {
            Toast.makeText(this, R.string.mirror_notification_required, Toast.LENGTH_LONG).show()
        }
    }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        pendingProjectionStart = false
        if (result.resultCode != RESULT_OK || result.data == null) {
            hideMirrorLoader()
            Toast.makeText(this, R.string.mirror_permission_denied, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        showMirrorLoader()
        ScreenCastService.start(this, result.resultCode, result.data!!)
    }

    private val mirrorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ScreenCastService.ACTION_MIRROR_STARTED -> {
                    mirroring = true
                    hideMirrorLoader()
                    updateUi()
                    Toast.makeText(
                        this@ScreenMirrorActivity,
                        getString(R.string.mirror_active, viewModel.deviceName() ?: "TV"),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                ScreenCastService.ACTION_MIRROR_STOPPED -> {
                    mirroring = false
                    hideMirrorLoader()
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
        (application as? TvRemoteApp)?.container?.castRepository?.initialize()
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
        if (NetworkUtils.getWifiIpv4Address() == null) {
            Toast.makeText(this, R.string.mirror_wifi_required, Toast.LENGTH_LONG).show()
            return
        }
        if (!viewModel.isConnected()) {
            Toast.makeText(this, R.string.cast_select_device, Toast.LENGTH_SHORT).show()
            binding.mirrorRouteButton.performClick()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        requestScreenCapture()
    }

    private fun requestScreenCapture() {
        if (pendingProjectionStart) return
        pendingProjectionStart = true
        showMirrorLoader()
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun showMirrorLoader() {
        if (mirrorLoaderVisible) return
        ConnectionLoader.show(binding.connectionLoader.root, ConnectionLoader.Mode.MIRROR)
        binding.connectionLoader.root.bringToFront()
        mirrorLoaderVisible = true
    }

    private fun hideMirrorLoader() {
        if (!mirrorLoaderVisible) return
        ConnectionLoader.hide(binding.connectionLoader.root)
        mirrorLoaderVisible = false
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

    override fun onDestroy() {
        hideMirrorLoader()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScreenMirrorActivity"
    }
}
