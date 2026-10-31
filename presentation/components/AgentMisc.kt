package com.aiagent.assistant.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aiagent.assistant.presentation.theme.AgentDesign
import com.aiagent.assistant.presentation.theme.AgentExtraShapes

/** পুরো screen-এর পেছনের gradient + নরম আলোর দুটি বৃত্ত */
@Composable
fun GradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val dark = AgentDesign.colors.isDark
    val base = cs.background
    val glowA = cs.primary.copy(alpha = if (dark) 0.22f else 0.14f)
    val glowB = cs.secondary.copy(alpha = if (dark) 0.16f else 0.10f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(base)
            .drawBehind {
                val radiusA = size.minDimension * 0.9f
                val centerA = Offset(size.width * 0.85f, size.height * 0.08f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowA, Color.Transparent),
                        center = centerA,
                        radius = radiusA
                    ),
                    radius = radiusA,
                    center = centerA
                )
                val radiusB = size.minDimension * 0.8f
                val centerB = Offset(size.width * 0.1f, size.height * 0.85f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowB, Color.Transparent),
                        center = centerB,
                        radius = radiusB
                    ),
                    radius = radiusB,
                    center = centerB
                )
            },
        content = content
    )
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier
            .clip(AgentExtraShapes.pill)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Column(modifier = modifier.padding(top = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AgentSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val cs = MaterialTheme.colorScheme
    val haptics = rememberAgentHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                haptics.tick()
                onCheckedChange(!checked)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                haptics.tick()
                onCheckedChange(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = cs.primary
            )
        )
    }
}

/** Light / Dark / System-এর মতো কয়েকটি বিকল্পের মধ্যে একটি বাছার টগল */
@Composable
fun SegmentedPicker(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val haptics = rememberAgentHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(shape = AgentExtraShapes.pill)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val fill: Brush = if (selected) AgentDesign.colors.brand else SolidColor(Color.Transparent)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(AgentExtraShapes.pill)
                    .background(fill)
                    .clickable {
                        haptics.tick()
                        onSelect(index)
                    }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) Color.White else cs.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
