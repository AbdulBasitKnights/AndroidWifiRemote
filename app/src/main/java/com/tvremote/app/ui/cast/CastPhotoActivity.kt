package com.tvremote.app.ui.cast

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.tvremote.app.ui.common.BaseActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.tabs.TabLayout
import com.tvremote.app.R
import com.tvremote.app.TvRemoteApp
import com.tvremote.app.databinding.ActivityCastPhotoBinding
import com.tvremote.app.ui.cast.adapter.PhotoGridAdapter
import com.tvremote.app.ui.common.AppViewModelFactory
import com.tvremote.app.util.OperationResult
import com.tvremote.app.util.SafeRun

class CastPhotoActivity : BaseActivity() {
    private lateinit var binding: ActivityCastPhotoBinding
    private var currentSource = PhotoSource.DOWNLOADS
    private val adapter = PhotoGridAdapter { uri -> castPhoto(uri) }

    private val viewModel: CastViewModel by viewModels {
        AppViewModelFactory((application as TvRemoteApp).container)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) loadPhotos() else Toast.makeText(this, R.string.permission_photos, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCastPhotoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel.initialize()
        binding.backButton.setOnClickListener { finish() }
        binding.photoGrid.layoutManager = GridLayoutManager(this, 3)
        binding.photoGrid.adapter = adapter

        binding.photoTabs.addTab(binding.photoTabs.newTab().setText(R.string.cast_tab_downloads))
        binding.photoTabs.addTab(binding.photoTabs.newTab().setText(R.string.cast_tab_gallery))
        binding.photoTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentSource = if (tab.position == 0) PhotoSource.DOWNLOADS else PhotoSource.GALLERY
                ensurePermissionAndLoad()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) = Unit
            override fun onTabReselected(tab: TabLayout.Tab?) = Unit
        })

        ensurePermissionAndLoad()
    }

    private fun ensurePermissionAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadPhotos()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    private fun loadPhotos() {
        SafeRun.run(TAG) {
            val photos = mutableListOf<Uri>()
            try {
                val selection = if (currentSource == PhotoSource.DOWNLOADS) {
                    "${MediaStore.Images.Media.DATA} LIKE ?"
                } else {
                    null
                }
                val selectionArgs = if (currentSource == PhotoSource.DOWNLOADS) {
                    arrayOf("%/Download/%")
                } else {
                    null
                }
                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Images.Media._ID),
                    selection,
                    selectionArgs,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC",
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                    if (idCol < 0) return@use
                    while (cursor.moveToNext() && photos.size < 120) {
                        val id = cursor.getLong(idCol)
                        photos.add(
                            Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()),
                        )
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.cast_failed, e.message ?: "error"), Toast.LENGTH_SHORT).show()
            }
            adapter.submit(photos)
        }
    }

    private fun castPhoto(uri: Uri) {
        if (!viewModel.isConnected()) {
            Toast.makeText(this, R.string.cast_select_device, Toast.LENGTH_SHORT).show()
            return
        }
        when (val result = viewModel.castPhoto(uri, getString(R.string.cast_photos_title))) {
            is OperationResult.Success ->
                Toast.makeText(this, R.string.cast_photo_sent, Toast.LENGTH_SHORT).show()
            is OperationResult.Failure ->
                Toast.makeText(this, getString(R.string.cast_failed, result.message), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        viewModel.stopLocalServer()
        super.onDestroy()
    }

    private enum class PhotoSource { DOWNLOADS, GALLERY }

    companion object {
        private const val TAG = "CastPhotoActivity"
    }
}
