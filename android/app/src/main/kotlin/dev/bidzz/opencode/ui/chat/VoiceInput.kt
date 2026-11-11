package dev.bidzz.opencode.ui.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dev.bidzz.opencode.AppLanguage
import dev.bidzz.opencode.R
import java.util.Locale

/**
 * Voice input (Composer Opsi C): the composer's mic button wired to the
 * platform [SpeechRecognizer] — real on-device / cloud recognition, never
 * a simulation and never a hardcoded transcript.
 *
 * Flow, owned by the chat screen:
 * tap mic → [hasRecordAudioPermission] → if not granted, the screen asks
 * MainActivity (via the application bridge) for RECORD_AUDIO once; a
 * denial lands in [VoiceInputState.DENIED] and is shown, never re-asked
 * in a loop → if granted, [VoiceInputController.start] listens with the
 * app language → partial results show live in the recording bar →
 * stop / final result appends the transcript to the draft via
 * [appendTranscript], editable before sending.
 *
 * Honest degradation: when [SpeechRecognizer.isRecognitionAvailable]
 * is false (no recognition service on the device) the controller reports
 * [VoiceInputState.UNAVAILABLE], the mic renders as an unavailable
 * (disabled-looking) button, and tapping it only shows the "not available
 * on this device" message. Nothing is faked.
 *
 * Lifecycle: the screen creates one controller per composition and must
 * call [VoiceInputController.destroy] when the screen leaves (a
 * DisposableEffect), and [VoiceInputController.cancel] when the session
 * changes, so no recognizer — and no transcript — outlives its session.
 * The recognizer holds the application context only, never an Activity.
 */

/** Where the mic button / recording bar is. */
enum class VoiceInputState {
    /** Idle: mic available, nothing recording. */
    IDLE,
    /** Listening: the recording bar is showing. */
    LISTENING,
    /** No recognition service on this device: mic is unavailable. */
    UNAVAILABLE,
    /** RECORD_AUDIO was denied: mic taps only show the hint. */
    DENIED,
}

/** True when the app already holds RECORD_AUDIO. */
fun hasRecordAudioPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

/**
 * The locale speech recognition listens in: the app's own language when
 * the user pinned one, the device locale otherwise. Pure, so it is
 * unit-tested without Android.
 */
fun speechLocale(language: AppLanguage): Locale = when (language) {
    AppLanguage.English -> Locale.ENGLISH
    AppLanguage.Indonesian -> Locale.forLanguageTag("id")
    AppLanguage.System -> Locale.getDefault()
}

/**
 * Merges a finished transcript into the draft: a blank draft is replaced
 * outright; anything already typed keeps its text, and the transcript is
 * appended after a space so it never glues itself onto a half-typed word.
 * A blank transcript leaves the draft untouched.
 */
fun appendTranscript(currentDraft: String, transcript: String): String {
    val text = transcript.trim()
    if (text.isEmpty()) return currentDraft
    if (currentDraft.isBlank()) return text
    if (currentDraft.last().isWhitespace()) return currentDraft + text
    return "$currentDraft $text"
}

/** "0:07", "1:05", "10:00" — the recording bar's elapsed time. */
fun formatVoiceElapsed(totalSeconds: Int): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/**
 * The user-facing message for a [SpeechRecognizer] error code: an honest
 * sentence per failure kind, never a raw code. Unknown codes fall back to
 * the generic failure string.
 */
fun voiceErrorMessageRes(error: Int): Int = when (error) {
    SpeechRecognizer.ERROR_NO_MATCH -> R.string.voice_error_no_match
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.voice_error_timeout
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    SpeechRecognizer.ERROR_SERVER -> R.string.voice_error_network
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_error_permission
    else -> R.string.voice_error_generic
}

/**
 * One platform recognizer, created once and destroyed by its owner.
 *
 * [onResult] fires once per finished listen with the best transcript (or
 * not at all when recognition fails — [onError] fires instead). [onPartial]
 * fires with interim text while listening; it is display-only and never
 * enters the draft. Callbacks arrive on the main thread, so the owner can
 * write Compose state from them directly.
 */
class VoiceInputController(
    context: Context,
    private val onResult: (String) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onError: (Int) -> Unit,
) {
    private val appContext: Context = context.applicationContext
    private val recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
            runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) }.getOrNull()
        } else {
            null
        }

    /** False when there is no recognition service (or none could be created). */
    val isAvailable: Boolean = recognizer != null

    init {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(error: Int) {
                onError(error)
            }

            override fun onResults(results: Bundle?) {
                results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { onResult(it) }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { onPartial(it) }
            }
        })
    }

    /**
     * Starts listening in [locale]. Returns false when there is no
     * recognizer or the platform refused to start — the caller then stays
     * idle and shows the generic failure instead of a stuck recording bar.
     * Free-form dictation, partial results on, one best result.
     */
    fun start(locale: Locale): Boolean {
        val target = recognizer ?: return false
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        }
        return runCatching { target.startListening(intent) }.isSuccess
    }

    /** Ends listening and delivers the final result (or an error). */
    fun stop() {
        runCatching { recognizer?.stopListening() }
    }

    /** Abandons listening with no result delivered. */
    fun cancel() {
        runCatching { recognizer?.cancel() }
    }

    /** Releases the recognizer. Must be called when the owner leaves. */
    fun destroy() {
        runCatching { recognizer?.destroy() }
    }
}
