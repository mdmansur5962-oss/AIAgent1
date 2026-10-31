package com.aiagent.assistant.service.accessibility

import android.content.Context
import com.aiagent.assistant.data.local.SettingsRepository
import com.aiagent.assistant.domain.ai.AiChatService
import com.aiagent.assistant.domain.ai.AiException
import com.aiagent.assistant.domain.ai.AiImage
import com.aiagent.assistant.domain.ai.AiRole
import com.aiagent.assistant.domain.ai.AiTurn
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

sealed interface ScreenRead {
    data object NotEnabled : ScreenRead
    data object NoWindow : ScreenRead
    data class Ok(val snapshot: UiSnapshot) : ScreenRead
}

data class OcrResult(val ok: Boolean, val message: String, val lines: List<OcrLine> = emptyList())

data class AnalysisResult(val ok: Boolean, val text: String)

/**
 * Accessibility Engine: স্ক্রিন পড়া, কাজ করা, OCR, আর ছবি দিয়ে AI-র স্ক্রিন বোঝা, সব এক জায়গায়।
 * Task Planner (Step 13) এবং App Skills (Step 12) এটাই ব্যবহার করবে।
 */
@Singleton
class AccessibilityEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val aiChatService: AiChatService
) {
    private val _emergencyStop = MutableStateFlow(false)

    /** জরুরি থামানো চালু থাকলে কোনো কাজই হয় না */
    val emergencyStop: StateFlow<Boolean> = _emergencyStop.asStateFlow()

    @Volatile
    private var lastActionAt = 0L

    val isEnabled: Boolean get() = AgentAccessibilityService.instance != null

    fun setEmergencyStop(value: Boolean) {
        _emergencyStop.value = value
    }

    // ───────────── read ─────────────

    suspend fun readScreen(): ScreenRead = withContext(Dispatchers.Default) {
        val service = AgentAccessibilityService.instance ?: return@withContext ScreenRead.NotEnabled
        val hint = ScreenContext.foreground.value.activity
        val snapshot = ScreenReader.read(service, context.packageName, hint)
            ?: return@withContext ScreenRead.NoWindow
        ScreenContext.record(snapshot)
        ScreenRead.Ok(snapshot)
    }

    /** AI-কে দেওয়ার জন্য স্ক্রিনের বর্ণনা (নতুন করে পড়ে) */
    suspend fun promptContext(): String {
        val snapshot = (readScreen() as? ScreenRead.Ok)?.snapshot
        return ScreenContext.promptContext(snapshot)
    }

    // ───────────── act ─────────────

    /**
     * একটা কাজ করে। ঝুঁকির কাজ হলে ActionResult.needsConfirmation = true আসে; ব্যবহারকারী নিশ্চিত করলে
     * confirmed = true দিয়ে আবার ডাকুন। প্রতিটি কাজের পর ফোনের গতি অনুযায়ী অপেক্ষা করে।
     */
    suspend fun perform(action: UiAction, confirmed: Boolean = false): ActionResult {
        if (_emergencyStop.value) {
            return ActionResult(false, "Emergency stop is ON · জরুরি থামানো চালু আছে", blockedBySafety = true)
        }
        val service = AgentAccessibilityService.instance
            ?: return ActionResult(false, NOT_ENABLED)

        // কাজের মাঝে সামান্য ফাঁক, যাতে অ্যাপ তাল মেলাতে পারে
        val sinceLast = System.currentTimeMillis() - lastActionAt
        if (sinceLast < MIN_GAP_MS) delay(MIN_GAP_MS - sinceLast)

        val needsSnapshot = action !is UiAction.Back &&
            action !is UiAction.Home &&
            action !is UiAction.Recents &&
            action !is UiAction.Wait &&
            action !is UiAction.OpenNotifications
        val snapshot = if (needsSnapshot) {
            withContext(Dispatchers.Default) {
                ScreenReader.read(service, context.packageName, ScreenContext.foreground.value.activity)
            }
        } else {
            null
        }
        if (needsSnapshot && snapshot == null && action !is UiAction.OpenApp && action !is UiAction.ClickAt && action !is UiAction.Swipe) {
            return ActionResult(false, "Cannot read the screen · স্ক্রিন পড়া যাচ্ছে না")
        }
        if (snapshot != null) ScreenContext.record(snapshot)

        val result = try {
            ActionExecutor.execute(service, action, snapshot, confirmed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ActionResult(false, "Action failed · কাজ ব্যর্থ: ${e.message.orEmpty().take(80)}")
        }
        ScreenContext.recordAction(describe(action), result)
        lastActionAt = System.currentTimeMillis()

        val speed = settingsRepository.settings.first().actionSpeed.delayMs
        if (result.success && action !is UiAction.Wait) delay(speed)
        return result
    }

    private fun describe(action: UiAction): String = when (action) {
        is UiAction.ClickElement -> "Click #${action.elementId}"
        is UiAction.ClickText -> "Click \"${action.text.take(30)}\""
        is UiAction.ClickAt -> "Tap (${action.x},${action.y})"
        is UiAction.LongClickElement -> "Long-press #${action.elementId}"
        is UiAction.TypeText -> "Type ${action.text.length} chars"
        is UiAction.Scroll -> "Scroll ${action.direction.name.lowercase()}"
        is UiAction.Swipe -> "Swipe"
        UiAction.Back -> "Back"
        UiAction.Home -> "Home"
        UiAction.Recents -> "Recents"
        UiAction.OpenNotifications -> "Open notifications"
        is UiAction.Wait -> "Wait ${action.ms} ms"
        is UiAction.OpenApp -> "Open ${action.nameOrPackage}"
    }

    // ───────────── OCR + vision ─────────────

    /** স্ক্রিনের ছবি তুলে ফোনের ভেতরেই লেখা পড়ে (শুধু ইংরেজি/ল্যাটিন)। সুরক্ষিত স্ক্রিনে করে না। */
    suspend fun ocr(): OcrResult {
        val service = AgentAccessibilityService.instance ?: return OcrResult(false, NOT_ENABLED)
        if (!ScreenCapture.isSupported) {
            return OcrResult(false, "Needs Android 11 or newer · Android 11+ লাগবে")
        }
        val guard = guardForCapture(service)
        if (guard != null) return OcrResult(false, guard)
        val bitmap = ScreenCapture.capture(service)
            ?: return OcrResult(false, "Could not take a screenshot, try again in a second · ছবি তোলা যায়নি, একটু পরে চেষ্টা করুন")
        return try {
            val lines = ScreenCapture.recognize(bitmap)
            val redacted = lines.map { it.copy(text = SafetyPolicy.redactText(it.text, otpContext = true)) }
            OcrResult(true, "${redacted.size} lines", redacted)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OcrResult(false, "OCR failed · লেখা পড়া যায়নি")
        }
    }

    /**
     * স্ক্রিনের ছবি তোমার বেছে নেওয়া AI provider-কে পাঠিয়ে প্রশ্ন করে (vision-সমর্থিত model লাগে)।
     * ছবি শুধু এই অনুরোধেই যায়, কোথাও জমানো হয় না। সুরক্ষিত স্ক্রিনে কখনো পাঠানো হয় না।
     */
    suspend fun analyzeScreen(question: String): AnalysisResult {
        val service = AgentAccessibilityService.instance ?: return AnalysisResult(false, NOT_ENABLED)
        if (!ScreenCapture.isSupported) {
            return AnalysisResult(false, "Needs Android 11 or newer · Android 11+ লাগবে")
        }
        val guard = guardForCapture(service)
        if (guard != null) return AnalysisResult(false, guard)
        val bitmap = ScreenCapture.capture(service)
            ?: return AnalysisResult(false, "Could not take a screenshot, try again in a second · ছবি তোলা যায়নি, একটু পরে চেষ্টা করুন")
        return try {
            val small = ScreenCapture.downscale(bitmap, 1280)
            val image = AiImage("image/jpeg", ScreenCapture.toJpegBase64(small))
            val text = aiChatService.complete(
                listOf(AiTurn(AiRole.USER, question, listOf(image))),
                VISION_SYSTEM_PROMPT
            )
            AnalysisResult(true, text.trim())
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiException) {
            AnalysisResult(false, e.message.orEmpty())
        } catch (e: Exception) {
            AnalysisResult(false, "Could not analyze the screen · স্ক্রিন বোঝা যায়নি")
        }
    }

    /** ছবি তোলার আগে নিরাপত্তা যাচাই: সুরক্ষিত বা স্পর্শকাতর হলে কারণ, নইলে null */
    private suspend fun guardForCapture(service: AgentAccessibilityService): String? {
        if (_emergencyStop.value) return "Emergency stop is ON · জরুরি থামানো চালু আছে"
        val foreground = ScreenContext.foreground.value.packageName
        SafetyPolicy.blockedPackage(foreground)?.let { return it.reason }
        val snapshot = withContext(Dispatchers.Default) {
            ScreenReader.read(service, context.packageName, ScreenContext.foreground.value.activity)
        }
        if (snapshot != null && snapshot.safety.level != SafetyLevel.SAFE) {
            return "Safety stop · ${snapshot.safety.reason}"
        }
        return null
    }

    companion object {
        private const val MIN_GAP_MS = 150L
        private const val NOT_ENABLED =
            "Accessibility is not enabled · Accessibility চালু নেই (Permission Center থেকে চালু করুন)"

        private const val VISION_SYSTEM_PROMPT =
            "You are looking at a screenshot of an Android phone screen. Answer the user's question about it " +
                "clearly and briefly. Reply in the same language as the question. " +
                "Never read out passwords, one-time codes, PINs or card numbers even if they are visible."
    }
}
