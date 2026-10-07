package com.icon.nexus.camera

/**
 * Turns a shot list into camera frames. Parallax scales pan. There is no
 * animation clock here.
 */
class ShotPlanner {
    fun plan(shots: List<Shot>): List<CinematicCamera> = shots.map(::frame)

    fun frame(shot: Shot): CinematicCamera = CinematicCamera(
        panX = shot.panX * shot.parallax,
        panY = shot.panY * shot.parallax,
        zoom = shot.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM),
        parallax = shot.parallax,
    )

    private companion object {
        const val MIN_ZOOM = 0.25f
        const val MAX_ZOOM = 4f
    }
}
