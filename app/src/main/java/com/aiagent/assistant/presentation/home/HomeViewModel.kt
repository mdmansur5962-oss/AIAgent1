package com.aiagent.assistant.presentation.home

import com.aiagent.assistant.core.mvi.BaseViewModel
import com.aiagent.assistant.core.mvi.NoEffect
import com.aiagent.assistant.core.mvi.UiEvent
import com.aiagent.assistant.core.mvi.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class HomeUiState(val tapCount: Int = 0) : UiState

sealed interface HomeEvent : UiEvent {
    data object OrbTapped : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor() :
    BaseViewModel<HomeUiState, HomeEvent, NoEffect>(HomeUiState()) {

    override fun onEvent(event: HomeEvent) {
        when (event) {
            HomeEvent.OrbTapped -> setState { copy(tapCount = tapCount + 1) }
        }
    }
}
