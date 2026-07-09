package com.tvremote.app.ui.cast

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.tvremote.app.R
import com.tvremote.app.databinding.FragmentCastBinding
import com.tvremote.app.ui.main.MainActivity
import com.tvremote.app.util.SafeRun
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector

class CastFragment : Fragment(R.layout.fragment_cast) {
    private var _binding: FragmentCastBinding? = null

    private val viewModel: CastViewModel by viewModels {
        (requireActivity() as MainActivity).viewModelFactory()
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

            binding.photosCard.setOnClickListener {
                SafeRun.run(TAG) {
                    if (!ensureCastDevice()) return@run
                    openMediaGallery(MediaPermissionHelper.MediaKind.PHOTO)
                }
            }
            binding.videosCard.setOnClickListener {
                SafeRun.run(TAG) {
                    if (!ensureCastDevice()) return@run
                    openMediaGallery(MediaPermissionHelper.MediaKind.VIDEO)
                }
            }
        }
    }

    private fun openMediaGallery(kind: MediaPermissionHelper.MediaKind) {
        startActivity(CastMediaGalleryActivity.intent(requireContext(), kind))
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

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "CastFragment"
    }
}
