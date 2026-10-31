package com.aiagent.assistant.presentation.ai

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagent.assistant.domain.ai.AiConnectionChecker
import com.aiagent.assistant.domain.ai.AiProvider
import com.aiagent.assistant.domain.ai.AiProviderRepository
import com.aiagent.assistant.domain.ai.AiProviderType
import com.aiagent.assistant.domain.ai.AiUrlValidator
import com.aiagent.assistant.domain.ai.ConnectionTestResult
import com.aiagent.assistant.domain.ai.ModelListResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProviderErrors(
    val name: String? = null,
    val url: String? = null,
    val key: String? = null,
    val model: String? = null,
    val flags: String? = null
) {
    val any: Boolean get() = name != null || url != null || key != null || model != null || flags != null
}

data class ProviderEditorState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val type: AiProviderType = AiProviderType.CUSTOM,
    val id: String = "",
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val showKey: Boolean = false,
    val model: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val systemPrompt: String = "",
    val enabled: Boolean = true,
    val makeDefault: Boolean = false,
    val makeBackup: Boolean = false,
    val wasDefault: Boolean = false,
    val errors: ProviderErrors = ProviderErrors(),
    val testing: Boolean = false,
    val testResult: ConnectionTestResult? = null,
    val fetchingModels: Boolean = false,
    val fetchedModels: List<String>? = null,
    val modelsError: String? = null,
    val saving: Boolean = false,
    /** সংরক্ষণ বা মোছা শেষ: screen বন্ধ হবে */
    val done: Boolean = false
)

@HiltViewModel
class ProviderEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AiProviderRepository,
    private val checker: AiConnectionChecker
) : ViewModel() {

    private val argProviderId: String = savedStateHandle.get<String>("providerId").orEmpty()
    private val argType: String = savedStateHandle.get<String>("type").orEmpty()

    private val _state = MutableStateFlow(ProviderEditorState())
    val state: StateFlow<ProviderEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        repository.isLoaded.first { it }
        val routing = repository.routing.value
        val existing = if (argProviderId.isNotBlank()) repository.get(argProviderId) else null
        if (existing != null) {
            _state.value = ProviderEditorState(
                loading = false,
                isNew = false,
                type = existing.type,
                id = existing.id,
                name = existing.name,
                baseUrl = existing.baseUrl,
                apiKey = existing.apiKey,
                model = existing.model,
                temperature = existing.temperature,
                maxTokens = existing.maxTokens,
                systemPrompt = existing.systemPrompt,
                enabled = existing.enabled,
                makeDefault = routing.defaultId == existing.id,
                makeBackup = routing.backupId == existing.id,
                wasDefault = routing.defaultId == existing.id
            )
        } else {
            val type = AiProviderType.entries.firstOrNull { it.name == argType } ?: AiProviderType.CUSTOM
            _state.value = ProviderEditorState(
                loading = false,
                isNew = true,
                type = type,
                id = UUID.randomUUID().toString(),
                name = type.displayName,
                baseUrl = type.defaultBaseUrl,
                model = type.defaultModel,
                makeDefault = routing.defaultId == null
            )
        }
    }

    fun edit(transform: (ProviderEditorState) -> ProviderEditorState) {
        _state.update { transform(it).copy(testResult = null) }
    }

    fun toggleShowKey() {
        _state.update { it.copy(showKey = !it.showKey) }
    }

    fun dismissModels() {
        _state.update { it.copy(fetchedModels = null) }
    }

    fun pickModel(model: String) {
        _state.update { it.copy(model = model, fetchedModels = null, testResult = null) }
    }

    // ───────────── actions ─────────────

    fun fetchModels() {
        val s = _state.value
        val errors = validate(s, forNetwork = true)
        if (errors.any) {
            _state.update { it.copy(errors = errors) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(fetchingModels = true, modelsError = null, errors = ProviderErrors()) }
            when (val result = checker.listModels(build(s, requireModel = false))) {
                is ModelListResult.Success ->
                    _state.update { it.copy(fetchingModels = false, fetchedModels = result.models) }
                is ModelListResult.Failure ->
                    _state.update { it.copy(fetchingModels = false, modelsError = result.message) }
            }
        }
    }

    fun testConnection() {
        val s = _state.value
        val errors = validate(s, forNetwork = true).copy(model = modelError(s))
        if (errors.any) {
            _state.update { it.copy(errors = errors) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(testing = true, testResult = null, errors = ProviderErrors()) }
            val result = checker.test(build(s))
            _state.update { it.copy(testing = false, testResult = result) }
        }
    }

    fun save() {
        val s = _state.value
        val errors = validate(s, forNetwork = false)
        if (errors.any) {
            _state.update { it.copy(errors = errors) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, errors = ProviderErrors()) }
            val provider = build(s)
            repository.upsert(provider)
            if (s.makeDefault) repository.setDefault(provider.id)
            if (s.makeBackup) {
                repository.setBackup(provider.id)
            } else if (repository.routing.value.backupId == provider.id) {
                repository.setBackup(null)
            }
            _state.update { it.copy(saving = false, done = true) }
        }
    }

    fun delete() {
        val id = _state.value.id
        viewModelScope.launch {
            repository.delete(id)
            _state.update { it.copy(done = true) }
        }
    }

    // ───────────── helpers ─────────────

    private fun modelError(s: ProviderEditorState): String? =
        if (s.model.isBlank()) "Model name is required · Model-এর নাম দিন" else null

    private fun validate(s: ProviderEditorState, forNetwork: Boolean): ProviderErrors {
        val urlError = AiUrlValidator.validate(s.baseUrl)
        val keyError = if (s.type.requiresKey && s.apiKey.isBlank()) "API key is required · API key দিন" else null
        if (forNetwork) {
            return ProviderErrors(url = urlError, key = keyError)
        }
        return ProviderErrors(
            name = if (s.name.isBlank()) "Name is required · নাম দিন" else null,
            url = urlError,
            key = keyError,
            model = modelError(s),
            flags = when {
                s.makeDefault && s.makeBackup ->
                    "One provider can't be both default and backup · একই provider ডিফল্ট ও ব্যাকআপ দুটোই হতে পারে না"
                !s.enabled && (s.makeDefault || s.makeBackup) ->
                    "Turn the provider on first · আগে provider চালু করুন"
                else -> null
            }
        )
    }

    private fun build(s: ProviderEditorState, requireModel: Boolean = true): AiProvider = AiProvider(
        id = s.id,
        type = s.type,
        name = s.name.trim(),
        baseUrl = s.baseUrl.trim(),
        apiKey = s.apiKey.trim(),
        model = if (requireModel) s.model.trim() else s.model.trim(),
        temperature = s.temperature,
        maxTokens = s.maxTokens,
        systemPrompt = s.systemPrompt.trim(),
        enabled = s.enabled
    )
}
