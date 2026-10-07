package com.icon.nexus.ui.presence

import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import com.icon.nexus.domain.AppState
import kotlin.math.min
import kotlin.math.sin

/**
 * Abstract core: a soft radial nucleus and concentric rings.
 * Paints and gradients are kept and rebuilt only when size or palette changes.
 */
@Composable
fun IconPresence(
    state: AppState,
    modifier: Modifier = Modifier,
) {
    val style = presenceStyleFor(state)
    val paints = remember { PresencePaints() }
    val phase = rememberInfiniteTransition(label = "presence").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = style.breathPeriodMillis,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width < 1f || height < 1f) return@Canvas
        paints.sync(width, height, style)
        val t = phase.value
        val wave = sin(t * TAU)
        val breath = (wave + 1f) * 0.5f
        val scale = style.scaleMin + (style.scaleMax - style.scaleMin) * breath
        val canvas = drawContext.canvas.nativeCanvas
        val cx = width * 0.5f
        val cy = height * 0.5f
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)
        canvas.translate(-cx, -cy)
        paints.glow.alpha = (160 + (70f * style.pulse * breath)).toInt().coerceIn(0, 255)
        paints.nucleus.alpha = (200 + (55f * style.pulse * wave)).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, paints.glowRadius, paints.glow)
        canvas.drawCircle(cx, cy, paints.nucleusRadius, paints.nucleus)
        val extent = paints.minExtent
        var index = 0
        while (index < RING_COUNT) {
            val ringWave = sin((t * style.ringCycles + index * 0.33f) * TAU)
            val pull = style.inward * (0.5f + 0.5f * ringWave)
            val radius = extent * RING_RADIUS[index] * (1f - 0.14f * pull)
            val shimmer = 0.7f + 0.3f * ringWave
            val paint = paints.rings[index]
            paint.strokeWidth = extent * RING_STROKE[index]
            paint.alpha = (paints.ringAlpha * shimmer).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, radius, paint)
            index += 1
        }
        canvas.restore()
        if (style.alert) {
            val outer = paints.rings[0]
            outer.strokeWidth = paints.minExtent * 0.018f
            outer.alpha = 210
            canvas.drawCircle(cx, cy, paints.minExtent * 0.86f, outer)
        }
    }
}

private class PresencePaints {
    val glow = fillPaint()
    val nucleus = fillPaint()
    val rings = Array(RING_COUNT) { strokePaint() }
    var nucleusRadius = 0f
    var glowRadius = 0f
    var minExtent = 0f
    var ringAlpha = 160

    private var cachedW = -1
    private var cachedH = -1
    private var cachedCore = 0
    private var cachedRing = 0
    private var cachedGlow = 0

    fun sync(width: Float, height: Float, style: PresenceStyle) {
        val w = width.toInt()
        val h = height.toInt()
        if (
            w == cachedW &&
            h == cachedH &&
            style.coreColor == cachedCore &&
            style.ringColor == cachedRing &&
            style.glowColor == cachedGlow
        ) {
            return
        }
        cachedW = w
        cachedH = h
        cachedCore = style.coreColor
        cachedRing = style.ringColor
        cachedGlow = style.glowColor
        val extent = min(width, height)
        minExtent = extent
        nucleusRadius = extent * 0.22f
        glowRadius = extent * 0.46f
        val cx = width * 0.5f
        val cy = height * 0.5f
        nucleus.shader = RadialGradient(
            cx,
            cy,
            nucleusRadius,
            intArrayOf(style.coreColor, fade(style.coreColor, 0x66), fade(style.coreColor, 0x00)),
            GRADIENT_STOPS,
            Shader.TileMode.CLAMP,
        )
        glow.shader = RadialGradient(
            cx,
            cy,
            glowRadius,
            intArrayOf(style.glowColor, fade(style.glowColor, 0x22), fade(style.glowColor, 0x00)),
            GRADIENT_STOPS,
            Shader.TileMode.CLAMP,
        )
        ringAlpha = (style.ringColor ushr 24) and 0xFF
        var index = 0
        while (index < RING_COUNT) {
            rings[index].color = style.ringColor
            index += 1
        }
    }
}

private fun fillPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    isDither = true
}

private fun strokePaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    isDither = true
}

private fun fade(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

private const val RING_COUNT = 3
private const val TAU = 6.2831855f
private val RING_RADIUS = floatArrayOf(0.40f, 0.56f, 0.72f)
private val RING_STROKE = floatArrayOf(0.008f, 0.006f, 0.0045f)
private val GRADIENT_STOPS = floatArrayOf(0f, 0.42f, 1f)
