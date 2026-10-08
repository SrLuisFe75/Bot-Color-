package com.icon.nexus.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icon.nexus.audio.SpeechMessages
import com.icon.nexus.domain.AppState
import com.icon.nexus.onboarding.canLeaveOnboarding
import com.icon.nexus.settings.ModelProviderId
import com.icon.nexus.viewmodel.MainViewModel
import com.icon.nexus.viewmodel.VOICE_PREVIEW_SENTENCE
import com.icon.nexus.visualizer.IconCoreScene
import kotlinx.coroutines.launch

private const val LAST_STEP = 6

@Composable
fun OnboardingScreen(
    viewModel: MainViewModel,
    onFinished: () -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    var microphoneGranted by remember { mutableStateOf(false) }
    var microphoneDenied by remember { mutableStateOf(false) }
    val demoMode by viewModel.demoMode.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val languageTag by viewModel.languageTag.collectAsStateWithLifecycle()
    val previewNotice by viewModel.voicePreviewNotice.collectAsStateWithLifecycle()
    val previewActive by viewModel.voicePreviewActive.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        microphoneGranted = granted
        microphoneDenied = !granted
    }
    fun finish() {
        if (!canLeaveOnboarding(microphoneGranted)) return
        scope.launch {
            viewModel.finishOnboarding(microphoneGranted)
            onFinished()
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        IconCoreScene(
            state = AppState.Idle,
            audioLevel = 0f,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.78f)),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "${step + 1} of ${LAST_STEP + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
            )
            when (step) {
                0 -> IntroStep()
                1 -> LinesStep()
                2 -> MicrophoneStep(
                    denied = microphoneDenied,
                    onAllow = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                )
                3 -> ProviderStep(
                    demoSelected = demoMode,
                    apiKey = apiKey,
                    onDemo = { scope.launch { viewModel.setProvider(ModelProviderId.DEMO) } },
                    onGemini = { scope.launch { viewModel.setProvider(ModelProviderId.GEMINI) } },
                    onApiKey = { value -> scope.launch { viewModel.setApiKey(value) } },
                )
                4 -> VoiceStep(
                    languageTag = languageTag,
                    previewActive = previewActive,
                    unavailable = !viewModel.speechAvailable ||
                        previewNotice == SpeechMessages.UNAVAILABLE,
                    onLanguage = { value -> scope.launch { viewModel.setLanguageTag(value) } },
                    onPreview = viewModel::previewVoice,
                    onStop = viewModel::stopVoicePreview,
                )
                5 -> VisualStep()
                else -> MeetStep()
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (step < LAST_STEP) {
                    QuietAction(label = "Skip", onClick = ::finish)
                    QuietAction(
                        label = "Continue",
                        onClick = {
                            if (step == 4) viewModel.stopVoicePreview()
                            step += 1
                        },
                    )
                } else {
                    QuietAction(label = "Meet ICON.", onClick = ::finish)
                }
            }
        }
    }
}

@Composable
private fun IntroStep() {
    Text(
        text = "ICON",
        style = MaterialTheme.typography.displaySmall,
    )
    Text(
        text = "Intelligent Conversational Operating Nexus",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
    )
    Text(
        text = "Your intelligent companion.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.secondary,
    )
}

@Composable
private fun LinesStep() {
    listOf("Talk.", "Think.", "Listen.", "Respond.").forEach { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun MicrophoneStep(
    denied: Boolean,
    onAllow: () -> Unit,
) {
    Text(
        text = "Microphone",
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = SpeechMessages.EXPLANATION,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
    )
    QuietAction(label = "Allow microphone", onClick = onAllow)
    if (denied) {
        Text(
            text = "You can continue without the microphone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun ProviderStep(
    demoSelected: Boolean,
    apiKey: String,
    onDemo: () -> Unit,
    onGemini: () -> Unit,
    onApiKey: (String) -> Unit,
) {
    Text(
        text = "Provider",
        style = MaterialTheme.typography.headlineSmall,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Choice(label = "Demo", selected = demoSelected, onClick = onDemo)
        Choice(label = "Gemini", selected = !demoSelected, onClick = onGemini)
    }
    if (!demoSelected) {
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKey,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Gemini API key" },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            textStyle = MaterialTheme.typography.bodyMedium,
            label = { Text("Gemini API key") },
        )
    } else {
        Text(
            text = "Demo stays on this device and does not need a key.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
        )
    }
}

@Composable
private fun VoiceStep(
    languageTag: String,
    previewActive: Boolean,
    unavailable: Boolean,
    onLanguage: (String) -> Unit,
    onPreview: () -> Unit,
    onStop: () -> Unit,
) {
    Text(
        text = "Voice",
        style = MaterialTheme.typography.headlineSmall,
    )
    OutlinedTextField(
        value = languageTag,
        onValueChange = onLanguage,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Language" },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        label = { Text("Language") },
        placeholder = { Text("Device language") },
    )
    Text(
        text = VOICE_PREVIEW_SENTENCE,
        style = MaterialTheme.typography.bodyLarge,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        QuietAction(label = "Preview", onClick = onPreview)
        if (previewActive) {
            QuietAction(label = "Stop", onClick = onStop)
        }
    }
    if (unavailable) {
        Text(
            text = SpeechMessages.UNAVAILABLE,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun VisualStep() {
    Text(
        text = "Visual",
        style = MaterialTheme.typography.headlineSmall,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(20.dp)
            .semantics { contentDescription = "ICON CORE" },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "ICON CORE",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "The only scene.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
        )
    }
}

@Composable
private fun MeetStep() {
    Text(
        text = "Meet ICON.",
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = "The presence is home.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
    )
}

@Composable
private fun Choice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f)
        },
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 6.dp),
    )
}

@Composable
private fun QuietAction(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 8.dp),
    )
}
