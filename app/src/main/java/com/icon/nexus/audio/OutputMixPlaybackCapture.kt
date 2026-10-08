package com.icon.nexus.audio

import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

/**
 * Output-mix capture on audio session 0. The waveform buffer and the deliver
 * runnable are allocated when capture starts, not on each callback.
 * Any failure returns false so the caller can use the utterance envelope.
 */
class OutputMixPlaybackCapture : PlaybackCapture {
    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val latestBits = AtomicInteger(0)
    private var listener: ((Float) -> Unit)? = null
    private var visualizer: Visualizer? = null
    private var buffer = ByteArray(0)

    @Volatile
    private var active = false

    private val deliver = Runnable {
        if (!active) return@Runnable
        listener?.invoke(Float.fromBits(latestBits.get()))
    }

    override fun setLevelListener(listener: ((Float) -> Unit)?) {
        this.listener = listener
    }

    override fun start(): Boolean {
        release()
        var created: Visualizer? = null
        return try {
            created = Visualizer(OUTPUT_MIX_SESSION)
            val range = Visualizer.getCaptureSizeRange()
            if (range.size < 2 || range[1] <= 0) error("Missing capture size")
            val size = range[1]
            if (created.setCaptureSize(size) != Visualizer.SUCCESS) error("Capture size rejected")
            val samples = ByteArray(size)
            synchronized(lock) {
                buffer = samples
                active = true
            }
            val rate = Visualizer.getMaxCaptureRate()
            val listened = created.setDataCaptureListener(captureListener(created), rate, true, false)
            if (listened != Visualizer.SUCCESS) error("Capture listener rejected")
            if (created.setEnabled(true) != Visualizer.SUCCESS) error("Visualizer disabled")
            visualizer = created
            true
        } catch (_: Throwable) {
            active = false
            visualizer = null
            try {
                created?.release()
            } catch (_: Throwable) {
                // The effect was never enabled.
            }
            false
        }
    }

    override fun release() {
        val current = synchronized(lock) {
            active = false
            val engine = visualizer
            visualizer = null
            engine
        }
        mainHandler.removeCallbacks(deliver)
        if (current == null) return
        try {
            current.setEnabled(false)
        } catch (_: Throwable) {
            // Already disabled.
        }
        try {
            current.release()
        } catch (_: Throwable) {
            // Already released.
        }
    }

    private fun captureListener(engine: Visualizer) = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(
            visualizer: Visualizer,
            waveform: ByteArray,
            samplingRate: Int,
        ) {
            if (visualizer !== engine) return
            publish(waveform)
        }

        override fun onFftDataCapture(
            visualizer: Visualizer,
            fft: ByteArray,
            samplingRate: Int,
        ) = Unit
    }

    private fun publish(waveform: ByteArray) {
        val level = synchronized(lock) {
            if (!active) return
            val local = buffer
            if (local.isEmpty()) return
            val count = min(waveform.size, local.size)
            System.arraycopy(waveform, 0, local, 0, count)
            WaveformEnergy.rms(local, count)
        }
        latestBits.set(level.toRawBits())
        mainHandler.removeCallbacks(deliver)
        mainHandler.post(deliver)
    }

    private companion object {
        const val OUTPUT_MIX_SESSION = 0
    }
}
