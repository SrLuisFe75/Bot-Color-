package com.icon.nexus.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShotPlannerTest {
    private val planner = ShotPlanner()

    @Test
    fun tourLastsAboutThirtySecondsAndKeepsMoving() {
        val tour = planner.tour(7L)
        assertEquals(ShotPlanner.TOUR_MILLIS, tour.durationMillis)
        assertTrue(kotlin.math.abs(tour.durationMillis - 30_000L) < 1_000L)
        assertTrue(tour.segments.size >= 2)
        var index = 0
        while (index < tour.segments.size) {
            val segment = tour.segments[index]
            assertTrue(segment.durationMillis > 0L)
            assertTrue(ShotPlanner.travel(segment.from, segment.to) > 0.01f)
            if (index > 0) {
                assertEquals(tour.segments[index - 1].to, segment.from)
            }
            index += 1
        }
    }

    @Test
    fun twoSeedsCanDiffer() {
        val first = planner.tour(1L).segments.map { it.to }
        val second = planner.tour(2L).segments.map { it.to }
        assertNotEquals(first, second)
    }

    @Test
    fun lastSegmentEasesToTheWideView() {
        val tour = planner.tour(19L)
        val last = tour.segments.last()
        assertTrue(last.eases)
        assertCamera(CinematicCamera(), last.to)
        assertCamera(CinematicCamera(), tour.segments.first().from)
        assertFalse(tour.segments.dropLast(1).any { it.eases })
        assertCamera(CinematicCamera(), planner.position(tour, tour.durationMillis))
    }

    @Test
    fun cancelEndsAtTheIdentityTransform() {
        val moved = CinematicCamera(panX = 0.2f, panY = -0.15f, zoom = 1.6f, parallax = 0.8f)
        assertEquals(moved, planner.cancel(moved, 0L))
        val midway = planner.cancel(moved, ShotPlanner.CANCEL_MILLIS / 2)
        assertTrue(midway.zoom > 1f)
        assertTrue(midway.zoom < moved.zoom)
        assertCamera(CinematicCamera(), planner.cancel(moved, ShotPlanner.CANCEL_MILLIS))
        assertCamera(CinematicCamera(), planner.cancel(moved, ShotPlanner.CANCEL_MILLIS + 40L))
    }

    private fun assertCamera(expected: CinematicCamera, actual: CinematicCamera) {
        assertEquals(expected.panX, actual.panX, 0.0001f)
        assertEquals(expected.panY, actual.panY, 0.0001f)
        assertEquals(expected.zoom, actual.zoom, 0.0001f)
        assertEquals(expected.parallax, actual.parallax, 0.0001f)
    }
}
