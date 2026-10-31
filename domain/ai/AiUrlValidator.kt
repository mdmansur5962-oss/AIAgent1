package com.aiagent.assistant.domain.ai

import java.net.URI

/**
 * Base URL যাচাই। http:// শুধু নিজের নেটওয়ার্কের ঠিকানায় চলবে (যেমন PC-র Ollama),
 * ইন্টারনেটের সার্ভারে https:// লাগবে, নইলে API key খোলা অবস্থায় যেত।
 */
object AiUrlValidator {

    fun validate(rawUrl: String): String? {
        val url = rawUrl.trim()
        if (url.isEmpty()) return "Base URL is required · Base URL দিন"
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return "Invalid URL · URL ঠিক নয়"
        }
        val scheme = uri.scheme?.lowercase() ?: return "Start with https:// · https:// দিয়ে শুরু করুন"
        val host = uri.host ?: return "Invalid URL · URL ঠিক নয়"
        if (scheme != "https" && scheme != "http") {
            return "Use https:// · https:// ব্যবহার করুন"
        }
        if (scheme == "http" && !isLocalHost(host)) {
            return "Use https:// for internet servers. http:// works only for your local network. · " +
                "ইন্টারনেটের সার্ভারে https:// লাগবে, http:// শুধু নিজের নেটওয়ার্কে চলে"
        }
        return null
    }

    private fun isLocalHost(host: String): Boolean {
        val h = host.lowercase()
        if (h == "localhost" || h.endsWith(".local")) return true
        val parts = h.split('.')
        if (parts.size == 4 && parts.all { (it.toIntOrNull() ?: -1) in 0..255 }) {
            val a = parts[0].toInt()
            val b = parts[1].toInt()
            return a == 10 ||
                a == 127 ||
                (a == 192 && b == 168) ||
                (a == 172 && b in 16..31) ||
                (a == 100 && b in 64..127)
        }
        return false
    }
}
