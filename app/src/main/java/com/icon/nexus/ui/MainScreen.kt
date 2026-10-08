package com.icon.nexus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icon.nexus.domain.AppState
import com.icon.nexus.viewmodel.MainViewModel
import com.icon.nexus.viewmodel.statusLabel

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onTalk: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCinematic: () -> Unit,
) {
    val state by viewModel.appState.collectAsStateWithLifecycle()
    val tour by viewModel.cinematicTour.collectAsStateWithLifecycle()
    val returning by viewModel.cinematicReturning.collectAsStateWithLifecycle()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (state is AppState.Alert) viewModel.onPresenceTapped()
                },
            ),
    ) {
        CorePresence(
            viewModel = viewModel,
            state = state,
            tour = tour,
            returning = returning,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = "ICON",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
                .semantics { contentDescription = "ICON" },
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 32.dp, end = 32.dp, bottom = 36.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = statusLabel(state),
                style = MaterialTheme.typography.labelLarge,
                color = if (state is AppState.Alert) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f)
                },
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.padding(top = 36.dp))
            Text(
                text = "Talk to ICON",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clickable(onClick = onTalk)
                    .semantics { contentDescription = "Talk to ICON" }
                    .padding(vertical = 12.dp, horizontal = 18.dp),
            )
            Spacer(modifier = Modifier.padding(top = 48.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                QuietLink(label = "Cinematic", onClick = onOpenCinematic)
                QuietLink(label = "Settings", onClick = onOpenSettings)
            }
        }
    }
}

@Composable
private fun QuietLink(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f),
        modifier = Modifier
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 8.dp),
    )
}
