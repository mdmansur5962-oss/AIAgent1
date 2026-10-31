package com.aiagent.assistant.presentation.ai

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiagent.assistant.domain.ai.AiProviderType
import com.aiagent.assistant.presentation.components.AgentChip
import com.aiagent.assistant.presentation.components.AgentDialog
import com.aiagent.assistant.presentation.components.AgentSlider
import com.aiagent.assistant.presentation.components.AgentSwitchRow
import com.aiagent.assistant.presentation.components.AgentTextField
import com.aiagent.assistant.presentation.components.ConfirmActionDialog
import com.aiagent.assistant.presentation.components.GlassButton
import com.aiagent.assistant.presentation.components.GlassCard
import com.aiagent.assistant.presentation.components.GradientButton
import com.aiagent.assistant.presentation.components.IconTile
import com.aiagent.assistant.presentation.components.SectionHeader
import com.aiagent.assistant.presentation.components.ShimmerBox
import com.aiagent.assistant.presentation.settings.SettingsPage
import com.aiagent.assistant.presentation.theme.AgentDesign
import kotlin.math.roundToInt

@Composable
fun ProviderEditorScreen(
    onBack: () -> Unit,
    viewModel: ProviderEditorViewModel = hiltViewModel()
) {
    val cs = MaterialTheme.colorScheme
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.done) {
        if (state.done) onBack()
    }

    SettingsPage(
        title = if (state.isNew) "Add provider · যোগ করুন" else "Edit provider · সম্পাদনা",
        subtitle = state.type.displayName,
        onBack = onBack
    ) {
        if (state.loading) {
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(60.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(60.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(60.dp))
            return@SettingsPage
        }

        val type = state.type

        // ───── header ─────
        GlassCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                IconTile(icon = type.icon(), tone = type.tone(), size = 48.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(type.displayName, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    Text(type.tagline, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            val helpUrl = type.keyHelpUrl
            if (helpUrl != null) {
                GlassButton(
                    text = "Get API key · API key নিন",
                    icon = Icons.Rounded.OpenInNew,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(helpUrl)))
                        } catch (e: Exception) {
                            // ব্রাউজার নেই
                        }
                    }
                )
            }
        }

        // ───── connection ─────
        SectionHeader(title = "Connection · সংযোগ")
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AgentTextField(
                    value = state.name,
                    onValueChange = { v -> viewModel.edit { it.copy(name = v.take(40), errors = it.errors.copy(name = null)) } },
                    label = "Name · নাম",
                    isError = state.errors.name != null,
                    supportingText = state.errors.name,
                    modifier = Modifier.fillMaxWidth()
                )
                AgentTextField(
                    value = state.baseUrl,
                    onValueChange = { v -> viewModel.edit { it.copy(baseUrl = v, errors = it.errors.copy(url = null)) } },
                    label = "Base URL",
                    isError = state.errors.url != null,
                    supportingText = state.errors.url ?: when (type) {
                        AiProviderType.OLLAMA ->
                            "ফোনে localhost মানে ফোন নিজেই। PC-র IP দিন, যেমন http://192.168.0.10:11434/v1 (PC আর ফোন একই Wi-Fi-তে)"
                        AiProviderType.CUSTOM -> "যেমন https://your-server.com/v1"
                        else -> null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                AgentTextField(
                    value = state.apiKey,
                    onValueChange = { v -> viewModel.edit { it.copy(apiKey = v, errors = it.errors.copy(key = null)) } },
                    label = if (type.requiresKey) "API key" else "API key (optional · ঐচ্ছিক)",
                    isError = state.errors.key != null,
                    supportingText = state.errors.key,
                    visualTransformation = if (state.showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingContent = {
                        IconButton(onClick = { viewModel.toggleShowKey() }) {
                            Icon(
                                imageVector = if (state.showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Show or hide key"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "🔒 Key এনক্রিপ্ট করে শুধু এই ফোনে থাকে · Stored encrypted on this phone only",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
            }
        }

        // ───── model ─────
        SectionHeader(title = "Model · মডেল")
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AgentTextField(
                    value = state.model,
                    onValueChange = { v -> viewModel.edit { it.copy(model = v, errors = it.errors.copy(model = null)) } },
                    label = "Model name · মডেলের নাম",
                    isError = state.errors.model != null,
                    supportingText = state.errors.model,
                    modifier = Modifier.fillMaxWidth()
                )
                GlassButton(
                    text = if (state.fetchingModels) "Loading... · আনা হচ্ছে" else "Fetch models · তালিকা আনুন",
                    icon = Icons.Rounded.Download,
                    enabled = !state.fetchingModels,
                    onClick = { viewModel.fetchModels() },
                    modifier = Modifier.fillMaxWidth()
                )
                val modelsError = state.modelsError
                if (modelsError != null) {
                    Text(modelsError, style = MaterialTheme.typography.bodySmall, color = cs.error)
                }
                if (type.suggestedModels.isNotEmpty()) {
                    Text(
                        text = "Suggestions · পরামর্শ",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        type.suggestedModels.forEach { suggestion ->
                            AgentChip(
                                text = suggestion,
                                selected = state.model == suggestion,
                                onClick = { viewModel.pickModel(suggestion) }
                            )
                        }
                    }
                }
            }
        }

        // ───── generation ─────
        SectionHeader(title = "Generation · উত্তর তৈরি")
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AgentSlider(
                    title = "Temperature · সৃজনশীলতা",
                    value = state.temperature,
                    valueText = { String.format("%.1f", it) },
                    valueRange = 0f..2f,
                    steps = 19,
                    onCommit = { v -> viewModel.edit { it.copy(temperature = (v * 10f).roundToInt() / 10f) } }
                )
                AgentSlider(
                    title = "Max tokens · সর্বোচ্চ দৈর্ঘ্য",
                    value = state.maxTokens.toFloat(),
                    valueText = { "${it.roundToInt()}" },
                    valueRange = 256f..8192f,
                    steps = 30,
                    onCommit = { v -> viewModel.edit { it.copy(maxTokens = v.roundToInt()) } }
                )
                AgentTextField(
                    value = state.systemPrompt,
                    onValueChange = { v -> viewModel.edit { it.copy(systemPrompt = v.take(4000)) } },
                    label = "System prompt (optional · ঐচ্ছিক)",
                    supportingText = "খালি রাখলে অ্যাপের নিজস্ব নির্দেশ ব্যবহার হবে",
                    singleLine = false,
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ───── roles ─────
        SectionHeader(title = "Role · ভূমিকা")
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AgentSwitchRow(
                    title = "Enabled · চালু",
                    subtitle = "বন্ধ থাকলে চ্যাটে এটা ব্যবহার হবে না",
                    checked = state.enabled,
                    onCheckedChange = { on -> viewModel.edit { it.copy(enabled = on) } }
                )
                if (state.wasDefault) {
                    Text(
                        text = "★ এটাই এখন ডিফল্ট provider · This is the default provider",
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.primary
                    )
                } else {
                    AgentSwitchRow(
                        title = "Use as default · ডিফল্ট করুন",
                        subtitle = "চ্যাটে সবসময় আগে এটা চেষ্টা হবে",
                        checked = state.makeDefault,
                        onCheckedChange = { on ->
                            viewModel.edit { it.copy(makeDefault = on, makeBackup = if (on) false else it.makeBackup) }
                        }
                    )
                }
                AgentSwitchRow(
                    title = "Use as backup · ব্যাকআপ করুন",
                    subtitle = "ডিফল্ট ব্যর্থ হলে এটা নিজে থেকে চলবে",
                    checked = state.makeBackup,
                    onCheckedChange = { on ->
                        viewModel.edit { it.copy(makeBackup = on, makeDefault = if (on && !it.wasDefault) false else it.makeDefault) }
                    }
                )
                val flagsError = state.errors.flags
                if (flagsError != null) {
                    Text(flagsError, style = MaterialTheme.typography.bodySmall, color = cs.error)
                }
            }
        }

        // ───── test ─────
        SectionHeader(title = "Test · পরীক্ষা")
        GlassCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GradientButton(
                    text = "Test connection · সংযোগ পরীক্ষা",
                    icon = Icons.Rounded.Wifi,
                    loading = state.testing,
                    onClick = { viewModel.testConnection() },
                    modifier = Modifier.fillMaxWidth()
                )
                val result = state.testResult
                if (result != null) {
                    val color = if (result.success) AgentDesign.colors.success else cs.error
                    Text(
                        text = result.message + if (result.success) "  (${result.latencyMs} ms)" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                }
                Text(
                    text = "একটা ছোট অনুরোধ পাঠানো হয়, তাই খুব সামান্য token খরচ হতে পারে।",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant
                )
            }
        }

        GradientButton(
            text = "Save · সংরক্ষণ করুন",
            icon = Icons.Rounded.Save,
            loading = state.saving,
            onClick = { viewModel.save() },
            modifier = Modifier.fillMaxWidth()
        )
        if (!state.isNew) {
            GradientButton(
                text = "Delete provider · মুছুন",
                icon = Icons.Rounded.Delete,
                destructive = true,
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // ───── model picker ─────
    val fetched = state.fetchedModels
    if (fetched != null) {
        ModelPickerDialog(
            models = fetched,
            onSelect = { viewModel.pickModel(it) },
            onDismiss = { viewModel.dismissModels() }
        )
    }

    if (confirmDelete) {
        ConfirmActionDialog(
            title = "Delete this provider? · মুছবেন?",
            message = "এই provider আর তার API key ফোন থেকে মুছে যাবে।",
            confirmText = "Delete · মুছুন",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun ModelPickerDialog(
    models: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val filtered = remember(models, query) {
        if (query.isBlank()) models else models.filter { it.contains(query.trim(), ignoreCase = true) }
    }

    AgentDialog(
        title = "Choose a model · মডেল বাছুন",
        subtitle = "${models.size} models",
        onDismiss = onDismiss,
        dismissText = "Close · বন্ধ"
    ) {
        AgentTextField(
            value = query,
            onValueChange = { query = it },
            label = "Search · খুঁজুন",
            leadingIcon = Icons.Rounded.Search,
            modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(
            modifier = Modifier.heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(filtered, key = { it }) { model ->
                Text(
                    text = model,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(model) }
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                )
            }
        }
    }
}
