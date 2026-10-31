package com.aiagent.assistant.service.agent

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import com.aiagent.assistant.domain.settings.AppSettings
import com.aiagent.assistant.service.voice.SpeechInput

/**
 * ব্যাকগ্রাউন্ডে wake word শোনার চক্র (main thread-এ চলে)।
 * wake word শুনলে বাকি কথাটা কাজ হিসেবে দেয়; শুধু wake word বললে পরের কথাটা কমান্ড হিসেবে শোনে।
 * কিছু ফোনে প্রতিবার শোনা শুরুতে ছোট বিপ শব্দ হয়, আর ব্যাটারি বেশি খরচ হয়।
 */
class BackgroundWake(
    context: Context,
    private val settings: () -> AppSettings,
    private val onWake: () -> Unit,
    private val onCommand: (String) -> Unit,
    private val onFatal: (String) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var paused = false
    private var commandMode = false
    private var expectCancel = false
    private var consecutiveErrors = 0

    private val input = SpeechInput(
        context.applicationContext,
        object : SpeechInput.Callbacks {
            override fun onReady() {}
            override fun onPartial(text: String) {}
            override fun onFinal(text: String) = handleFinal(text)
            override fun onError(code: Int) = handleError(code)
            override fun onLevel(level: Float) {}
        }
    )

    fun start() {
        if (active) return
        if (!input.isAvailable()) {
            onFatal("Speech recognition is not available · ফোনে speech service নেই")
            return
        }
        active = true
        paused = false
        commandMode = false
        consecutiveErrors = 0
        listen()
    }

    fun stop() {
        active = false
        handler.removeCallbacksAndMessages(null)
        expectCancel = true
        input.cancel()
        input.destroy()
    }

    /** উত্তর তৈরি বা বলার সময় মাইক বন্ধ রাখে (নিজের কণ্ঠ যাতে না শোনে) */
    fun pause() {
        if (!active || paused) return
        paused = true
        handler.removeCallbacksAndMessages(null)
        expectCancel = true
        input.cancel()
    }

    fun resume() {
        if (!active || !paused) return
        paused = false
        commandMode = false
        schedule(400)
    }

    private fun schedule(delayMs: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ listen() }, delayMs)
    }

    private fun listen() {
        if (!active || paused) return
        try {
            input.start(settings().voiceLanguage)
        } catch (e: Exception) {
            active = false
            onFatal("Could not start listening · শোনা শুরু করা যায়নি")
        }
    }

    private fun handleFinal(text: String) {
        consecutiveErrors = 0
        val clean = text.trim()
        if (clean.isEmpty()) {
            commandMode = false
            schedule(300)
            return
        }
        if (commandMode) {
            commandMode = false
            onCommand(clean)
            return
        }
        val wake = settings().wakeWord.trim()
        val index = if (wake.isEmpty()) -1 else clean.indexOf(wake, ignoreCase = true)
        if (index < 0) {
            schedule(250)
            return
        }
        onWake()
        val rest = clean.substring(index + wake.length).trim().trimStart(',', '.', '!', ' ')
        if (rest.length >= 2) {
            onCommand(rest)
        } else {
            commandMode = true
            schedule(250)
        }
    }

    private fun handleError(code: Int) {
        when (code) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                commandMode = false
                schedule(400)
            }
            SpeechRecognizer.ERROR_CLIENT -> {
                if (expectCancel) {
                    expectCancel = false
                } else {
                    backoff()
                }
            }
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                input.reset()
                schedule(900)
            }
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                active = false
                onFatal("Microphone permission is missing · মাইকের অনুমতি নেই")
            }
            else -> backoff()
        }
    }

    /** বারবার ত্রুটি হলে ধীরে ধীরে অপেক্ষা বাড়ায়, অনেকবার হলে থামে */
    private fun backoff() {
        consecutiveErrors++
        if (consecutiveErrors >= 8) {
            active = false
            onFatal("Wake word stopped after repeated errors · বারবার ত্রুটির কারণে wake word থেমে গেছে")
        } else {
            input.reset()
            schedule((consecutiveErrors * 2000L).coerceAtMost(10_000L))
        }
    }
}
