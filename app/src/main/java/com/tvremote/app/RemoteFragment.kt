package com.tvremote.app

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.tvremote.app.databinding.FragmentRemoteBinding
import com.tvremote.control.commands.Key

class RemoteFragment : Fragment(R.layout.fragment_remote) {
    private var _binding: FragmentRemoteBinding? = null
    private val binding get() = _binding!!
    private var volumeLevel = 7

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentRemoteBinding.bind(view)
        val remote = (requireActivity() as MainActivity).appContainer().remoteManager

        remote.remoteStateChanged = { state ->
            _binding?.connectionStatusLabel?.text = state
        }

        binding.powerButton.setOnClickListener { remote.power() }
        binding.headerCastButton.setOnClickListener {
            (requireActivity() as MainActivity).navigateToCast()
        }

        binding.netflixChip.setOnClickListener { remote.runNetflix() }
        binding.youtubeChip.setOnClickListener { remote.runYouTube() }
        binding.primeChip.setOnClickListener { remote.runPrime() }
        binding.hotstarChip.setOnClickListener { remote.runHotstar() }

        binding.upButton.setOnClickListener { remote.sendKey(Key.KEYCODE_DPAD_UP) }
        binding.downButton.setOnClickListener { remote.sendKey(Key.KEYCODE_DPAD_DOWN) }
        binding.leftButton.setOnClickListener { remote.sendKey(Key.KEYCODE_DPAD_LEFT) }
        binding.rightButton.setOnClickListener { remote.sendKey(Key.KEYCODE_DPAD_RIGHT) }
        binding.okButton.setOnClickListener { remote.sendKey(Key.KEYCODE_DPAD_CENTER) }

        binding.volUpButton.setOnClickListener {
            volumeLevel = (volumeLevel + 1).coerceAtMost(15)
            updateVolumeFill()
            remote.volUp()
        }
        binding.volDownButton.setOnClickListener {
            volumeLevel = (volumeLevel - 1).coerceAtLeast(0)
            updateVolumeFill()
            remote.volDown()
        }

        binding.channelUpButton.setOnClickListener { remote.channelUp() }
        binding.channelDownButton.setOnClickListener { remote.channelDown() }

        binding.homeButton.setOnClickListener { remote.sendKey(Key.KEYCODE_HOME) }
        binding.backButton.setOnClickListener { remote.sendKey(Key.KEYCODE_BACK) }
        binding.appsButton.setOnClickListener { remote.apps() }
        binding.inputButton.setOnClickListener { remote.tvInput() }
        binding.micButton.setOnClickListener { remote.voiceSearch() }
        binding.muteButton.setOnClickListener { remote.mute() }
        binding.rewindButton.setOnClickListener { remote.rewind() }
        binding.playPauseButton.setOnClickListener { remote.playPause() }
        binding.forwardButton.setOnClickListener { remote.forward() }

        updateVolumeFill()
    }

    private fun updateVolumeFill() {
        val binding = _binding ?: return
        val maxHeight = resources.getDimensionPixelSize(R.dimen.volume_slider_height) - 32
        val fillHeight = (maxHeight * (volumeLevel / 15f)).toInt().coerceAtLeast(24)
        binding.volumeFill.layoutParams = binding.volumeFill.layoutParams.apply {
            height = fillHeight
        }
    }

    override fun onDestroyView() {
        (activity as? MainActivity)?.appContainer()?.remoteManager?.remoteStateChanged = null
        _binding = null
        super.onDestroyView()
    }
}
