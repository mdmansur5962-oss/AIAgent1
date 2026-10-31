package com.aiagent.assistant.domain.ai

import kotlinx.coroutines.flow.StateFlow

interface AiProviderRepository {
    val providers: StateFlow<List<AiProvider>>
    val routing: StateFlow<AiRouting>

    /** এনক্রিপ্ট করা সংরক্ষণ থেকে পড়া শেষ হলে true */
    val isLoaded: StateFlow<Boolean>

    suspend fun get(id: String): AiProvider?
    suspend fun upsert(provider: AiProvider)
    suspend fun delete(id: String)
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun setDefault(id: String)
    suspend fun setBackup(id: String?)

    /**
     * চ্যাটের জন্য চেষ্টা করার ক্রম: আগে ডিফল্ট, ব্যর্থ হলে ব্যাকআপ। শুধু চালু ও সম্পূর্ণ সাজানো provider থাকে।
     * Step 8-এর AI client এটা ব্যবহার করবে।
     */
    suspend fun activeChain(): List<AiProvider>
}
