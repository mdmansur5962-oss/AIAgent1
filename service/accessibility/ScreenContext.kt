package com.aiagent.assistant.service.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ForegroundInfo(val packageName: String, val activity: String)

data class SnapshotSummary(
    val packageName: String,
    val activity: String,
    val elementCount: Int,
    val safety: SafetyLevel,
    val capturedAt: Long
)

data class ActionRecord(
    val description: String,
    val success: Boolean,
    val message: String,
    val at: Long
)

/**
 * Screen context memory: এখন কোন অ্যাপ সামনে, সাম্প্রতিক snapshot-এর সারাংশ, আর সাম্প্রতিক কাজের ইতিহাস।
 * AI-কে দেওয়া "স্ক্রিনের বর্ণনা" এখান থেকে বানানো হয় (Task Planner এটা ব্যবহার করবে)।
 * শুধু সারাংশ রাখা হয়, স্ক্রিনের লেখা জমানো হয় না।
 */
object ScreenContext {

    private const val MAX_SNAPSHOTS = 10
    private const val MAX_ACTIONS = 20
    private const val MAX_PROMPT_ELEMENTS = 150
    private const val MAX_PROMPT_CHARS = 7000

    private val _foreground = MutableStateFlow(ForegroundInfo("", ""))
    val foreground: StateFlow<ForegroundInfo> = _foreground.asStateFlow()

    @Volatile
    var lastContentChange: Long = 0L
        private set

    private val snapshots = ArrayDeque<SnapshotSummary>()
    private val actions = ArrayDeque<ActionRecord>()

    fun onWindowChanged(packageName: String?, activity: String?) {
        val pkg = packageName.orEmpty()
        val cls = activity.orEmpty()
        // keyboard, notification shade ইত্যাদি "সামনের অ্যাপ" নয়
        if (pkg.isEmpty() || pkg == "com.android.systemui" || pkg.contains("inputmethod", ignoreCase = true)) return
        if (cls.startsWith("android.inputmethodservice")) return
        _foreground.value = ForegroundInfo(pkg, cls)
    }

    fun onContentChanged() {
        lastContentChange = System.currentTimeMillis()
    }

    fun onServiceDisconnected() {
        _foreground.value = ForegroundInfo("", "")
    }

    @Synchronized
    fun record(snapshot: UiSnapshot) {
        snapshots.addLast(
            SnapshotSummary(
                snapshot.packageName,
                snapshot.activity,
                snapshot.elements.size,
                snapshot.safety.level,
                snapshot.capturedAt
            )
        )
        while (snapshots.size > MAX_SNAPSHOTS) snapshots.removeFirst()
    }

    @Synchronized
    fun recordAction(description: String, result: ActionResult) {
        actions.addLast(ActionRecord(description, result.success, result.message, System.currentTimeMillis()))
        while (actions.size > MAX_ACTIONS) actions.removeFirst()
    }

    @Synchronized
    fun recentActions(): List<ActionRecord> = actions.toList()

    @Synchronized
    fun recentSnapshots(): List<SnapshotSummary> = snapshots.toList()

    @Synchronized
    fun clear() {
        snapshots.clear()
        actions.clear()
    }

    /** AI-কে দেওয়ার জন্য স্ক্রিনের সংক্ষিপ্ত বর্ণনা: কোন অ্যাপ, কোন উপাদান, সাম্প্রতিক কাজ */
    fun promptContext(snapshot: UiSnapshot?): String {
        if (snapshot == null) return "Screen: unavailable (accessibility is off or no window)."
        val sb = StringBuilder()
        sb.append("App: ").append(snapshot.packageName).append('\n')
        if (snapshot.activity.isNotBlank()) sb.append("Screen: ").append(snapshot.activity).append('\n')
        sb.append("Size: ").append(snapshot.screenWidth).append('x').append(snapshot.screenHeight).append('\n')
        sb.append("Safety: ").append(snapshot.safety.level)
        if (snapshot.safety.reason.isNotBlank()) sb.append(" (").append(snapshot.safety.reason).append(')')
        sb.append('\n')

        when (snapshot.safety.level) {
            SafetyLevel.BLOCKED -> {
                sb.append("This screen is protected. Do NOT interact. Only Back or Home are allowed.\n")
                return sb.toString()
            }
            SafetyLevel.SENSITIVE ->
                sb.append("Sensitive screen: only Back, Home, Scroll and Wait are allowed.\n")
            SafetyLevel.SAFE -> Unit
        }

        sb.append("Elements (id | type | label | flags | center):\n")
        val sorted = snapshot.elements.sortedWith(compareBy({ it.top }, { it.left }))
        var count = 0
        for (e in sorted) {
            if (count >= MAX_PROMPT_ELEMENTS || sb.length > MAX_PROMPT_CHARS) {
                sb.append("... (more elements not shown)\n")
                break
            }
            val flags = buildList {
                if (e.clickable) add("click")
                if (e.longClickable) add("longclick")
                if (e.editable) add("edit")
                if (e.scrollable) add("scroll")
                if (e.checkable) add(if (e.checked) "checked" else "unchecked")
                if (!e.enabled) add("disabled")
                if (e.focused) add("focused")
                if (e.password) add("password")
            }.joinToString(",")
            sb.append('[').append(e.id).append("] ").append(e.type)
            if (e.label.isNotBlank()) sb.append(" \"").append(e.label.replace('\n', ' ').take(60)).append('"')
            if (flags.isNotEmpty()) sb.append(" {").append(flags).append('}')
            sb.append(" @").append(e.centerX).append(',').append(e.centerY).append('\n')
            count++
        }
        if (snapshot.truncated) sb.append("(screen is large; list was truncated)\n")

        val recent = recentActions().takeLast(6)
        if (recent.isNotEmpty()) {
            sb.append("Recent actions:\n")
            recent.forEachIndexed { index, record ->
                sb.append(index + 1).append(") ").append(record.description)
                    .append(" -> ").append(if (record.success) "ok" else "failed: ${record.message.take(60)}")
                    .append('\n')
            }
        }
        return sb.toString()
    }
}
