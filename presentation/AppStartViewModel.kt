package com.aiagent.assistant.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiagent.assistant.data.local.AppPreferences
import com.aiagent.assistant.data.local.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppStartViewModel @Inject constructor(
    private val prefs: AppPreferences,
    settingsRepository: SettingsRepository
) : ViewModel() {

    /** null = এখনো লোড হচ্ছে */
    val onboardingDone: StateFlow<Boolean?> = prefs.onboardingDone
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val userName: StateFlow<String> = prefs.userName
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val appName: StateFlow<String> = settingsRepository.settings
        .map { it.appName }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "MYRA")

    fun completeOnboarding() {
        viewModelScope.launch { prefs.setOnboardingDone(true) }
    }
}
