package com.tvremote.control.misc

interface Logger {
    fun debugLog(message: String)
    fun infoLog(message: String)
    fun errorLog(message: String)
}

class DefaultLogger : Logger {
    override fun debugLog(message: String) {
        android.util.Log.d(TAG, message)
    }

    override fun infoLog(message: String) {
        android.util.Log.i(TAG, message)
    }

    override fun errorLog(message: String) {
        android.util.Log.e(TAG, message)
    }

    companion object {
        private const val TAG = "AndroidTVRemote"
    }
}
