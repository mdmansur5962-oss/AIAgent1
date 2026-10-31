package com.aiagent.assistant.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val Context.agentDataStore: DataStore<Preferences> by preferencesDataStore(name = "agent_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val CUSTOM_PERMISSIONS = stringSetPreferencesKey("custom_permissions")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent_palette")
        val USER_NAME = stringPreferencesKey("user_name")
    }

    val onboardingDone: Flow<Boolean> =
        context.agentDataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    val customPermissions: Flow<Set<String>> =
        context.agentDataStore.data.map { it[Keys.CUSTOM_PERMISSIONS] ?: emptySet() }

    /** Theme mode-এর নাম (SYSTEM / LIGHT / DARK), না থাকলে null */
    val themeModeName: Flow<String?> =
        context.agentDataStore.data.map { it[Keys.THEME_MODE] }

    /** Accent palette-এর নাম (VIOLET, OCEAN...), না থাকলে null */
    val accentName: Flow<String?> =
        context.agentDataStore.data.map { it[Keys.ACCENT] }

    /** User-এর নাম (Home-এর শুভেচ্ছায় দেখায়), না থাকলে খালি */
    val userName: Flow<String> =
        context.agentDataStore.data.map { it[Keys.USER_NAME] ?: "" }

    suspend fun setOnboardingDone(done: Boolean) {
        context.agentDataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun addCustomPermission(name: String) {
        context.agentDataStore.edit {
            it[Keys.CUSTOM_PERMISSIONS] = (it[Keys.CUSTOM_PERMISSIONS] ?: emptySet()) + name
        }
    }

    suspend fun removeCustomPermission(name: String) {
        context.agentDataStore.edit {
            it[Keys.CUSTOM_PERMISSIONS] = (it[Keys.CUSTOM_PERMISSIONS] ?: emptySet()) - name
        }
    }

    suspend fun setThemeModeName(name: String) {
        context.agentDataStore.edit { it[Keys.THEME_MODE] = name }
    }

    suspend fun setAccentName(name: String) {
        context.agentDataStore.edit { it[Keys.ACCENT] = name }
    }

    suspend fun setUserName(name: String) {
        context.agentDataStore.edit { it[Keys.USER_NAME] = name }
    }
}
