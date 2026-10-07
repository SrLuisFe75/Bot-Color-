package com.icon.nexus.camera

/**
 * 2D camera over the live scene: pan, zoom, and parallax. Not a video clip
 * and not a 3D transform.
 */
data class CinematicCamera(
    val panX: Float = 0f,
    val panY: Float = 0f,
    val zoom: Float = 1f,
    val parallax: Float = 1f,
)
