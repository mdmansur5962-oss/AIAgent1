package com.aiagent.assistant.domain.chat

/** Home-এ দেখানো সাম্প্রতিক চ্যাটের সারাংশ */
data class RecentChat(
    val id: Long,
    val title: String,
    val preview: String,
    val updatedAt: Long
)
