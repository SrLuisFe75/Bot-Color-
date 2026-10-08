package com.icon.nexus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icon.nexus.viewmodel.MainViewModel

@Composable
fun MemoryScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val enabled by viewModel.memoryEnabled.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
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
            text = "Memory",
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Remember what I ask you to keep.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = enabled,
                onCheckedChange = viewModel::setMemoryEnabled,
                modifier = Modifier.semantics { contentDescription = "Remember what I ask you to keep." },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Memory text" },
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                placeholder = { Text("A fact to keep") },
            )
            TextButton(
                onClick = {
                    viewModel.addMemory(draft)
                    draft = ""
                },
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "Add memory" },
            ) {
                Text("Add")
            }
        }
        memories.forEach { memory ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = memory.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Delete",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f),
                    modifier = Modifier
                        .clickable { viewModel.deleteMemory(memory.id) }
                        .semantics { contentDescription = "Delete ${memory.text}" }
                        .padding(vertical = 6.dp),
                )
            }
        }
        if (memories.isNotEmpty()) {
            Text(
                text = "Clear all",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f),
                modifier = Modifier
                    .clickable(onClick = viewModel::clearMemories)
                    .semantics { contentDescription = "Clear all" }
                    .padding(vertical = 6.dp),
            )
        }
    }
}
