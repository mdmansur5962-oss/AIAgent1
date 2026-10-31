package com.aiagent.assistant.data.local

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.aiagent.assistant.domain.settings.ActionSpeed
import com.aiagent.assistant.domain.settings.AppLanguage
import com.aiagent.assistant.domain.settings.AppSettings
import com.aiagent.assistant.domain.settings.AutonomyMode
import com.aiagent.assistant.domain.settings.VoiceLanguage
import com.aiagent.assistant.domain.settings.VoiceStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private object K {
    val APP_NAME = stringPreferencesKey("s_app_name")
    val ASSISTANT_NAME = stringPreferencesKey("s_assistant_name")
    val WAKE_WORD = stringPreferencesKey("s_wake_word")
    val WAKE_WORD_ENABLED = booleanPreferencesKey("s_wake_word_enabled")
    val LANGUAGE = stringPreferencesKey("s_language")
    val HAPTICS = booleanPreferencesKey("s_haptics")
    val FONT_SCALE = floatPreferencesKey("s_font_scale")
    val VOICE_STYLE = stringPreferencesKey("s_voice_style")
    val VOICE_SPEED = floatPreferencesKey("s_voice_speed")
    val VOICE_PITCH = floatPreferencesKey("s_voice_pitch")
    val SPEAK_REPLIES = booleanPreferencesKey("s_speak_replies")
    val CONTINUOUS = booleanPreferencesKey("s_continuous_listening")
    val VOICE_LANGUAGE = stringPreferencesKey("s_voice_language")
    val AUTONOMY = stringPreferencesKey("s_autonomy")
    val ACTION_SPEED = stringPreferencesKey("s_action_speed")
    val MAX_RETRIES = intPreferencesKey("s_max_retries")
    val STOP_ON_ERROR = booleanPreferencesKey("s_stop_on_error")
    val PROGRESS_BUBBLE = booleanPreferencesKey("s_progress_bubble")
    val BG_AGENT = booleanPreferencesKey("s_background_agent")
    val BUBBLE = booleanPreferencesKey("s_bubble")
}

private inline fun <reified T : Enum<T>> enumOf(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

private fun Preferences.toSettings(): AppSettings {
    val d = AppSettings()
    return AppSettings(
        appName = this[K.APP_NAME] ?: d.appName,
        assistantName = this[K.ASSISTANT_NAME] ?: d.assistantName,
        wakeWord = this[K.WAKE_WORD] ?: d.wakeWord,
        wakeWordEnabled = this[K.WAKE_WORD_ENABLED] ?: d.wakeWordEnabled,
        language = enumOf(this[K.LANGUAGE], d.language),
        hapticsEnabled = this[K.HAPTICS] ?: d.hapticsEnabled,
        fontScale = this[K.FONT_SCALE] ?: d.fontScale,
        voiceStyle = enumOf(this[K.VOICE_STYLE], d.voiceStyle),
        voiceSpeed = this[K.VOICE_SPEED] ?: d.voiceSpeed,
        voicePitch = this[K.VOICE_PITCH] ?: d.voicePitch,
        speakReplies = this[K.SPEAK_REPLIES] ?: d.speakReplies,
        continuousListening = this[K.CONTINUOUS] ?: d.continuousListening,
        voiceLanguage = enumOf(this[K.VOICE_LANGUAGE], d.voiceLanguage),
        autonomyMode = enumOf(this[K.AUTONOMY], d.autonomyMode),
        actionSpeed = enumOf(this[K.ACTION_SPEED], d.actionSpeed),
        maxRetries = this[K.MAX_RETRIES] ?: d.maxRetries,
        stopOnError = this[K.STOP_ON_ERROR] ?: d.stopOnError,
        showProgressBubble = this[K.PROGRESS_BUBBLE] ?: d.showProgressBubble,
        backgroundAgentEnabled = this[K.BG_AGENT] ?: d.backgroundAgentEnabled,
        bubbleEnabled = this[K.BUBBLE] ?: d.bubbleEnabled
    )
}

private fun MutablePreferences.writeSettings(s: AppSettings) {
    this[K.APP_NAME] = s.appName
    this[K.ASSISTANT_NAME] = s.assistantName
    this[K.WAKE_WORD] = s.wakeWord
    this[K.WAKE_WORD_ENABLED] = s.wakeWordEnabled
    this[K.LANGUAGE] = s.language.name
    this[K.HAPTICS] = s.hapticsEnabled
    this[K.FONT_SCALE] = s.fontScale
    this[K.VOICE_STYLE] = s.voiceStyle.name
    this[K.VOICE_SPEED] = s.voiceSpeed
    this[K.VOICE_PITCH] = s.voicePitch
    this[K.SPEAK_REPLIES] = s.speakReplies
    this[K.CONTINUOUS] = s.continuousListening
    this[K.VOICE_LANGUAGE] = s.voiceLanguage.name
    this[K.AUTONOMY] = s.autonomyMode.name
    this[K.ACTION_SPEED] = s.actionSpeed.name
    this[K.MAX_RETRIES] = s.maxRetries
    this[K.STOP_ON_ERROR] = s.stopOnError
    this[K.PROGRESS_BUBBLE] = s.showProgressBubble
    this[K.BG_AGENT] = s.backgroundAgentEnabled
    this[K.BUBBLE] = s.bubbleEnabled
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val settings: Flow<AppSettings> = context.agentDataStore.data
        .map { it.toSettings() }
        .distinctUntilChanged()

    /** বর্তমান settings পড়ে বদলে আবার লেখে (একসাথে নিরাপদে) */
    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.agentDataStore.edit { prefs ->
            prefs.writeSettings(transform(prefs.toSettings()))
        }
    }
}
