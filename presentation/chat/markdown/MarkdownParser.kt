package com.aiagent.assistant.presentation.chat.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String, val indent: Int) : MdBlock
    data class Numbered(val number: String, val text: String, val indent: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
    data object Rule : MdBlock
}

/**
 * ছোট নিজস্ব Markdown parser (বাইরের library ছাড়া)।
 * সমর্থিত: # শিরোনাম, **bold**, *italic*, ~~strike~~, `code`, [link](url), তালিকা (- ও 1.),
 * > quote, ``` code block ```, --- রেখা। (টেবিল সাধারণ লেখা হিসেবে দেখায়।)
 */
object MarkdownParser {

    private val headingRe = Regex("^(#{1,6})\\s+(.*)$")
    private val bulletRe = Regex("^(\\s*)[-*+•]\\s+(.*)$")
    private val numberedRe = Regex("^(\\s*)(\\d{1,3})[.)]\\s+(.*)$")
    private val quoteRe = Regex("^>\\s?(.*)$")
    private val ruleRe = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")

    fun parseBlocks(markdown: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val lines = markdown.replace("\r\n", "\n").split("\n")
        val para = StringBuilder()

        fun flushParagraph() {
            if (para.isNotEmpty()) {
                blocks.add(MdBlock.Paragraph(para.toString().trimEnd()))
                para.clear()
            }
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()

            if (trimmed.startsWith("```")) {
                flushParagraph()
                val language = trimmed.removePrefix("```").trim()
                val code = StringBuilder()
                var first = true
                i++
                // শেষ fence না এলেও (streaming চলাকালীন) বাকি অংশ code হিসেবে দেখাবে
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    if (!first) code.append('\n')
                    code.append(lines[i])
                    first = false
                    i++
                }
                blocks.add(MdBlock.Code(language, code.toString()))
                i++
                continue
            }

            val heading = headingRe.matchEntire(line)
            val bullet = bulletRe.matchEntire(line)
            val numbered = numberedRe.matchEntire(line)
            val quote = quoteRe.matchEntire(line)

            when {
                line.isBlank() -> flushParagraph()
                ruleRe.matches(line) -> {
                    flushParagraph()
                    blocks.add(MdBlock.Rule)
                }
                heading != null -> {
                    flushParagraph()
                    blocks.add(MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim()))
                }
                bullet != null -> {
                    flushParagraph()
                    blocks.add(MdBlock.Bullet(bullet.groupValues[2], bullet.groupValues[1].length / 2))
                }
                numbered != null -> {
                    flushParagraph()
                    blocks.add(
                        MdBlock.Numbered(
                            number = numbered.groupValues[2],
                            text = numbered.groupValues[3],
                            indent = numbered.groupValues[1].length / 2
                        )
                    )
                }
                quote != null -> {
                    flushParagraph()
                    val last = blocks.lastOrNull()
                    val text = quote.groupValues[1]
                    if (last is MdBlock.Quote) {
                        blocks[blocks.lastIndex] = last.copy(text = last.text + "\n" + text)
                    } else {
                        blocks.add(MdBlock.Quote(text))
                    }
                }
                else -> {
                    if (para.isNotEmpty()) para.append('\n')
                    para.append(line)
                }
            }
            i++
        }
        flushParagraph()
        return blocks
    }

    fun parseInline(text: String, codeStyle: SpanStyle, linkStyle: SpanStyle): AnnotatedString =
        buildAnnotatedString { appendInline(text, codeStyle, linkStyle) }

    private fun AnnotatedString.Builder.appendInline(text: String, codeStyle: SpanStyle, linkStyle: SpanStyle) {
        var i = 0
        val n = text.length
        while (i < n) {
            val c = text[i]
            when {
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        withStyle(codeStyle) { append(text.substring(i + 1, end)) }
                        i = end + 1
                    } else {
                        append(c)
                        i++
                    }
                }
                text.startsWith("**", i) || text.startsWith("__", i) -> {
                    val marker = text.substring(i, i + 2)
                    val end = text.indexOf(marker, i + 2)
                    if (end > i + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            appendInline(text.substring(i + 2, end), codeStyle, linkStyle)
                        }
                        i = end + 2
                    } else {
                        append(c)
                        i++
                    }
                }
                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end > i + 2) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            appendInline(text.substring(i + 2, end), codeStyle, linkStyle)
                        }
                        i = end + 2
                    } else {
                        append(c)
                        i++
                    }
                }
                (c == '*' || c == '_') && isItalicStart(text, i) -> {
                    val end = findItalicEnd(text, i + 1, c)
                    if (end > i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            appendInline(text.substring(i + 1, end), codeStyle, linkStyle)
                        }
                        i = end + 1
                    } else {
                        append(c)
                        i++
                    }
                }
                c == '[' -> {
                    val close = text.indexOf("](", i + 1)
                    val paren = if (close > 0) text.indexOf(')', close + 2) else -1
                    if (close > i && paren > close) {
                        withStyle(linkStyle) { append(text.substring(i + 1, close)) }
                        i = paren + 1
                    } else {
                        append(c)
                        i++
                    }
                }
                else -> {
                    append(c)
                    i++
                }
            }
        }
    }

    private fun isItalicStart(text: String, i: Int): Boolean {
        if (i + 1 >= text.length) return false
        if (text[i + 1].isWhitespace()) return false
        if (text[i] == '_' && i > 0 && text[i - 1].isLetterOrDigit()) return false
        return true
    }

    private fun findItalicEnd(text: String, from: Int, marker: Char): Int {
        var j = from
        while (j < text.length) {
            if (text[j] == marker && !text[j - 1].isWhitespace()) {
                val nextOk = marker == '*' || j + 1 >= text.length || !text[j + 1].isLetterOrDigit()
                if (nextOk) return j
            }
            j++
        }
        return -1
    }
}
