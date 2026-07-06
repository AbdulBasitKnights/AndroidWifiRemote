package com.tvremote.app.ui.main

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.tvremote.app.R
import com.tvremote.app.TvRemoteApp
import com.tvremote.app.databinding.ActivityMainBinding
import com.tvremote.app.di.AppContainer
import com.tvremote.app.ui.cast.CastFragment
import com.tvremote.app.ui.channels.ChannelsFragment
import com.tvremote.app.ui.common.AppViewModelFactory
import com.tvremote.app.ui.common.BaseActivity
import com.tvremote.app.ui.mirror.MirrorFragment
import com.tvremote.app.ui.remote.RemoteFragment
import com.tvremote.app.ui.settings.SettingsFragment
import com.tvremote.app.util.SafeRun
import com.tvremote.app.util.ThemeHelper

class MainActivity : BaseActivity() {
    private var binding: ActivityMainBinding? = null
    private var container: AppContainer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applySavedTheme(this)
        super.onCreate(savedInstanceState)
        SafeRun.run(TAG) {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding!!.root)

            container = (application as? TvRemoteApp)?.container
            container?.castRepository?.initialize()

            if (savedInstanceState == null) {
                showFragment(RemoteFragment(), TAG_REMOTE)
            }

            binding?.bottomNav?.setOnItemSelectedListener { item ->
                SafeRun.run(TAG) {
                    when (item.itemId) {
                        R.id.nav_remote -> showFragment(RemoteFragment(), TAG_REMOTE)
                        R.id.nav_channel -> showFragment(ChannelsFragment(), TAG_CHANNEL)
                        R.id.nav_mirror -> showFragment(MirrorFragment(), TAG_MIRROR)
                        R.id.nav_cast -> showFragment(CastFragment(), TAG_CAST)
                        else -> return@run
                    }
                }
                true
            }
        }
    }

    private fun showFragment(fragment: Fragment, tag: String) {
        SafeRun.run(TAG) {
            if (isFinishing || isDestroyed) return@run
            val fm = supportFragmentManager
            if (fm.isStateSaved) return@run
            val current = fm.findFragmentById(R.id.fragmentContainer)
            if (current?.javaClass == fragment.javaClass) return@run
            if (fm.backStackEntryCount > 0) {
                fm.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            }
            fm.beginTransaction()
                .replace(R.id.fragmentContainer, fragment, tag)
                .commit()
        }
    }

    fun appContainer(): AppContainer {
        return container ?: (application as TvRemoteApp).container
    }

    fun viewModelFactory(): AppViewModelFactory = AppViewModelFactory(appContainer())

    fun navigateToCast() {
        SafeRun.runOnMain(TAG) {
            binding?.bottomNav?.selectedItemId = R.id.nav_cast
        }
    }

    fun openSettings() {
        SafeRun.run(TAG) {
            if (isFinishing || isDestroyed) return@run
            val fm = supportFragmentManager
            if (fm.isStateSaved) return@run
            fm.beginTransaction()
                .replace(R.id.fragmentContainer, SettingsFragment(), TAG_SETTINGS)
                .addToBackStack(TAG_SETTINGS)
                .commit()
        }
    }

    @Deprecated("Use openSettings()", ReplaceWith("openSettings()"))
    fun navigateToSettings() = openSettings()

    override fun onStart() {
        super.onStart()
        container?.tvRemoteRepository?.syncConnectionState()
        container?.tvRemoteRepository?.ensureConnected()
    }

    override fun onDestroy() {
        if (isFinishing) {
            container?.tvRemoteRepository?.disconnectForAppClose()
        }
        binding = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val TAG_REMOTE = "remote"
        private const val TAG_CHANNEL = "channel"
        private const val TAG_MIRROR = "mirror"
        private const val TAG_CAST = "cast"
        private const val TAG_SETTINGS = "settings"
    }
}
