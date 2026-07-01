package com.tvremote.app.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.tvremote.app.R
import com.tvremote.app.TvRemoteApp
import com.tvremote.app.databinding.ActivityMainBinding
import com.tvremote.app.di.AppContainer
import com.tvremote.app.ui.cast.CastFragment
import com.tvremote.app.ui.common.AppViewModelFactory
import com.tvremote.app.ui.remote.RemoteFragment
import com.tvremote.app.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        container = (application as TvRemoteApp).container
        container.castRepository.initialize()

        if (savedInstanceState == null) {
            showFragment(RemoteFragment(), TAG_REMOTE)
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_remote -> {
                    showFragment(RemoteFragment(), TAG_REMOTE)
                    true
                }
                R.id.nav_cast -> {
                    showFragment(CastFragment(), TAG_CAST)
                    true
                }
                R.id.nav_settings -> {
                    showFragment(SettingsFragment(), TAG_SETTINGS)
                    true
                }
                else -> false
            }
        }
    }

    private fun showFragment(fragment: Fragment, tag: String) {
        val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (current?.javaClass == fragment.javaClass) return
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment, tag)
            .commit()
    }

    fun appContainer(): AppContainer = container

    fun viewModelFactory(): AppViewModelFactory = AppViewModelFactory(container)

    fun navigateToCast() {
        binding.bottomNav.selectedItemId = R.id.nav_cast
    }

    fun navigateToSettings() {
        binding.bottomNav.selectedItemId = R.id.nav_settings
    }

    companion object {
        private const val TAG_REMOTE = "remote"
        private const val TAG_CAST = "cast"
        private const val TAG_SETTINGS = "settings"
    }
}
