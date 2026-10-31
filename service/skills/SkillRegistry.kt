package com.aiagent.assistant.service.skills

import android.content.Context
import com.aiagent.assistant.data.local.CustomSkillRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Universal App Knowledge Engine-এর তালিকা: ১৮টি built-in আর ব্যবহারকারীর নিজের skill */
@Singleton
class SkillRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    customRepository: CustomSkillRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val skills: StateFlow<List<AppSkill>> = customRepository.specs
        .map { specs -> BuiltInSkills.all + specs.map { it.toAppSkill() } }
        .stateIn(scope, SharingStarted.Eagerly, BuiltInSkills.all)

    fun byId(id: String): AppSkill? = skills.value.firstOrNull { it.id == id }

    fun forPackage(packageName: String): AppSkill? =
        skills.value.firstOrNull { skill -> skill.packages.any { it.equals(packageName, ignoreCase = true) } }

    /** "open youtube", "ইউটিউব খোলো" ধরনের লেখা থেকে অ্যাপ চেনে (সবচেয়ে লম্বা মিল আগে) */
    fun findByName(text: String): AppSkill? {
        val query = text.trim().lowercase()
        if (query.isEmpty()) return null
        var best: AppSkill? = null
        var bestLength = 0
        for (skill in skills.value) {
            val names = skill.aliases + skill.name + skill.id
            for (name in names) {
                val n = name.trim().lowercase()
                if (n.isEmpty()) continue
                val hit = query == n || query.contains(n)
                if (hit && n.length > bestLength) {
                    best = skill
                    bestLength = n.length
                }
            }
        }
        return best
    }

    /** AI planner-কে দেওয়ার জন্য সব skill ও তাদের কাজের সংক্ষিপ্ত তালিকা */
    fun catalogForPrompt(): String = buildString {
        appendLine("App skills (reliable direct actions; prefer them over tapping the screen):")
        for (skill in skills.value) {
            val installed = PackageUtil.firstInstalled(context, skill.packages) != null
            append("- ").append(skill.id).append(" (").append(skill.name).append(')')
            append(if (installed) " [installed]" else " [not installed]")
            if (skill.loginRequired) append(" [login needed]")
            appendLine()
            for (action in skill.actions) {
                append("    ").append(action.id).append('(')
                append(action.params.joinToString(", ") { it.name + if (it.required) "" else "?" })
                append(')')
                if (action.risk == ActionRisk.CONFIRM) append(" [needs user confirmation]")
                append(" - ").appendLine(action.description)
            }
        }
    }

    /** একটা skill-এর পুরো বিবরণ (টিপস ও বোতামের লেখা সহ), planner দরকারে চায় */
    fun describe(skill: AppSkill): String = buildString {
        appendLine("${skill.name}: ${skill.description}")
        if (skill.loginRequired) appendLine("The user must be logged in.")
        if (skill.knowledge.isNotBlank()) appendLine("How it works: ${skill.knowledge}")
        val concepts = skill.hints.keys + CommonHints.map.keys
        appendLine("Button labels:")
        for (concept in concepts.distinct()) {
            val labels = skill.labelsFor(concept)
            if (labels.isNotEmpty()) appendLine("  $concept: ${labels.joinToString(" | ")}")
        }
    }
}
