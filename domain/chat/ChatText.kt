package com.aiagent.assistant.domain.chat

private val codeFenceRegex = Regex("```[\\s\\S]*?(```|$)")
private val markdownCharsRegex = Regex("[`*_#>~]")
private val whitespaceRegex = Regex("\\s+")

/** Markdown চিহ্ন ছেঁটে এক লাইনের ছোট ঝলক বানায় (তালিকায় দেখানোর জন্য) */
fun String.toPreview(max: Int = 90): String {
    val cleaned = this
        .replace(codeFenceRegex, " code ")
        .replace(markdownCharsRegex, "")
        .replace(whitespaceRegex, " ")
        .trim()
    return if (cleaned.length > max) cleaned.take(max).trimEnd() + "…" else cleaned
}
