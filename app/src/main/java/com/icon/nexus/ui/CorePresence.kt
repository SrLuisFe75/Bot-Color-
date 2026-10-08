package com.icon.nexus.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.icon.nexus.camera.CameraTour
import com.icon.nexus.domain.AppState
import com.icon.nexus.viewmodel.MainViewModel
import com.icon.nexus.visualizer.IconCoreScene

/**
 * Reads the voice level here so a new sample redraws the core without
 * rebuilding status text or the rest of the chrome.
 */
@Composable
fun CorePresence(
    viewModel: MainViewModel,
    state: AppState,
    tour: CameraTour?,
    returning: Boolean,
    modifier: Modifier = Modifier,
) {
    val audioLevel by viewModel.audioLevel.collectAsStateWithLifecycle()
    val visualSensitivity by viewModel.visualSensitivity.collectAsStateWithLifecycle()
    IconCoreScene(
        state = state,
        audioLevel = audioLevel,
        tour = tour,
        returning = returning,
        sensitivity = visualSensitivity,
        modifier = modifier,
    )
}
