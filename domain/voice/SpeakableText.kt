package com.aiagent.assistant.domain.voice

import java.util.Locale

/** চ্যাটের Markdown উত্তরকে জোরে পড়ার উপযোগী সাধারণ লেখায় বদলায় */
object SpeakableText {

    private val codeFence = Regex("```[\\s\\S]*?(```|$)")
    private val link = Regex("\\[([^\\]]*)\\]\\([^)]*\\)")
    private val symbols = Regex("[`*_#>~|]")
    private val bullet = Regex("(?m)^\\s*([-+•]|\\d{1,3}[.)])\\s+")
    private val emoji = Regex("[\\p{So}\\p{Cs}]")
    private val spaces = Regex("[ \\t]+")
    private val blankLines = Regex("\\n{2,}")

    fun from(markdown: String): String = markdown
        .replace(codeFence, " ")
        .replace(link, "$1")
        .replace(bullet, "")
        .replace(symbols, "")
        .replace(emoji, "")
        .replace(spaces, " ")
        .replace(blankLines, "\n")
        .trim()

    /** বাংলা অক্ষর বেশি থাকলে বাংলা, নইলে ইংরেজি */
    fun detectLocale(text: String): Locale {
        var letters = 0
        var bangla = 0
        for (c in text) {
            if (c.isLetter()) {
                letters++
                if (c.code in 0x0980..0x09FF) bangla++
            }
        }
        return if (letters > 0 && bangla.toFloat() / letters > 0.3f) {
            Locale.forLanguageTag("bn-BD")
        } else {
            Locale.US
        }
    }

    /** TTS-এর দৈর্ঘ্যসীমা এড়াতে বাক্যের সীমানায় টুকরো করে */
    fun chunk(text: String, maxLen: Int): List<String> {
        if (text.length <= maxLen) return listOf(text)
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        val sentences = text.split(Regex("(?<=[.!?।\\n])\\s*"))
        for (sentence in sentences) {
            if (sentence.isBlank()) continue
            if (current.length + sentence.length + 1 > maxLen && current.isNotEmpty()) {
                chunks.add(current.toString().trim())
                current.clear()
            }
            if (sentence.length > maxLen) {
                sentence.chunked(maxLen).forEach { part -> chunks.add(part.trim()) }
            } else {
                if (current.isNotEmpty()) current.append(' ')
                current.append(sentence)
            }
        }
        if (current.isNotBlank()) chunks.add(current.toString().trim())
        return chunks
    }
}
