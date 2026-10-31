package com.aiagent.assistant.domain.settings

enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    BANGLA("bn")
}

/**
 * Voice-এর ধরন। Android TTS-এ সব ধরনের কণ্ঠ থাকে না, তাই এখন pitch ও speed দিয়ে কাছাকাছি করা হয়।
 * Step 9-এ ফোনের আসল male/female voice খুঁজে বসানো হবে।
 */
enum class VoiceStyle(val pitch: Float, val speed: Float) {
    FEMALE(1.10f, 1.00f),
    MALE(0.85f, 1.00f),
    CHILD(1.60f, 1.10f),
    ROBOT(0.70f, 0.90f),
    SOFT(1.05f, 0.85f),
    DEEP(0.60f, 0.95f),
    CUSTOM(1.00f, 1.00f)
}

/** কথা শোনার ভাষা */
enum class VoiceLanguage {
    AUTO,
    ENGLISH,
    BANGLA
}

enum class AutonomyMode {
    /** Plan দেখাবে, OK/শুরু করো বললে পুরোটা নিজে চালাবে */
    CONFIRM_PLAN,

    /** প্রতিটি ধাপের আগে জিজ্ঞেস করবে */
    STEP_BY_STEP
}

enum class ActionSpeed(val delayMs: Long) {
    CAREFUL(1500L),
    NORMAL(800L),
    FAST(300L)
}

data class AppSettings(
    val appName: String = "MYRA",
    val assistantName: String = "MYRA",
    val wakeWord: String = "Hey MYRA",
    val wakeWordEnabled: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val hapticsEnabled: Boolean = true,
    val fontScale: Float = 1.0f,
    val voiceStyle: VoiceStyle = VoiceStyle.FEMALE,
    val voiceSpeed: Float = 1.0f,
    val voicePitch: Float = 1.1f,
    val speakReplies: Boolean = true,
    val voiceLanguage: VoiceLanguage = VoiceLanguage.AUTO,
    val continuousListening: Boolean = true,
    val autonomyMode: AutonomyMode = AutonomyMode.CONFIRM_PLAN,
    val actionSpeed: ActionSpeed = ActionSpeed.NORMAL,
    val maxRetries: Int = 2,
    val stopOnError: Boolean = true,
    val showProgressBubble: Boolean = true,
    /** ব্যাকগ্রাউন্ডে চলা agent (foreground service) চালু আছে কিনা */
    val backgroundAgentEnabled: Boolean = false,
    /** অন্য অ্যাপের উপরে ভাসমান assistant বাবল */
    val bubbleEnabled: Boolean = true
)
