package com.aiagent.assistant.presentation.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiagent.assistant.presentation.components.AgentSwitchRow
import com.aiagent.assistant.presentation.components.GlassButton
import com.aiagent.assistant.presentation.components.GlassCard
import com.aiagent.assistant.presentation.components.SectionHeader
import com.aiagent.assistant.presentation.components.StatusPill
import com.aiagent.assistant.presentation.settings.SettingsPage
import com.aiagent.assistant.presentation.theme.AgentDesign
import com.aiagent.assistant.service.agent.AgentTaskInfo
import com.aiagent.assistant.service.agent.TaskStatus

/** Background Agent-এর নিয়ন্ত্রণ: চালু/বন্ধ, বাবল, wake word, দরকারি অনুমতি, আর task queue */
@Composable
fun BackgroundAgentScreen(
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    viewModel: BackgroundAgentViewModel = hiltViewModel()
) {
    val cs = MaterialTheme.colorScheme
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val perms = state.permissions

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
    }

    SettingsPage(
        title = "Background Agent · ব্যাকগ্রাউন্ড এজেন্ট",
        subtitle = "অ্যাপ বন্ধ থাকলেও assistant কাছে থাকে",
        onBack = onBack
    ) {
        // ───── main switch ─────
        GlassCard(highlighted = state.running) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AgentSwitchRow(
                        title = "Background agent · ব্যাকগ্রাউন্ড এজেন্ট",
                        subtitle = "সবসময় থাকা notification দেখায়, সেখান থেকেই প্রশ্ন করা যায়। ফোন চালু হলে নিজে থেকে চালু হয়।",
                        checked = settings.backgroundAgentEnabled && state.running,
                        onCheckedChange = { on -> viewModel.setAgentEnabled(on) }
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusPill(
                    text = if (state.running) "Running · চলছে" else "Stopped · বন্ধ",
                    color = if (state.running) AgentDesign.colors.success else cs.onSurfaceVariant
                )
                if (state.wakeListening) {
                    StatusPill(text = "Listening · শুনছে", color = cs.primary)
                }
            }
        }

        // ───── features ─────
        SectionHeader(title = "Features · সুবিধা")
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AgentSwitchRow(
                    title = "Floating bubble · ভাসমান বাবল",
                    subtitle = "অন্য অ্যাপের উপরে টেনে সরানো যায় এমন বাবল। ট্যাপ করলে ছোট মেনু খোলে।",
                    checked = settings.bubbleEnabled,
                    onCheckedChange = { on -> viewModel.setBubble(on) }
                )
                AgentSwitchRow(
                    title = "Wake word in background · ব্যাকগ্রাউন্ডে wake word",
                    subtitle = "\"${settings.wakeWord}\" বললে সাড়া দেয়। মাইক সবসময় চালু থাকে, তাই ব্যাটারি বেশি খরচ হয়।",
                    checked = settings.wakeWordEnabled,
                    onCheckedChange = { on -> viewModel.setWake(on) }
                )
                val wakeError = state.wakeError
                if (wakeError != null) {
                    Text(wakeError, style = MaterialTheme.typography.bodySmall, color = cs.error)
                }
            }
        }

        // ───── permissions ─────
        SectionHeader(title = "Needs · যা লাগবে")
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PermissionLine("Notifications · নোটিফিকেশন", "সবসময় থাকা notification-এর জন্য", perms.notifications)
                PermissionLine("Display over other apps · অন্য অ্যাপের উপরে", "ভাসমান বাবলের জন্য", perms.overlay)
                PermissionLine("Microphone · মাইক্রোফোন", "wake word-এর জন্য", perms.microphone)
                PermissionLine("Battery exemption · ব্যাটারি ছাড়", "ফোন ঘুমালেও যাতে বন্ধ না হয়", perms.battery)
                GlassButton(
                    text = "Open Permission Center · পারমিশন সেন্টার",
                    icon = Icons.Rounded.Security,
                    onClick = onOpenPermissions,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ───── task queue ─────
        SectionHeader(title = "Task queue · কাজের সারি")
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Notification, wake word বা পরীক্ষা থেকে আসা কাজ একটার পর একটা চলে।",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        text = "Test task · পরীক্ষা",
                        icon = Icons.Rounded.PlayArrow,
                        onClick = { viewModel.sendTestTask() },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.clearFinished() }) {
                        Text("Clear · মুছুন", color = cs.secondary)
                    }
                }
                if (state.tasks.isEmpty()) {
                    Text(
                        text = "কোনো কাজ নেই · No tasks yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                } else {
                    state.tasks.forEach { task -> TaskRow(task) }
                }
            }
        }

        // ───── honest notes ─────
        GlassCard(tint = cs.secondary) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Good to know · জেনে রাখুন",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface
                )
                Text(
                    text = "কিছু ফোন (Xiaomi, Oppo, Vivo, Samsung ইত্যাদি) ব্যাকগ্রাউন্ড অ্যাপ নিজে থেকে বন্ধ করে দেয়। " +
                        "ফোনের Settings-এ এই অ্যাপের ব্যাটারি \"Unrestricted / No restrictions\" করে দিন।",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
                Text(
                    text = "Wake word চালু করতে অ্যাপ সামনে থাকা অবস্থায় সুইচ চালু করতে হয় (Android-এর নিয়ম)। ফোন বন্ধ-চালু করলে একবার অ্যাপ খুলুন।",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PermissionLine(title: String, subtitle: String, ok: Boolean) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        StatusPill(
            text = if (ok) "OK" else "Needed",
            color = if (ok) AgentDesign.colors.success else cs.error
        )
    }
}

@Composable
private fun TaskRow(task: AgentTaskInfo) {
    val cs = MaterialTheme.colorScheme
    val (label, color) = when (task.status) {
        TaskStatus.QUEUED -> "Queued" to cs.onSurfaceVariant
        TaskStatus.RUNNING -> "Running" to cs.primary
        TaskStatus.DONE -> "Done" to AgentDesign.colors.success
        TaskStatus.FAILED -> "Failed" to cs.error
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            StatusPill(text = label, color = color)
        }
        if (task.detail.isNotBlank()) {
            Text(
                text = task.detail,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}
