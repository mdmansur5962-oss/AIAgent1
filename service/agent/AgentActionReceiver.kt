package com.aiagent.assistant.service.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Notification-এর "Ask" ঘরে লেখা প্রশ্ন এখানে আসে এবং queue-তে যায় */
@AndroidEntryPoint
class AgentActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var controller: AgentController

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_REPLY) return
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(AgentNotifications.KEY_REPLY)
            ?.toString()
            ?.trim()
            .orEmpty()
        if (text.isNotEmpty()) {
            controller.enqueueAsk(text, spoken = false, source = "notification")
        }
    }

    companion object {
        const val ACTION_REPLY = "com.aiagent.assistant.action.AGENT_REPLY"
    }
}
