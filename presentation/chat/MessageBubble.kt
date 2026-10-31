package com.aiagent.assistant.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.aiagent.assistant.domain.chat.ChatMessage
import com.aiagent.assistant.domain.chat.MessageRole
import com.aiagent.assistant.presentation.chat.markdown.MarkdownText
import com.aiagent.assistant.presentation.components.TypingDots
import com.aiagent.assistant.presentation.components.rememberAgentHaptics
import com.aiagent.assistant.presentation.theme.AgentDesign
import com.aiagent.assistant.presentation.theme.AgentExtraShapes

/**
 * একটি বার্তা। চেপে ধরলে মেনু (কপি/সম্পাদনা/মোছা/আবার উত্তর)।
 * User-এর বার্তা ডানে gradient বাবলে, assistant-এর বাঁয়ে Markdown সহ।
 */
@Composable
fun MessageBubble(
    message: ChatMessage,
    canRegenerate: Boolean,
    onCopy: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val isUser = message.role == MessageRole.USER
    val haptics = rememberAgentHaptics()
    var menuOpen by remember { mutableStateOf(false) }

    val shape = if (isUser) AgentExtraShapes.bubbleUser else AgentExtraShapes.bubbleAssistant
    val base = Modifier
        .widthIn(max = 340.dp)
        .clip(shape)
    val colored = when {
        isUser -> base.background(AgentDesign.colors.brand)
        message.isError -> base.background(cs.errorContainer)
        else -> base.background(cs.surfaceContainerHigh)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box {
            Box(
                modifier = colored
                    .pointerInput(message.id) {
                        detectTapGestures(
                            onLongPress = {
                                haptics.confirm()
                                menuOpen = true
                            }
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (isUser) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                } else {
                    MarkdownText(
                        markdown = message.content,
                        onCopyCode = onCopy,
                        textColor = if (message.isError) cs.onErrorContainer else cs.onSurface
                    )
                }
            }

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Copy · কপি") },
                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onCopy(message.content)
                    }
                )
                if (isUser) {
                    DropdownMenuItem(
                        text = { Text("Edit · সম্পাদনা") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        }
                    )
                } else if (canRegenerate) {
                    DropdownMenuItem(
                        text = { Text("Regenerate · আবার উত্তর") },
                        leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRegenerate()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete · মুছুন") },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }

        if (!isUser) {
            Row(
                modifier = Modifier.padding(start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                IconButton(onClick = { onCopy(message.content) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy",
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (canRegenerate) {
                    IconButton(onClick = onRegenerate, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Regenerate",
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/** উত্তর লেখা চলাকালীন আংশিক বাবল। এখনো লেখা না এলে তিনটি লাফানো বিন্দু। */
@Composable
fun StreamingBubble(
    text: String,
    onCopyCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(AgentExtraShapes.bubbleAssistant)
                .background(cs.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (text.isBlank()) {
                TypingDots(modifier = Modifier.padding(vertical = 4.dp))
            } else {
                MarkdownText(
                    markdown = text,
                    onCopyCode = onCopyCode,
                    textColor = cs.onSurface
                )
            }
        }
    }
}
