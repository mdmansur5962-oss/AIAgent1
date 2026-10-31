package com.aiagent.assistant.service.agent

import com.aiagent.assistant.data.local.SettingsRepository
import com.aiagent.assistant.domain.ai.AiErrorKind
import com.aiagent.assistant.domain.ai.AiException
import com.aiagent.assistant.domain.chat.ChatRepository
import com.aiagent.assistant.domain.chat.ChatResponder
import com.aiagent.assistant.domain.chat.MessageRole
import com.aiagent.assistant.domain.chat.ReplyEvent
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Background agent-এর মগজ: task queue (একটার পর একটা চলে), অবস্থা, আর service-কে খবর দেওয়ার event।
 * Foreground service (AgentService) এটার অবস্থা দেখিয়ে notification ও বাবল আপডেট করে।
 */
@Singleton
class AgentController @Inject constructor(
    private val repository: ChatRepository,
    private val responder: ChatResponder,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = Channel<AgentTask.Ask>(Channel.UNLIMITED)
    private val nextId = AtomicLong(1)
    private val chatIds = mutableMapOf<String, Long>()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _wakeListening = MutableStateFlow(false)
    val wakeListening: StateFlow<Boolean> = _wakeListening.asStateFlow()

    private val _wakeError = MutableStateFlow<String?>(null)
    val wakeError: StateFlow<String?> = _wakeError.asStateFlow()

    private val _tasks = MutableStateFlow<List<AgentTaskInfo>>(emptyList())
    val tasks: StateFlow<List<AgentTaskInfo>> = _tasks.asStateFlow()

    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    init {
        scope.launch {
            for (task in queue) {
                process(task)
            }
        }
    }

    // ───────────── service state ─────────────

    fun onServiceStarted() {
        _running.value = true
    }

    fun onServiceStopped() {
        _running.value = false
        _wakeListening.value = false
    }

    fun setWakeListening(value: Boolean) {
        _wakeListening.value = value
        if (value) _wakeError.value = null
    }

    fun setWakeError(message: String?) {
        _wakeError.value = message
        if (message != null) _wakeListening.value = false
    }

    // ───────────── queue ─────────────

    fun enqueueAsk(prompt: String, spoken: Boolean, source: String): Long {
        val id = nextId.getAndIncrement()
        _tasks.update { list ->
            (listOf(AgentTaskInfo(id, prompt.take(60), source, TaskStatus.QUEUED)) + list).take(20)
        }
        queue.trySend(AgentTask.Ask(id, prompt, spoken, source))
        return id
    }

    fun clearFinishedTasks() {
        _tasks.update { list -> list.filter { it.status == TaskStatus.QUEUED || it.status == TaskStatus.RUNNING } }
    }

    private fun updateTask(id: Long, status: TaskStatus, detail: String = "") {
        _tasks.update { list ->
            list.map { if (it.id == id) it.copy(status = status, detail = detail) else it }
        }
    }

    private suspend fun chatIdFor(source: String): Long {
        chatIds[source]?.let { return it }
        val title = when (source) {
            "notification" -> "Notification chat"
            "wake" -> "Wake word chat"
            else -> "Agent test"
        }
        val id = repository.createChat(title)
        chatIds[source] = id
        return id
    }

    private suspend fun process(task: AgentTask.Ask) {
        updateTask(task.id, TaskStatus.RUNNING)
        _events.emit(AgentEvent.TaskStarted(task.id, task.prompt))
        try {
            val settings = settingsRepository.settings.first()
            val chatId = chatIdFor(task.source)
            repository.addMessage(chatId, MessageRole.USER, task.prompt)
            val history = repository.getMessages(chatId)
            val buffer = StringBuilder()
            responder.reply(history, settings.assistantName, task.spoken).collect { event ->
                if (event is ReplyEvent.Text) buffer.append(event.chunk)
            }
            if (buffer.isBlank()) {
                throw AiException(AiErrorKind.UNKNOWN, "Empty reply · উত্তর খালি এসেছে")
            }
            val reply = buffer.toString()
            repository.addMessage(chatId, MessageRole.ASSISTANT, reply)
            updateTask(task.id, TaskStatus.DONE, reply.take(120))
            _events.emit(AgentEvent.TaskDone(task.id, chatId, task.prompt, reply, task.spoken))
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiException) {
            fail(task, e.message.orEmpty())
        } catch (e: Exception) {
            fail(task, "Something went wrong · কিছু একটা ভুল হয়েছে\n" + e.message.orEmpty())
        }
    }

    private suspend fun fail(task: AgentTask.Ask, message: String) {
        updateTask(task.id, TaskStatus.FAILED, message.take(160))
        _events.emit(AgentEvent.TaskFailed(task.id, task.prompt, message))
    }
}
