package com.tvremote.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.tabs.TabLayout
import com.tvremote.app.databinding.ActivityCastPhotoBinding

class CastPhotoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCastPhotoBinding
    private lateinit var container: AppContainer
    private var currentSource = PhotoSource.DOWNLOADS
    private val adapter = PhotoGridAdapter { uri -> castPhoto(uri) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) loadPhotos() else Toast.makeText(this, R.string.permission_photos, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCastPhotoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        container = (application as TvRemoteApp).container
        container.castManager.initialize()

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
        val bucket = when (currentSource) {
            PhotoSource.DOWNLOADS -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            PhotoSource.GALLERY -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
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
        val photos = mutableListOf<Uri>()
        contentResolver.query(
            bucket,
            arrayOf(MediaStore.Images.Media._ID),
            selection,
            selectionArgs,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext() && photos.size < 120) {
                val id = cursor.getLong(idCol)
                photos.add(
                    Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString(),
                    ),
                )
            }
        }
        adapter.submit(photos)
    }

    private fun castPhoto(uri: Uri) {
        if (!container.castManager.isConnected()) {
            Toast.makeText(this, R.string.cast_select_device, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val url = LocalMediaServer.serve(this, uri, "cast_photo.jpg")
            container.castManager.castImage(Uri.parse(url), getString(R.string.cast_photos_title))
            Toast.makeText(this, R.string.cast_photo_sent, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.cast_failed, e.message ?: "error"), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        LocalMediaServer.stop()
        super.onDestroy()
    }

    private enum class PhotoSource { DOWNLOADS, GALLERY }
}
