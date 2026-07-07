package com.tvremote.app.ui.mirror

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.tvremote.app.R
import com.tvremote.app.databinding.FragmentMirrorBinding
import com.tvremote.app.ui.cast.CastViewModel
import com.tvremote.app.ui.main.MainActivity
import com.tvremote.app.util.SafeRun
import com.tvremote.app.util.SystemMirrorHelper
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector

class MirrorFragment : Fragment(R.layout.fragment_mirror) {
    private var _binding: FragmentMirrorBinding? = null

    private val castViewModel: CastViewModel by viewModels {
        (requireActivity() as MainActivity).viewModelFactory()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SafeRun.run(TAG) {
            _binding = FragmentMirrorBinding.bind(view)
            val binding = _binding ?: return@run

            castViewModel.initialize()
            binding.mirrorRouteButton.visibility = View.VISIBLE
            binding.mirrorRouteButton.routeSelector = MediaRouteSelector.Builder()
                .addControlCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO)
                .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                .build()

            binding.startMirrorButton.setOnClickListener {
                SafeRun.run(TAG) {
                    if (!ensureCastDevice()) return@run
                    val opened = SystemMirrorHelper.openMirrorSettings(requireContext())
                    if (!opened) {
                        Toast.makeText(
                            requireContext(),
                            SystemMirrorHelper.fallbackMessage(requireContext()),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
        }
    }

    private fun ensureCastDevice(): Boolean {
        val binding = _binding ?: return false
        if (!castViewModel.isConnected()) {
            Toast.makeText(requireContext(), R.string.mirror_cast_first, Toast.LENGTH_SHORT).show()
            binding.mirrorRouteButton.performClick()
            return false
        }
        return true
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "MirrorFragment"
    }
}
