package com.tvremote.app

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.tvremote.app.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private var _binding: FragmentSettingsBinding? = null
    private val discoveredTvs = linkedMapOf<String, DiscoveredTv>()
    private var tvAdapter: DiscoveredTvAdapter? = null
    private var pairingHost: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)

        val container = (requireActivity() as MainActivity).appContainer()
        val adapter = DiscoveredTvAdapter { tv -> onTvSelected(container, tv) }
        tvAdapter = adapter
        bindingOrNull()?.apply {
            discoveredTvsList.layoutManager = LinearLayoutManager(requireContext())
            discoveredTvsList.adapter = adapter
        }

        pairingHost = container.savedTvHost.takeIf { container.remoteManager.isWaitingForPairingCode() }

        bindingOrNull()?.let {
            updatePhoneIpLabel(it)
            setupClickListeners(container)
            updatePairingCodeUi(it, container.remoteManager.isWaitingForPairingCode())
        }

        attachCallbacks(container)
        refreshDeviceList()
        startTvScan(container)
    }

    override fun onResume() {
        super.onResume()
        val container = (requireActivity() as MainActivity).appContainer()
        attachCallbacks(container)
        bindingOrNull()?.let {
            updatePairingCodeUi(it, container.remoteManager.isWaitingForPairingCode())
            if (discoveredTvs.isEmpty()) {
                startTvScan(container)
            } else {
                refreshDeviceList()
            }
        }
    }

    private fun attachCallbacks(container: AppContainer) {
        val remoteManager = container.remoteManager
        val tvDiscovery = container.tvDiscovery

        remoteManager.pairingStateChanged = { state ->
            ifBinding { pairingStateLabel.text = getString(R.string.pairing_state_label, state) }
        }

        remoteManager.onWaitingForCodeChanged = { waiting ->
            ifBinding {
                updatePairingCodeUi(this, waiting)
                if (!waiting) {
                    pairingHost = null
                    tvAdapter?.setPairingHost(null)
                }
            }
        }

        remoteManager.remoteStateChanged = { state ->
            ifBinding {
                remoteStateLabel.text = getString(R.string.remote_state_label, state)
                if (state.contains("Paired", ignoreCase = true)) {
                    pairingHost = null
                    tvAdapter?.setPairingHost(null)
                }
            }
        }

        tvDiscovery.onDeviceFound = { tv ->
            discoveredTvs[tv.host] = tv
            view?.post { ifBinding { refreshDeviceList() } }
        }

        tvDiscovery.onScanFinished = { devices ->
            devices.forEach { discoveredTvs[it.host] = it }
            view?.post {
                ifBinding {
                    scanButton.isEnabled = true
                    scanButton.text = getString(R.string.scan_tvs)
                    refreshDeviceList()
                    scanStatusLabel.text = if (devices.isEmpty()) {
                        getString(R.string.discovered_tvs_none)
                    } else {
                        getString(R.string.discovered_tvs_count, devices.size)
                    }
                }
            }
        }

        tvDiscovery.onError = { message ->
            if (isAdded) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupClickListeners(container: AppContainer) {
        val binding = bindingOrNull() ?: return
        val remoteManager = container.remoteManager

        binding.scanButton.setOnClickListener { startTvScan(container) }
        binding.manualIpToggle.setOnClickListener {
            binding.manualIpSection.isVisible = !binding.manualIpSection.isVisible
            binding.manualIpToggle.text = if (binding.manualIpSection.isVisible) {
                getString(R.string.hide_manual_ip)
            } else {
                getString(R.string.enter_ip_manually)
            }
        }
        binding.pairManualButton.setOnClickListener {
            val host = binding.hostInput.text?.toString().orEmpty().trim()
            if (!validateHost(host)) return@setOnClickListener
            startPairingForHost(container, host, DiscoveredTv("Manual TV", host, 6467))
        }
        binding.restartPairingButton.setOnClickListener {
            val host = pairingHost ?: container.savedTvHost
            if (host.isEmpty()) {
                Toast.makeText(requireContext(), R.string.select_tv_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.codeInput.text?.clear()
            remoteManager.restartPairing(host)
        }
        binding.completePairingButton.setOnClickListener { submitPairingCode(remoteManager) }
        binding.codeInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitPairingCode(remoteManager)
                true
            } else {
                false
            }
        }
    }

    private fun onTvSelected(container: AppContainer, tv: DiscoveredTv) {
        if (container.remoteManager.isWaitingForPairingCode()) {
            Toast.makeText(requireContext(), R.string.use_complete_pairing, Toast.LENGTH_LONG).show()
            return
        }
        startPairingForHost(container, tv.host, tv)
    }

    private fun startPairingForHost(container: AppContainer, host: String, tv: DiscoveredTv) {
        if (!validateHost(host)) return
        container.savedTvHost = host
        pairingHost = host
        tvAdapter?.setSelectedHost(host)
        tvAdapter?.setPairingHost(host)
        ifBinding {
            codeInput.text?.clear()
            scanStatusLabel.text = getString(R.string.discovered_tv_item, tv.name, tv.host)
        }
        container.remoteManager.pairOnly(host)
        Toast.makeText(requireContext(), R.string.pairing_started_toast, Toast.LENGTH_LONG).show()
    }

    private fun refreshDeviceList() {
        val binding = bindingOrNull() ?: return
        val adapter = tvAdapter ?: return
        val list = discoveredTvs.values.toList()
        adapter.submit(list)
        binding.discoveredTvsList.isVisible = list.isNotEmpty()
        binding.noTvsLabel.isVisible = list.isEmpty() && binding.scanButton.isEnabled
        val savedHost = (activity as? MainActivity)?.appContainer()?.savedTvHost.orEmpty()
        adapter.setSelectedHost(pairingHost ?: savedHost)
        adapter.setPairingHost(pairingHost)
    }

    private fun updatePairingCodeUi(binding: FragmentSettingsBinding, waiting: Boolean) {
        binding.pairingCodeCard.isVisible = waiting
        binding.completePairingButton.isEnabled = waiting
        binding.completePairingButton.text = getString(R.string.complete_pairing)
        binding.restartPairingButton.isVisible = waiting
        if (waiting) binding.codeInput.requestFocus()
    }

    private fun submitPairingCode(remoteManager: RemoteTvManager) {
        val binding = bindingOrNull() ?: return
        val code = binding.codeInput.text?.toString().orEmpty()
        if (code.length != 6) {
            Toast.makeText(requireContext(), R.string.enter_full_code, Toast.LENGTH_SHORT).show()
            return
        }
        binding.completePairingButton.isEnabled = false
        binding.completePairingButton.text = getString(R.string.submitting_code)
        if (remoteManager.submitPairingCode(code)) {
            Toast.makeText(requireContext(), R.string.code_sent, Toast.LENGTH_SHORT).show()
        } else {
            binding.completePairingButton.isEnabled = remoteManager.isWaitingForPairingCode()
            binding.completePairingButton.text = getString(R.string.complete_pairing)
        }
    }

    private fun updatePhoneIpLabel(binding: FragmentSettingsBinding) {
        val phoneIp = NetworkUtils.getWifiIpv4Address()
        binding.phoneIpLabel.text = if (phoneIp != null) {
            getString(R.string.phone_ip_label, phoneIp)
        } else {
            getString(R.string.phone_ip_unknown)
        }
    }

    private fun startTvScan(container: AppContainer) {
        discoveredTvs.clear()
        ifBinding {
            scanButton.isEnabled = false
            scanButton.text = getString(R.string.scanning_tvs)
            scanStatusLabel.text = getString(R.string.scanning_tvs)
            noTvsLabel.isVisible = false
        }
        tvAdapter?.submit(emptyList())
        container.tvDiscovery.startScan()
    }

    private fun validateHost(host: String): Boolean {
        if (host.isEmpty()) {
            Toast.makeText(requireContext(), R.string.select_tv_first, Toast.LENGTH_SHORT).show()
            return false
        }
        if (!NetworkUtils.isConnectableHost(host)) {
            Toast.makeText(requireContext(), R.string.invalid_ip, Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    private fun bindingOrNull(): FragmentSettingsBinding? = _binding

    private inline fun ifBinding(block: FragmentSettingsBinding.() -> Unit) {
        _binding?.block()
    }

    private fun detachCallbacks(container: AppContainer) {
        container.tvDiscovery.stopScan()
        container.tvDiscovery.onDeviceFound = null
        container.tvDiscovery.onScanFinished = null
        container.tvDiscovery.onError = null
        container.remoteManager.pairingStateChanged = null
        container.remoteManager.onWaitingForCodeChanged = null
        container.remoteManager.remoteStateChanged = null
    }

    override fun onDestroyView() {
        val container = (activity as? MainActivity)?.appContainer()
        if (container != null) {
            detachCallbacks(container)
        }
        tvAdapter = null
        _binding = null
        super.onDestroyView()
    }
}
