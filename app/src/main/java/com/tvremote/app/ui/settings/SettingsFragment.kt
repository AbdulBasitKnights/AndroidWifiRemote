package com.tvremote.app.ui.settings

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.tvremote.app.R
import com.tvremote.app.databinding.FragmentSettingsBinding
import com.tvremote.app.ui.main.MainActivity
import com.tvremote.app.ui.settings.adapter.DiscoveredTvAdapter
import com.tvremote.app.util.SafeRun
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private var _binding: FragmentSettingsBinding? = null
    private var tvAdapter: DiscoveredTvAdapter? = null

    private val viewModel: SettingsViewModel by viewModels {
        (requireActivity() as MainActivity).viewModelFactory()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)

        val adapter = DiscoveredTvAdapter { tv -> viewModel.onTvSelected(tv) }
        tvAdapter = adapter
        _binding?.discoveredTvsList?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }

        setupClickListeners()
        observeState()
        viewModel.startScan()
    }

    override fun onResume() {
        super.onResume()
        val state = viewModel.uiState.value
        if (state.discoveredTvs.isEmpty() && !state.isScanning) {
            viewModel.startScan()
        }
    }

    override fun onPause() {
        viewModel.stopScan()
        super.onPause()
    }

    private fun setupClickListeners() {
        val binding = _binding ?: return
        binding.scanButton.setOnClickListener { viewModel.startScan() }
        binding.manualIpToggle.setOnClickListener {
            binding.manualIpSection.isVisible = !binding.manualIpSection.isVisible
            binding.manualIpToggle.text = if (binding.manualIpSection.isVisible) {
                getString(R.string.hide_manual_ip)
            } else {
                getString(R.string.enter_ip_manually)
            }
        }
        binding.reconnectButton.setOnClickListener { viewModel.reconnect() }
        binding.disconnectButton.setOnClickListener { viewModel.disconnect() }
        binding.pairManualButton.setOnClickListener {
            val host = binding.hostInput.text?.toString().orEmpty().trim()
            viewModel.pairManual(host)
        }
        binding.restartPairingButton.setOnClickListener {
            val host = viewModel.uiState.value.pairingHost ?: viewModel.uiState.value.savedTvHost
            binding.codeInput.text?.clear()
            viewModel.restartPairing(host)
        }
        binding.completePairingButton.setOnClickListener { submitPairingCode() }
        binding.codeInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitPairingCode()
                true
            } else {
                false
            }
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
        binding.completePairingButton.text = getString(R.string.submitting_code)
        if (viewModel.submitPairingCode(code)) {
            Toast.makeText(requireContext(), R.string.code_sent, Toast.LENGTH_SHORT).show()
        } else {
            binding.completePairingButton.isEnabled = viewModel.isWaitingForCode()
            binding.completePairingButton.text = getString(R.string.complete_pairing)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state -> render(state) }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            SettingsViewModel.SettingsEvent.PairingStarted ->
                                Toast.makeText(requireContext(), R.string.pairing_started_toast, Toast.LENGTH_LONG).show()
                            SettingsViewModel.SettingsEvent.UseCompletePairing ->
                                Toast.makeText(requireContext(), R.string.use_complete_pairing, Toast.LENGTH_LONG).show()
                            SettingsViewModel.SettingsEvent.SelectTvFirst ->
                                Toast.makeText(requireContext(), R.string.select_tv_first, Toast.LENGTH_SHORT).show()
                            SettingsViewModel.SettingsEvent.InvalidIp ->
                                Toast.makeText(requireContext(), R.string.invalid_ip, Toast.LENGTH_LONG).show()
                            SettingsViewModel.SettingsEvent.Reconnecting ->
                                Toast.makeText(requireContext(), R.string.reconnecting_remote, Toast.LENGTH_SHORT).show()
                            SettingsViewModel.SettingsEvent.Disconnected ->
                                Toast.makeText(requireContext(), R.string.disconnected_from_tv, Toast.LENGTH_SHORT).show()
                            is SettingsViewModel.SettingsEvent.Error ->
                                Toast.makeText(requireContext(), event.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun render(state: SettingsUiState) {
        SafeRun.run(TAG) {
            val binding = _binding ?: return@run
            val adapter = tvAdapter ?: return@run

        binding.phoneIpLabel.text = state.phoneIp?.let {
            getString(R.string.phone_ip_label, it)
        } ?: getString(R.string.phone_ip_unknown)
        binding.pairingStateLabel.text = getString(R.string.pairing_state_label, state.pairingState)
        binding.remoteStateLabel.text = getString(R.string.remote_state_label, state.remoteState)

        binding.pairingCodeCard.isVisible = state.waitingForCode
        binding.completePairingButton.isEnabled = state.waitingForCode
        binding.completePairingButton.text = getString(R.string.complete_pairing)
        binding.restartPairingButton.isVisible = state.waitingForCode
        if (state.waitingForCode) binding.codeInput.requestFocus()

        binding.scanButton.isEnabled = !state.isScanning
        binding.scanButton.text = if (state.isScanning) {
            getString(R.string.scanning_tvs)
        } else {
            getString(R.string.scan_tvs)
        }

        adapter.submit(state.discoveredTvs)
        adapter.setSelectedHost(state.pairingHost ?: state.savedTvHost)
        adapter.setPairingHost(state.pairingHost)
        adapter.setPairedHost(if (state.isPaired) state.savedTvHost else null)

        binding.reconnectButton.isVisible = state.isPaired && !state.waitingForCode && !state.isSessionReady
        binding.disconnectButton.isVisible = state.isPaired && !state.waitingForCode
        binding.sessionStatusLabel.isVisible = false

        binding.discoveredTvsList.isVisible = state.discoveredTvs.isNotEmpty()
        binding.noTvsLabel.isVisible = state.discoveredTvs.isEmpty() && !state.isScanning

        binding.scanStatusLabel.text = when {
            state.isScanning -> getString(R.string.scanning_tvs)
            state.discoveredTvs.isEmpty() -> getString(R.string.discovered_tvs_none)
            else -> getString(R.string.discovered_tvs_count, state.discoveredTvs.size)
        }
        }
    }

    override fun onDestroyView() {
        tvAdapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAG = "SettingsFragment"
    }
}
