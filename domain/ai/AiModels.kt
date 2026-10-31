package com.aiagent.assistant.domain.ai

data class AiProvider(
    val id: String,
    val type: AiProviderType,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val systemPrompt: String = "",
    val enabled: Boolean = true
) {
    /** URL, model (এবং দরকার হলে API key) দেওয়া আছে কিনা */
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank() && (!type.requiresKey || apiKey.isNotBlank())
}

/** কোন provider ডিফল্ট, আর ডিফল্ট ব্যর্থ হলে কোনটা ব্যাকআপ */
data class AiRouting(
    val defaultId: String? = null,
    val backupId: String? = null
)

data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
    val latencyMs: Long
)

sealed interface ModelListResult {
    data class Success(val models: List<String>) : ModelListResult
    data class Failure(val message: String) : ModelListResult
}
