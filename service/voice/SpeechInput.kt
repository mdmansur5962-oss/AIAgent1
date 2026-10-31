package com.aiagent.assistant.service.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.aiagent.assistant.domain.settings.VoiceLanguage
import java.util.Locale

/**
 * Android SpeechRecognizer-এর মোড়ক। অবশ্যই main thread থেকে ব্যবহার করতে হবে।
 * Auto ভাষায় Android 13+ এ ভাষা চেনার অনুরোধ (বাংলা/ইংরেজি) পাঠানো হয়; সব ফোনে এটা কাজ করে না,
 * তাই দরকারে English বা বাংলা আলাদা করে বেছে নেওয়া যায়।
 */
class SpeechInput(
    private val context: Context,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onReady()
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(code: Int)
        fun onLevel(level: Float)
    }

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(language: VoiceLanguage) {
        val r = recognizer ?: createRecognizer().also { recognizer = it }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            when (language) {
                VoiceLanguage.ENGLISH -> putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                VoiceLanguage.BANGLA -> putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                VoiceLanguage.AUTO -> {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    if (Build.VERSION.SDK_INT >= 33) {
                        putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                        putStringArrayListExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                            arrayListOf("bn-BD", "en-US")
                        )
                    }
                }
            }
        }
        r.startListening(intent)
    }

    /** শোনা বন্ধ করে, কোনো ফল ছাড়া */
    fun cancel() {
        recognizer?.cancel()
    }

    /** ত্রুটির পর নতুন করে শুরু করার জন্য */
    fun reset() {
        recognizer?.destroy()
        recognizer = null
    }

    fun destroy() = reset()

    private fun createRecognizer(): SpeechRecognizer {
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        r.setRecognitionListener(listener)
        return r
    }

    private fun firstResult(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = callbacks.onReady()

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {
            callbacks.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) = callbacks.onError(error)

        override fun onResults(results: Bundle?) = callbacks.onFinal(firstResult(results))

        override fun onPartialResults(partialResults: Bundle?) {
            val text = firstResult(partialResults)
            if (text.isNotEmpty()) callbacks.onPartial(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
