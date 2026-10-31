package com.aiagent.assistant.domain.chat

enum class MessageRole { USER, ASSISTANT }

data class ChatMessage(
    val id: Long,
    val chatId: Long,
    val role: MessageRole,
    val content: String,
    val createdAt: Long,
    val isError: Boolean = false
)

data class Chat(
    val id: Long,
    val title: String,
    val pinned: Boolean,
    val folderId: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

/** History তালিকার একটি সারি: চ্যাট + শেষ বার্তা + বার্তার সংখ্যা */
data class ChatSummary(
    val id: Long,
    val title: String,
    val pinned: Boolean,
    val folderId: Long?,
    val updatedAt: Long,
    val lastMessage: String,
    val messageCount: Int
)

data class ChatFolder(
    val id: Long,
    val name: String
)
