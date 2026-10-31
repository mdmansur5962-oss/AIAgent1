package com.aiagent.assistant.presentation.chat

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.aiagent.assistant.core.mvi.BaseViewModel
import com.aiagent.assistant.core.mvi.UiEffect
import com.aiagent.assistant.core.mvi.UiEvent
import com.aiagent.assistant.core.mvi.UiState
import com.aiagent.assistant.data.local.SettingsRepository
import com.aiagent.assistant.domain.ai.AiErrorKind
import com.aiagent.assistant.domain.ai.AiException
import com.aiagent.assistant.domain.ai.AiProviderRepository
import com.aiagent.assistant.domain.chat.Chat
import com.aiagent.assistant.domain.chat.ChatExporter
import com.aiagent.assistant.domain.chat.ChatFolder
import com.aiagent.assistant.domain.chat.ChatMessage
import com.aiagent.assistant.domain.chat.ChatRepository
import com.aiagent.assistant.domain.chat.ChatResponder
import com.aiagent.assistant.domain.chat.MessageRole
import com.aiagent.assistant.domain.chat.ReplyEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** উত্তর আনতে গিয়ে যে ত্রুটি হয়েছে (ব্যানারে দেখায়, চ্যাটের ইতিহাসে জমে না) */
data class ChatError(
    val kind: AiErrorKind,
    val message: String
) {
    /** Settings-এ গিয়ে ঠিক করতে হবে এমন ত্রুটি */
    val needsSetup: Boolean
        get() = kind == AiErrorKind.NO_PROVIDER || kind == AiErrorKind.AUTH || kind == AiErrorKind.MODEL_OR_URL
}

data class ChatUiState(
    val chatId: Long? = null,
    val title: String = "",
    val pinned: Boolean = false,
    val folderId: Long? = null,
    val messages: List<ChatMessage> = emptyList(),
    /** উত্তর লেখা চলছে: এখনো database-এ যায়নি এমন আংশিক লেখা */
    val streamingText: String? = null,
    val isGenerating: Boolean = false,
    val input: String = "",
    val editingMessageId: Long? = null,
    val folders: List<ChatFolder> = emptyList(),
    val appName: String = "MYRA",
    val assistantName: String = "MYRA",
    /** কমপক্ষে একটি চালু ও সম্পূর্ণ সাজানো AI provider আছে কিনা */
    val hasProvider: Boolean = true,
    val error: ChatError? = null,
    /** ডিফল্ট ব্যর্থ হয়ে যে ব্যাকআপ provider উত্তর দিচ্ছে তার নাম */
    val backupNote: String? = null
) : UiState

sealed interface ChatEvent : UiEvent {
    data class InputChanged(val text: String) : ChatEvent
    data object Send : ChatEvent
    data class SendText(val text: String) : ChatEvent
    data object Stop : ChatEvent
    data object NewChat : ChatEvent
    data class StartEdit(val message: ChatMessage) : ChatEvent
    data object CancelEdit : ChatEvent
    data class DeleteMessage(val messageId: Long) : ChatEvent
    data object Regenerate : ChatEvent
    data object Retry : ChatEvent
    data object DismissError : ChatEvent
    data class Rename(val title: String) : ChatEvent
    data object TogglePin : ChatEvent
    data class MoveToFolder(val folderId: Long?) : ChatEvent
    data object DeleteChat : ChatEvent
    data object CopyChat : ChatEvent
    data object ShareChat : ChatEvent
    data object SaveChat : ChatEvent
}

