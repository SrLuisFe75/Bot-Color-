package com.icon.nexus.visualizer

import com.icon.nexus.domain.VisualThemeId

interface VisualizerTheme {
    val id: VisualThemeId
    val label: String
}

data class PaletteTheme(
    override val id: VisualThemeId,
    override val label: String,
) : VisualizerTheme

object VisualizerThemes {
    val nexus: VisualizerTheme = PaletteTheme(VisualThemeId.Nexus, "Nexus")
    val aurora: VisualizerTheme = PaletteTheme(VisualThemeId.Aurora, "Aurora")
    val ember: VisualizerTheme = PaletteTheme(VisualThemeId.Ember, "Ember")

    fun forId(id: VisualThemeId): VisualizerTheme = when (id) {
        VisualThemeId.Nexus -> nexus
        VisualThemeId.Aurora -> aurora
        VisualThemeId.Ember -> ember
    }
}
