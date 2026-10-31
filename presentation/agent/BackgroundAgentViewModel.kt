package com.aiagent.assistant.presentation.agent

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagent.assistant.data.local.SettingsRepository
import com.aiagent.assistant.data.permission.PermissionChecker
import com.aiagent.assistant.domain.permission.PermissionId
import com.aiagent.assistant.domain.settings.AppSettings
import com.aiagent.assistant.service.agent.AgentController
import com.aiagent.assistant.service.agent.AgentService
import com.aiagent.assistant.service.agent.AgentTaskInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AgentPermissions(
    val overlay: Boolean = true,
    val notifications: Boolean = true,
    val battery: Boolean = true,
    val microphone: Boolean = true
)

private data class AgentSnapshot(
    val running: Boolean,
    val wakeListening: Boolean,
    val wakeError: String?,
    val tasks: List<AgentTaskInfo>
)

data class BackgroundAgentUiState(
    val settings: AppSettings = AppSettings(),
    val running: Boolean = false,
    val wakeListening: Boolean = false,
    val wakeError: String? = null,
    val tasks: List<AgentTaskInfo> = emptyList(),
    val permissions: AgentPermissions = AgentPermissions()
)

@HiltViewModel
class BackgroundAgentViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val controller: AgentController,
    private val checker: PermissionChecker
) : ViewModel() {

    private val permissions = MutableStateFlow(readPermissions())

    private val agent = combine(
        controller.running,
        controller.wakeListening,
        controller.wakeError,
        controller.tasks
    ) { running, listening, error, tasks ->
        AgentSnapshot(running, listening, error, tasks)
    }

    val state: StateFlow<BackgroundAgentUiState> = combine(
        settingsRepository.settings,
        agent,
        permissions
    ) { settings, snapshot, perms ->
        BackgroundAgentUiState(
            settings = settings,
            running = snapshot.running,
            wakeListening = snapshot.wakeListening,
            wakeError = snapshot.wakeError,
            tasks = snapshot.tasks,
            permissions = perms
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BackgroundAgentUiState())

    private fun readPermissions() = AgentPermissions(
        overlay = checker.status(PermissionId.OVERLAY).isSettled,
        notifications = checker.status(PermissionId.BACKGROUND_SERVICE).isSettled,
        battery = checker.status(PermissionId.BATTERY_OPTIMIZATION).isSettled,
        microphone = checker.status(PermissionId.MICROPHONE).isSettled
    )

    /** Permission Center থেকে ফিরে এলে অবস্থা আবার পড়ে */
    fun refreshPermissions() {
        permissions.value = readPermissions()
    }

    fun setAgentEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(backgroundAgentEnabled = enabled) }
        }
        if (enabled) AgentService.start(appContext) else AgentService.stop(appContext)
    }

    fun setBubble(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.update { it.copy(bubbleEnabled = enabled) } }
    }

    fun setWake(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.update { it.copy(wakeWordEnabled = enabled) } }
        // service চলছে না থাকলে চালু করি, যাতে wake word সত্যিই শুরু হয় (অ্যাপ এখন সামনে, তাই mic চলবে)
        if (enabled && !controller.running.value) {
            viewModelScope.launch { settingsRepository.update { it.copy(backgroundAgentEnabled = true) } }
            AgentService.start(appContext)
        }
    }

    fun sendTestTask() {
        controller.enqueueAsk("Say hello in one short sentence.", spoken = false, source = "test")
    }

    fun clearFinished() {
        controller.clearFinishedTasks()
    }
}
