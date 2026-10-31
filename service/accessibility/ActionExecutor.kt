package com.aiagent.assistant.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * ফোনে আসল কাজ করে (ক্লিক, টাইপ, scroll, swipe, Back...)। প্রতিটি কাজের আগে নিরাপত্তা নিয়ম যাচাই হয়।
 * কাজ ব্যর্থ হলে ফোনে কোনো অর্ধেক পরিবর্তন রেখে যায় না: ফল সবসময় ActionResult-এ জানানো হয়।
 */
object ActionExecutor {

    private const val MAX_TYPE_LENGTH = 2000

    suspend fun execute(
        service: AccessibilityService,
        action: UiAction,
        snapshot: UiSnapshot?,
        confirmed: Boolean
    ): ActionResult {
        // ১) নিরাপত্তা-স্তরে এই কাজ চলে কিনা
        SafetyPolicy.denyReason(action, snapshot?.safety)?.let {
            return ActionResult(false, it, blockedBySafety = true)
        }

        return when (action) {
            UiAction.Back -> global(service, AccessibilityService.GLOBAL_ACTION_BACK, "Back")
            UiAction.Home -> global(service, AccessibilityService.GLOBAL_ACTION_HOME, "Home")
            UiAction.Recents -> global(service, AccessibilityService.GLOBAL_ACTION_RECENTS, "Recents")
            UiAction.OpenNotifications ->
                global(service, AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "Notifications")
            is UiAction.Wait -> {
                delay(action.ms.coerceIn(0L, 10_000L))
                ActionResult(true, "Waited ${action.ms} ms")
            }
            is UiAction.OpenApp -> openApp(service, action.nameOrPackage)
            is UiAction.ClickAt -> {
                if (!insideScreen(service, action.x, action.y)) {
                    ActionResult(false, "Point is outside the screen · বিন্দুটা স্ক্রিনের বাইরে")
                } else {
                    gestureResult(tap(service, action.x, action.y), "Tapped (${action.x}, ${action.y})")
                }
            }
            is UiAction.Swipe -> gestureResult(
                swipe(service, action.fromX, action.fromY, action.toX, action.toY, action.durationMs),
                "Swiped"
            )
            is UiAction.ClickElement -> withElement(snapshot, action.elementId) { clickElement(service, it, confirmed, long = false) }
            is UiAction.LongClickElement -> withElement(snapshot, action.elementId) { clickElement(service, it, confirmed, long = true) }
            is UiAction.ClickText -> {
                val match = findByText(snapshot, action.text, action.exact)
                if (match == null) {
                    ActionResult(false, "No element with text \"${action.text}\" · এই লেখার কিছু পাওয়া যায়নি")
                } else {
                    clickElement(service, match, confirmed, long = false)
                }
            }
            is UiAction.TypeText -> typeText(service, action, snapshot)
            is UiAction.Scroll -> scroll(service, action, snapshot)
        }
    }

    // ───────────── helpers ─────────────

    private suspend fun withElement(
        snapshot: UiSnapshot?,
        id: Int,
        block: suspend (UiElement) -> ActionResult
    ): ActionResult {
        val element = snapshot?.elements?.firstOrNull { it.id == id }
            ?: return ActionResult(false, "Element #$id not found on this screen · স্ক্রিনে #$id নেই")
        return block(element)
    }

    private fun findByText(snapshot: UiSnapshot?, text: String, exact: Boolean): UiElement? {
        val needle = text.trim()
        if (needle.isEmpty() || snapshot == null) return null
        val candidates = snapshot.elements.filter { e ->
            val label = e.label
            if (exact) label.equals(needle, ignoreCase = true) else label.contains(needle, ignoreCase = true)
        }
        // ক্লিক করা যায় এমনটা, আর ছোট (নির্দিষ্ট) উপাদান আগে
        return candidates.sortedWith(compareByDescending<UiElement> { it.clickable }.thenBy { it.area }).firstOrNull()
    }

    private fun global(service: AccessibilityService, action: Int, name: String): ActionResult {
        val ok = service.performGlobalAction(action)
        return ActionResult(ok, if (ok) name else "$name failed · $name হয়নি")
    }

    private fun gestureResult(ok: Boolean, success: String): ActionResult =
        ActionResult(ok, if (ok) success else "Gesture was not completed · ইশারা সম্পন্ন হয়নি")

    private fun insideScreen(service: AccessibilityService, x: Int, y: Int): Boolean {
        val m = service.resources.displayMetrics
        return x in 0..m.widthPixels && y in 0..m.heightPixels
    }

    // ───────────── node lookup ─────────────

