package com.tvremote.app.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

const val DATA_STORE_NAME = "tv-remote-flow"

val IS_ONBOARD = booleanPreferencesKey("onboarding-check")
val IS_LANGUAGE_SPLASH = booleanPreferencesKey("language-check")
val IS_LANGUAGE = stringPreferencesKey("language")
