package com.tvremote.app.ui.cast

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.tvremote.app.R
import com.tvremote.app.databinding.FragmentCastBinding
import com.tvremote.app.ui.main.MainActivity
import com.tvremote.app.util.OperationResult
import com.tvremote.app.util.SafeRun
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector

class CastFragment : Fragment(R.layout.fragment_cast) {
    private var _binding: FragmentCastBinding? = null

    private val viewModel: CastViewModel by viewModels {
        (requireActivity() as MainActivity).viewModelFactory()
    }

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
        SafeRun.run(TAG) {
            _binding = FragmentCastBinding.bind(view)
            viewModel.initialize()

            val binding = _binding ?: return@run
            binding.castRouteButton.visibility = View.VISIBLE
            binding.castRouteButton.routeSelector = MediaRouteSelector.Builder()
                .addControlCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO)
                .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                .build()

            binding.screenMirrorCard.setOnClickListener {
                SafeRun.run(TAG) {
                    if (!ensureCastDevice()) return@run
                    startActivity(Intent(requireContext(), ScreenMirrorActivity::class.java))
                }
            }
            binding.photosCard.setOnClickListener {
                SafeRun.run(TAG) {
                    if (!ensureCastDevice()) return@run
                    startActivity(Intent(requireContext(), CastPhotoActivity::class.java))
                }
            }
            binding.videosCard.setOnClickListener {
                SafeRun.run(TAG) {
                    if (!ensureCastDevice()) return@run
                    videoPicker.launch(arrayOf("video/*"))
                }
            }
            binding.audioCard.setOnClickListener {
                SafeRun.run(TAG) {
                    if (!ensureCastDevice()) return@run
                    audioPicker.launch(arrayOf("audio/*"))
                }
            }
        }
    }

    private fun ensureCastDevice(): Boolean {
        val binding = _binding ?: return false
        if (!viewModel.isConnected()) {
            Toast.makeText(requireContext(), R.string.cast_select_device, Toast.LENGTH_SHORT).show()
            binding.castRouteButton.performClick()
            return false
        }
        return true
    }

    private fun castUri(uri: Uri, isVideo: Boolean, isAudio: Boolean = false) {
        SafeRun.run(TAG) {
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
            when (val result = viewModel.castMedia(uri, isVideo, isAudio)) {
                is OperationResult.Success -> Toast.makeText(
                    requireContext(),
                    getString(R.string.cast_started, viewModel.deviceName() ?: "TV"),
                    Toast.LENGTH_SHORT,
                ).show()
                is OperationResult.Failure -> Toast.makeText(
                    requireContext(),
                    getString(R.string.cast_failed, result.message),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "CastFragment"
    }
}
