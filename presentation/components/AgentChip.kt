package com.aiagent.assistant.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aiagent.assistant.presentation.theme.AgentExtraShapes

/** "Explain something", "Write an email"-এর মতো দ্রুত পরামর্শের pill */
@Composable
fun AgentChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    val haptics = rememberAgentHaptics()
    val shape = AgentExtraShapes.pill

    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) cs.primary.copy(alpha = 0.22f) else AgentDesignGlass())
            .border(1.dp, if (selected) cs.primary else cs.primary.copy(alpha = 0.40f), shape)
            .clickable(role = Role.Button) {
                haptics.tick()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurface,
            maxLines = 1
        )
    }
}

@Composable
private fun AgentDesignGlass() = com.aiagent.assistant.presentation.theme.AgentDesign.colors.glassFill
