package com.aiagent.assistant.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** সাধারণ dialog: আইকন, শিরোনাম, বিবরণ, নিজের content, আর দুটি বাটন */
@Composable
fun AgentDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = null,
    destructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = cs.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (destructive) cs.errorContainer else cs.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (destructive) cs.onErrorContainer else cs.onPrimaryContainer
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = cs.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }
                content()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (dismissText != null) {
                        GlassButton(
                            text = dismissText,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (confirmText != null && onConfirm != null) {
                        GradientButton(
                            text = confirmText,
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            destructive = destructive
                        )
                    }
                }
            }
        }
    }
}

/**
 * স্পর্শকাতর কাজের আগে (কল, SMS পাঠানো, delete) confirmation চাওয়ার dialog।
 * Step 13-এ Task Planner এটা ব্যবহার করবে।
 */
@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "Confirm · নিশ্চিত করুন",
    dismissText: String = "Cancel · বাতিল",
    destructive: Boolean = false
) {
    AgentDialog(
        title = title,
        subtitle = message,
        onDismiss = onDismiss,
        modifier = modifier,
        icon = if (destructive) Icons.Rounded.Warning else Icons.Rounded.Info,
        confirmText = confirmText,
        onConfirm = onConfirm,
        dismissText = dismissText,
        destructive = destructive
    )
}
