package com.aiagent.assistant.service.skills

import android.content.Intent
import android.net.Uri
import com.aiagent.assistant.service.accessibility.SafetyPolicy
import java.text.SimpleDateFormat
import java.util.Locale

internal object SkillIntents {

    fun missing(name: String) = SkillOutcome(false, "Missing parameter: $name · $name দিন")

    fun launchApp(rt: SkillRuntime): SkillOutcome {
        val pkg = rt.packageName
            ?: return SkillOutcome(false, "App is not installed · অ্যাপ ইনস্টল করা নেই")
        val intent = rt.context.packageManager.getLaunchIntentForPackage(pkg)
            ?: return SkillOutcome(false, "$pkg can't be opened · খোলা যায় না")
        return if (rt.launch(intent)) SkillOutcome(true, "Opened $pkg") else SkillOutcome(false, "Could not open $pkg")
    }

    /** শুধু নিরাপদ http/https ঠিকানা; ব্যাংকিং সাইট বা অন্য ধরনের scheme (file:, javascript: ইত্যাদি) নয় */
    fun safeWebUrl(raw: String): String? {
        var url = raw.trim()
        if (url.isEmpty() || url.length > 2000 || url.any { it.isWhitespace() }) return null
        if (!url.contains("://")) {
            if (!url.contains('.')) return null
            url = "https://$url"
        }
        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return null
        val host = uri.host ?: return null
        if (SafetyPolicy.blockedHost(host) != null) return null
        return url
    }

    /** অ্যাপে খোলার চেষ্টা করে; না পারলে যেকোনো অ্যাপ/ব্রাউজারে */
    fun viewUrl(rt: SkillRuntime, url: String, preferApp: Boolean = true): SkillOutcome {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        var ok = false
        if (preferApp && rt.packageName != null) {
            intent.setPackage(rt.packageName)
            ok = rt.launch(intent)
            if (!ok) intent.setPackage(null)
        }
        if (!ok) ok = rt.launch(intent)
        return if (ok) SkillOutcome(true, "Opened") else SkillOutcome(false, "Could not open the link · লিংক খোলা যায়নি")
    }

    fun enc(value: String): String = Uri.encode(value.trim())

    fun parseTime(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        for (pattern in listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd")) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US)
                format.isLenient = false
                val date = format.parse(raw.trim())
                if (date != null) return date.time
            } catch (e: Exception) {
                // পরের ধরন চেষ্টা
            }
        }
        return null
    }
}
