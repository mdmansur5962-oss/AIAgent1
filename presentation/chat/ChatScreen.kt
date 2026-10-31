package com.aiagent.assistant.presentation.chat

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiagent.assistant.R
import com.aiagent.assistant.presentation.components.AgentChip
import com.aiagent.assistant.presentation.components.AgentDialog
import com.aiagent.assistant.presentation.components.AgentIconButton
import com.aiagent.assistant.presentation.components.AgentTextField
import com.aiagent.assistant.presentation.components.BrandLogo
import com.aiagent.assistant.presentation.components.ConfirmActionDialog
import com.aiagent.assistant.presentation.components.GlassButton
import com.aiagent.assistant.presentation.components.GlassCard
import com.aiagent.assistant.presentation.components.GradientButton
import com.aiagent.assistant.presentation.components.GradientBackground
import com.aiagent.assistant.presentation.components.OnlineBadge
import com.aiagent.assistant.presentation.components.brandLetter
import com.aiagent.assistant.presentation.navigation.Routes

private sealed interface ChatDialog {
    data object Rename : ChatDialog
    data object Move : ChatDialog
    data object Delete : ChatDialog
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

/**
 * Chat পেজ। Chat ট্যাব হিসেবে খুললে (onOpenDrawer) চলমান চ্যাট থাকে;
 * History থেকে খুললে (onBack) সেই চ্যাট খোলে।
 */
@Composable
fun ChatScreen(
    onNavigate: (String) -> Unit,
    onOpenDrawer: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme
    val listState = rememberLazyListState()

    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<ChatDialog?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var pendingSave by remember { mutableStateOf<String?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        val text = pendingSave
        pendingSave = null
        if (uri != null && text != null) {
            val ok = try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                } != null
            } catch (e: Exception) {
                false
            }
            toast(context, if (ok) "Saved · সংরক্ষিত" else "Could not save · সংরক্ষণ করা যায়নি")
        }
    }

    val copyToClipboard: (String) -> Unit = { text ->
        clipboard.setText(AnnotatedString(text))
        toast(context, "Copied · কপি হয়েছে")
    }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ChatEffect.CopyText -> copyToClipboard(effect.text)
                is ChatEffect.ShareText -> {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, effect.title)
                        putExtra(Intent.EXTRA_TEXT, effect.text)
                    }
                    try {
                        context.startActivity(Intent.createChooser(send, effect.title))
                    } catch (e: Exception) {
                        toast(context, "Could not share · শেয়ার করা যায়নি")
                    }
                }
                is ChatEffect.SaveText -> {
                    pendingSave = effect.text
                    saveLauncher.launch(effect.fileName)
                }
                ChatEffect.ChatDeleted -> {
                    toast(context, "Deleted · মুছে ফেলা হয়েছে")
                    onBack?.invoke()
                }
            }
        }
    }

    // নতুন বার্তা এলে বা উত্তর লেখা শুরু হলে নিচে (index 0) নামে; reverseLayout বলে লম্বা উত্তরও নিচে আটকে থাকে
    LaunchedEffect(state.messages.size, state.isGenerating) {
        listState.animateScrollToItem(0)
    }

    val hasContent = state.messages.isNotEmpty() || state.streamingText != null
    val lastMessageId = state.messages.lastOrNull()?.id
    val titleText = state.title.ifBlank { stringResource(R.string.chat_new_title) }

    GradientBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            // ───── Top bar ─────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = cs.onBackground)
                    }
                } else if (onOpenDrawer != null) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Rounded.Menu, contentDescription = "Menu", tint = cs.onBackground)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onBackground,
                        maxLines = 1
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = state.assistantName,
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant
                        )
                        OnlineBadge()
                    }
                }
                AgentIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = "New chat",
                    onClick = { viewModel.onEvent(ChatEvent.NewChat) }
                )
                if (state.chatId != null) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = cs.onBackground)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename · নাম বদলান") },
                                leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    renameDraft = state.title
                                    dialog = ChatDialog.Rename
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (state.pinned) "Unpin · পিন সরান" else "Pin · পিন করুন") },
                                leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.onEvent(ChatEvent.TogglePin)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Move to folder · ফোল্ডারে সরান") },
                                leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    dialog = ChatDialog.Move
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Copy chat · চ্যাট কপি") },
                                leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.onEvent(ChatEvent.CopyChat)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share · শেয়ার") },
                                leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.onEvent(ChatEvent.ShareChat)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save as file · ফাইলে সংরক্ষণ") },
                                leadingIcon = { Icon(Icons.Rounded.SaveAlt, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.onEvent(ChatEvent.SaveChat)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete chat · চ্যাট মুছুন") },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    dialog = ChatDialog.Delete
                                }
                            )
                        }
                    }
                }
            }

            // ───── Messages ─────
            if (!hasContent) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    GlassCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            BrandLogo(size = 44.dp, letter = state.appName.brandLetter())
                            Text(
                                text = stringResource(R.string.chat_welcome, state.assistantName),
                                style = MaterialTheme.typography.bodyLarge,
                                color = cs.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (!state.hasProvider) {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            highlighted = true
                        ) {
                            Text(
                                text = "Connect an AI to get real answers · আসল উত্তর পেতে একটা AI যুক্ত করুন",
                                style = MaterialTheme.typography.titleMedium,
                                color = cs.onSurface
                            )
                            Text(
                                text = "নিজের API key দিয়ে Groq, OpenAI, Claude ইত্যাদি যোগ করুন। Groq-এ ফ্রি সুবিধা আছে।",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                            )
                            GradientButton(
                                text = "Connect AI · AI যুক্ত করুন",
                                onClick = { onNavigate(Routes.SETTINGS_AI) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    val explain = stringResource(R.string.chat_chip_explain)
                    val email = stringResource(R.string.chat_chip_email)
                    val solve = stringResource(R.string.chat_chip_solve)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AgentChip(
                            text = explain,
                            icon = Icons.Rounded.Lightbulb,
                            onClick = { viewModel.onEvent(ChatEvent.SendText(explain)) }
                        )
                        AgentChip(
                            text = email,
                            icon = Icons.Rounded.Email,
                            onClick = { viewModel.onEvent(ChatEvent.SendText(email)) }
                        )
                        AgentChip(
                            text = solve,
                            icon = Icons.Rounded.Psychology,
                            onClick = { viewModel.onEvent(ChatEvent.SendText(solve)) }
                        )
                    }
                }
            } else {
                val reversed = remember(state.messages) { state.messages.asReversed() }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val streaming = state.streamingText
                    if (streaming != null) {
                        item(key = "streaming") {
                            StreamingBubble(text = streaming, onCopyCode = copyToClipboard)
                        }
                    }
                    items(reversed, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            canRegenerate = message.id == lastMessageId && !state.isGenerating,
                            onCopy = copyToClipboard,
                            onEdit = { viewModel.onEvent(ChatEvent.StartEdit(message)) },
                            onDelete = { viewModel.onEvent(ChatEvent.DeleteMessage(message.id)) },
                            onRegenerate = { viewModel.onEvent(ChatEvent.Regenerate) }
                        )
                    }
                }
            }

            // ───── Edit banner ─────
            if (state.editingMessageId != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Editing message · বার্তা সম্পাদনা চলছে",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.onEvent(ChatEvent.CancelEdit) }) {
                        Text("Cancel · বাতিল")
                    }
                }
            }

            // ───── Error / backup note ─────
            val chatError = state.error
            if (chatError != null) {
                ErrorBanner(
                    error = chatError,
                    onRetry = { viewModel.onEvent(ChatEvent.Retry) },
                    onOpenAiSettings = { onNavigate(Routes.SETTINGS_AI) },
                    onDismiss = { viewModel.onEvent(ChatEvent.DismissError) }
                )
            }
            val backupName = state.backupNote
            if (backupName != null && chatError == null) {
                Text(
                    text = "↪ ডিফল্ট ব্যর্থ হয়েছে, ব্যাকআপ \"$backupName\" উত্তর দিচ্ছে · Using backup: $backupName",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.secondary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            // ───── Input ─────
            ChatInputBar(
                value = state.input,
                onValueChange = { viewModel.onEvent(ChatEvent.InputChanged(it)) },
                onSend = { viewModel.onEvent(ChatEvent.Send) },
                onStop = { viewModel.onEvent(ChatEvent.Stop) },
                isGenerating = state.isGenerating,
                hint = stringResource(R.string.chat_input_hint),
                onAttach = { onNavigate(Routes.FILES) },
                onMic = { onNavigate(Routes.voice(state.chatId)) }
            )
        }
    }

    // ───── Dialogs ─────
    when (dialog) {
        ChatDialog.Rename -> AgentDialog(
            title = "Rename chat · নাম বদলান",
            onDismiss = { dialog = null },
            icon = Icons.Rounded.DriveFileRenameOutline,
            confirmText = "Save · সংরক্ষণ",
            onConfirm = {
                viewModel.onEvent(ChatEvent.Rename(renameDraft))
                dialog = null
            },
            dismissText = "Cancel · বাতিল"
        ) {
            AgentTextField(
                value = renameDraft,
                onValueChange = { renameDraft = it.take(60) },
                label = "Title · শিরোনাম",
                modifier = Modifier.fillMaxWidth()
            )
        }
        ChatDialog.Move -> AgentDialog(
            title = "Move to folder · ফোল্ডারে সরান",
            subtitle = "নতুন ফোল্ডার বানাতে History পেজে যান।",
            onDismiss = { dialog = null },
            icon = Icons.Rounded.FolderOpen,
            dismissText = "Close · বন্ধ"
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton(
                    text = "No folder · কোনো ফোল্ডার নয়",
                    highlighted = state.folderId == null,
                    onClick = {
                        viewModel.onEvent(ChatEvent.MoveToFolder(null))
                        dialog = null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                state.folders.forEach { folder ->
                    GlassButton(
                        text = folder.name,
                        highlighted = state.folderId == folder.id,
                        onClick = {
                            viewModel.onEvent(ChatEvent.MoveToFolder(folder.id))
                            dialog = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        ChatDialog.Delete -> ConfirmActionDialog(
            title = "Delete this chat? · চ্যাট মুছবেন?",
            message = "এই চ্যাটের সব বার্তা মুছে যাবে, ফিরে পাওয়া যাবে না।",
            confirmText = "Delete · মুছুন",
            destructive = true,
            onConfirm = {
                dialog = null
                viewModel.onEvent(ChatEvent.DeleteChat)
            },
            onDismiss = { dialog = null }
        )
        null -> Unit
    }
}


@Composable
private fun ErrorBanner(
    error: ChatError,
    onRetry: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(cs.errorContainer)
            .padding(start = 14.dp, top = 10.dp, end = 4.dp, bottom = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = cs.onErrorContainer,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onErrorContainer,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, top = 2.dp)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = cs.onErrorContainer)
            }
        }
        Row {
            TextButton(onClick = onRetry) {
                Text("Retry · আবার চেষ্টা", color = cs.onErrorContainer)
            }
            if (error.needsSetup) {
                TextButton(onClick = onOpenAiSettings) {
                    Text("AI Model · সেটিংস", color = cs.onErrorContainer)
                }
            }
        }
    }
}
