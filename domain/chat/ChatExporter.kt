package com.aiagent.assistant.domain.chat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ChatExporter {

    fun toMarkdown(
        title: String,
        messages: List<ChatMessage>,
        appName: String,
        assistantName: String
    ): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val sb = StringBuilder()
        sb.append("# ").append(title.ifBlank { "Chat" }).append("\n\n")
        sb.append("_Exported from ").append(appName).append(" · ").append(fmt.format(Date())).append("_\n\n")
        messages.forEach { m ->
            val who = if (m.role == MessageRole.USER) "You" else assistantName
            sb.append("**").append(who).append("** (").append(fmt.format(Date(m.createdAt))).append(")\n\n")
            sb.append(m.content.trim()).append("\n\n---\n\n")
        }
        return sb.toString().trimEnd() + "\n"
    }

    fun toFileName(title: String): String {
        val cleaned = title
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}_-]+"), "_")
            .trim('_')
            .take(40)
        return (if (cleaned.isBlank()) "chat" else cleaned) + ".md"
    }
}
