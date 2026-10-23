package com.aiagent.assistant.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver

@Composable
fun agentBackgroundBrush(): Brush {
    val cs = MaterialTheme.colorScheme
    return Brush.verticalGradient(
        colors = listOf(
            cs.primary.copy(alpha = 0.20f).compositeOver(cs.background),
            cs.background
        )
    )
}

@Composable
fun agentAccentBrush(): Brush {
    val cs = MaterialTheme.colorScheme
    return Brush.linearGradient(listOf(cs.primary, cs.secondary))
}
