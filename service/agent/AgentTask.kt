package com.aiagent.assistant.service.agent

enum class TaskStatus { QUEUED, RUNNING, DONE, FAILED }

/** Task তালিকায় দেখানোর তথ্য */
data class AgentTaskInfo(
    val id: Long,
    val title: String,
    val source: String,
    val status: TaskStatus,
    val detail: String = ""
)

/** Queue-তে সারি দেওয়া কাজ। Step 13-এ automation-এর কাজও এখানে যোগ হবে। */
sealed interface AgentTask {
    val id: Long

    /** AI-কে একটা প্রশ্ন করা; source = notification / wake / test */
    data class Ask(
        override val id: Long,
        val prompt: String,
        val spoken: Boolean,
        val source: String
    ) : AgentTask
}

sealed interface AgentEvent {
    data class TaskStarted(val id: Long, val prompt: String) : AgentEvent
    data class TaskDone(
        val id: Long,
        val chatId: Long,
        val prompt: String,
        val reply: String,
        val spoken: Boolean
    ) : AgentEvent

    data class TaskFailed(val id: Long, val prompt: String, val message: String) : AgentEvent
}
