package com.icon.nexus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icon.nexus.BuildConfig
import com.icon.nexus.settings.ModelProviderId
import com.icon.nexus.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenMemory: () -> Unit,
) {
    val personaName by viewModel.personaName.collectAsStateWithLifecycle()
    val personality by viewModel.personality.collectAsStateWithLifecycle()
    val voiceRate by viewModel.voiceRate.collectAsStateWithLifecycle()
    val voiceVolume by viewModel.voiceVolume.collectAsStateWithLifecycle()
    val languageTag by viewModel.languageTag.collectAsStateWithLifecycle()
    val demoMode by viewModel.demoMode.collectAsStateWithLifecycle()
    val geminiModel by viewModel.geminiModel.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val showConversation by viewModel.transcriptStartsVisible.collectAsStateWithLifecycle()
    val visualSensitivity by viewModel.visualSensitivity.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Back",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
            modifier = Modifier
                .clickable(onClick = onBack)
                .semantics { contentDescription = "Back" }
                .padding(vertical = 6.dp),
        )
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleMedium,
        )
        Category("ICON")
        OutlinedTextField(
            value = personaName,
            onValueChange = { value -> scope.launch { viewModel.setPersonaName(value) } },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Assistant name" },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            label = { Text("Name") },
            placeholder = { Text("ICON") },
        )
        OutlinedTextField(
            value = personality,
            onValueChange = { value -> scope.launch { viewModel.setPersonality(value) } },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Personality" },
            minLines = 2,
            maxLines = 4,
            textStyle = MaterialTheme.typography.bodyMedium,
            label = { Text("Personality") },
        )
        Category("VOICE")
        SettingNumber(
            label = "Speech rate",
            value = voiceRate,
            onValue = { value -> scope.launch { viewModel.setVoiceRate(value) } },
        )
        SettingNumber(
            label = "Volume",
            value = voiceVolume,
            onValue = { value -> scope.launch { viewModel.setVoiceVolume(value) } },
        )
        OutlinedTextField(
            value = languageTag,
            onValueChange = { value -> scope.launch { viewModel.setLanguageTag(value) } },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Language" },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            label = { Text("Language") },
            placeholder = { Text("Device language") },
        )
        Category("AI")
        ProviderChoice(
            demoSelected = demoMode,
            onDemo = { scope.launch { viewModel.setProvider(ModelProviderId.DEMO) } },
            onGemini = { scope.launch { viewModel.setProvider(ModelProviderId.GEMINI) } },
        )
        OutlinedTextField(
            value = geminiModel,
            onValueChange = { value -> scope.launch { viewModel.setGeminiModel(value) } },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Model" },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            label = { Text("Model") },
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { value -> scope.launch { viewModel.setApiKey(value) } },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Gemini API key" },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            textStyle = MaterialTheme.typography.bodyMedium,
            label = { Text("Gemini API key") },
        )
        Category("VISUAL")
        Text(
            text = "Theme",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
        )
        Text(
            text = "ICON CORE",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { contentDescription = "Theme ICON CORE" },
        )
        SettingNumber(
            label = "Visual sensitivity",
            value = visualSensitivity,
            onValue = { value -> scope.launch { viewModel.setVisualSensitivity(value) } },
        )
        SettingSwitch(
            label = "Show conversation",
            checked = showConversation,
            onCheckedChange = { visible ->
                scope.launch { viewModel.setTranscriptStartsVisible(visible) }
            },
        )
        Category("MEMORY")
        Text(
            text = "Memory",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenMemory)
                .semantics { contentDescription = "Memory" }
                .padding(vertical = 8.dp),
        )
        Category("PRIVACY")
        Text(
            text = "Settings, including the Gemini API key, stay in encrypted storage on this device. Conversation history stays in the app database. Demo sessions are not saved. Memories stay in a separate database, and only after you turn remembering on. Spoken replies use Android text-to-speech on the device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
        )
        Text(
            text = "Demo runs on this device and does not contact Gemini. With Gemini, your message, the conversation so far, the assistant name, the personality, and any facts you asked ICON to keep are sent to Google. The API key goes only in the request header. Speech recognition prefers on-device recognition and may use the network if the device cannot recognize offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
        )
        Category("ABOUT")
        Text(
            text = "ICON",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = "Intelligent Conversational Operating Nexus",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
        )
        Text(
            text = "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
            modifier = Modifier.semantics { contentDescription = "Version" },
        )
    }
}

@Composable
private fun Category(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ProviderChoice(
    demoSelected: Boolean,
    onDemo: () -> Unit,
    onGemini: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        ProviderOption(label = "Demo", selected = demoSelected, onClick = onDemo)
        ProviderOption(label = "Gemini", selected = !demoSelected, onClick = onGemini)
    }
}

@Composable
private fun ProviderOption(
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
private fun SettingNumber(
    label: String,
    value: Float,
    onValue: (Float) -> Unit,
) {
    var text by remember { mutableStateOf(formatSettingNumber(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { next ->
            text = next
            val parsed = next.toFloatOrNull() ?: return@OutlinedTextField
            onValue(parsed)
        },
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.bodyMedium,
        label = { Text(label) },
    )
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

private fun formatSettingNumber(value: Float): String {
    val text = value.toString()
    return if (text.endsWith(".0")) text.removeSuffix(".0") else text
}
