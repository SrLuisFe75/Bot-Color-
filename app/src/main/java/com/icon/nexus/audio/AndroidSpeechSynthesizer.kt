package com.icon.nexus.audio

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android [TextToSpeech] on the application context. Sentences are queued
 * with [TextToSpeech.QUEUE_ADD] at speech rate 1.0 in the system locale.
 * [stop] halts playback immediately and drops anything not yet spoken.
 */
class AndroidSpeechSynthesizer(
    context: Context,
) : SpeechSynthesizer {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val utteranceIds = AtomicInteger(0)
    private val waiting = ArrayDeque<Pair<Long, String>>()
    private val active = LinkedHashMap<String, Utterance>()
    private var engine: TextToSpeech? = null
    private var listener: SpeechPlaybackListener? = null
    private var ready = false
    private var failed = false
    private var epoch = 0

    override val available: Boolean
        get() = !failed

    init {
        onMain { createEngine() }
    }

    override fun setPlaybackListener(listener: SpeechPlaybackListener?) {
        this.listener = listener
    }

    override fun enqueue(turnId: Long, sentences: List<String>) {
        onMain {
            if (failed) {
                listener?.onSpeechUnavailable()
                return@onMain
            }
            sentences.forEach { sentence ->
                if (sentence.isNotBlank()) waiting.addLast(turnId to sentence)
            }
            if (ready) drain()
        }
    }

    override fun stop() {
        onMain {
            epoch += 1
            waiting.clear()
            active.clear()
            try {
                engine?.stop()
            } catch (_: RuntimeException) {
                // The engine may already have shut down.
            }
        }
    }

    override fun release() {
        onMain {
            epoch += 1
            waiting.clear()
            active.clear()
            val current = engine
            engine = null
            ready = false
            try {
                current?.stop()
                current?.shutdown()
            } catch (_: RuntimeException) {
                // Shutdown is best-effort.
            }
        }
    }

    private fun createEngine() {
        if (engine != null || failed) return
        try {
            engine = TextToSpeech(appContext) { status ->
                onMain { onEngineReady(status) }
            }
        } catch (_: RuntimeException) {
            fail()
        }
    }

    private fun onEngineReady(status: Int) {
        val current = engine ?: return
        if (status != TextToSpeech.SUCCESS) {
            fail()
            return
        }
        current.setOnUtteranceProgressListener(progressListener())
        current.setSpeechRate(SPEECH_RATE)
        val language = current.setLanguage(Locale.getDefault())
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            fail()
            return
        }
        ready = true
        drain()
    }

    private fun drain() {
        val current = engine ?: return
        if (!ready || failed) return
        val spokenEpoch = epoch
        while (waiting.isNotEmpty()) {
            val (turnId, sentence) = waiting.removeFirst()
            val utteranceId = "icon-$turnId-${utteranceIds.incrementAndGet()}"
            active[utteranceId] = Utterance(turnId = turnId, epoch = spokenEpoch)
            val result = try {
                current.speak(sentence, TextToSpeech.QUEUE_ADD, Bundle(), utteranceId)
            } catch (_: RuntimeException) {
                TextToSpeech.ERROR
            }
            if (result == TextToSpeech.ERROR) {
                active.remove(utteranceId)
                fail()
                return
            }
        }
    }

    private fun progressListener() = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            finish(utteranceId)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            finish(utteranceId)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            finish(utteranceId)
        }
    }

    private fun finish(utteranceId: String?) {
        if (utteranceId == null) return
        onMain {
            val utterance = active.remove(utteranceId) ?: return@onMain
            if (utterance.epoch != epoch) return@onMain
            listener?.onUtteranceFinished(utterance.turnId)
        }
    }

    private fun fail() {
        if (failed) return
        failed = true
        ready = false
        epoch += 1
        waiting.clear()
        active.clear()
        try {
            engine?.stop()
        } catch (_: RuntimeException) {
            // Already failing.
        }
        listener?.onSpeechUnavailable()
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private data class Utterance(
        val turnId: Long,
        val epoch: Int,
    )

    private companion object {
        const val SPEECH_RATE = 1.0f
    }
}
