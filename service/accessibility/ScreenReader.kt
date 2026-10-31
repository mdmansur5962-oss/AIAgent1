package com.aiagent.assistant.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** Accessibility tree থেকে স্ক্রিনের উপাদানের তালিকা (snapshot) বানায়। password-এর লেখা কখনো পড়া হয় না। */
object ScreenReader {

    private const val MAX_NODES = 450
    private const val MAX_DEPTH = 40
    private const val MAX_TEXT = 200

    fun read(service: AccessibilityService, ownPackage: String, activityHint: String): UiSnapshot? {
        val root = service.rootInActiveWindow ?: return null
        val packageName = root.packageName?.toString().orEmpty()

        // সুরক্ষিত অ্যাপে tree-ই পড়া হয় না
        val blocked = SafetyPolicy.blockedPackage(packageName)
        val metrics = service.resources.displayMetrics
        if (blocked != null) {
            return UiSnapshot(
                packageName = packageName,
                activity = activityHint,
                capturedAt = System.currentTimeMillis(),
                screenWidth = metrics.widthPixels,
                screenHeight = metrics.heightPixels,
                elements = emptyList(),
                safety = blocked,
                truncated = false
            )
        }

        val raw = ArrayList<UiElement>()
        var truncated = false
        val rect = Rect()

        fun walk(node: AccessibilityNodeInfo, path: List<Int>, depth: Int) {
            if (raw.size >= MAX_NODES) {
                truncated = true
                return
            }
            if (node.isVisibleToUser) {
                val isPassword = node.isPassword
                val text = if (isPassword) "" else node.text?.toString().orEmpty().take(MAX_TEXT)
                val description = node.contentDescription?.toString().orEmpty().take(MAX_TEXT)
                val interesting = text.isNotBlank() ||
                    description.isNotBlank() ||
                    node.isClickable ||
                    node.isLongClickable ||
                    node.isEditable ||
                    node.isScrollable ||
                    node.isCheckable
                if (interesting) {
                    node.getBoundsInScreen(rect)
                    if (rect.width() > 0 && rect.height() > 0) {
                        raw.add(
                            UiElement(
                                id = raw.size,
                                path = path,
                                type = node.className?.toString().orEmpty().substringAfterLast('.'),
                                text = text,
                                description = description,
                                viewId = node.viewIdResourceName?.substringAfter(":id/").orEmpty(),
                                left = rect.left,
                                top = rect.top,
                                right = rect.right,
                                bottom = rect.bottom,
                                clickable = node.isClickable,
                                longClickable = node.isLongClickable,
                                scrollable = node.isScrollable,
                                editable = node.isEditable,
                                checkable = node.isCheckable,
                                checked = node.isChecked,
                                enabled = node.isEnabled,
                                focused = node.isFocused,
                                password = isPassword
                            )
                        )
                    }
                }
            }
            if (depth >= MAX_DEPTH) return
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child, path + i, depth + 1)
            }
        }
        walk(root, emptyList(), 0)

        val (elements, safety) = SafetyPolicy.process(packageName, ownPackage, raw)
        return UiSnapshot(
            packageName = packageName,
            activity = activityHint,
            capturedAt = System.currentTimeMillis(),
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            elements = elements,
            safety = safety,
            truncated = truncated
        )
    }
}
