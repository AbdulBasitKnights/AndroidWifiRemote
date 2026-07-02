package com.tvremote.app.ui.remote

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tvremote.app.R
import com.tvremote.app.databinding.FragmentRemoteBinding
import com.tvremote.app.ui.main.MainActivity
import com.tvremote.app.util.SafeRun
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
        binding.powerButton.setOnClickListener { SafeRun.run(TAG) { viewModel.power() } }
        binding.headerCastButton.setOnClickListener {
            SafeRun.run(TAG) {
                (requireActivity() as? MainActivity)?.navigateToCast()
            }
        }
        binding.netflixChip.setOnClickListener { SafeRun.run(TAG) { viewModel.runNetflix() } }
        binding.youtubeChip.setOnClickListener { SafeRun.run(TAG) { viewModel.runYouTube() } }
        binding.primeChip.setOnClickListener { SafeRun.run(TAG) { viewModel.runPrime() } }
        binding.hotstarChip.setOnClickListener { SafeRun.run(TAG) { viewModel.runHotstar() } }
        binding.upButton.setOnClickListener { SafeRun.run(TAG) { viewModel.sendKey(Key.KEYCODE_DPAD_UP) } }
        binding.downButton.setOnClickListener { SafeRun.run(TAG) { viewModel.sendKey(Key.KEYCODE_DPAD_DOWN) } }
        binding.leftButton.setOnClickListener { SafeRun.run(TAG) { viewModel.sendKey(Key.KEYCODE_DPAD_LEFT) } }
        binding.rightButton.setOnClickListener { SafeRun.run(TAG) { viewModel.sendKey(Key.KEYCODE_DPAD_RIGHT) } }
        binding.okButton.setOnClickListener { SafeRun.run(TAG) { viewModel.sendKey(Key.KEYCODE_DPAD_CENTER) } }
        binding.volUpButton.setOnClickListener { SafeRun.run(TAG) { viewModel.volUp() } }
        binding.volDownButton.setOnClickListener { SafeRun.run(TAG) { viewModel.volDown() } }
        binding.channelUpButton.setOnClickListener { SafeRun.run(TAG) { viewModel.channelUp() } }
        binding.channelDownButton.setOnClickListener { SafeRun.run(TAG) { viewModel.channelDown() } }
        binding.homeButton.setOnClickListener { SafeRun.run(TAG) { viewModel.sendKey(Key.KEYCODE_HOME) } }
        binding.backButton.setOnClickListener { SafeRun.run(TAG) { viewModel.sendKey(Key.KEYCODE_BACK) } }
        binding.appsButton.setOnClickListener { SafeRun.run(TAG) { viewModel.apps() } }
        binding.inputButton.setOnClickListener { SafeRun.run(TAG) { viewModel.tvInput() } }
        binding.micButton.setOnClickListener { SafeRun.run(TAG) { viewModel.voiceSearch() } }
        binding.muteButton.setOnClickListener { SafeRun.run(TAG) { viewModel.mute() } }
        binding.rewindButton.setOnClickListener { SafeRun.run(TAG) { viewModel.rewind() } }
        binding.playPauseButton.setOnClickListener { SafeRun.run(TAG) { viewModel.playPause() } }
        binding.forwardButton.setOnClickListener { SafeRun.run(TAG) { viewModel.forward() } }
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
                launch {
                    viewModel.events.collect { event ->
                        if (event == RemoteViewModel.RemoteEvent.PausedWhileCasting) {
                            Toast.makeText(
                                requireContext(),
                                R.string.remote_paused_casting,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
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

    companion object {
        private const val TAG = "RemoteFragment"
    }
}
