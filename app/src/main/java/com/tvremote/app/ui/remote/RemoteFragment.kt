package com.tvremote.app.ui.remote

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tvremote.app.R
import com.tvremote.app.databinding.FragmentRemoteBinding
import com.tvremote.app.ui.main.MainActivity
import com.tvremote.control.commands.Key
import kotlinx.coroutines.launch

class RemoteFragment : Fragment(R.layout.fragment_remote) {
    private var _binding: FragmentRemoteBinding? = null
    private val viewModel: RemoteViewModel by viewModels {
        (requireActivity() as MainActivity).viewModelFactory()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentRemoteBinding.bind(view)
        setupControls()
        observeState()
    }

    private fun setupControls() {
        val binding = _binding ?: return
        binding.powerButton.setOnClickListener { viewModel.power() }
        binding.headerCastButton.setOnClickListener {
            (requireActivity() as? MainActivity)?.navigateToCast()
        }
        binding.netflixChip.setOnClickListener { viewModel.runNetflix() }
        binding.youtubeChip.setOnClickListener { viewModel.runYouTube() }
        binding.primeChip.setOnClickListener { viewModel.runPrime() }
        binding.hotstarChip.setOnClickListener { viewModel.runHotstar() }
        binding.upButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_DPAD_UP) }
        binding.downButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_DPAD_DOWN) }
        binding.leftButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_DPAD_LEFT) }
        binding.rightButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_DPAD_RIGHT) }
        binding.okButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_DPAD_CENTER) }
        binding.volUpButton.setOnClickListener { viewModel.volUp() }
        binding.volDownButton.setOnClickListener { viewModel.volDown() }
        binding.channelUpButton.setOnClickListener { viewModel.channelUp() }
        binding.channelDownButton.setOnClickListener { viewModel.channelDown() }
        binding.homeButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_HOME) }
        binding.backButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_BACK) }
        binding.appsButton.setOnClickListener { viewModel.apps() }
        binding.inputButton.setOnClickListener { viewModel.tvInput() }
        binding.micButton.setOnClickListener { viewModel.voiceSearch() }
        binding.muteButton.setOnClickListener { viewModel.mute() }
        binding.rewindButton.setOnClickListener { viewModel.rewind() }
        binding.playPauseButton.setOnClickListener { viewModel.playPause() }
        binding.forwardButton.setOnClickListener { viewModel.forward() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.connectionStatus.collect { status ->
                        _binding?.connectionStatusLabel?.text = status
                    }
                }
                launch {
                    viewModel.volumeLevel.collect { level ->
                        updateVolumeFill(level)
                    }
                }
            }
        }
    }

    private fun updateVolumeFill(level: Int) {
        val binding = _binding ?: return
        val maxHeight = resources.getDimensionPixelSize(R.dimen.volume_slider_height) - 32
        val fillHeight = (maxHeight * (level / 15f)).toInt().coerceAtLeast(24)
        binding.volumeFill.layoutParams = binding.volumeFill.layoutParams.apply {
            height = fillHeight
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
