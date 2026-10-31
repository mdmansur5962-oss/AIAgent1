package com.aiagent.assistant.domain.ai

enum class ApiStyle {
    /** OpenAI-র মতো /chat/completions (Groq, OpenRouter, Mistral, Together, DeepSeek, Ollama...) */
    OPENAI_COMPATIBLE,

    /** Anthropic Claude-এর নিজস্ব /v1/messages */
    ANTHROPIC
}

/**
 * সমর্থিত provider-এর তালিকা। defaultModel শুধু প্রাথমিক পরামর্শ: model-এর নাম প্রায়ই বদলায়,
 * তাই editor-এ "Fetch models" দিয়ে provider-এর আসল তালিকা আনা যায়।
 */
enum class AiProviderType(
    val displayName: String,
    val tagline: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val apiStyle: ApiStyle,
    val requiresKey: Boolean,
    val suggestedModels: List<String>,
    val keyHelpUrl: String?
) {
    GROQ(
        displayName = "Groq",
        tagline = "Very fast open models",
        defaultBaseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "llama-3.3-70b-versatile",
        apiStyle = ApiStyle.OPENAI_COMPATIBLE,
        requiresKey = true,
        suggestedModels = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant"),
        keyHelpUrl = "https://console.groq.com/keys"
    ),
    OPENROUTER(
        displayName = "OpenRouter",
        tagline = "One key, hundreds of models",
        defaultBaseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "openai/gpt-4o-mini",
        apiStyle = ApiStyle.OPENAI_COMPATIBLE,
        requiresKey = true,
        suggestedModels = listOf("openai/gpt-4o-mini", "meta-llama/llama-3.3-70b-instruct"),
        keyHelpUrl = "https://openrouter.ai/keys"
    ),
    OPENAI(
        displayName = "OpenAI",
        tagline = "GPT models",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-mini",
        apiStyle = ApiStyle.OPENAI_COMPATIBLE,
        requiresKey = true,
        suggestedModels = listOf("gpt-4o", "gpt-4o-mini"),
        keyHelpUrl = "https://platform.openai.com/api-keys"
    ),
    ANTHROPIC(
        displayName = "Anthropic Claude",
        tagline = "Great for writing and analysis",
        defaultBaseUrl = "https://api.anthropic.com",
        defaultModel = "claude-sonnet-5-5",
        apiStyle = ApiStyle.ANTHROPIC,
        requiresKey = true,
        suggestedModels = listOf("claude-haiku-5-5", "claude-sonnet-5-5", "claude-opus-5-5"),
        keyHelpUrl = "https://console.anthropic.com/settings/keys"
    ),
    MISTRAL(
        displayName = "Mistral",
        tagline = "Efficient European models",
        defaultBaseUrl = "https://api.mistral.ai/v1",
        defaultModel = "mistral-small-latest",
        apiStyle = ApiStyle.OPENAI_COMPATIBLE,
        requiresKey = true,
        suggestedModels = listOf("mistral-small-latest", "mistral-large-latest"),
        keyHelpUrl = "https://console.mistral.ai/api-keys"
    ),
    TOGETHER(
        displayName = "Together AI",
        tagline = "Open-source models in the cloud",
        defaultBaseUrl = "https://api.together.xyz/v1",
        defaultModel = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
        apiStyle = ApiStyle.OPENAI_COMPATIBLE,
        requiresKey = true,
        suggestedModels = listOf("meta-llama/Llama-3.3-70B-Instruct-Turbo"),
        keyHelpUrl = "https://api.together.ai/settings/api-keys"
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        tagline = "Low-cost reasoning and chat",
        defaultBaseUrl = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-chat",
        apiStyle = ApiStyle.OPENAI_COMPATIBLE,
        requiresKey = true,
        suggestedModels = listOf("deepseek-chat", "deepseek-reasoner"),
        keyHelpUrl = "https://platform.deepseek.com/api_keys"
    ),
    OLLAMA(
        displayName = "Ollama (local)",
        tagline = "Models running on your own PC",
        defaultBaseUrl = "http://192.168.0.10:11434/v1",
        defaultModel = "llama3.2",
        apiStyle = ApiStyle.OPENAI_COMPATIBLE,
        requiresKey = false,
        suggestedModels = listOf("llama3.2", "qwen2.5"),
        keyHelpUrl = null
    ),
    CUSTOM(
        displayName = "Custom (OpenAI-compatible)",
        tagline = "Any server with an OpenAI-style API",
        defaultBaseUrl = "",
        defaultModel = "",
        apiStyle = ApiStyle.OPENAI_COMPATIBLE,
        requiresKey = false,
        suggestedModels = emptyList(),
        keyHelpUrl = null
    )
}
