package com.tvremote.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import com.tvremote.app.databinding.FragmentCastBinding

class CastFragment : Fragment(R.layout.fragment_cast) {
    private var _binding: FragmentCastBinding? = null
    private val binding get() = _binding!!

    private val videoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        castUri(uri, isVideo = true)
    }

    private val audioPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        castUri(uri, isVideo = false, isAudio = true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCastBinding.bind(view)
        val container = (requireActivity() as MainActivity).appContainer()
        container.castManager.initialize()

        binding.castRouteButton.visibility = View.VISIBLE
        binding.castRouteButton.routeSelector = MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO)
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
            .build()

        binding.screenMirrorCard.setOnClickListener {
            if (!container.castManager.isConnected()) {
                Toast.makeText(requireContext(), R.string.cast_select_device, Toast.LENGTH_SHORT).show()
                binding.castRouteButton.performClick()
                return@setOnClickListener
            }
            startActivity(Intent(requireContext(), ScreenMirrorActivity::class.java))
        }

        binding.photosCard.setOnClickListener {
            if (!ensureCastDevice()) return@setOnClickListener
            startActivity(Intent(requireContext(), CastPhotoActivity::class.java))
        }

        binding.videosCard.setOnClickListener {
            if (!ensureCastDevice()) return@setOnClickListener
            videoPicker.launch(arrayOf("video/*"))
        }

        binding.audioCard.setOnClickListener {
            if (!ensureCastDevice()) return@setOnClickListener
            audioPicker.launch(arrayOf("audio/*"))
        }
    }

    private fun ensureCastDevice(): Boolean {
        val container = (requireActivity() as MainActivity).appContainer()
        if (!container.castManager.isConnected()) {
            Toast.makeText(requireContext(), R.string.cast_select_device, Toast.LENGTH_SHORT).show()
            binding.castRouteButton.performClick()
            return false
        }
        return true
    }

    private fun castUri(uri: Uri, isVideo: Boolean, isAudio: Boolean = false) {
        val container = (requireActivity() as MainActivity).appContainer()
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
        }
        try {
            val ext = when {
                isAudio -> "mp3"
                isVideo -> "mp4"
                else -> "jpg"
            }
            val url = LocalMediaServer.serve(requireContext(), uri, "cast_media.$ext")
            when {
                isAudio -> container.castManager.castAudio(Uri.parse(url))
                isVideo -> container.castManager.castVideo(Uri.parse(url))
                else -> container.castManager.castImage(Uri.parse(url))
            }
            Toast.makeText(
                requireContext(),
                getString(R.string.cast_started, container.castManager.deviceName() ?: "TV"),
                Toast.LENGTH_SHORT,
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                getString(R.string.cast_failed, e.message ?: "error"),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
