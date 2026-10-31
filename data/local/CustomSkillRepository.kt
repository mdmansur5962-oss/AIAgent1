package com.aiagent.assistant.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aiagent.assistant.service.skills.CustomSkillSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** ব্যবহারকারীর বানানো skill-এর তালিকা (গোপন কিছু নেই, তাই সাধারণ DataStore-এ রাখা হয়) */
@Singleton
class CustomSkillRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    private val key = stringPreferencesKey("custom_skills_json")
    private val serializer = ListSerializer(CustomSkillSpec.serializer())

    private fun decode(text: String?): List<CustomSkillSpec> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(serializer, text)
        } catch (e: Exception) {
            emptyList()
        }
    }

    val specs: Flow<List<CustomSkillSpec>> = context.agentDataStore.data.map { decode(it[key]) }

    suspend fun upsert(spec: CustomSkillSpec) {
        context.agentDataStore.edit { prefs ->
            val list = decode(prefs[key]).toMutableList()
            val index = list.indexOfFirst { it.id == spec.id }
            if (index >= 0) list[index] = spec else list.add(spec)
            prefs[key] = json.encodeToString(serializer, list)
        }
    }

    suspend fun delete(id: String) {
        context.agentDataStore.edit { prefs ->
            val list = decode(prefs[key]).filter { it.id != id }
            prefs[key] = json.encodeToString(serializer, list)
        }
    }
}
