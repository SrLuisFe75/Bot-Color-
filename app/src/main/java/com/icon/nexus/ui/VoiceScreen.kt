package com.icon.nexus.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icon.nexus.audio.SpeechMessages
import com.icon.nexus.domain.AppState
import com.icon.nexus.ui.theme.IconPalette
import com.icon.nexus.viewmodel.MainViewModel
import com.icon.nexus.viewmodel.voiceChrome

@Composable
fun VoiceScreen(
    viewModel: MainViewModel,
    onHome: () -> Unit,
    onConversations: () -> Unit,
) {
    val state by viewModel.appState.collectAsStateWithLifecycle()
    val userLine by viewModel.userLine.collectAsStateWithLifecycle()
    val iconLine by viewModel.iconLine.collectAsStateWithLifecycle()
    val sessionActive by viewModel.voiceSessionActive.collectAsStateWithLifecycle()
    val demoMode by viewModel.demoMode.collectAsStateWithLifecycle()
    val microphoneExplanation by viewModel.microphoneExplanation.collectAsStateWithLifecycle()
    val tour by viewModel.cinematicTour.collectAsStateWithLifecycle()
    val returning by viewModel.cinematicReturning.collectAsStateWithLifecycle()
    val chrome = voiceChrome(state, userLine, iconLine, sessionActive)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onMicrophonePermissionResult(granted)
    }
    val alertMessage = (state as? AppState.Alert)?.message
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            QuietAction(label = "Home", onClick = onHome)
            QuietAction(label = "Conversations", onClick = onConversations)
        }
        CorePresence(
            viewModel = viewModel,
            state = state,
            tour = tour,
            returning = returning,
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(168.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (state is AppState.Alert) viewModel.onPresenceTapped()
                    },
                ),
        )
        Text(
            text = chrome.status,
            style = MaterialTheme.typography.titleMedium,
            color = if (state is AppState.Alert) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (!demoMode && !alertMessage.isNullOrBlank() && alertMessage != "Preview") {
            Text(
                text = alertMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Exchange(
            userLine = chrome.userLine,
            iconLine = chrome.iconLine,
            modifier = Modifier.padding(top = 16.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        if (!chrome.sessionActive && state is AppState.Idle) {
            Text(
                text = "Start",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .clickable(onClick = viewModel::startVoiceSession)
                    .semantics { contentDescription = "Start" }
                    .padding(horizontal = 28.dp, vertical = 12.dp),
            )
        }
        Text(
            text = "End session",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            modifier = Modifier
                .padding(top = 10.dp)
                .clickable(onClick = viewModel::endVoiceSession)
                .semantics { contentDescription = "End session" }
                .padding(vertical = 6.dp),
        )
        MessageField(
            onSend = viewModel::sendText,
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth(),
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
private fun Exchange(
    userLine: String,
    iconLine: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(IconPalette.INDIGO), shape)
            .clip(shape)
            .background(Color(IconPalette.FIELD_RAISED))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Line(speaker = "You", sentence = userLine)
        Line(speaker = "ICON", sentence = iconLine)
    }
}

@Composable
private fun Line(
    speaker: String,
    sentence: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = speaker,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
        )
        Text(
            text = sentence.ifBlank { " " },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.86f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MessageField(
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

@Composable
private fun QuietAction(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 6.dp),
    )
}
