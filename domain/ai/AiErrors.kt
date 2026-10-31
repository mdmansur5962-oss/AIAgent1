package com.aiagent.assistant.domain.ai

enum class AiErrorKind {
    /** কোনো চালু provider নেই */
    NO_PROVIDER,

    /** API key ভুল বা অনুমতি নেই */
    AUTH,

    /** URL বা model ভুল */
    MODEL_OR_URL,

    /** সীমা বা ক্রেডিট শেষ */
    RATE_LIMIT,

    SERVER,
    NETWORK,
    BAD_REQUEST,
    UNKNOWN
}

class AiException(
    val kind: AiErrorKind,
    message: String,
    val providerName: String? = null
) : Exception(message)
