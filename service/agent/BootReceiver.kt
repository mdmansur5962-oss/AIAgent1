package com.aiagent.assistant.service.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aiagent.assistant.data.local.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ফোন চালু হলে Background Agent আবার চালু করে (যদি আগে চালু থাকে)।
 * Android 14+ এ ফোন চালুর সময় মাইক ব্যবহার করা যায় না, তাই wake word আবার চালু হবে অ্যাপ একবার খুললে।
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = settingsRepository.settings.first()
                if (settings.backgroundAgentEnabled) AgentService.start(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
