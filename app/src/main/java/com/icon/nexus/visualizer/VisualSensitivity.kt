package com.icon.nexus.visualizer

/**
 * Scales a smoothed level before ICON CORE draws it. 1 leaves the level
 * unchanged. The product stays in 0..1.
 */
fun visualLevel(level: Float, sensitivity: Float): Float {
    val gain = sensitivity.coerceIn(0f, MAX_SENSITIVITY)
    return (level.coerceIn(0f, 1f) * gain).coerceIn(0f, 1f)
}

internal const val MAX_SENSITIVITY = 4f
