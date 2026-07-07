package com.tvremote.app.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException

private val Context.flowDataStore by preferencesDataStore(
    name = DATA_STORE_NAME,
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

object FlowPreferences {
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun <T> writeDataStoreValue(key: Preferences.Key<T>, value: T) {
        scope.launch {
            appContext.flowDataStore.edit { it[key] = value }
        }
    }

    fun <T> readDataStoreValue(key: Preferences.Key<T>, defaultValue: T, onCompleted: T.() -> Unit) {
        scope.launch {
            val value = readValue(key, defaultValue)
            onCompleted(value)
        }
    }

    suspend fun <T> readValue(key: Preferences.Key<T>, defaultValue: T): T {
        return appContext.flowDataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[key] ?: defaultValue }
            .first()
    }

    fun <T> readValueBlocking(key: Preferences.Key<T>, defaultValue: T): T {
        return runBlocking { readValue(key, defaultValue) }
    }
}
