package com.aiagent.assistant.service.skills

import android.net.Uri
import com.aiagent.assistant.service.accessibility.SafetyPolicy
import kotlinx.serialization.Serializable

@Serializable
data class CustomLink(
    val id: String,
    val title: String,
    val description: String = "",
    /** যেমন https://example.com/search?q={query} — {নাম} জায়গায় মান বসে */
    val template: String
)

/** ব্যবহারকারীর নিজের বানানো skill (JSON হিসেবে ফোনে জমা থাকে) */
@Serializable
data class CustomSkillSpec(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val packages: List<String> = emptyList(),
    val description: String = "",
    val knowledge: String = "",
    val hints: Map<String, List<String>> = emptyMap(),
    val links: List<CustomLink> = emptyList()
)

object CustomLinkRules {

    private val placeholder = Regex("\\{(\\w+)\\}")

    /** call/SMS/mail আর file-ধরনের scheme বন্ধ: কল ও বার্তার confirmation নিয়ম এড়িয়ে যাওয়া ঠেকাতে */
    private val blockedSchemes = setOf(
        "tel", "sms", "smsto", "mms", "mmsto", "mailto", "file", "content", "javascript", "intent", "data", "market"
    )

    fun placeholders(template: String): List<String> =
        placeholder.findAll(template).map { it.groupValues[1] }.distinct().toList()

    fun fill(template: String, values: Map<String, String>): String =
        placeholder.replace(template) { match -> Uri.encode(values[match.groupValues[1]].orEmpty().trim()) }

    /** ঠিক থাকলে null, নইলে কারণ */
    fun validate(template: String): String? {
        val t = template.trim()
        if (t.isEmpty()) return "Link template is empty · লিংকের ছাঁচ খালি"
        if (t.length > 500) return "Link template is too long"
        val probe = placeholder.replace(t, "x")
        val uri = try {
            Uri.parse(probe)
        } catch (e: Exception) {
            return "Invalid link · লিংক ঠিক নয়"
        }
        val scheme = uri.scheme?.lowercase() ?: return "Link must start with https:// or app-scheme:// · https:// দিয়ে শুরু করুন"
        if (scheme in blockedSchemes) return "This kind of link ($scheme:) is not allowed · এই ধরনের লিংক চলে না"
        if (!Regex("^[a-z][a-z0-9+.-]*$").matches(scheme)) return "Invalid link scheme"
        if (scheme == "http" || scheme == "https") {
            val host = uri.host ?: return "Link needs a website name · ওয়েবসাইটের নাম লাগবে"
            SafetyPolicy.blockedHost(host)?.let { return it.reason }
        }
        return null
    }
}

fun CustomSkillSpec.toAppSkill(): AppSkill {
    val open = SkillAction(
        id = "open",
        title = "Open $name",
        description = "Open the $name app.",
        requiresApp = true,
        run = { rt, _ -> SkillIntents.launchApp(rt) }
    )
    val linkActions = links.map { link ->
        SkillAction(
            id = link.id,
            title = link.title,
            description = link.description.ifBlank { link.title },
            params = CustomLinkRules.placeholders(link.template).map { SkillParam(it, "Value for $it") },
            run = { rt, values ->
                val problem = CustomLinkRules.validate(link.template)
                if (problem != null) {
                    SkillOutcome(false, problem)
                } else {
                    SkillIntents.viewUrl(rt, CustomLinkRules.fill(link.template, values))
                }
            }
        )
    }
    return AppSkill(
        id = id,
        name = name,
        aliases = aliases,
        packages = packages,
        description = description.ifBlank { "Custom skill" },
        loginRequired = false,
        knowledge = knowledge,
        hints = hints,
        actions = listOf(open) + linkActions,
        builtIn = false
    )
}
