package com.aiagent.assistant.domain.chat

import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeSummaries(): Flow<List<ChatSummary>>
    fun observeFolders(): Flow<List<ChatFolder>>
    fun observeChat(id: Long): Flow<Chat?>
    fun observeMessages(chatId: Long): Flow<List<ChatMessage>>

    suspend fun getMessages(chatId: Long): List<ChatMessage>
    suspend fun searchChatIds(query: String): List<Long>

    suspend fun createChat(title: String): Long
    suspend fun renameChat(id: Long, title: String)
    suspend fun setPinned(id: Long, pinned: Boolean)
    suspend fun setFolder(id: Long, folderId: Long?)
    suspend fun deleteChat(id: Long)
    suspend fun deleteAll()

    suspend fun addMessage(chatId: Long, role: MessageRole, content: String, isError: Boolean = false): Long
    suspend fun updateMessage(id: Long, content: String)
    suspend fun deleteMessage(id: Long)

    /** messageId-এর পরের সব বার্তা মুছে দেয় (সম্পাদনার পর নতুন করে উত্তর আনতে) */
    suspend fun deleteMessagesAfter(chatId: Long, messageId: Long)

    suspend fun createFolder(name: String): Long
    suspend fun renameFolder(id: Long, name: String)
    suspend fun deleteFolder(id: Long)
}
