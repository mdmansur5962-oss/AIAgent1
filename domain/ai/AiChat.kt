package com.aiagent.assistant.domain.ai

import kotlinx.coroutines.flow.Flow

enum class AiRole { USER, ASSISTANT }

/** ছবি পাঠানোর জন্য (vision/screen analysis): base64 করা JPEG/PNG */
data class AiImage(
    val mimeType: String,
    val base64: String
)

data class AiTurn(
    val role: AiRole,
    val text: String,
    val images: List<AiImage> = emptyList()
)

sealed interface AiStreamEvent {
    /** কোন provider চেষ্টা শুরু হলো (isBackup = ডিফল্ট ব্যর্থ হয়ে ব্যাকআপে এসেছে) */
    data class ProviderStarted(val providerName: String, val isBackup: Boolean) : AiStreamEvent

    data class Text(val chunk: String) : AiStreamEvent
}

interface AiChatService {
    /**
     * ডিফল্ট provider দিয়ে চেষ্টা করে; উত্তর শুরুর আগেই ব্যর্থ হলে ব্যাকআপে যায়।
     * উত্তর শুরু হওয়ার পর ব্যর্থ হলে ত্রুটি ছুঁড়ে দেয় (আধা উত্তর দুই provider-এর মিশেল হবে না)।
     * Provider-এর নিজস্ব system prompt থাকলে সেটা, নইলে defaultSystemPrompt ব্যবহার হয়।
     * ত্রুটি: [AiException]
     */
    fun stream(turns: List<AiTurn>, defaultSystemPrompt: String): Flow<AiStreamEvent>

    /** পুরো উত্তর একসাথে (Task Planner, screen analysis ইত্যাদির জন্য) */
    suspend fun complete(turns: List<AiTurn>, defaultSystemPrompt: String): String
}
