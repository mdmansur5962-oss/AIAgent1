package com.aiagent.assistant.service.skills

import android.content.Context
import android.content.Intent
import com.aiagent.assistant.service.accessibility.AgentAccessibilityService

enum class ActionRisk {
    SAFE,

    /** ব্যবহারকারীর confirmation ছাড়া চলে না (কল, SMS পাঠানো ইত্যাদি) */
    CONFIRM
}

data class SkillParam(
    val name: String,
    val description: String,
    val required: Boolean = true
)

data class SkillOutcome(
    val success: Boolean,
    val message: String,
    val needsConfirmation: Boolean = false
)

/** একটি skill-action চালানোর পরিবেশ: context, আর skill-এর অ্যাপটা ফোনে ইনস্টল থাকলে তার package */
class SkillRuntime(
    val context: Context,
    val packageName: String?
) {
    /** Accessibility service থাকলে সেটা দিয়ে খোলে (ব্যাকগ্রাউন্ড থেকেও অ্যাপ খুলতে পারে) */
    fun launch(intent: Intent): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val launcher: Context = AgentAccessibilityService.instance ?: context
        return try {
            launcher.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * একটা অ্যাপে করা যায় এমন নির্ভরযোগ্য কাজ (deep link বা intent দিয়ে)। স্ক্রিনে ট্যাপ করার চেয়ে এগুলো অনেক বেশি
 * নির্ভরযোগ্য, তাই planner আগে এগুলো ব্যবহার করবে।
 */
data class SkillAction(
    val id: String,
    val title: String,
    /** AI planner-কে বোঝানোর জন্য ইংরেজি বিবরণ */
    val description: String,
    val params: List<SkillParam> = emptyList(),
    val risk: ActionRisk = ActionRisk.SAFE,
    /** অ্যাপটা ইনস্টল না থাকলে এই কাজ সম্ভব নয় */
    val requiresApp: Boolean = false,
    val run: (SkillRuntime, Map<String, String>) -> SkillOutcome
)

/** একটা অ্যাপ সম্পর্কে assistant-এর জ্ঞান */
data class AppSkill(
    val id: String,
    val name: String,
    /** ব্যবহারকারী যে নামে অ্যাপটা ডাকে (বাংলা নামসহ) */
    val aliases: List<String>,
    val packages: List<String>,
    val description: String,
    val loginRequired: Boolean,
    /** স্ক্রিনে কীভাবে কাজ করতে হয় তার টিপস (AI planner-এর জন্য) */
    val knowledge: String,
    /** ধারণা → বোতামের সম্ভাব্য লেখা (ইংরেজি ও বাংলা) */
    val hints: Map<String, List<String>>,
    val actions: List<SkillAction>,
    val builtIn: Boolean
) {
    fun labelsFor(concept: String): List<String> {
        val key = concept.trim().lowercase()
        return hints[key] ?: CommonHints.map[key].orEmpty()
    }
}

/** সব অ্যাপে প্রায় একই রকম বোতামের লেখা */
object CommonHints {
    val map: Map<String, List<String>> = mapOf(
        "search" to listOf("Search", "Search or type URL", "অনুসন্ধান", "খুঁজুন", "সার্চ"),
        "menu" to listOf("More options", "Menu", "More", "মেনু", "আরও"),
        "settings" to listOf("Settings", "সেটিংস"),
        "back" to listOf("Navigate up", "Back", "ফিরে যান"),
        "share" to listOf("Share", "শেয়ার"),
        "download" to listOf("Download", "ডাউনলোড"),
        "save" to listOf("Save", "সংরক্ষণ", "সেভ"),
        "done" to listOf("Done", "OK", "সম্পন্ন", "ঠিক আছে"),
        "next" to listOf("Next", "Continue", "পরবর্তী", "এগিয়ে যান"),
        "cancel" to listOf("Cancel", "বাতিল"),
        "add" to listOf("Add", "New", "Create", "যোগ করুন", "নতুন"),
        "send" to listOf("Send", "পাঠান"),
        "post" to listOf("Post", "Share now", "পোস্ট"),
        "delete" to listOf("Delete", "Remove", "মুছুন", "ডিলিট"),
        "play" to listOf("Play", "Pause", "চালান"),
        "install" to listOf("Install", "Update", "ইনস্টল", "আপডেট"),
        "export" to listOf("Export", "এক্সপোর্ট")
    )
}
