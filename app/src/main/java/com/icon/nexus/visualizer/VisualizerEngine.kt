package com.icon.nexus.visualizer

import kotlinx.coroutines.flow.StateFlow

/**
 * Drives [VisualState] for a procedural scene. Drawing itself is not this type.
 */
interface VisualizerEngine {
    val visualState: StateFlow<VisualState>

    fun applyTheme(theme: VisualizerTheme)

    fun submitAmplitude(level: Float)
}
