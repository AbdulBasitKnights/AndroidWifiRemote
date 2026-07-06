package com.tvremote.app.ui.mirror

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.tvremote.app.R
import com.tvremote.app.databinding.FragmentMirrorBinding
import com.tvremote.app.util.SafeRun
import com.tvremote.app.util.SystemMirrorHelper

class MirrorFragment : Fragment(R.layout.fragment_mirror) {
    private var _binding: FragmentMirrorBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        SafeRun.run(TAG) {
            _binding = FragmentMirrorBinding.bind(view)
            val binding = _binding ?: return@run

            binding.mirrorRouteButton.visibility = View.GONE

            binding.startMirrorButton.setOnClickListener {
                SafeRun.run(TAG) {
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

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "MirrorFragment"
    }
}
