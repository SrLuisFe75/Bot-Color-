package com.icon.nexus.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.icon.nexus.language.ENGLISH_TAG
import com.icon.nexus.language.SPANISH_TAG
import com.icon.nexus.language.recognitionPlan

/**
 * One-shot [SpeechRecognizer]. The recognizer is created and destroyed on
 * the main thread. The platform endpointer ends the utterance. This class
 * does not start another recognition when the current one finishes or fails.
 */
class RecognizerSpeechInput(
    context: Context,
) : SpeechInput {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var activeTurn: Long? = null
    private var listener: SpeechEventListener? = null
    private var languageTag = ""

    override val isActive: Boolean
        get() = activeTurn != null

    override fun setListener(listener: SpeechEventListener?) {
        this.listener = listener
    }

    override fun setLanguageTag(tag: String) {
        val trimmed = tag.trim()
        onMain { languageTag = trimmed }
    }

    override fun startListening(turnId: Long) {
        onMain { startOnMain(turnId) }
    }

    override fun stopListening() {
        onMain { stopOnMain() }
    }

    private fun startOnMain(turnId: Long) {
        stopOnMain()
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            listener?.onSpeechEvent(SpeechEvent.Failure(turnId, SpeechFailure.Unavailable))
            return
        }
        val created = try {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        } catch (_: RuntimeException) {
            listener?.onSpeechEvent(SpeechEvent.Failure(turnId, SpeechFailure.Unavailable))
            return
        }
        recognizer = created
        activeTurn = turnId
        created.setRecognitionListener(sessionListener(turnId))
        try {
            created.startListening(recognitionIntent())
        } catch (_: RuntimeException) {
            stopOnMain()
            listener?.onSpeechEvent(SpeechEvent.Failure(turnId, SpeechFailure.Unavailable))
        }
    }

    private fun stopOnMain() {
        activeTurn = null
        val current = recognizer
        recognizer = null
        if (current == null) return
        current.setRecognitionListener(null)
        try {
            current.cancel()
        } catch (_: RuntimeException) {
            // The recognizer may already have finished.
        }
        try {
            current.destroy()
        } catch (_: RuntimeException) {
            // Destroy is best-effort once cancel has run.
        }
    }

    private fun sessionListener(turnId: Long) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onPartialResults(partialResults: Bundle?) = Unit

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onResults(results: Bundle?) {
            if (activeTurn != turnId) return
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            stopOnMain()
            listener?.onSpeechEvent(SpeechEvent.Result(turnId, text))
        }

        override fun onError(error: Int) {
            if (activeTurn != turnId) return
            stopOnMain()
            listener?.onSpeechEvent(SpeechEvent.Failure(turnId, speechFailureForCode(error)))
        }
    }

    private fun recognitionIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            val plan = recognitionPlan(languageTag)
            val pin = plan.pinLanguage
            if (pin != null) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, pin)
            }
            if (plan.detectLanguage) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                    arrayListOf(SPANISH_TAG, ENGLISH_TAG),
                )
            }
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
