package com.icon.nexus.visualizer

import android.app.ActivityManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.Choreographer
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalContext
import com.icon.nexus.camera.CameraTour
import com.icon.nexus.camera.CinematicCamera
import com.icon.nexus.camera.ShotPlanner
import com.icon.nexus.domain.AppState
import com.icon.nexus.domain.VisualThemeId
import com.icon.nexus.ui.theme.IconPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws [IconCoreTheme] on a full-bleed canvas. Motes, paints, and the oval
 * path are allocated once. Gradients are rebuilt only when the size or the
 * alert palette changes.
 *
 * Idle breathes slowly at low energy. Listening pulls motes inward and
 * brightens the core. Thinking speeds the orbits without a spinner. Speaking
 * sizes the nucleus and glow from [audioLevel], and uses the slow breath only
 * when that level is near zero. Alert swaps in an amber core and a separate
 * outer amber ring.
 */
class IconCoreEngine(
    memoryClassMb: Int = IconCoreTheme.LOW_HEAP_CLASS_MB,
) : VisualizerEngine {
    private val state = MutableStateFlow(VisualState(themeId = IconCoreTheme.id))
    override val visualState: StateFlow<VisualState> = state.asStateFlow()

    private val motes = CoreMotes(IconCoreTheme.moteCap(memoryClassMb))
    private val fieldPaint = fillPaint()
    private val glowPaint = fillPaint()
    private val nucleusPaint = fillPaint()
    private val ringPaint = strokePaint()
    private val motePaint = fillPaint()
    private val alertRingPaint = strokePaint()
    private val oval = Path()
    private val ovalBounds = RectF()

    private var amplitude = 0f
    private var inward = 0f
    private var orbit = IDLE_ORBIT
    private var breath = 0.5f
    private var startNanos = 0L
    private var lastNanos = 0L
    private val ringSpin = FloatArray(RING_COUNT)
    private var cachedW = -1
    private var cachedH = -1
    private var cachedAlert = false
    private var minExtent = 0f

    override fun applyTheme(theme: VisualizerTheme) {
        val resolved = VisualizerThemes.forId(theme.id)
        if (state.value.themeId == resolved.id) return
        state.value = state.value.copy(themeId = resolved.id)
    }

    override fun submitAmplitude(level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        if (clamped == amplitude) return
        amplitude = clamped
        state.value = state.value.copy(
            amplitude = clamped,
            active = clamped > 0f,
        )
    }

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        appState: AppState,
        nowNanos: Long,
        camera: CinematicCamera = IDENTITY_CAMERA,
    ) {
        if (width < 1f || height < 1f) return
        val dt = advance(nowNanos)
        step(appState, dt)
        val alert = appState is AppState.Alert
        syncShaders(width, height, alert)
        val cx = width * 0.5f
        val cy = height * 0.5f
        val shiftX = camera.panX * minExtent
        val shiftY = camera.panY * minExtent
        canvas.save()
        canvas.translate(shiftX * FIELD_PARALLAX, shiftY * FIELD_PARALLAX)
        canvas.drawRect(-width, -height, width * 2f, height * 2f, fieldPaint)
        canvas.restore()
        canvas.save()
        canvas.translate(cx + shiftX, cy + shiftY)
        canvas.scale(camera.zoom, camera.zoom)
        canvas.translate(-cx, -cy)
        drawMotes(canvas, cx, cy, appState, dt)
        drawRings(canvas, cx, cy)
        val speaking = appState is AppState.Speaking
        val calm = amplitude < VOICE_FLOOR
        val nucleusScale = nucleusScale(appState, speaking)
        val nucleusRadius = minExtent * 0.16f * nucleusScale
        glowPaint.alpha = glowAlpha(appState, speaking, calm)
        nucleusPaint.alpha = nucleusAlpha(appState, speaking, calm)
        canvas.drawCircle(cx, cy, nucleusRadius * 2.35f, glowPaint)
        canvas.drawCircle(cx, cy, nucleusRadius, nucleusPaint)
        if (alert) {
            alertRingPaint.strokeWidth = minExtent * 0.012f
            alertRingPaint.alpha = (150 + 55f * breath).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, minExtent * 0.78f, alertRingPaint)
        }
        canvas.restore()
    }

    private fun advance(nowNanos: Long): Float {
        if (nowNanos == 0L) return 0f
        if (startNanos == 0L) startNanos = nowNanos
        if (lastNanos == 0L) {
            lastNanos = nowNanos
            return 0f
        }
        val dt = ((nowNanos - lastNanos).toFloat() / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastNanos = nowNanos
        return dt
    }

    private fun step(appState: AppState, dt: Float) {
        val elapsed = if (startNanos == 0L || lastNanos == 0L) {
            0f
        } else {
            (lastNanos - startNanos).toFloat() / 1_000_000_000f
        }
        breath = (sin(elapsed * TAU / IDLE_BREATH_SECONDS) + 1f) * 0.5f
        val blend = (dt * 1.7f).coerceIn(0f, 1f)
        val inwardTarget = coreInward(appState)
        val orbitTarget = coreOrbit(appState)
        inward += (inwardTarget - inward) * blend
        orbit += (orbitTarget - orbit) * blend
        var index = 0
        while (index < RING_COUNT) {
            ringSpin[index] += RING_SPEED[index] * orbit * dt
            index += 1
        }
    }

    private fun nucleusScale(appState: AppState, speaking: Boolean): Float {
        val breathScale = 0.96f + 0.08f * breath
        return when {
            speaking -> speakingNucleusScale(amplitude, breath)
            appState is AppState.Listening -> 1.06f
            appState is AppState.Thinking -> 1.0f + 0.03f * sin(breath * TAU)
            appState is AppState.Alert -> 1.0f
            else -> breathScale
        }
    }

    private fun glowAlpha(appState: AppState, speaking: Boolean, calm: Boolean): Int {
        val value = when {
            speaking && !calm -> 60f + 180f * amplitude
            speaking -> 110f + 40f * breath
            appState is AppState.Listening -> 210f
            appState is AppState.Thinking -> 160f
            appState is AppState.Alert -> 140f
            else -> 90f + 35f * breath
        }
        return value.toInt().coerceIn(0, 255)
    }

    private fun nucleusAlpha(appState: AppState, speaking: Boolean, calm: Boolean): Int {
        val value = when {
            speaking && !calm -> 140f + 115f * amplitude
            appState is AppState.Listening -> 245f
            appState is AppState.Alert -> 230f
            appState is AppState.Thinking -> 210f
            else -> 170f + 30f * breath
        }
        return value.toInt().coerceIn(0, 255)
    }

    private fun drawRings(canvas: Canvas, cx: Float, cy: Float) {
        var index = 0
        while (index < RING_COUNT) {
            val spin = ringSpin[index]
            val radius = minExtent * RING_RADIUS[index]
            ringPaint.strokeWidth = minExtent * RING_STROKE[index]
            ringPaint.alpha = if (index == 0) 150 else 110 - index * 18
            oval.rewind()
            ovalBounds.set(-radius, -radius * RING_RY[index], radius, radius * RING_RY[index])
            oval.addOval(ovalBounds, Path.Direction.CW)
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate(SPIN_DEGREES[index] + spin * RAD_TO_DEG)
            canvas.scale(RING_RX[index], 1f)
            canvas.drawPath(oval, ringPaint)
            canvas.restore()
            index += 1
        }
    }

    private fun drawMotes(canvas: Canvas, cx: Float, cy: Float, appState: AppState, dt: Float) {
        val energy = coreMoteEnergy(appState, amplitude)
        val reach = minExtent * 0.46f
        var index = 0
        val items = motes.motes
        while (index < items.size) {
            val mote = items[index]
            mote.angle += mote.speed * orbit * dt
            val dist = mote.homeRadius * reach * (1f - inward * 0.68f)
            val x = cx + cos(mote.angle) * dist * mote.stretchX
            val y = cy + sin(mote.angle) * dist * mote.stretchY
            val twinkle = 0.55f + 0.45f * sin(mote.angle * 2f + breath * TAU)
            motePaint.alpha = (energy * twinkle * 210f).toInt().coerceIn(0, 255)
            canvas.drawCircle(x, y, mote.size * minExtent, motePaint)
            index += 1
        }
    }

    private fun syncShaders(width: Float, height: Float, alert: Boolean) {
        val w = width.toInt()
        val h = height.toInt()
        if (w == cachedW && h == cachedH && alert == cachedAlert) return
        cachedW = w
        cachedH = h
        cachedAlert = alert
        minExtent = min(width, height)
        val cx = width * 0.5f
        val cy = height * 0.5f
        val extent = minExtent
        fieldPaint.shader = RadialGradient(
            cx,
            cy,
            extent * 0.72f,
            if (alert) FIELD_ALERT else FIELD_CORE,
            GRADIENT_STOPS,
            Shader.TileMode.CLAMP,
        )
        val core = if (alert) CORE_ALERT else CORE_NORMAL
        val glow = if (alert) GLOW_ALERT else GLOW_NORMAL
        nucleusPaint.shader = RadialGradient(
            cx,
            cy,
            extent * 0.20f,
            core,
            GRADIENT_STOPS,
            Shader.TileMode.CLAMP,
        )
        glowPaint.shader = RadialGradient(
            cx,
            cy,
            extent * 0.42f,
            glow,
            GRADIENT_STOPS,
            Shader.TileMode.CLAMP,
        )
        val ring = if (alert) IconPalette.AMBER else IconPalette.CYAN
        ringPaint.color = ring
        motePaint.color = if (alert) IconPalette.AMBER else 0xFFD9FBFF.toInt()
        alertRingPaint.color = IconPalette.AMBER
    }
}