    private fun matches(node: AccessibilityNodeInfo, element: UiElement): Boolean {
        val type = node.className?.toString().orEmpty().substringAfterLast('.')
        if (type != element.type) return false
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        // ঢাকা (redacted) লেখা মেলানো যায় না, তাই লেখা খালি/ঢাকা হলে শুধু ধরন মিললেই চলে
        if (element.text.isNotBlank() && !element.text.contains('•') && text.isNotBlank() && text != element.text) return false
        if (element.description.isNotBlank() && !element.description.contains('•') && desc.isNotBlank() && desc != element.description) return false
        return true
    }

    private fun resolve(service: AccessibilityService, element: UiElement): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        var node: AccessibilityNodeInfo? = root
        for (index in element.path) {
            node = node?.getChild(index)
            if (node == null) break
        }
        if (node != null && matches(node, element)) return node
        return findBest(root, element)
    }

    /** পথ মিলে না গেলে (স্ক্রিন বদলে গেছে) লেখা, id ও জায়গা দেখে সবচেয়ে কাছেরটা খোঁজে */
    private fun findBest(root: AccessibilityNodeInfo, element: UiElement): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestScore = 0
        val rect = Rect()
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 40) return
            var score = 0
            val id = node.viewIdResourceName?.substringAfter(":id/").orEmpty()
            if (element.viewId.isNotBlank() && id == element.viewId) score += 3
            val text = node.text?.toString().orEmpty()
            if (element.text.isNotBlank() && !element.text.contains('•') && text == element.text) score += 3
            val desc = node.contentDescription?.toString().orEmpty()
            if (element.description.isNotBlank() && !element.description.contains('•') && desc == element.description) score += 2
            if (node.className?.toString().orEmpty().substringAfterLast('.') == element.type) score += 1
            node.getBoundsInScreen(rect)
            if (rect.left == element.left && rect.top == element.top && rect.right == element.right && rect.bottom == element.bottom) score += 2
            if (score > bestScore) {
                bestScore = score
                best = node
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child, depth + 1)
            }
        }
        walk(root, 0)
        return if (bestScore >= 3) best else null
    }

    // ───────────── click ─────────────

    private suspend fun clickElement(
        service: AccessibilityService,
        element: UiElement,
        confirmed: Boolean,
        long: Boolean
    ): ActionResult {
        if (element.password) {
            return ActionResult(false, "Password fields are never touched · password ঘরে হাত দেওয়া হয় না", blockedBySafety = true)
        }
        if (!element.enabled) {
            return ActionResult(false, "\"${element.label.take(30)}\" is disabled · বোতামটা বন্ধ")
        }
        if (!confirmed) {
            SafetyPolicy.clickRisk(element)?.let {
                return ActionResult(false, it, needsConfirmation = true)
            }
        }
        val node = resolve(service, element)
        if (node != null) {
            var current: AccessibilityNodeInfo? = node
            var hops = 0
            val wanted = if (long) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
            while (current != null && hops < 6) {
                val capable = if (long) current.isLongClickable else current.isClickable
                if (capable && current.isEnabled && current.performAction(wanted)) {
                    return ActionResult(true, (if (long) "Long-pressed" else "Clicked") + " \"${element.label.take(40)}\"")
                }
                current = current.parent
                hops++
            }
        }
        // শেষ উপায়: উপাদানের মাঝখানে স্পর্শ (ইশারা)
        val ok = if (long) {
            longPress(service, element.centerX, element.centerY)
        } else {
            tap(service, element.centerX, element.centerY)
        }
        return ActionResult(
            ok,
            if (ok) "Tapped \"${element.label.take(40)}\" (gesture)" else "Could not click \"${element.label.take(40)}\" · ক্লিক হয়নি"
        )
    }

    // ───────────── type ─────────────

    private fun typeText(
        service: AccessibilityService,
        action: UiAction.TypeText,
        snapshot: UiSnapshot?
    ): ActionResult {
        if (action.text.length > MAX_TYPE_LENGTH) {
            return ActionResult(false, "Text is too long · লেখা অনেক বড়")
        }
        val node: AccessibilityNodeInfo? = if (action.elementId != null) {
            val element = snapshot?.elements?.firstOrNull { it.id == action.elementId }
                ?: return ActionResult(false, "Element #${action.elementId} not found · স্ক্রিনে নেই")
            if (element.password) {
                return ActionResult(false, "Password fields are never touched · password ঘরে লেখা হয় না", blockedBySafety = true)
            }
            resolve(service, element)
        } else {
            service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }
        if (node == null) {
            return ActionResult(false, "No text field is selected · কোনো লেখার ঘর বাছা নেই")
        }
        if (!node.isEditable) {
            return ActionResult(false, "This is not a text field · এটা লেখার ঘর নয়")
        }
        if (node.isPassword) {
            return ActionResult(false, "Password fields are never touched · password ঘরে লেখা হয় না", blockedBySafety = true)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val existing = if (action.append) node.text?.toString().orEmpty() else ""
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, existing + action.text)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return ActionResult(ok, if (ok) "Typed ${action.text.length} characters" else "Typing failed · লেখা যায়নি")
    }

    // ───────────── scroll ─────────────

    private suspend fun scroll(
        service: AccessibilityService,
        action: UiAction.Scroll,
        snapshot: UiSnapshot?
    ): ActionResult {
        val element: UiElement? = if (action.elementId != null) {
            snapshot?.elements?.firstOrNull { it.id == action.elementId }
        } else {
            snapshot?.elements?.filter { it.scrollable }?.maxByOrNull { it.area }
        }
        if (element != null) {
            val node = resolve(service, element)
            if (node != null) {
                val id = when (action.direction) {
                    Direction.DOWN -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
                    Direction.UP -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
                    Direction.LEFT -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id
                    Direction.RIGHT -> AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id
                }
                if (node.performAction(id)) {
                    return ActionResult(true, "Scrolled ${action.direction.name.lowercase()}")
                }
            }
        }
        // বিকল্প: আঙুল টানার ইশারা
        val m = service.resources.displayMetrics
        val w = m.widthPixels
        val h = m.heightPixels
        val ok = when (action.direction) {
            Direction.DOWN -> swipe(service, w / 2, (h * 0.75f).toInt(), w / 2, (h * 0.25f).toInt(), 350)
            Direction.UP -> swipe(service, w / 2, (h * 0.25f).toInt(), w / 2, (h * 0.75f).toInt(), 350)
            Direction.LEFT -> swipe(service, (w * 0.25f).toInt(), h / 2, (w * 0.75f).toInt(), h / 2, 350)
            Direction.RIGHT -> swipe(service, (w * 0.75f).toInt(), h / 2, (w * 0.25f).toInt(), h / 2, 350)
        }
        return gestureResult(ok, "Scrolled ${action.direction.name.lowercase()} (gesture)")
    }

    // ───────────── gestures ─────────────

    private suspend fun dispatch(service: AccessibilityService, gesture: GestureDescription): Boolean =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val accepted = service.dispatchGesture(
                    gesture,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(false)
                        }
                    },
                    null
                )
                if (!accepted && cont.isActive) cont.resume(false)
            }
        }

    private suspend fun tap(service: AccessibilityService, x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        return dispatch(service, GestureDescription.Builder().addStroke(stroke).build())
    }

    private suspend fun longPress(service: AccessibilityService, x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 700)
        return dispatch(service, GestureDescription.Builder().addStroke(stroke).build())
    }

    private suspend fun swipe(
        service: AccessibilityService,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
        durationMs: Long
    ): Boolean {
        val path = Path().apply {
            moveTo(fromX.toFloat(), fromY.toFloat())
            lineTo(toX.toFloat(), toY.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(50L, 3000L))
        return dispatch(service, GestureDescription.Builder().addStroke(stroke).build())
    }

    // ───────────── open app ─────────────

    @Suppress("DEPRECATION")
    private fun resolvePackage(service: AccessibilityService, nameOrPackage: String): String? {
        val query = nameOrPackage.trim()
        if (query.isEmpty()) return null
        val pm = service.packageManager
        if (query.contains('.')) {
            try {
                pm.getPackageInfo(query, 0)
                return query
            } catch (e: PackageManager.NameNotFoundException) {
                // নাম হিসেবে খুঁজি
            }
        }
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
        val exact = apps.firstOrNull { it.loadLabel(pm).toString().equals(query, ignoreCase = true) }
        val partial = apps.firstOrNull { it.loadLabel(pm).toString().contains(query, ignoreCase = true) }
        return (exact ?: partial)?.activityInfo?.packageName
    }

    private fun openApp(service: AccessibilityService, nameOrPackage: String): ActionResult {
        val pkg = resolvePackage(service, nameOrPackage)
            ?: return ActionResult(false, "App \"$nameOrPackage\" not found · অ্যাপ পাওয়া যায়নি")
        SafetyPolicy.blockedPackage(pkg)?.let {
            return ActionResult(false, it.reason, blockedBySafety = true)
        }
        val intent = service.packageManager.getLaunchIntentForPackage(pkg)
            ?: return ActionResult(false, "$pkg can't be opened · খোলা যায় না")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            service.startActivity(intent)
            ActionResult(true, "Opened $pkg")
        } catch (e: Exception) {
            ActionResult(false, "Could not open $pkg · খোলা যায়নি")
        }
    }
}
