package com.aiagent.assistant.data.chat

import com.aiagent.assistant.domain.chat.Chat
import com.aiagent.assistant.domain.chat.ChatFolder
import com.aiagent.assistant.domain.chat.ChatMessage
import com.aiagent.assistant.domain.chat.ChatRepository
import com.aiagent.assistant.domain.chat.ChatSummary
import com.aiagent.assistant.domain.chat.MessageRole
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"

private fun ChatEntity.toDomain() = Chat(
    id = id,
    title = title,
    pinned = pinned,
    folderId = folderId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

private fun MessageEntity.toDomain() = ChatMessage(
    id = id,
    chatId = chatId,
    role = if (role == ROLE_ASSISTANT) MessageRole.ASSISTANT else MessageRole.USER,
    content = content,
    createdAt = createdAt,
    isError = isError
)

private fun ChatSummaryRow.toDomain() = ChatSummary(
    id = id,
    title = title,
    pinned = pinned,
    folderId = folderId,
    updatedAt = updatedAt,
    lastMessage = lastMessage.orEmpty(),
    messageCount = messageCount
)

private fun FolderEntity.toDomain() = ChatFolder(id = id, name = name)

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val dao: ChatDao
) : ChatRepository {

    override fun observeSummaries(): Flow<List<ChatSummary>> =
        dao.observeSummaries().map { rows -> rows.map { it.toDomain() } }

    override fun observeFolders(): Flow<List<ChatFolder>> =
        dao.observeFolders().map { list -> list.map { it.toDomain() } }

    override fun observeChat(id: Long): Flow<Chat?> =
        dao.observeChat(id).map { it?.toDomain() }

    override fun observeMessages(chatId: Long): Flow<List<ChatMessage>> =
        dao.observeMessages(chatId).map { list -> list.map { it.toDomain() } }

    override suspend fun getMessages(chatId: Long): List<ChatMessage> =
        dao.getMessages(chatId).map { it.toDomain() }

    override suspend fun searchChatIds(query: String): List<Long> =
        dao.searchChatIds(query)

    override suspend fun createChat(title: String): Long {
        val now = System.currentTimeMillis()
        return dao.insertChat(ChatEntity(title = title, createdAt = now, updatedAt = now))
    }

    override suspend fun renameChat(id: Long, title: String) = dao.renameChat(id, title)

    override suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)

    override suspend fun setFolder(id: Long, folderId: Long?) = dao.setFolder(id, folderId)

    override suspend fun deleteChat(id: Long) = dao.deleteChat(id)

    override suspend fun deleteAll() = dao.deleteAllChats()

    override suspend fun addMessage(
        chatId: Long,
        role: MessageRole,
        content: String,
        isError: Boolean
    ): Long {
        val now = System.currentTimeMillis()
        val id = dao.insertMessage(
            MessageEntity(
                chatId = chatId,
                role = if (role == MessageRole.ASSISTANT) ROLE_ASSISTANT else ROLE_USER,
                content = content,
                createdAt = now,
                isError = isError
            )
        )
        dao.touch(chatId, now)
        return id
    }

    override suspend fun updateMessage(id: Long, content: String) = dao.updateMessageContent(id, content)

    override suspend fun deleteMessage(id: Long) = dao.deleteMessage(id)

    override suspend fun deleteMessagesAfter(chatId: Long, messageId: Long) =
        dao.deleteMessagesAfter(chatId, messageId)

    override suspend fun createFolder(name: String): Long =
        dao.insertFolder(FolderEntity(name = name, createdAt = System.currentTimeMillis()))

    override suspend fun renameFolder(id: Long, name: String) = dao.renameFolder(id, name)

    override suspend fun deleteFolder(id: Long) {
        dao.clearFolder(id)
        dao.deleteFolder(id)
    }
}
