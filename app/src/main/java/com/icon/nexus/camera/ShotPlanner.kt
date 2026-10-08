package com.icon.nexus.camera

import kotlin.math.abs
import kotlin.random.Random

/**
 * Builds a continuous 2D camera path over the live scene. [plan] still maps
 * shots to frames. [tour] is one seeded run of about 30 seconds. Only the
 * last segment eases, and it eases into the wide view. [cancel] eases back
 * to the identity framing without a jump.
 */
class ShotPlanner {
    fun plan(shots: List<Shot>): List<CinematicCamera> = shots.map(::frame)

    fun frame(shot: Shot): CinematicCamera = CinematicCamera(
        panX = shot.panX * shot.parallax,
        panY = shot.panY * shot.parallax,
        zoom = shot.zoom.coerceIn(MIN_ZOOM, MAX_ZOOM),
        parallax = shot.parallax,
    )

    fun tour(seed: Long): CameraTour {
        val random = Random(seed)
        val wander = wanderShots(random)
        val frames = ArrayList<CinematicCamera>(wander.size + 2)
        frames.add(CinematicCamera())
        wander.forEach { frames.add(frame(it)) }
        frames.add(frame(WIDE))
        val durations = durations(frames.size - 1, random)
        val segments = ArrayList<CameraSegment>(durations.size)
        var index = 0
        while (index < durations.size) {
            segments.add(
                CameraSegment(
                    from = frames[index],
                    to = frames[index + 1],
                    durationMillis = durations[index],
                    eases = index == durations.lastIndex,
                ),
            )
            index += 1
        }
        return CameraTour(seed = seed, segments = segments)
    }

    fun position(tour: CameraTour, elapsedMillis: Long): CinematicCamera {
        if (tour.segments.isEmpty()) return CinematicCamera()
        var remaining = elapsedMillis.coerceAtLeast(0L)
        for (segment in tour.segments) {
            if (remaining < segment.durationMillis) {
                return sample(segment, remaining)
            }
            remaining -= segment.durationMillis
        }
        return tour.segments.last().to
    }

    fun cancel(from: CinematicCamera, elapsedMillis: Long): CinematicCamera {
        val raw = (elapsedMillis.toFloat() / CANCEL_MILLIS.toFloat()).coerceIn(0f, 1f)
        return lerp(from, CinematicCamera(), easeOut(raw))
    }

    private fun wanderShots(random: Random): List<Shot> {
        val count = 3 + random.nextInt(3)
        val shots = ArrayList<Shot>(count)
        var previous = CinematicCamera()
        var index = 0
        while (index < count) {
            var shot = randomShot(random, index)
            var guard = 0
            while (travel(previous, frame(shot)) < MIN_TRAVEL && guard < 6) {
                shot = randomShot(random, index + guard + 1)
                guard += 1
            }
            if (travel(previous, frame(shot)) < MIN_TRAVEL) {
                shot = separated(previous, index)
            }
            shots.add(shot)
            previous = frame(shot)
            index += 1
        }
        return shots
    }

    private fun randomShot(random: Random, index: Int): Shot {
        return Shot(
            id = "move-$index",
            panX = random.nextFloat() * 0.36f - 0.18f,
            panY = random.nextFloat() * 0.36f - 0.18f,
            zoom = 1.2f + random.nextFloat() * 0.6f,
            parallax = 0.72f + random.nextFloat() * 0.36f,
        )
    }

    private fun separated(previous: CinematicCamera, index: Int): Shot {
        return Shot(
            id = "move-$index",
            panX = if (previous.panX < 0f) 0.16f else -0.16f,
            panY = if (previous.panY < 0f) 0.12f else -0.12f,
            zoom = if (previous.zoom < 1.45f) 1.75f else 1.25f,
            parallax = if (previous.parallax < 0.9f) 1.05f else 0.75f,
        )
    }

    private fun durations(count: Int, random: Random): LongArray {
        val weights = FloatArray(count) { 0.85f + random.nextFloat() * 0.5f }
        var weightSum = 0f
        var index = 0
        while (index < count) {
            weightSum += weights[index]
            index += 1
        }
        val durations = LongArray(count)
        index = 0
        while (index < count) {
            durations[index] = (weights[index] / weightSum * TOUR_MILLIS).toLong().coerceAtLeast(1L)
            index += 1
        }
        var drift = TOUR_MILLIS - durations.sum()
        index = count - 1
        while (drift != 0L && index >= 0) {
            val adjusted = durations[index] + drift
            if (adjusted >= 1L) {
                durations[index] = adjusted
                drift = 0L
            } else {
                drift -= 1L - durations[index]
                durations[index] = 1L
                index -= 1
            }
        }
        return durations
    }

    private fun sample(segment: CameraSegment, elapsedMillis: Long): CinematicCamera {
        val raw = elapsedMillis.toFloat() / segment.durationMillis.toFloat()
        val t = if (segment.eases) easeOut(raw) else raw.coerceIn(0f, 1f)
        return lerp(segment.from, segment.to, t)
    }

    companion object {
        const val TOUR_MILLIS = 30_000L
        const val CANCEL_MILLIS = 800L
        private const val MIN_ZOOM = 0.25f
        private const val MAX_ZOOM = 4f
        private const val MIN_TRAVEL = 0.04f
        private val WIDE = Shot(
            id = "wide",
            panX = 0f,
            panY = 0f,
            zoom = 1f,
            parallax = 1f,
        )

        fun travel(from: CinematicCamera, to: CinematicCamera): Float {
            return abs(from.panX - to.panX) +
                abs(from.panY - to.panY) +
                abs(from.zoom - to.zoom) +
                abs(from.parallax - to.parallax)
        }

        private fun easeOut(t: Float): Float {
            val x = t.coerceIn(0f, 1f)
            val remaining = 1f - x
            return 1f - remaining * remaining
        }

        private fun lerp(from: CinematicCamera, to: CinematicCamera, t: Float): CinematicCamera {
            return CinematicCamera(
                panX = from.panX + (to.panX - from.panX) * t,
                panY = from.panY + (to.panY - from.panY) * t,
                zoom = from.zoom + (to.zoom - from.zoom) * t,
                parallax = from.parallax + (to.parallax - from.parallax) * t,
            )
        }
    }
}
