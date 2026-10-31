package com.aiagent.assistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.aiagent.assistant.data.local.SettingsRepository
import com.aiagent.assistant.presentation.AgentRoot
import com.aiagent.assistant.presentation.components.LocalHapticsEnabled
import com.aiagent.assistant.presentation.navigation.LaunchRouter
import com.aiagent.assistant.presentation.settings.SettingsViewModel
import com.aiagent.assistant.presentation.theme.AgentTheme
import com.aiagent.assistant.presentation.theme.LocalizedContext
import com.aiagent.assistant.presentation.theme.ThemeViewModel
import com.aiagent.assistant.service.agent.AgentController
import com.aiagent.assistant.service.agent.AgentService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var agentController: AgentController

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LaunchRouter.handle(intent)

        // Background Agent চালু থাকার কথা কিন্তু service চলছে না (যেমন অ্যাপ বন্ধ হয়ে গিয়েছিল): অ্যাপ সামনে থাকতেই চালু করি
        lifecycleScope.launch {
            val settings = settingsRepository.settings.first()
            if (settings.backgroundAgentEnabled && !agentController.running.value) {
                AgentService.start(this@MainActivity)
            }
        }

        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val accent by themeViewModel.accent.collectAsStateWithLifecycle()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            // ভাষা বদলালে শুধু strings বদলায়; Activity একই থাকে
            val activityContext = LocalContext.current
            val localized = remember(activityContext, settings.language) {
                LocalizedContext.create(activityContext, settings.language)
            }

            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration,
                LocalHapticsEnabled provides settings.hapticsEnabled
            ) {
                AgentTheme(
                    themeMode = themeMode,
                    accent = accent,
                    fontScale = settings.fontScale
                ) {
                    AgentRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        LaunchRouter.handle(intent)
    }
}
