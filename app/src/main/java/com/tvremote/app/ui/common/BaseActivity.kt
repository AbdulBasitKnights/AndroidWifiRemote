package com.tvremote.app.ui.common

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.adjust.sdk.Adjust
import com.tvremote.app.data.session.AppPreferences
import com.tvremote.app.util.EdgeToEdgeHelper
import java.util.Locale

abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val prefs = AppPreferences(newBase)
        val locale = Locale.forLanguageTag(prefs.selectedLanguage)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.configureSystemBars(this)
    }

    override fun onResume() {
        super.onResume()
        Adjust.onResume()
    }

    override fun onPause() {
        Adjust.onPause()
        super.onPause()
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        EdgeToEdgeHelper.applyInsetsToContentRoot(this)
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        view?.let { EdgeToEdgeHelper.applyContentInsets(it) }
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        view?.let { EdgeToEdgeHelper.applyContentInsets(it) }
    }
}
