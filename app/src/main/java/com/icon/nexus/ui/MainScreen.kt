package com.icon.nexus.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.icon.nexus.viewmodel.MainViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.appState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "ICON",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = state.name,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 12.dp),
        )
        Box(
            modifier = Modifier
                .padding(top = 28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .combinedClickable(
                    onClick = { viewModel.onMicClicked() },
                    onLongClick = { viewModel.onUserInterrupt() },
                )
                .semantics { contentDescription = "Mic" }
                .padding(horizontal = 28.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Mic",
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
