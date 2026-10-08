package com.icon.nexus.visualizer

import com.icon.nexus.domain.VisualThemeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionVisualizerEngine(
    initial: VisualizerTheme = VisualizerThemes.forId(VisualThemeId.Nexus),
) : VisualizerEngine {
    private val state = MutableStateFlow(
        VisualState(themeId = initial.id),
    )

    override val visualState: StateFlow<VisualState> = state.asStateFlow()

    override fun applyTheme(theme: VisualizerTheme) {
        state.value = state.value.copy(themeId = theme.id)
    }

    override fun submitAmplitude(level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        state.value = state.value.copy(
            amplitude = clamped,
            active = clamped > 0f,
        )
    }
}
