package com.aiagent.assistant.domain.chat

import kotlinx.coroutines.flow.Flow

sealed interface ReplyEvent {
    /** কোন provider উত্তর দিচ্ছে (isBackup = ব্যাকআপ) */
    data class Provider(val name: String, val isBackup: Boolean) : ReplyEvent

    /** উত্তরের পরের অংশ */
    data class Text(val chunk: String) : ReplyEvent
}

/**
 * Assistant-এর উত্তর কীভাবে আসবে তার চুক্তি। ত্রুটি হলে [com.aiagent.assistant.domain.ai.AiException] ছোঁড়ে।
 */
interface ChatResponder {
    /** spoken = true: ব্যবহারকারী কথা বলে জিজ্ঞেস করছে, তাই ছোট ও বলার উপযোগী উত্তর চাই */
    fun reply(history: List<ChatMessage>, assistantName: String, spoken: Boolean = false): Flow<ReplyEvent>
}
