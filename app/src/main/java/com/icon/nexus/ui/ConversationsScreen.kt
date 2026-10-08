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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icon.nexus.data.ConversationHistory
import com.icon.nexus.viewmodel.MainViewModel

@Composable
fun ConversationsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshHistory() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
            text = "Conversations",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { contentDescription = "Conversations" },
        )
        Text(
            text = "New conversation",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = viewModel::newConversation)
                .semantics { contentDescription = "New conversation" }
                .padding(vertical = 6.dp),
        )
        if (history.isEmpty()) {
            Text(
                text = "No conversations yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            )
        }
        history.forEach { summary ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = summary.firstLine,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val whenLabel = ConversationHistory.formatStartedAt(summary.startedAt)
                if (whenLabel.isNotEmpty()) {
                    Text(
                        text = whenLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    Text(
                        text = "Resume",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { viewModel.continueConversation(summary.id) }
                            .semantics { contentDescription = "Resume ${summary.firstLine}" }
                            .padding(vertical = 4.dp),
                    )
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .clickable { viewModel.deleteConversation(summary.id) }
                            .semantics { contentDescription = "Delete ${summary.firstLine}" }
                            .padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}