/**
 * Full-bleed ICON CORE scene. [themeId] other than core still draws [IconCoreTheme].
 */
@Composable
fun IconCoreScene(
    state: AppState,
    audioLevel: Float,
    modifier: Modifier = Modifier,
    themeId: VisualThemeId = VisualThemeId.Core,
    tour: CameraTour? = null,
    returning: Boolean = false,
    sensitivity: Float = 1f,
) {
    val context = LocalContext.current
    val engine = remember(context) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        IconCoreEngine(manager?.memoryClass ?: IconCoreTheme.LOW_HEAP_CLASS_MB)
    }
    val planner = remember { ShotPlanner() }
    val clock = remember { CameraClock() }
    Box(
        modifier = modifier.then(
            CoreDrawElement(
                engine = engine,
                planner = planner,
                clock = clock,
                state = state,
                audioLevel = audioLevel,
                themeId = themeId,
                tour = tour,
                returning = returning,
                sensitivity = sensitivity,
            ),
        ),
    )
}

/**
 * Draws ICON CORE from the choreographer. Frame time stays on this node, so
 * a new frame invalidates this draw only and does not rebuild the chrome text.
 */
private data class CoreDrawElement(
    val engine: IconCoreEngine,
    val planner: ShotPlanner,
    val clock: CameraClock,
    val state: AppState,
    val audioLevel: Float,
    val themeId: VisualThemeId,
    val tour: CameraTour?,
    val returning: Boolean,
    val sensitivity: Float,
) : ModifierNodeElement<CoreDrawNode>() {
    override fun create(): CoreDrawNode = CoreDrawNode(engine, planner, clock)

    override fun update(node: CoreDrawNode) {
        node.state = state
        node.audioLevel = audioLevel
        node.themeId = themeId
        node.tour = tour
        node.returning = returning
        node.sensitivity = sensitivity
        node.engine.applyTheme(VisualizerThemes.forId(themeId))
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "iconCore"
    }
}

