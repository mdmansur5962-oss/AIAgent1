package com.aiagent.assistant.data.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    // ───── chats ─────
    @Query(
        """
        SELECT c.id AS id, c.title AS title, c.pinned AS pinned, c.folderId AS folderId, c.updatedAt AS updatedAt,
            (SELECT m.content FROM messages m WHERE m.chatId = c.id ORDER BY m.id DESC LIMIT 1) AS lastMessage,
            (SELECT COUNT(*) FROM messages m WHERE m.chatId = c.id) AS messageCount
        FROM chats c
        ORDER BY c.pinned DESC, c.updatedAt DESC
        """
    )
    fun observeSummaries(): Flow<List<ChatSummaryRow>>

    @Query("SELECT * FROM chats WHERE id = :id")
    fun observeChat(id: Long): Flow<ChatEntity?>

    @Insert
    suspend fun insertChat(chat: ChatEntity): Long

    @Query("UPDATE chats SET title = :title WHERE id = :id")
    suspend fun renameChat(id: Long, title: String)

    @Query("UPDATE chats SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE chats SET folderId = :folderId WHERE id = :id")
    suspend fun setFolder(id: Long, folderId: Long?)

    @Query("UPDATE chats SET updatedAt = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Long)

    @Query("DELETE FROM chats WHERE id = :id")
    suspend fun deleteChat(id: Long)

    @Query("DELETE FROM chats")
    suspend fun deleteAllChats()

    // ───── messages ─────
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY id ASC")
    fun observeMessages(chatId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY id ASC")
    suspend fun getMessages(chatId: Long): List<MessageEntity>

    @Query("SELECT DISTINCT chatId FROM messages WHERE content LIKE '%' || :query || '%'")
    suspend fun searchChatIds(query: String): List<Long>

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateMessageContent(id: Long, content: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("DELETE FROM messages WHERE chatId = :chatId AND id > :messageId")
    suspend fun deleteMessagesAfter(chatId: Long, messageId: Long)

    // ───── folders ─────
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE ASC")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Insert
    suspend fun insertFolder(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: Long)

    @Query("UPDATE chats SET folderId = NULL WHERE folderId = :folderId")
    suspend fun clearFolder(folderId: Long)
}
