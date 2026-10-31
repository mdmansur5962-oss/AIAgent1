package com.aiagent.assistant.presentation.ai

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapHoriz
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiagent.assistant.domain.ai.AiProvider
import com.aiagent.assistant.domain.ai.AiProviderType
import com.aiagent.assistant.presentation.components.AgentDialog
import com.aiagent.assistant.presentation.components.ConfirmActionDialog
import com.aiagent.assistant.presentation.components.GlassCard
import com.aiagent.assistant.presentation.components.GradientButton
import com.aiagent.assistant.presentation.components.IconTile
import com.aiagent.assistant.presentation.components.RobotMascot
import com.aiagent.assistant.presentation.components.ShimmerBox
import com.aiagent.assistant.presentation.components.StatusPill
import com.aiagent.assistant.presentation.components.rememberAgentHaptics
import com.aiagent.assistant.presentation.settings.SettingsPage
import com.aiagent.assistant.presentation.theme.AgentDesign
import com.aiagent.assistant.presentation.theme.IconTone

/** MYRA design-এর "AI Model" পেজ: provider-এর তালিকা, ডিফল্ট/ব্যাকআপ বাছা, নতুন provider যোগ */
@Composable
fun AiModelScreen(
    onBack: () -> Unit,
    onEdit: (providerId: String?, type: String?) -> Unit,
    viewModel: AiProvidersViewModel = hiltViewModel()
) {
    val cs = MaterialTheme.colorScheme
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showPicker by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<AiProvider?>(null) }

    SettingsPage(
        title = "AI Model · এআই মডেল",
        subtitle = "আপনার AI provider বেছে নিন",
        onBack = onBack
    ) {
        GlassCard(highlighted = true) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                IconTile(icon = Icons.Rounded.Psychology, tone = IconTone.PURPLE, size = 48.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Choose Your AI Model",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface
                    )
                    Text(
                        text = "নিজের API key দিয়ে এক বা একাধিক provider যোগ করুন। যেকোনো সময় বদলানো যায়।",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }

        when {
            !state.loaded -> {
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(76.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(76.dp))
            }
            state.providers.isEmpty() -> {
                GlassCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RobotMascot(Modifier.size(80.dp))
                        Text(
                            text = "No provider yet · এখনো কোনো provider নেই",
                            style = MaterialTheme.typography.titleMedium,
                            color = cs.onSurface
                        )
                        Text(
                            text = "একটা provider যোগ করলেই চ্যাটে আসল AI উত্তর দেবে।",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                state.providers.forEach { provider ->
                    val isDefault = state.routing.defaultId == provider.id
                    val isBackup = state.routing.backupId == provider.id
                    ProviderCard(
                        provider = provider,
                        isDefault = isDefault,
                        isBackup = isBackup,
                        onSelect = {
                            when {
                                !provider.isConfigured -> onEdit(provider.id, null)
                                !provider.enabled -> Toast.makeText(
                                    context,
                                    "Enable it first · আগে চালু করুন",
                                    Toast.LENGTH_SHORT
                                ).show()
                                else -> viewModel.setDefault(provider.id)
                            }
                        },
                        onEdit = { onEdit(provider.id, null) },
                        onToggleBackup = {
                            if (isBackup) {
                                viewModel.setBackup(null)
                            } else if (isDefault) {
                                Toast.makeText(
                                    context,
                                    "Default can't be backup · ডিফল্ট আবার ব্যাকআপ হতে পারে না",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                viewModel.setBackup(provider.id)
                            }
                        },
                        onToggleEnabled = { viewModel.setEnabled(provider.id, !provider.enabled) },
                        onDelete = { deleteTarget = provider }
                    )
                }
            }
        }

        GradientButton(
            text = "Add provider · provider যোগ করুন",
            icon = Icons.Rounded.Add,
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth()
        )

        GlassCard(tint = cs.primary) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = cs.primary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Automatic fallback · স্বয়ংক্রিয় বিকল্প",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface
                    )
                    Text(
                        text = "ডিফল্ট provider ব্যর্থ হলে (ইন্টারনেট নেই, সীমা শেষ, সার্ভারে সমস্যা) ব্যাকআপ provider নিজে থেকে কাজ করবে।",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                    Text(
                        text = "If the default provider fails, the backup provider is used automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
        }

        GlassCard {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = cs.secondary)
                Text(
                    text = "API key এনক্রিপ্ট করে শুধু এই ফোনে রাখা হয়। Key বা তার screenshot কারো সাথে শেয়ার করবেন না। " +
                        "Your API keys are stored encrypted on this phone only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
        }
    }

    if (showPicker) {
        AgentDialog(
            title = "Add provider · provider যোগ করুন",
            subtitle = "কোনটা যোগ করবেন?",
            onDismiss = { showPicker = false },
            dismissText = "Cancel · বাতিল"
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AiProviderType.entries.forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                showPicker = false
                                onEdit(null, type.name)
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconTile(icon = type.icon(), tone = type.tone(), size = 40.dp, iconSize = 20.dp)
                        Column {
                            Text(
                                text = type.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                color = cs.onSurface
                            )
                            Text(
                                text = type.tagline,
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    val target = deleteTarget
    if (target != null) {
        ConfirmActionDialog(
            title = "Delete ${target.name}? · মুছবেন?",
            message = "এই provider আর তার API key ফোন থেকে মুছে যাবে।",
            confirmText = "Delete · মুছুন",
            destructive = true,
            onConfirm = {
                viewModel.delete(target.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun ProviderCard(
    provider: AiProvider,
    isDefault: Boolean,
    isBackup: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onToggleBackup: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val colors = AgentDesign.colors
    val haptics = rememberAgentHaptics()
    val shape = MaterialTheme.shapes.medium
    var menuOpen by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = if (isDefault) cs.primary else colors.glassBorder,
        label = "providerBorder"
    )
    val fillColor by animateColorAsState(
        targetValue = if (isDefault) cs.primary.copy(alpha = 0.12f) else colors.glassFill,
        label = "providerFill"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fillColor)
            .border(if (isDefault) 1.5.dp else 1.dp, borderColor, shape)
            .clickable {
                haptics.tick()
                onSelect()
            }
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconTile(icon = provider.type.icon(), tone = provider.type.tone(), size = 44.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = provider.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
                maxLines = 1
            )
            Text(
                text = provider.model.ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isDefault) StatusPill("Default", cs.primary)
                if (isBackup) StatusPill("Backup", colors.warning)
                if (!provider.enabled) StatusPill("Off", cs.onSurfaceVariant)
                if (!provider.isConfigured) StatusPill("Needs setup", cs.error)
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = cs.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Edit · সম্পাদনা") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (isBackup) "Remove backup · ব্যাকআপ সরান" else "Use as backup · ব্যাকআপ করুন") },
                    leadingIcon = { Icon(Icons.Rounded.Star, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onToggleBackup()
                    }
                )
                DropdownMenuItem(
                    text = { Text(if (provider.enabled) "Turn off · বন্ধ করুন" else "Turn on · চালু করুন") },
                    leadingIcon = { Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onToggleEnabled()
                    }
                )
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
    }
}
