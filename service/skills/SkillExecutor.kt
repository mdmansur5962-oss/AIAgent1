package com.aiagent.assistant.service.skills

import android.content.Context
import com.aiagent.assistant.service.accessibility.AccessibilityEngine
import com.aiagent.assistant.service.accessibility.SafetyPolicy
import com.aiagent.assistant.service.accessibility.UiAction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Skill-এর কাজ চালায়। প্রতিবার: সুরক্ষিত অ্যাপ কিনা, দরকারি তথ্য আছে কিনা, ঝুঁকির কাজে confirmation আছে কিনা,
 * জরুরি থামানো চালু কিনা, সব যাচাই করে তবে চলে।
 */
@Singleton
class SkillExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: SkillRegistry,
    private val engine: AccessibilityEngine
) {
    suspend fun run(
        skillId: String,
        actionId: String,
        params: Map<String, String>,
        confirmed: Boolean = false
    ): SkillOutcome {
        val skill = registry.byId(skillId) ?: return SkillOutcome(false, "Unknown skill: $skillId")
        val action = skill.actions.firstOrNull { it.id == actionId }
            ?: return SkillOutcome(false, "${skill.name} has no action \"$actionId\"")

        if (engine.emergencyStop.value) {
            return SkillOutcome(false, "Emergency stop is ON · জরুরি থামানো চালু আছে")
        }

        // সুরক্ষিত অ্যাপের skill (ব্যাংকিং ইত্যাদি) কখনো চলে না
        for (pkg in skill.packages) {
            SafetyPolicy.blockedPackage(pkg)?.let { return SkillOutcome(false, it.reason) }
        }

        val missing = action.params.firstOrNull { it.required && params[it.name].isNullOrBlank() }
        if (missing != null) return SkillIntents.missing(missing.name)

        if (action.risk == ActionRisk.CONFIRM && !confirmed) {
            return SkillOutcome(
                success = false,
                message = "Needs your confirmation: ${action.title} · আপনার নিশ্চিতকরণ লাগবে",
                needsConfirmation = true
            )
        }

        val installed = PackageUtil.firstInstalled(context, skill.packages)
        if (installed == null && action.requiresApp) {
            return SkillOutcome(false, "${skill.name} is not installed · অ্যাপটা ইনস্টল করা নেই")
        }

        return withContext(Dispatchers.Main) {
            try {
                action.run(SkillRuntime(context, installed), params)
            } catch (e: Exception) {
                SkillOutcome(false, "Action failed · কাজ ব্যর্থ: ${e.message.orEmpty().take(80)}")
            }
        }
    }

    /**
     * স্ক্রিনের বোতাম "ধারণা" দিয়ে চাপে (যেমন "export"): skill-এ লেখা সম্ভাব্য লেখাগুলো একে একে চেষ্টা করে।
     * ঝুঁকির বোতাম হলে confirmation চায়।
     */
    suspend fun clickConcept(skillId: String, concept: String, confirmed: Boolean = false): SkillOutcome {
        val skill = registry.byId(skillId) ?: return SkillOutcome(false, "Unknown skill: $skillId")
        val labels = skill.labelsFor(concept)
        if (labels.isEmpty()) return SkillOutcome(false, "No button labels known for \"$concept\"")
        for (label in labels) {
            val result = engine.perform(UiAction.ClickText(label), confirmed)
            if (result.success) return SkillOutcome(true, "Clicked \"$label\"")
            if (result.needsConfirmation) return SkillOutcome(false, result.message, needsConfirmation = true)
            if (result.blockedBySafety) return SkillOutcome(false, result.message)
        }
        return SkillOutcome(false, "No button for \"$concept\" found on this screen · স্ক্রিনে এই বোতাম পাওয়া যায়নি")
    }
}
