package com.aiagent.assistant.presentation.ai

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.ui.graphics.vector.ImageVector
import com.aiagent.assistant.domain.ai.AiProviderType
import com.aiagent.assistant.presentation.theme.IconTone

internal fun AiProviderType.icon(): ImageVector = when (this) {
    AiProviderType.GROQ -> Icons.Rounded.Bolt
    AiProviderType.OPENROUTER -> Icons.Rounded.Hub
    AiProviderType.OPENAI -> Icons.Rounded.AutoAwesome
    AiProviderType.ANTHROPIC -> Icons.Rounded.Psychology
    AiProviderType.MISTRAL -> Icons.Rounded.Cloud
    AiProviderType.TOGETHER -> Icons.Rounded.Groups
    AiProviderType.DEEPSEEK -> Icons.Rounded.Explore
    AiProviderType.OLLAMA -> Icons.Rounded.Computer
    AiProviderType.CUSTOM -> Icons.Rounded.Extension
}

internal fun AiProviderType.tone(): IconTone = when (this) {
    AiProviderType.GROQ -> IconTone.ORANGE
    AiProviderType.OPENROUTER -> IconTone.PURPLE
    AiProviderType.OPENAI -> IconTone.GREEN
    AiProviderType.ANTHROPIC -> IconTone.PINK
    AiProviderType.MISTRAL -> IconTone.SKY
    AiProviderType.TOGETHER -> IconTone.BLUE
    AiProviderType.DEEPSEEK -> IconTone.TEAL
    AiProviderType.OLLAMA -> IconTone.SLATE
    AiProviderType.CUSTOM -> IconTone.PURPLE
}
