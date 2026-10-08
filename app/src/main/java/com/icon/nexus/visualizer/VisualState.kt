package com.icon.nexus.visualizer

import com.icon.nexus.domain.VisualThemeId

data class VisualState(
    val amplitude: Float = 0f,
    val themeId: VisualThemeId = VisualThemeId.Nexus,
    val active: Boolean = false,
)