sealed interface ChatEffect : UiEffect {
    data class CopyText(val text: String) : ChatEffect
    data class ShareText(val title: String, val text: String) : ChatEffect
    data class SaveText(val fileName: String, val text: String) : ChatEffect
    data object ChatDeleted : ChatEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ChatRepository,
    private val responder: ChatResponder,
    private val settingsRepository: SettingsRepository,
    private val aiProviderRepository: AiProviderRepository
) : BaseViewModel<ChatUiState, ChatEvent, ChatEffect>(ChatUiState()) {

    /** null = এখনো কোনো চ্যাট তৈরি হয়নি (প্রথম বার্তা পাঠালে তৈরি হবে) */
    private val chatIdFlow = MutableStateFlow(savedStateHandle.get<Long>("chatId")?.takeIf { it > 0 })

    private var generationJob: Job? = null

    init {
        viewModelScope.launch {
            chatIdFlow.collect { id -> setState { copy(chatId = id) } }
        }
        viewModelScope.launch {
            chatIdFlow
                .flatMapLatest { id ->
                    if (id == null) flowOf(emptyList<ChatMessage>()) else repository.observeMessages(id)
                }
                .collect { list -> setState { copy(messages = list) } }
        }
        viewModelScope.launch {
            chatIdFlow
                .flatMapLatest { id ->
                    if (id == null) flowOf<Chat?>(null) else repository.observeChat(id)
                }
                .collect { chat ->
                    setState {
                        copy(
                            title = chat?.title.orEmpty(),
                            pinned = chat?.pinned ?: false,
                            folderId = chat?.folderId
                        )
                    }
                }
        }
        viewModelScope.launch {
            repository.observeFolders().collect { list -> setState { copy(folders = list) } }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                setState { copy(appName = s.appName, assistantName = s.assistantName) }
            }
        }
        viewModelScope.launch {
            aiProviderRepository.providers
                .map { list -> list.any { it.enabled && it.isConfigured } }
                .collect { has -> setState { copy(hasProvider = has) } }
        }
    }

    override fun onEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.InputChanged -> setState { copy(input = event.text) }
            ChatEvent.Send -> send(currentState.input)
            is ChatEvent.SendText -> send(event.text)
            ChatEvent.Stop -> generationJob?.cancel()
            ChatEvent.NewChat -> newChat()
            is ChatEvent.StartEdit -> setState {
                copy(editingMessageId = event.message.id, input = event.message.content)
            }
            ChatEvent.CancelEdit -> setState { copy(editingMessageId = null, input = "") }
            is ChatEvent.DeleteMessage -> viewModelScope.launch { repository.deleteMessage(event.messageId) }
            ChatEvent.Regenerate -> regenerate()
            ChatEvent.Retry -> retry()
            ChatEvent.DismissError -> setState { copy(error = null) }
            is ChatEvent.Rename -> renameChat(event.title)
            ChatEvent.TogglePin -> togglePin()
            is ChatEvent.MoveToFolder -> moveToFolder(event.folderId)
            ChatEvent.DeleteChat -> deleteChat()
            ChatEvent.CopyChat -> sendEffect(ChatEffect.CopyText(exportText()))
            ChatEvent.ShareChat -> sendEffect(ChatEffect.ShareText(displayTitle(), exportText()))
            ChatEvent.SaveChat -> sendEffect(
                ChatEffect.SaveText(ChatExporter.toFileName(displayTitle()), exportText())
            )
        }
    }

    // ───────────── sending ─────────────

    private fun send(raw: String) {
        val text = raw.trim()
        if (text.isEmpty() || currentState.isGenerating) return
        val editingId = currentState.editingMessageId

        viewModelScope.launch {
            if (editingId != null) {
                val cid = chatIdFlow.value ?: return@launch
                repository.updateMessage(editingId, text)
                repository.deleteMessagesAfter(cid, editingId)
                setState { copy(input = "", editingMessageId = null) }
                generateReply(cid)
            } else {
                val cid = ensureChat(text)
                repository.addMessage(cid, MessageRole.USER, text)
                setState { copy(input = "") }
                generateReply(cid)
            }
        }
    }

    private suspend fun ensureChat(firstMessage: String): Long {
        val existing = chatIdFlow.value
        if (existing != null) return existing
        val title = firstMessage.lineSequence().firstOrNull().orEmpty().trim().take(40)
        val id = repository.createChat(title)
        chatIdFlow.value = id
        return id
    }

    private fun generateReply(chatId: Long) {
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            setState { copy(isGenerating = true, streamingText = "", error = null, backupNote = null) }
            val buffer = StringBuilder()
            var lastUiUpdate = 0L
            try {
                val history = repository.getMessages(chatId)
                responder.reply(history, currentState.assistantName).collect { event ->
                    when (event) {
                        is ReplyEvent.Provider -> setState {
                            copy(backupNote = if (event.isBackup) event.name else null)
                        }
                        is ReplyEvent.Text -> {
                            buffer.append(event.chunk)
                            // প্রতি টুকরোয় screen আঁকলে ধীর হয়; ~৪০ms পরপর আপডেট
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastUiUpdate >= 40) {
                                lastUiUpdate = now
                                setState { copy(streamingText = buffer.toString()) }
                            }
                        }
                    }
                }
                if (buffer.isBlank()) {
                    throw AiException(AiErrorKind.UNKNOWN, "Empty reply · উত্তর খালি এসেছে")
                }
                repository.addMessage(chatId, MessageRole.ASSISTANT, buffer.toString())
            } catch (e: CancellationException) {
                // থামালে এ পর্যন্ত যা এসেছে তা রেখে দেয়
                if (buffer.isNotBlank()) {
                    withContext(NonCancellable) {
                        repository.addMessage(chatId, MessageRole.ASSISTANT, buffer.toString())
                    }
                }
                throw e
            } catch (e: AiException) {
                keepPartial(chatId, buffer)
                setState { copy(error = ChatError(e.kind, e.message.orEmpty())) }
            } catch (e: Exception) {
                keepPartial(chatId, buffer)
                setState {
                    copy(error = ChatError(AiErrorKind.UNKNOWN, "Something went wrong · কিছু একটা ভুল হয়েছে\n" + e.message.orEmpty()))
                }
            } finally {
                setState { copy(isGenerating = false, streamingText = null) }
            }
        }
    }

    /** উত্তর মাঝপথে থেমে গেলে যতটুকু এসেছে ততটুকু রেখে দেয় */
    private suspend fun keepPartial(chatId: Long, buffer: StringBuilder) {
        if (buffer.isNotBlank()) {
            repository.addMessage(chatId, MessageRole.ASSISTANT, buffer.toString())
        }
    }

    private fun regenerate() {
        val cid = chatIdFlow.value ?: return
        if (currentState.isGenerating) return
        val lastAssistant = currentState.messages.lastOrNull { it.role == MessageRole.ASSISTANT } ?: return
        viewModelScope.launch {
            repository.deleteMessage(lastAssistant.id)
            generateReply(cid)
        }
    }

    /** ত্রুটির পর আবার চেষ্টা: মাঝপথে থামা আধা উত্তর থাকলে সেটা মুছে নতুন করে আনে */
    private fun retry() {
        val cid = chatIdFlow.value ?: return
        if (currentState.isGenerating) return
        val last = currentState.messages.lastOrNull() ?: return
        viewModelScope.launch {
            if (last.role == MessageRole.ASSISTANT) repository.deleteMessage(last.id)
            setState { copy(error = null) }
            generateReply(cid)
        }
    }

    // ───────────── chat actions ─────────────

    private fun newChat() {
        generationJob?.cancel()
        chatIdFlow.value = null
        setState {
            copy(
                input = "",
                editingMessageId = null,
                streamingText = null,
                isGenerating = false,
                error = null,
                backupNote = null
            )
        }
    }

    private fun renameChat(title: String) {
        val id = chatIdFlow.value ?: return
        val clean = title.trim().take(60)
        if (clean.isEmpty()) return
        viewModelScope.launch { repository.renameChat(id, clean) }
    }

    private fun togglePin() {
        val id = chatIdFlow.value ?: return
        val pinned = currentState.pinned
        viewModelScope.launch { repository.setPinned(id, !pinned) }
    }

    private fun moveToFolder(folderId: Long?) {
        val id = chatIdFlow.value ?: return
        viewModelScope.launch { repository.setFolder(id, folderId) }
    }

    private fun deleteChat() {
        val id = chatIdFlow.value ?: return
        generationJob?.cancel()
        viewModelScope.launch {
            chatIdFlow.value = null
            repository.deleteChat(id)
            setState {
                copy(
                    input = "",
                    editingMessageId = null,
                    streamingText = null,
                    isGenerating = false,
                    error = null,
                    backupNote = null
                )
            }
            sendEffect(ChatEffect.ChatDeleted)
        }
    }

    private fun displayTitle(): String = currentState.title.ifBlank { "Chat" }

    private fun exportText(): String = ChatExporter.toMarkdown(
        title = displayTitle(),
        messages = currentState.messages,
        appName = currentState.appName,
        assistantName = currentState.assistantName
    )
}
