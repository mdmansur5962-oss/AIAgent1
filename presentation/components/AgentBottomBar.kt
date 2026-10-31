package com.aiagent.assistant.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

data class BottomBarItem(val label: String, val icon: ImageVector)

/** MYRA design-এর নিচের বার: বাছা ট্যাবের চারপাশে বেগুনি আভাযুক্ত গোল কোণের pill */
@Composable
fun AgentBottomBar(
    items: List<BottomBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val haptics = rememberAgentHaptics()
    val pillShape = RoundedCornerShape(18.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(cs.surfaceContainer.copy(alpha = 0.97f))
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val pillColor by animateColorAsState(
                targetValue = if (selected) cs.primary.copy(alpha = 0.22f) else Color.Transparent,
                label = "bottomPill$index"
            )
            val borderColor by animateColorAsState(
                targetValue = if (selected) cs.primary.copy(alpha = 0.55f) else Color.Transparent,
                label = "bottomBorder$index"
            )
            val tint by animateColorAsState(
                targetValue = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                label = "bottomTint$index"
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(pillShape)
                    .background(pillColor)
                    .border(1.dp, borderColor, pillShape)
                    .clickable(role = Role.Tab) {
                        haptics.tick()
                        onSelect(index)
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    maxLines = 1
                )
            }
        }
    }
}
