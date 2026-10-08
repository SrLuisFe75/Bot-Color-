package com.icon.nexus.camera

/**
 * One continuous move. [eases] is true only for the final wide view.
 * The camera does not hold or cut between segments.
 */
data class CameraSegment(
    val from: CinematicCamera,
    val to: CinematicCamera,
    val durationMillis: Long,
    val eases: Boolean,
)

data class CameraTour(
    val seed: Long,
    val segments: List<CameraSegment>,
) {
    val durationMillis: Long
        get() = segments.sumOf { it.durationMillis }
}
