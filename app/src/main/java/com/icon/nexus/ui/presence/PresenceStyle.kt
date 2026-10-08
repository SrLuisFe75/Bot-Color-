package com.icon.nexus.ui.presence

import com.icon.nexus.domain.AppState
import com.icon.nexus.ui.theme.IconPalette

/**
 * How the presence should look and move for one [AppState].
 * Motion numbers are identity, not audio levels.
 */
data class PresenceStyle(
    val coreColor: Int,
    val ringColor: Int,
    val glowColor: Int,
    val breathPeriodMillis: Int,
    val scaleMin: Float,
    val scaleMax: Float,
    val ringCycles: Float,
    val inward: Float,
    val pulse: Float,
    val alert: Boolean,
)

fun presenceStyleFor(state: AppState): PresenceStyle = when (state) {
    AppState.Idle -> IdlePresence
    is AppState.Listening -> ListeningPresence
    is AppState.Thinking -> ThinkingPresence
    is AppState.Speaking -> SpeakingPresence
    is AppState.Alert -> AlertPresence
}

private val IdlePresence = PresenceStyle(
    coreColor = IconPalette.CORE,
    ringColor = 0x5A8AF3FF.toInt(),
    glowColor = 0x3348D4EA.toInt(),
    breathPeriodMillis = 5200,
    scaleMin = 0.96f,
    scaleMax = 1.04f,
    ringCycles = 0.25f,
    inward = 0f,
    pulse = 0.08f,
    alert = false,
)

private val ListeningPresence = PresenceStyle(
    coreColor = 0xFFF4FEFF.toInt(),
    ringColor = 0xA07EEFFF.toInt(),
    glowColor = 0x5538E4FF,
    breathPeriodMillis = 2600,
    scaleMin = 0.97f,
    scaleMax = 1.03f,
    ringCycles = 0.8f,
    inward = 0.85f,
    pulse = 0.20f,
    alert = false,
)

private val ThinkingPresence = PresenceStyle(
    coreColor = IconPalette.CORE,
    ringColor = 0xC49AF6FF.toInt(),
    glowColor = 0x6640D0FF,
    breathPeriodMillis = 1400,
    scaleMin = 0.95f,
    scaleMax = 1.06f,
    ringCycles = 2.2f,
    inward = 0.22f,
    pulse = 0.34f,
    alert = false,
)

private val SpeakingPresence = PresenceStyle(
    coreColor = 0xFFF7FEFF.toInt(),
    ringColor = 0xE8C9FBFF.toInt(),
    glowColor = 0x7758E8FF,
    breathPeriodMillis = 860,
    scaleMin = 0.93f,
    scaleMax = 1.09f,
    ringCycles = 1.2f,
    inward = 0.04f,
    pulse = 0.72f,
    alert = false,
)

private val AlertPresence = PresenceStyle(
    coreColor = IconPalette.HIGHLIGHT,
    ringColor = IconPalette.VIOLET,
    glowColor = 0x668AF3FF,
    breathPeriodMillis = 2800,
    scaleMin = 0.985f,
    scaleMax = 1.035f,
    ringCycles = 0.45f,
    inward = 0f,
    pulse = 0.24f,
    alert = true,
)
