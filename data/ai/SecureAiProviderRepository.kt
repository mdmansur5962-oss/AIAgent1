package com.aiagent.assistant.data.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aiagent.assistant.domain.ai.AiProvider
import com.aiagent.assistant.domain.ai.AiProviderRepository
import com.aiagent.assistant.domain.ai.AiProviderType
import com.aiagent.assistant.domain.ai.AiRouting
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class StoredProvider(
    val id: String,
    val type: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Float,
    val maxTokens: Int,
    val systemPrompt: String,
    val enabled: Boolean
)

@Serializable
private data class StoredState(
    val providers: List<StoredProvider> = emptyList(),
    val defaultId: String? = null,
    val backupId: String? = null
)

private data class Snapshot(
    val providers: List<AiProvider>,
    val routing: AiRouting
)

private fun AiProvider.toStored() = StoredProvider(
    id = id,
    type = type.name,
    name = name,
    baseUrl = baseUrl,
    apiKey = apiKey,
    model = model,
    temperature = temperature,
    maxTokens = maxTokens,
    systemPrompt = systemPrompt,
    enabled = enabled
)

private fun StoredProvider.toDomain() = AiProvider(
    id = id,
    type = AiProviderType.entries.firstOrNull { it.name == type } ?: AiProviderType.CUSTOM,
    name = name,
    baseUrl = baseUrl,
    apiKey = apiKey,
    model = model,
    temperature = temperature,
    maxTokens = maxTokens,
    systemPrompt = systemPrompt,
    enabled = enabled
)

/**
 * Provider-এর তালিকা (API key সহ) পুরোটা এনক্রিপ্ট করে রাখে: Android Keystore-এর AES-256 master key দিয়ে
 * EncryptedSharedPreferences। API key কখনো log বা সাধারণ ফাইলে যায় না।
 */
@Singleton
class SecureAiProviderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) : AiProviderRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _providers = MutableStateFlow<List<AiProvider>>(emptyList())
    private val _routing = MutableStateFlow(AiRouting())
    private val _loaded = MutableStateFlow(false)

    override val providers: StateFlow<List<AiProvider>> = _providers.asStateFlow()
    override val routing: StateFlow<AiRouting> = _routing.asStateFlow()
    override val isLoaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val prefs: SharedPreferences by lazy { createPrefs() }

    init {
        scope.launch {
            mutex.withLock {
                load()
                _loaded.value = true
            }
        }
    }

    private fun createPrefs(): SharedPreferences {
        fun create(): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
        return try {
            create()
        } catch (e: Exception) {
            // Keystore-এর কী নষ্ট হলে পুরনো (পড়া-অযোগ্য) ফাইল মুছে নতুন করে শুরু
            context.deleteSharedPreferences(FILE_NAME)
            create()
        }
    }

    private fun load() {
        try {
            val text = prefs.getString(KEY_STATE, null)
            if (text != null) {
                val stored = json.decodeFromString(StoredState.serializer(), text)
                _providers.value = stored.providers.map { it.toDomain() }
                _routing.value = AiRouting(stored.defaultId, stored.backupId)
            }
        } catch (e: Exception) {
            _providers.value = emptyList()
            _routing.value = AiRouting()
        }
    }

    private fun persist(snapshot: Snapshot) {
        val state = StoredState(
            providers = snapshot.providers.map { it.toStored() },
            defaultId = snapshot.routing.defaultId,
            backupId = snapshot.routing.backupId
        )
        prefs.edit()
            .putString(KEY_STATE, json.encodeToString(StoredState.serializer(), state))
            .apply()
    }

    private suspend fun mutate(block: (Snapshot) -> Snapshot) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val next = block(Snapshot(_providers.value, _routing.value))
                _providers.value = next.providers
                _routing.value = next.routing
                persist(next)
            }
        }
    }

    /** ডিফল্ট সরে গেলে বা বন্ধ হলে পরের উপযুক্ত provider-কে ডিফল্ট করে */
    private fun fixRouting(providers: List<AiProvider>, routing: AiRouting): AiRouting {
        fun usable(id: String?) = id != null && providers.any { it.id == id && it.enabled && it.isConfigured }
        var defaultId = routing.defaultId
        var backupId = routing.backupId
        if (!usable(defaultId)) {
            defaultId = providers.firstOrNull { it.enabled && it.isConfigured && it.id != backupId }?.id
        }
        if (!usable(backupId) || backupId == defaultId) backupId = null
        return AiRouting(defaultId, backupId)
    }

    override suspend fun get(id: String): AiProvider? = _providers.value.firstOrNull { it.id == id }

    override suspend fun upsert(provider: AiProvider) = mutate { snap ->
        val list = snap.providers.toMutableList()
        val index = list.indexOfFirst { it.id == provider.id }
        if (index >= 0) list[index] = provider else list.add(provider)
        Snapshot(list, fixRouting(list, snap.routing))
    }

    override suspend fun delete(id: String) = mutate { snap ->
        val list = snap.providers.filter { it.id != id }
        Snapshot(list, fixRouting(list, snap.routing))
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) = mutate { snap ->
        val list = snap.providers.map { if (it.id == id) it.copy(enabled = enabled) else it }
        Snapshot(list, fixRouting(list, snap.routing))
    }

    override suspend fun setDefault(id: String) = mutate { snap ->
        val target = snap.providers.firstOrNull { it.id == id }
        if (target == null || !target.enabled || !target.isConfigured) {
            snap
        } else {
            val backup = if (snap.routing.backupId == id) null else snap.routing.backupId
            Snapshot(snap.providers, AiRouting(defaultId = id, backupId = backup))
        }
    }

    override suspend fun setBackup(id: String?) = mutate { snap ->
        when {
            id == null -> Snapshot(snap.providers, snap.routing.copy(backupId = null))
            id == snap.routing.defaultId -> snap
            else -> {
                val target = snap.providers.firstOrNull { it.id == id }
                if (target == null || !target.enabled || !target.isConfigured) {
                    snap
                } else {
                    Snapshot(snap.providers, snap.routing.copy(backupId = id))
                }
            }
        }
    }

    override suspend fun activeChain(): List<AiProvider> {
        val providers = _providers.value
        val routing = _routing.value
        return listOfNotNull(routing.defaultId, routing.backupId)
            .mapNotNull { id -> providers.firstOrNull { it.id == id } }
            .filter { it.enabled && it.isConfigured }
    }

    private companion object {
        const val FILE_NAME = "myra_secure_ai"
        const val KEY_STATE = "state"
    }
}
