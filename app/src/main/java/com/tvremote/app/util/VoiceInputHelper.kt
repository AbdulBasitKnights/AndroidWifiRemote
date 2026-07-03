package com.tvremote.app.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.tvremote.app.data.remote.RemoteTvManager
import java.net.URLEncoder
import java.util.Locale

class VoiceInputHelper(
    private val context: Context,
    private val remoteManager: RemoteTvManager,
) {
    private var speechRecognizer: SpeechRecognizer? = null
    var onListeningChanged: ((Boolean) -> Unit)? = null
    var onResult: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(languageCode: String = "en") {
        if (!isAvailable()) {
            onError?.invoke("Speech recognition not available")
            return
        }
        stopListening()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onListeningChanged?.invoke(true)
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    onListeningChanged?.invoke(false)
                }

                override fun onError(error: Int) {
                    onListeningChanged?.invoke(false)
                    onError?.invoke(mapError(error))
                }

                override fun onResults(results: Bundle?) {
                    onListeningChanged?.invoke(false)
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                    if (!text.isNullOrBlank()) {
                        sendVoiceQueryToTv(text)
                        onResult?.invoke(text)
                    } else {
                        onError?.invoke("No speech detected")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.forLanguageTag(languageCode))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        SafeRun.run(TAG) {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
            onListeningChanged?.invoke(false)
        }
    }

    private fun sendVoiceQueryToTv(query: String) {
        remoteManager.voiceSearch()
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        remoteManager.runSearchQuery(encoded)
    }

    private fun mapError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_CLIENT -> "Speech client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "Could not understand speech"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Speech server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
        else -> "Voice input failed"
    }

    companion object {
        private const val TAG = "VoiceInputHelper"
    }
}
