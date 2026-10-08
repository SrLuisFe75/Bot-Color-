package com.icon.nexus.ui

import android.Manifest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icon.nexus.audio.SpeechMessages
import com.icon.nexus.domain.AppState
import com.icon.nexus.visualizer.IconCoreScene
import com.icon.nexus.viewmodel.MainViewModel
import com.icon.nexus.viewmodel.statusLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.appState.collectAsStateWithLifecycle()
    val chromeVisible by viewModel.chromeVisible.collectAsStateWithLifecycle()
    val transcriptVisible by viewModel.transcriptVisible.collectAsStateWithLifecycle()
    val userLine by viewModel.userLine.collectAsStateWithLifecycle()
    val iconLine by viewModel.iconLine.collectAsStateWithLifecycle()
    val audioLevel by viewModel.audioLevel.collectAsStateWithLifecycle()
    val demoMode by viewModel.demoMode.collectAsStateWithLifecycle()
    val transcriptStartsVisible by viewModel.transcriptStartsVisible.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val geminiModel by viewModel.geminiModel.collectAsStateWithLifecycle()
    val microphoneExplanation by viewModel.microphoneExplanation.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onMicrophonePermissionResult(granted)
    }
    val scope = rememberCoroutineScope()
    val presenceInteraction = remember { MutableInteractionSource() }
    var settingsOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    val alertMessage = (state as? AppState.Alert)?.message
    val statusColor = if (state is AppState.Alert) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        IconCoreScene(
            state = state,
            audioLevel = audioLevel,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (state is AppState.Alert) {
                        Modifier
                            .clickable(
                                interactionSource = presenceInteraction,
                                indication = null,
                                onClick = viewModel::onPresenceTapped,
                            )
                            .semantics { contentDescription = "Presence" }
                    } else {
                        Modifier
                    },
                ),
        )
        if (chromeVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 28.dp, end = 28.dp, bottom = 18.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (transcriptVisible || !demoMode) {
                    TranscriptLayer(
                        userLine = userLine,
                        iconLine = iconLine,
                    )
                }
                Text(
                    text = statusLabel(state),
                    style = MaterialTheme.typography.labelLarge,
                    color = statusColor,
                    textAlign = TextAlign.Center,
                )
                if (!demoMode && !alertMessage.isNullOrBlank()) {
                    Text(
                        text = alertMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (!demoMode) {
                    ChatComposer(
                        onSend = viewModel::sendText,
                        modifier = Modifier
                            .padding(top = 14.dp)
                            .fillMaxWidth(),
                    )
                }
                MicControl(
                    onClick = viewModel::onMicClicked,
                    onLongClick = viewModel::onMicLongPress,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuietControl(
                        label = "Conversation",
                        onClick = {
                            if (demoMode) {
                                viewModel.toggleTranscript()
                            } else {
                                viewModel.refreshHistory()
                                historyOpen = true
                            }
                        },
                    )
                    if (!demoMode) {
                        QuietControl(
                            label = "New conversation",
                            onClick = viewModel::newConversation,
                        )
                    }
                    QuietControl(
                        label = "Cinematic",
                        onClick = viewModel::toggleCinematic,
                    )
                    QuietControl(
                        label = "Settings",
                        onClick = { settingsOpen = true },
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (state is AppState.Alert) {
                                viewModel.onPresenceTapped()
                            }
                            viewModel.showChrome()
                        },
                    )
                    .semantics { contentDescription = "Show controls" },
            )
        }
    }

    if (settingsOpen) {
        SettingsSheet(
            demoMode = demoMode,
            transcriptStartsVisible = transcriptStartsVisible,
            apiKey = apiKey,
            geminiModel = geminiModel,
            onDemoMode = { enabled ->
                scope.launch { viewModel.setDemoMode(enabled) }
            },
            onTranscriptStartsVisible = { visible ->
                scope.launch { viewModel.setTranscriptStartsVisible(visible) }
            },
            onApiKey = { value ->
                scope.launch { viewModel.setApiKey(value) }
            },
            onGeminiModel = { value ->
                scope.launch { viewModel.setGeminiModel(value) }
            },
            onDismiss = { settingsOpen = false },
        )
    }

    if (historyOpen) {
        HistorySheet(
            conversations = history,
            transcriptVisible = transcriptVisible,
            onToggleTranscript = viewModel::toggleTranscript,
            onContinue = { id ->
                viewModel.continueConversation(id)
                historyOpen = false
            },
            onDelete = viewModel::deleteConversation,
            onDismiss = { historyOpen = false },
        )
    }

    if (microphoneExplanation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissMicrophoneExplanation,
            title = { Text("Microphone") },
            text = { Text(SpeechMessages.EXPLANATION) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.acceptMicrophoneExplanation()
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissMicrophoneExplanation) {
                    Text("Not now")
                }
            },
        )
    }
}

@Composable
private fun TranscriptLayer(
    userLine: String,
    iconLine: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (userLine.isNotBlank()) {
            TranscriptSentence(speaker = "You", sentence = userLine)
        }
        if (iconLine.isNotBlank()) {
            TranscriptSentence(speaker = "ICON", sentence = iconLine)
        }
    }
}

@Composable
private fun TranscriptSentence(
    speaker: String,
    sentence: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = speaker,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
            textAlign = TextAlign.Center,
        )
        Text(
            text = sentence,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ChatComposer(
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }
    fun submit() {
        val text = draft.trim()
        if (text.isEmpty()) return
        onSend(text)
        draft = ""
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Message" },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            placeholder = { Text("Message") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
        )
        TextButton(
            onClick = ::submit,
            modifier = Modifier.semantics { contentDescription = "Send" },
        ) {
            Text("Send")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MicControl(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics { contentDescription = "Mic" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Mic",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun QuietControl(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 6.dp),
    )
}
