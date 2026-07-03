package com.tvremote.app.ui.remote

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.button.MaterialButton
import com.tvremote.app.R
import com.tvremote.app.data.model.DiscoveredTv
import com.tvremote.app.data.repository.TvRemoteRepository
import com.tvremote.app.data.session.AppPreferences
import com.tvremote.app.databinding.FragmentRemoteBinding
import com.tvremote.app.ui.main.MainActivity
import com.tvremote.app.ui.settings.adapter.DiscoveredTvAdapter
import com.tvremote.control.commands.Key
import kotlinx.coroutines.launch
import kotlin.math.abs

class RemoteFragment : Fragment(R.layout.fragment_remote) {
    private var _binding: FragmentRemoteBinding? = null
    private var tvAdapter: DiscoveredTvAdapter? = null
    private var touchStartX = 0f
    private var touchStartY = 0f

    private val viewModel: RemoteViewModel by viewModels {
        (requireActivity() as MainActivity).viewModelFactory()
    }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startVoice() else {
            Toast.makeText(requireContext(), R.string.mic_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentRemoteBinding.bind(view)
        val binding = _binding ?: return

        val adapter = DiscoveredTvAdapter { tv -> viewModel.onTvSelected(tv) }
        tvAdapter = adapter
        binding.discoveredTvsList.layoutManager = LinearLayoutManager(requireContext())
        binding.discoveredTvsList.adapter = adapter

        setupConnectPanel()
        setupRemoteControls()
        setupModeToggle()
        setupTouchpad()
        setupNumpad()
        observeState()
        viewModel.startScan()
    }

    override fun onResume() {
        super.onResume()
        val state = viewModel.uiState.value
        if (!state.isSessionReady && state.discoveredTvs.isEmpty() && !state.isScanning) {
            viewModel.startScan()
        }
    }

    override fun onPause() {
        viewModel.stopScan()
        viewModel.stopVoiceInput()
        super.onPause()
    }

    private fun setupConnectPanel() {
        val binding = _binding ?: return
        binding.connectCastButton.setOnClickListener {
            (requireActivity() as? MainActivity)?.navigateToCast()
        }
        binding.manualIpToggle.setOnClickListener {
            binding.manualIpSection.isVisible = !binding.manualIpSection.isVisible
        }
        binding.pairManualButton.setOnClickListener {
            val host = binding.hostInput.text?.toString().orEmpty().trim()
            viewModel.pairManual(host)
        }
        binding.reconnectButton.setOnClickListener { viewModel.reconnect() }
        binding.disconnectButton.setOnClickListener { viewModel.disconnect() }
        binding.completePairingButton.setOnClickListener { submitPairingCode() }
        binding.codeInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitPairingCode()
                true
            } else false
        }
    }

    private fun submitPairingCode() {
        val binding = _binding ?: return
        val code = binding.codeInput.text?.toString().orEmpty()
        if (code.length != 6) {
            Toast.makeText(requireContext(), R.string.enter_full_code, Toast.LENGTH_SHORT).show()
            return
        }
        binding.completePairingButton.isEnabled = false
        if (!viewModel.submitPairingCode(code)) {
            binding.completePairingButton.isEnabled = viewModel.isWaitingForCode()
        }
    }

    private fun setupRemoteControls() {
        val binding = _binding ?: return
        val dpad = binding.dpadPanel
        binding.remoteHeader.headerCastButton.setOnClickListener {
            (requireActivity() as? MainActivity)?.navigateToCast()
        }
        binding.powerButton.setOnClickListener { viewModel.power() }
        binding.homeButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_HOME) }
        dpad.upButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_DPAD_UP) }
        dpad.downButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_DPAD_DOWN) }
        dpad.leftButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_DPAD_LEFT) }
        dpad.rightButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_DPAD_RIGHT) }
        dpad.okButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_DPAD_CENTER) }
        binding.mediaRow.volUpButton.setOnClickListener { viewModel.volUp() }
        binding.mediaRow.volDownButton.setOnClickListener { viewModel.volDown() }
        binding.mediaRow.backButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_BACK) }
        binding.mediaRow.inputButton.setOnClickListener { viewModel.tvInput() }
        binding.mediaRow.appsButton.setOnClickListener { viewModel.apps() }
        binding.mediaRow.micButton.setOnClickListener { requestMicAndSpeak() }
        binding.mediaRow.muteButton.setOnClickListener { viewModel.mute() }
        binding.mediaRow.rewindButton.setOnClickListener { viewModel.rewind() }
        binding.mediaRow.playPauseButton.setOnClickListener { viewModel.playPause() }
        binding.mediaRow.forwardButton.setOnClickListener { viewModel.forward() }
        binding.mediaRow.headerSettingsShortcut.setOnClickListener {
            (requireActivity() as? MainActivity)?.navigateToSettings()
        }
    }

    private fun setupModeToggle() {
        val binding = _binding ?: return
        binding.modeToggle.check(R.id.modeDpad)
        binding.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            binding.dpadPanel.root.isVisible = checkedId == R.id.modeDpad
            binding.touchpadPanel.isVisible = checkedId == R.id.modeTouchpad
            binding.numpadPanel.isVisible = checkedId == R.id.modeNumpad
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchpad() {
        val binding = _binding ?: return
        binding.touchpadPanel.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - touchStartX
                    val dy = event.y - touchStartY
                    if (abs(dx) > abs(dy) && abs(dx) > 48) {
                        viewModel.sendKey(if (dx > 0) Key.KEYCODE_DPAD_RIGHT else Key.KEYCODE_DPAD_LEFT)
                    } else if (abs(dy) > 48) {
                        viewModel.sendKey(if (dy > 0) Key.KEYCODE_DPAD_DOWN else Key.KEYCODE_DPAD_UP)
                    } else if (abs(dx) < 24 && abs(dy) < 24) {
                        viewModel.sendKey(Key.KEYCODE_DPAD_CENTER)
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun setupNumpad() {
        val binding = _binding ?: return
        val map = mapOf(
            binding.num0 to Key.KEYCODE_0,
            binding.num1 to Key.KEYCODE_1,
            binding.num2 to Key.KEYCODE_2,
            binding.num3 to Key.KEYCODE_3,
            binding.num4 to Key.KEYCODE_4,
            binding.num5 to Key.KEYCODE_5,
            binding.num6 to Key.KEYCODE_6,
            binding.num7 to Key.KEYCODE_7,
            binding.num8 to Key.KEYCODE_8,
            binding.num9 to Key.KEYCODE_9,
        )
        map.forEach { (btn, key) -> btn.setOnClickListener { viewModel.sendKey(key) } }
        binding.numExit.setOnClickListener { viewModel.sendKey(Key.KEYCODE_ESCAPE) }
        binding.numDel.setOnClickListener { viewModel.sendKey(Key.KEYCODE_DEL) }
    }

    private fun requestMicAndSpeak() {
        if (!viewModel.uiState.value.isSessionReady) return
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startVoice()
        }
    }

    private fun startVoice() {
        viewModel.startVoiceInput(AppPreferences(requireContext()).selectedLanguage)
        Toast.makeText(requireContext(), R.string.voice_listening, Toast.LENGTH_SHORT).show()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(it) } }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is TvRemoteRepository.RepositoryEvent.PairingStarted ->
                                Toast.makeText(requireContext(), R.string.pairing_started_toast, Toast.LENGTH_SHORT).show()
                            is TvRemoteRepository.RepositoryEvent.Disconnected ->
                                Toast.makeText(requireContext(), R.string.disconnected_from_tv, Toast.LENGTH_SHORT).show()
                            is TvRemoteRepository.RepositoryEvent.Reconnecting ->
                                Toast.makeText(requireContext(), R.string.reconnecting_remote, Toast.LENGTH_SHORT).show()
                            is TvRemoteRepository.RepositoryEvent.InvalidIp ->
                                Toast.makeText(requireContext(), R.string.invalid_ip, Toast.LENGTH_LONG).show()
                            is TvRemoteRepository.RepositoryEvent.SelectTvFirst ->
                                Toast.makeText(requireContext(), R.string.select_tv_first, Toast.LENGTH_SHORT).show()
                            is TvRemoteRepository.RepositoryEvent.Error ->
                                Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                            else -> Unit
                        }
                    }
                }
                launch {
                    viewModel.isListening.collect { listening ->
                        _binding?.mediaRow?.micButton?.alpha = if (listening) 0.5f else 1f
                    }
                }
            }
        }
    }

    private fun render(state: RemoteViewModel.RemoteUiState) {
        val binding = _binding ?: return
        val adapter = tvAdapter ?: return

        val connected = state.isSessionReady
        binding.connectPanel.isVisible = !connected
        binding.remotePanel.isVisible = connected
        binding.connectionLoader.isVisible = state.showConnectionLoader

        adapter.submit(state.discoveredTvs)
        adapter.setSelectedHost(state.pairingHost ?: state.savedTvHost)
        adapter.setPairingHost(state.pairingHost)
        adapter.setPairedHost(if (state.isPaired) state.savedTvHost else null)

        binding.scanStatusLabel.text = when {
            state.isScanning -> getString(R.string.scanning_tvs)
            state.discoveredTvs.isEmpty() -> getString(R.string.discovered_tvs_none)
            else -> getString(R.string.discovered_tvs_count, state.discoveredTvs.size)
        }

        binding.pairingCodeCard.isVisible = state.waitingForCode
        binding.completePairingButton.isEnabled = state.waitingForCode
        binding.reconnectButton.isVisible = state.isPaired && !state.waitingForCode && !connected
        binding.disconnectButton.isVisible = state.isPaired && !connected
    }

    override fun onDestroyView() {
        tvAdapter = null
        _binding = null
        super.onDestroyView()
    }
}
