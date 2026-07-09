package com.tvremote.app.ui.cast

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import com.tvremote.app.R
import com.tvremote.app.TvRemoteApp
import com.tvremote.app.databinding.ActivityCastMediaGalleryBinding
import com.tvremote.app.ui.cast.adapter.CastMediaGridAdapter
import com.tvremote.app.ui.common.AppViewModelFactory
import com.tvremote.app.ui.common.BaseActivity
import com.tvremote.app.util.OperationResult
import com.tvremote.app.util.SafeRun

class CastMediaGalleryActivity : BaseActivity() {
    private lateinit var binding: ActivityCastMediaGalleryBinding
    private lateinit var mediaKind: MediaPermissionHelper.MediaKind

    private val viewModel: CastViewModel by viewModels {
        AppViewModelFactory((application as TvRemoteApp).container)
    }

    private val adapter = CastMediaGridAdapter { item -> castItem(item) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            loadMedia()
        } else {
            showPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCastMediaGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mediaKind = intent.getStringExtra(EXTRA_MEDIA_KIND)
            ?.let { runCatching { MediaPermissionHelper.MediaKind.valueOf(it) }.getOrNull() }
            ?: MediaPermissionHelper.MediaKind.PHOTO

        viewModel.initialize()
        setupHeader()
        binding.backButton.setOnClickListener { finish() }
        binding.mediaGrid.layoutManager = GridLayoutManager(
            this,
            if (mediaKind == MediaPermissionHelper.MediaKind.VIDEO) 2 else 3,
        )
        binding.mediaGrid.adapter = adapter
        binding.permissionButton.setOnClickListener { requestMediaPermission() }

        ensurePermissionAndLoad()
    }

    private fun setupHeader() {
        when (mediaKind) {
            MediaPermissionHelper.MediaKind.PHOTO -> {
                binding.galleryTitle.setText(R.string.cast_photos_title)
                binding.gallerySubtitle.setText(R.string.cast_gallery_photo_subtitle)
                binding.emptyIcon.setImageResource(R.drawable.ic_photos)
            }
            MediaPermissionHelper.MediaKind.VIDEO -> {
                binding.galleryTitle.setText(R.string.cast_videos_title)
                binding.gallerySubtitle.setText(R.string.cast_gallery_video_subtitle)
                binding.emptyIcon.setImageResource(R.drawable.ic_video)
            }
        }
    }

    private fun ensurePermissionAndLoad() {
        if (MediaPermissionHelper.hasPermission(this, mediaKind)) {
            loadMedia()
        } else {
            requestMediaPermission()
        }
    }

    private fun requestMediaPermission() {
        permissionLauncher.launch(MediaPermissionHelper.requiredPermission(mediaKind))
    }

    private fun loadMedia() {
        showLoading()
        SafeRun.run(TAG) {
            val items = when (mediaKind) {
                MediaPermissionHelper.MediaKind.PHOTO -> CastMediaLoader.loadPhotos(this)
                MediaPermissionHelper.MediaKind.VIDEO -> CastMediaLoader.loadVideos(this)
            }
            runOnUiThread {
                if (items.isEmpty()) {
                    showEmpty(
                        title = if (mediaKind == MediaPermissionHelper.MediaKind.PHOTO) {
                            getString(R.string.cast_gallery_empty_photos_title)
                        } else {
                            getString(R.string.cast_gallery_empty_videos_title)
                        },
                        message = getString(R.string.cast_gallery_empty_message),
                        showPermissionButton = false,
                    )
                } else {
                    showGrid(items)
                }
            }
        }
    }

    private fun showLoading() {
        binding.loadingIndicator.isVisible = true
        binding.mediaGrid.isVisible = false
        binding.emptyState.isVisible = false
        binding.mediaCountChip.isVisible = false
    }

    private fun showGrid(items: List<CastMediaItem>) {
        binding.loadingIndicator.isVisible = false
        binding.emptyState.isVisible = false
        binding.mediaGrid.isVisible = true
        binding.mediaCountChip.isVisible = true
        binding.mediaCountChip.text = items.size.toString()
        adapter.submitList(items)
    }

    private fun showPermissionDenied() {
        val message = when (mediaKind) {
            MediaPermissionHelper.MediaKind.PHOTO -> getString(R.string.permission_photos)
            MediaPermissionHelper.MediaKind.VIDEO -> getString(R.string.permission_videos)
        }
        showEmpty(
            title = getString(R.string.cast_gallery_permission_title),
            message = message,
            showPermissionButton = true,
        )
    }

    private fun showEmpty(title: String, message: String, showPermissionButton: Boolean) {
        binding.loadingIndicator.isVisible = false
        binding.mediaGrid.isVisible = false
        binding.mediaCountChip.isVisible = false
        binding.emptyState.isVisible = true
        binding.emptyTitle.text = title
        binding.emptyMessage.text = message
        binding.permissionButton.isVisible = showPermissionButton
        binding.permissionButton.text = getString(R.string.cast_gallery_allow_access)
    }

    private fun castItem(item: CastMediaItem) {
        if (!viewModel.isConnected()) {
            Toast.makeText(this, R.string.cast_select_device, Toast.LENGTH_SHORT).show()
            return
        }
        val result = if (item.isVideo) {
            viewModel.castMedia(item.uri, isVideo = true)
        } else {
            viewModel.castPhoto(item.uri, getString(R.string.cast_photos_title))
        }
        when (result) {
            is OperationResult.Success -> {
                val message = if (item.isVideo) R.string.cast_video_sent else R.string.cast_photo_sent
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
            is OperationResult.Failure ->
                Toast.makeText(this, getString(R.string.cast_failed, result.message), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        viewModel.stopLocalServer()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CastMediaGallery"
        private const val EXTRA_MEDIA_KIND = "media_kind"

        fun intent(context: Context, kind: MediaPermissionHelper.MediaKind): Intent =
            Intent(context, CastMediaGalleryActivity::class.java).apply {
                putExtra(EXTRA_MEDIA_KIND, kind.name)
            }
    }
}
