package com.tvremote.app.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.tvremote.app.R
import com.tvremote.app.data.session.AppPreferences
import com.tvremote.app.databinding.FragmentSettingsBinding
import com.tvremote.app.ui.language.LanguageActivity
import com.tvremote.app.util.ThemeHelper

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private var _binding: FragmentSettingsBinding? = null

    private val languageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            requireActivity().recreate()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)
        setupTheme()
        setupRows()
    }

    private fun setupTheme() {
        val binding = _binding ?: return
        val prefs = AppPreferences(requireContext())
        when (prefs.themeMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> {
                binding.themeToggle.check(R.id.themeLight)
                binding.themeSubtitle.text = getString(R.string.theme_light)
            }
            AppCompatDelegate.MODE_NIGHT_YES -> {
                binding.themeToggle.check(R.id.themeDark)
                binding.themeSubtitle.text = getString(R.string.theme_dark)
            }
            else -> {
                binding.themeToggle.check(R.id.themeSystem)
                binding.themeSubtitle.text = getString(R.string.theme_system)
            }
        }
        binding.themeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.themeLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.themeDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            ThemeHelper.setThemeMode(requireContext(), mode)
            requireActivity().recreate()
        }
        binding.languageButton.setOnClickListener {
            languageLauncher.launch(
                Intent(requireContext(), LanguageActivity::class.java)
                    .putExtra(LanguageActivity.EXTRA_FROM_SETTINGS, true),
            )
        }
        binding.versionLabel.text = getString(R.string.app_version, "1.0")
    }

    private fun setupRows() {
        val binding = _binding ?: return
        val toast = { msg: String ->
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
        binding.howToConnectRow.setOnClickListener {
            toast(getString(R.string.pairing_hint))
        }
        binding.cantFindRow.setOnClickListener { toast(getString(R.string.cant_find_remote_sub)) }
        binding.feedbackRow.setOnClickListener { toast(getString(R.string.feedback)) }
        binding.rateRow.setOnClickListener { toast(getString(R.string.rate_us_sub)) }
        binding.shareRow.setOnClickListener {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, getString(R.string.app_name))
                    },
                    getString(R.string.share_app),
                ),
            )
        }
        binding.privacyRow.setOnClickListener { toast(getString(R.string.privacy_policy_sub)) }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
