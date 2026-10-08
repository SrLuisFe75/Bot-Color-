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

    const val LOW_HEAP_CLASS_MB = 128
    const val LOW_MOTE_CAP = 24
    const val FULL_MOTE_CAP = 48

    /**
     * Mote count for a device heap class in megabytes. Below 128 MB the
     * field is smaller. The array is allocated once from this result.
     */
    fun moteCap(memoryClassMb: Int): Int {
        return if (memoryClassMb < LOW_HEAP_CLASS_MB) LOW_MOTE_CAP else FULL_MOTE_CAP
    }
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
