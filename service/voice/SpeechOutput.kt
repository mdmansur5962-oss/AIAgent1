package com.aiagent.assistant.service.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.aiagent.assistant.domain.settings.VoiceStyle
import com.aiagent.assistant.domain.voice.SpeakableText
import java.util.Locale

enum class SpeakResult { OK, NOT_READY, LANGUAGE_MISSING }

/**
 * Android TextToSpeech-এর মোড়ক: Markdown ছেঁটে পড়ে, ভাষা নিজে চেনে (বাংলা/ইংরেজি), লম্বা লেখা টুকরো করে,
 * ফোনে আসল পুরুষ/নারী voice থাকলে সেটা বাছে, নইলে সুর ও গতি বদলে কাছাকাছি কণ্ঠ বানায়।
 * অবশ্যই main thread থেকে তৈরি করতে হবে।
 */
class SpeechOutput(context: Context) {

    @Volatile
    private var ready = false

    @Volatile
    private var pendingDone: (() -> Unit)? = null

    @Volatile
    private var lastUtteranceId: String? = null

    private var tts: TextToSpeech? = null

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}

        override fun onDone(utteranceId: String?) {
            if (utteranceId != null && utteranceId == lastUtteranceId) finish()
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finish()

        override fun onError(utteranceId: String?, errorCode: Int) = finish()
    }

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) tts?.setOnUtteranceProgressListener(progress)
        }
    }

    private fun finish() {
        val callback = pendingDone
        pendingDone = null
        callback?.invoke()
    }

    val isReady: Boolean get() = ready

    /**
     * locale = null হলে লেখা দেখে ভাষা ঠিক করে। onDone: পুরো লেখা পড়া শেষ হলে (বন্ধ করলে ডাকা হয় না)।
     */
    fun speak(
        text: String,
        locale: Locale?,
        style: VoiceStyle,
        speed: Float,
        pitch: Float,
        onDone: (() -> Unit)? = null
    ): SpeakResult {
        val engine = tts ?: return SpeakResult.NOT_READY
        if (!ready) return SpeakResult.NOT_READY

        val clean = SpeakableText.from(text)
        if (clean.isBlank()) {
            onDone?.invoke()
            return SpeakResult.OK
        }
        val target = locale ?: SpeakableText.detectLocale(clean)
        if (!applyLanguage(engine, target)) return SpeakResult.LANGUAGE_MISSING

        val realVoice = pickVoice(engine, target, style)
        if (realVoice != null) engine.voice = realVoice
        // আসল পুরুষ/নারী voice পেলে preset সুর আর লাগে না
        val effectivePitch = if (realVoice != null) 1.0f else pitch
        engine.setSpeechRate(speed)
        engine.setPitch(effectivePitch)

        val maxLen = (TextToSpeech.getMaxSpeechInputLength() - 100).coerceIn(500, 3900)
        val chunks = SpeakableText.chunk(clean, maxLen)
        pendingDone = onDone
        val base = System.nanoTime()
        chunks.forEachIndexed { index, chunk ->
            val id = "myra_${base}_$index"
            if (index == chunks.lastIndex) lastUtteranceId = id
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(chunk, mode, null, id)
        }
        return SpeakResult.OK
    }

    /** চলমান পড়া থামায় (onDone ডাকা হবে না) */
    fun stop() {
        pendingDone = null
        tts?.stop()
    }

    fun shutdown() {
        pendingDone = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private fun applyLanguage(engine: TextToSpeech, locale: Locale): Boolean {
        fun ok(code: Int) = code != TextToSpeech.LANG_MISSING_DATA && code != TextToSpeech.LANG_NOT_SUPPORTED
        if (ok(engine.setLanguage(locale))) return true
        if (locale.language == "bn" && ok(engine.setLanguage(Locale.forLanguageTag("bn-IN")))) return true
        return false
    }

    /** ফোনে ইনস্টল করা voice-এর নামে male/female থাকলে সেটা বাছে (Google TTS-এ থাকে) */
    private fun pickVoice(engine: TextToSpeech, locale: Locale, style: VoiceStyle): Voice? {
        if (style != VoiceStyle.FEMALE && style != VoiceStyle.MALE) return null
        val voices: Set<Voice> = try {
            engine.voices ?: return null
        } catch (e: Exception) {
            return null
        }
        return voices
            .filter { it.locale.language == locale.language && !it.features.contains("notInstalled") }
            .firstOrNull { voice ->
                val name = voice.name.lowercase()
                if (style == VoiceStyle.FEMALE) {
                    name.contains("female")
                } else {
                    name.contains("male") && !name.contains("female")
                }
            }
    }
}
