package com.icon.nexus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icon.nexus.viewmodel.MainViewModel

@Composable
fun CinematicScreen(
    viewModel: MainViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.appState.collectAsStateWithLifecycle()
    val tour by viewModel.cinematicTour.collectAsStateWithLifecycle()
    val returning by viewModel.cinematicReturning.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        if (tour == null && !returning) {
            viewModel.toggleCinematic()
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        CorePresence(
            viewModel = viewModel,
            state = state,
            tour = tour,
            returning = returning,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = "Close",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp)
                .clickable(onClick = onClose)
                .semantics { contentDescription = "Close" }
                .padding(vertical = 8.dp, horizontal = 12.dp),
        )
    }
}
