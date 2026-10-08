package com.icon.nexus.visualizer

import com.icon.nexus.domain.VisualThemeId

interface VisualizerTheme {
    val id: VisualThemeId
    val label: String
}

/**
 * The only implemented scene. Any other [VisualThemeId] still resolves here.
 */
object IconCoreTheme : VisualizerTheme {
    override val id: VisualThemeId = VisualThemeId.Core
    override val label: String = "ICON CORE"
}

object VisualizerThemes {
    fun forId(id: VisualThemeId): VisualizerTheme = when (id) {
        VisualThemeId.Core,
        VisualThemeId.Nexus,
        VisualThemeId.Aurora,
        VisualThemeId.Ember,
        -> IconCoreTheme
    }
}
