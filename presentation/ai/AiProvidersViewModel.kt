package com.aiagent.assistant.presentation.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagent.assistant.domain.ai.AiProvider
import com.aiagent.assistant.domain.ai.AiProviderRepository
import com.aiagent.assistant.domain.ai.AiRouting
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AiProvidersUiState(
    val providers: List<AiProvider> = emptyList(),
    val routing: AiRouting = AiRouting(),
    val loaded: Boolean = false
)

@HiltViewModel
class AiProvidersViewModel @Inject constructor(
    private val repository: AiProviderRepository
) : ViewModel() {

    val state: StateFlow<AiProvidersUiState> = combine(
        repository.providers,
        repository.routing,
        repository.isLoaded
    ) { providers, routing, loaded ->
        AiProvidersUiState(providers, routing, loaded)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiProvidersUiState())

    fun setDefault(id: String) {
        viewModelScope.launch { repository.setDefault(id) }
    }

    fun setBackup(id: String?) {
        viewModelScope.launch { repository.setBackup(id) }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(id, enabled) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}
