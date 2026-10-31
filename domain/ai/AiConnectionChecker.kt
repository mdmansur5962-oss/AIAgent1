package com.aiagent.assistant.domain.ai

interface AiConnectionChecker {
    /** ছোট একটা অনুরোধ পাঠিয়ে URL, API key ও model সব ঠিক আছে কিনা দেখে */
    suspend fun test(provider: AiProvider): ConnectionTestResult

    /** Provider-এর আসল model-এর তালিকা আনে */
    suspend fun listModels(provider: AiProvider): ModelListResult
}
