package com.aiagent.assistant.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aiagent.assistant.presentation.components.glassSurface
import com.aiagent.assistant.presentation.components.rememberAgentHaptics
import com.aiagent.assistant.presentation.theme.AgentDesign

/** নিচের বার্তা লেখার ঘর: বাঁয়ে attach, ডানে (লেখা থাকলে) send, না থাকলে mic, উত্তর আসার সময় stop */
@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isGenerating: Boolean,
    hint: String,
    onAttach: () -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val haptics = rememberAgentHaptics()
    val shape = RoundedCornerShape(28.dp)
    val hasText = value.isNotBlank()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .glassSurface(shape = shape)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onAttach) {
            Icon(
                imageVector = Icons.Rounded.AttachFile,
                contentDescription = "Attach",
                tint = cs.onSurfaceVariant
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 14.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
            cursorBrush = SolidColor(cs.primary),
            maxLines = 5,
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodyLarge,
                            color = cs.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            }
        )

        val actionModifier = Modifier
            .padding(4.dp)
            .size(44.dp)
            .clip(CircleShape)
        when {
            isGenerating -> Box(
                modifier = actionModifier
                    .background(cs.error)
                    .clickable(role = Role.Button) {
                        haptics.confirm()
                        onStop()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Stop, contentDescription = "Stop", tint = Color.White)
            }
            hasText -> Box(
                modifier = actionModifier
                    .background(AgentDesign.colors.brand)
                    .clickable(role = Role.Button) {
                        haptics.confirm()
                        onSend()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send", tint = Color.White)
            }
            else -> Box(
                modifier = actionModifier
                    .background(cs.surfaceVariant.copy(alpha = 0.8f))
                    .clickable(role = Role.Button) {
                        haptics.tick()
                        onMic()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = "Voice", tint = cs.onSurface)
            }
        }
    }
}