private class CoreDrawNode(
    val engine: IconCoreEngine,
    private val planner: ShotPlanner,
    private val clock: CameraClock,
) : Modifier.Node(), DrawModifierNode, Choreographer.FrameCallback {
    var state: AppState = AppState.Idle
    var audioLevel: Float = 0f
    var themeId: VisualThemeId = VisualThemeId.Core
    var tour: CameraTour? = null
    var returning: Boolean = false
    var sensitivity: Float = 1f
    private var frameNanos: Long = 0L

    override fun onAttach() {
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetach() {
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isAttached) return
        frameNanos = frameTimeNanos
        invalidateDraw()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun ContentDrawScope.draw() {
        val width = size.width
        val height = size.height
        if (width < 1f || height < 1f) return
        engine.submitAmplitude(visualLevel(audioLevel, sensitivity))
        engine.draw(
            canvas = drawContext.canvas.nativeCanvas,
            width = width,
            height = height,
            appState = state,
            nowNanos = frameNanos,
            camera = clock.sample(planner, tour, returning, frameNanos),
        )
    }
}

/**
 * Samples the live tour from frame time. Cancel eases from the camera that
 * is on screen, so the picture does not jump.
 */
private class CameraClock {
    private var activeTour: CameraTour? = null
    private var tourStartNanos = 0L
    private var easing = false
    private var cancelFrom = CinematicCamera()
    private var cancelStartNanos = 0L

    fun sample(
        planner: ShotPlanner,
        tour: CameraTour?,
        returning: Boolean,
        frameNanos: Long,
    ): CinematicCamera {
        if (tour == null && !returning) {
            activeTour = null
            easing = false
            tourStartNanos = 0L
            return IDENTITY_CAMERA
        }
        if (tour != null && tour !== activeTour) {
            activeTour = tour
            tourStartNanos = 0L
            easing = false
        }
        if (tour != null && tourStartNanos == 0L && frameNanos > 0L) {
            tourStartNanos = frameNanos
        }
        val elapsed = elapsedMillis(tourStartNanos, frameNanos)
        if (returning && !easing) {
            easing = true
            cancelFrom = if (tour != null) {
                planner.position(tour, elapsed)
            } else {
                IDENTITY_CAMERA
            }
            cancelStartNanos = 0L
        }
        if (!returning) easing = false
        if (easing) {
            if (cancelStartNanos == 0L && frameNanos > 0L) cancelStartNanos = frameNanos
            return planner.cancel(cancelFrom, elapsedMillis(cancelStartNanos, frameNanos))
        }
        val current = tour ?: return IDENTITY_CAMERA
        return planner.position(current, elapsed)
    }

    private fun elapsedMillis(startNanos: Long, frameNanos: Long): Long {
        if (startNanos == 0L || frameNanos == 0L || frameNanos < startNanos) return 0L
        return (frameNanos - startNanos) / 1_000_000L
    }
}

/**
 * Sparse mote field. [CAP] is the hard limit; the array is filled once.
 */
internal class CoreMotes(
    cap: Int = IconCoreTheme.FULL_MOTE_CAP,
) {
    val motes: Array<Mote> = Array(cap) { index ->
        val unit = unit(index)
        val next = unit(index + 17)
        val third = unit(index + 41)
        Mote(
            angle = unit * TAU,
            homeRadius = 0.28f + next * 0.72f,
            speed = 0.35f + third * 1.4f,
            size = 0.0045f + unit(index + 9) * 0.008f,
            stretchX = 0.75f + unit(index + 3) * 0.7f,
            stretchY = 0.75f + unit(index + 11) * 0.7f,
        )
    }

    companion object {
        const val CAP = IconCoreTheme.FULL_MOTE_CAP
    }
}

private val IDENTITY_CAMERA = CinematicCamera()

internal class Mote(
    var angle: Float,
    val homeRadius: Float,
    val speed: Float,
    val size: Float,
    val stretchX: Float,
    val stretchY: Float,
)

private fun unit(index: Int): Float {
    var seed = index * 1103515245 + 12345
    seed = seed xor (seed ushr 16)
    return ((seed and 0xFFFFFF) / 16777215f).coerceIn(0f, 1f)
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

private const val FIELD_PARALLAX = 0.28f
private const val VOICE_FLOOR = 0.05f
private const val IDLE_BREATH_SECONDS = 5.2f
private const val IDLE_ORBIT = 0.22f
private const val THINKING_ORBIT = 1.7f
private const val TAU = 6.2831855f
private const val RAD_TO_DEG = 57.29578f
private const val RING_COUNT = 3

private val RING_RX = floatArrayOf(1.32f, 0.78f, 1.12f)
private val RING_RY = floatArrayOf(0.58f, 0.46f, 0.70f)
private val RING_RADIUS = floatArrayOf(0.28f, 0.40f, 0.52f)
private val RING_SPEED = floatArrayOf(0.16f, -0.11f, 0.07f)
private val RING_STROKE = floatArrayOf(0.007f, 0.005f, 0.0035f)
private val SPIN_DEGREES = floatArrayOf(18f, -32f, 8f)
private val GRADIENT_STOPS = floatArrayOf(0f, 0.45f, 1f)

private val FIELD_CORE = intArrayOf(0xFF241C4A.toInt(), 0xFF100C24.toInt(), 0xFF070614.toInt())
private val FIELD_ALERT = intArrayOf(0xFF3A2A18.toInt(), 0xFF140E18.toInt(), 0xFF070614.toInt())
private val CORE_NORMAL = intArrayOf(0xFFF4FEFF.toInt(), 0xFF8AF3FF.toInt(), 0x008AF3FF)
private val GLOW_NORMAL = intArrayOf(0x668AF3FF, 0x2248D4EA, 0x0048D4EA)
private val CORE_ALERT = intArrayOf(0xFFFFF6E8.toInt(), IconPalette.AMBER, 0x00C6A36A)
private val GLOW_ALERT = intArrayOf(0x66C6A36A, 0x22C6A36A, 0x00C6A36A)

/**
 * Orbit speed for one [AppState]. The five states do not share a speed.
 */
fun coreOrbit(state: AppState): Float = when (state) {
    AppState.Idle -> IDLE_ORBIT
    is AppState.Listening -> 0.42f
    is AppState.Thinking -> THINKING_ORBIT
    is AppState.Speaking -> 0.58f
    is AppState.Alert -> 0.18f
}

/**
 * How far motes pull toward the nucleus. Only Listening pulls inward.
 */
fun coreInward(state: AppState): Float = if (state is AppState.Listening) 0.76f else 0f

/**
 * Mote brightness for one [AppState]. Speaking rises with [amplitude].
 */
fun coreMoteEnergy(state: AppState, amplitude: Float): Float {
    val level = amplitude.coerceIn(0f, 1f)
    return when (state) {
        AppState.Idle -> 0.28f
        is AppState.Listening -> 0.85f
        is AppState.Thinking -> 0.62f
        is AppState.Speaking -> 0.40f + 0.60f * level
        is AppState.Alert -> 0.34f
    }
}

/**
 * True when the core uses the amber alert palette.
 */
fun coreUsesAlertPalette(state: AppState): Boolean = state is AppState.Alert

/**
 * Speaking nucleus size. Below the voice floor the slow breath is used.
 * At and above that floor the size grows with [amplitude].
 */
fun speakingNucleusScale(amplitude: Float, breath: Float): Float {
    val level = amplitude.coerceIn(0f, 1f)
    val breathScale = 0.96f + 0.08f * breath
    return if (level < VOICE_FLOOR) breathScale else 0.84f + 0.48f * level
}
