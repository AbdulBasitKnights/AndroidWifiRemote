package com.tvremote.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tvremote.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        container = (application as TvRemoteApp).container
        container.castManager.initialize()

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
