package com.aiagent.assistant.data.chat

import com.aiagent.assistant.data.local.AppPreferences
import com.aiagent.assistant.domain.ai.AiChatService
import com.aiagent.assistant.domain.ai.AiContext
import com.aiagent.assistant.domain.ai.AiRole
import com.aiagent.assistant.domain.ai.AiStreamEvent
import com.aiagent.assistant.domain.ai.AiTurn
import com.aiagent.assistant.domain.ai.SystemPrompts
import com.aiagent.assistant.domain.chat.ChatMessage
import com.aiagent.assistant.domain.chat.ChatResponder
import com.aiagent.assistant.domain.chat.MessageRole
import com.aiagent.assistant.domain.chat.ReplyEvent
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/** চ্যাটের ইতিহাসকে AI-এর বার্তায় বদলে আসল provider থেকে উত্তর আনে */
class AiChatResponder @Inject constructor(
    private val aiChatService: AiChatService,
    private val prefs: AppPreferences
) : ChatResponder {

    override fun reply(history: List<ChatMessage>, assistantName: String, spoken: Boolean): Flow<ReplyEvent> = flow {
        val userName = prefs.userName.first()
        val system = SystemPrompts.default(assistantName, userName, SystemPrompts.nowLabel(), spoken)
        val turns = AiContext.trim(
            history
                .filter { !it.isError && it.content.isNotBlank() }
                .map { message ->
                    AiTurn(
                        role = if (message.role == MessageRole.USER) AiRole.USER else AiRole.ASSISTANT,
                        text = message.content
                    )
                }
        )
        aiChatService.stream(turns, system).collect { event ->
            when (event) {
                is AiStreamEvent.ProviderStarted -> emit(ReplyEvent.Provider(event.providerName, event.isBackup))
                is AiStreamEvent.Text -> emit(ReplyEvent.Text(event.chunk))
            }
        }
    }
}
