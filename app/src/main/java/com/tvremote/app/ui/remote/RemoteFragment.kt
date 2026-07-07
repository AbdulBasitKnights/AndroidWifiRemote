package com.tvremote.app.ui.remote

import android.content.res.ColorStateList
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
import androidx.fragment.app.activityViewModels
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
import com.tvremote.app.ui.common.ConnectionLoader
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
    private var connectPanelOpen = false

    private val viewModel: RemoteViewModel by activityViewModels {
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
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshConnectionState()
        viewModel.refreshCastState()
        if (connectPanelOpen) {
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
        binding.connectPanelClose.setOnClickListener { hideConnectPanel() }
        binding.rescanButton.setOnClickListener { viewModel.startScan() }
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
        val topBar = binding.remoteTopBar

        topBar.settingsButton.setOnClickListener {
            (requireActivity() as? MainActivity)?.openSettings()
        }
        topBar.devicePill.setOnClickListener {
            if (viewModel.uiState.value.isSessionReady) return@setOnClickListener
            if (connectPanelOpen) hideConnectPanel() else showConnectPanel()
        }
        topBar.powerButton.setOnClickListener { viewModel.power() }
        binding.backNavButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_BACK) }
        binding.homeButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_HOME) }
        binding.appsNavButton.setOnClickListener { viewModel.apps() }
        dpad.upButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_DPAD_UP) }
        dpad.downButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_DPAD_DOWN) }
        dpad.leftButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_DPAD_LEFT) }
        dpad.rightButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_DPAD_RIGHT) }
        dpad.okButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_DPAD_CENTER) }
        binding.mediaRow.volUpButton.setOnClickListener { viewModel.volUp() }
        binding.mediaRow.volDownButton.setOnClickListener { viewModel.volDown() }
        binding.mediaRow.backButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_BACK) }
        binding.mediaRow.homeButton.setOnClickListener { viewModel.sendKey(Key.KEYCODE_HOME) }
        binding.mediaRow.inputButton.setOnClickListener { viewModel.tvInput() }
        binding.mediaRow.appsButton.setOnClickListener { viewModel.apps() }
        binding.mediaRow.micButton.setOnClickListener { requestMicAndSpeak() }
        binding.mediaRow.muteButton.setOnClickListener { viewModel.mute() }
        binding.mediaRow.rewindButton.setOnClickListener { viewModel.rewind() }
        binding.mediaRow.playPauseButton.setOnClickListener { viewModel.playPause() }
        binding.mediaRow.forwardButton.setOnClickListener { viewModel.forward() }
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
        val keyboard = _binding?.numpadPanel ?: return
        keyboard.onTextInput = { text -> viewModel.sendText(text) }
        keyboard.onKeyPress = { key -> viewModel.sendKey(key) }
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
                            is TvRemoteRepository.RepositoryEvent.PairingStarted -> {
                                showConnectPanel()
                                Toast.makeText(requireContext(), R.string.pairing_started_toast, Toast.LENGTH_SHORT).show()
                            }
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

    private fun showConnectPanel() {
        val binding = _binding ?: return
        connectPanelOpen = true
        binding.connectPanel.isVisible = true
        binding.connectPanel.bringToFront()
        binding.connectionLoader.root.bringToFront()
        castRepositoryInitializeForDiscovery()
        viewModel.startScan()
    }

    private fun castRepositoryInitializeForDiscovery() {
        (requireActivity() as? MainActivity)?.appContainer()?.castRepository?.initialize()
    }

    private fun hideConnectPanel() {
        val binding = _binding ?: return
        connectPanelOpen = false
        binding.connectPanel.isVisible = false
        viewModel.stopScan()
    }

    private fun render(state: RemoteViewModel.RemoteUiState) {
        val binding = _binding ?: return
        val adapter = tvAdapter ?: return

        if (state.isSessionReady && connectPanelOpen) {
            hideConnectPanel()
        }
        if (state.waitingForCode && !connectPanelOpen) {
            showConnectPanel()
        }

        binding.connectPanel.isVisible = connectPanelOpen
        if (connectPanelOpen) {
            binding.connectPanel.bringToFront()
        }
        if (state.showConnectionLoader) {
            ConnectionLoader.show(
                binding.connectionLoader.root,
                ConnectionLoader.Mode.REMOTE,
                state.remoteState.takeIf { it != "idle" && it.isNotBlank() },
            )
            binding.connectionLoader.root.bringToFront()
        } else {
            ConnectionLoader.hide(binding.connectionLoader.root)
        }

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
        binding.reconnectButton.isVisible = state.isPaired && !state.waitingForCode && !state.isSessionReady
        binding.disconnectButton.isVisible = state.isPaired && !state.isSessionReady

        updateTopBar(state)
    }

    private fun updateTopBar(state: RemoteViewModel.RemoteUiState) {
        val binding = _binding ?: return
        val topBar = binding.remoteTopBar
        val displayName = state.discoveredTvs
            .find { it.host == state.savedTvHost }
            ?.name
            ?.takeIf { it.isNotBlank() }
            ?: state.savedTvHost.takeIf { it.isNotBlank() }
            ?: getString(R.string.connected_tv_default)

        topBar.deviceNameLabel.text = when {
            state.isSessionReady -> displayName
            state.isPaired && state.savedTvHost.isNotBlank() -> displayName
            else -> getString(R.string.tap_to_connect)
        }
        val dotColor = if (state.isSessionReady) {
            R.color.connection_connected
        } else {
            R.color.connection_disconnected
        }
        topBar.deviceStatusDot.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), dotColor),
        )
    }

    override fun onDestroyView() {
        _binding?.connectionLoader?.root?.let { ConnectionLoader.hide(it) }
        tvAdapter = null
        _binding = null
        super.onDestroyView()
    }
}
