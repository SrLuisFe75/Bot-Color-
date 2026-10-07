package com.icon.nexus.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icon.nexus.domain.AppState
import com.icon.nexus.ui.presence.IconPresence
import com.icon.nexus.viewmodel.MainViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.appState.collectAsStateWithLifecycle()
    val caption = if (state is AppState.Alert) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        IconPresence(
            state = state,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "ICON",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
            )
            Text(
                text = state.name,
                style = MaterialTheme.typography.labelLarge,
                color = caption,
                modifier = Modifier.padding(top = 4.dp),
            )
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .combinedClickable(
                        onClick = { viewModel.onMicClicked() },
                        onLongClick = { viewModel.onUserInterrupt() },
                    )
                    .semantics { contentDescription = "Mic" }
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Mic",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
