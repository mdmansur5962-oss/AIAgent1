package com.aiagent.assistant.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * অন্য অ্যাপের স্ক্রিন পড়া ও তাতে কাজ করার চোখ-হাত। event থেকে শুধু "কোন অ্যাপ সামনে" জানে;
 * স্ক্রিনের লেখা event থেকে পড়া হয় না। আসল পড়া ও কাজ AccessibilityEngine চাইলে তবেই হয়।
 */
class AgentAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                ScreenContext.onWindowChanged(event.packageName?.toString(), event.className?.toString())
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> ScreenContext.onContentChanged()
        }
    }

    override fun onInterrupt() {
        // কিছু করার নেই
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        ScreenContext.onServiceDisconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: AgentAccessibilityService? = null
            private set
    }
}
