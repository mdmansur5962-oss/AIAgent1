package com.aiagent.assistant.service.accessibility

/** স্ক্রিনের একটি উপাদান (বোতাম, লেখা, ঘর ইত্যাদি)। id প্রতিটি snapshot-এর ভেতরে অনন্য। */
data class UiElement(
    val id: Int,
    /** root থেকে এই উপাদানে পৌঁছানোর child-সূচক; কাজের সময় উপাদানটা আবার খুঁজে পেতে লাগে */
    val path: List<Int>,
    val type: String,
    val text: String,
    val description: String,
    val viewId: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val clickable: Boolean,
    val longClickable: Boolean,
    val scrollable: Boolean,
    val editable: Boolean,
    val checkable: Boolean,
    val checked: Boolean,
    val enabled: Boolean,
    val focused: Boolean,
    val password: Boolean
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val area: Long get() = (right - left).toLong() * (bottom - top).toLong()

    val label: String
        get() = when {
            text.isNotBlank() -> text
            description.isNotBlank() -> description
            else -> viewId
        }
}

enum class SafetyLevel {
    /** স্বাভাবিক: সব কাজ চলবে */
    SAFE,

    /** স্পর্শকাতর (password/OTP/কার্ড দেখা যাচ্ছে): শুধু Back, Home, scroll, অপেক্ষা চলবে */
    SENSITIVE,

    /** সুরক্ষিত অ্যাপ (ব্যাংকিং, payment, authenticator, password manager): শুধু Back, Home চলবে */
    BLOCKED
}

data class ScreenSafety(
    val level: SafetyLevel,
    val reason: String = ""
)

data class UiSnapshot(
    val packageName: String,
    val activity: String,
    val capturedAt: Long,
    val screenWidth: Int,
    val screenHeight: Int,
    val elements: List<UiElement>,
    val safety: ScreenSafety,
    val truncated: Boolean
)

enum class Direction { UP, DOWN, LEFT, RIGHT }

/** assistant যে কাজগুলো ফোনে করতে পারে */
sealed interface UiAction {
    data class ClickElement(val elementId: Int) : UiAction
    data class ClickText(val text: String, val exact: Boolean = false) : UiAction
    data class ClickAt(val x: Int, val y: Int) : UiAction
    data class LongClickElement(val elementId: Int) : UiAction
    data class TypeText(val text: String, val elementId: Int? = null, val append: Boolean = false) : UiAction

    /** DOWN = নিচের অংশ দেখার জন্য scroll */
    data class Scroll(val direction: Direction, val elementId: Int? = null) : UiAction
    data class Swipe(
        val fromX: Int,
        val fromY: Int,
        val toX: Int,
        val toY: Int,
        val durationMs: Long = 300
    ) : UiAction

    data object Back : UiAction
    data object Home : UiAction
    data object Recents : UiAction
    data object OpenNotifications : UiAction
    data class Wait(val ms: Long) : UiAction

    /** অ্যাপের নাম (যেমন "Chrome") বা package name */
    data class OpenApp(val nameOrPackage: String) : UiAction
}

data class ActionResult(
    val success: Boolean,
    val message: String,
    /** নিরাপত্তা নিয়মে আটকেছে */
    val blockedBySafety: Boolean = false,
    /** ঝুঁকির কাজ: ব্যবহারকারীর confirmation লাগবে, নিশ্চিত হলে confirmed = true দিয়ে আবার করুন */
    val needsConfirmation: Boolean = false
)

data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)
