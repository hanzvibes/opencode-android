package dev.bidzz.opencode.ui.chat

import android.speech.SpeechRecognizer
import dev.bidzz.opencode.AppLanguage
import dev.bidzz.opencode.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Voice input's pure logic: locale choice, transcript merging, elapsed
 * time, and error wording. The recognizer itself is platform code and is
 * deliberately not exercised here — no Android framework in unit tests.
 */
class VoiceInputTest {

    @Test
    fun `pinned English listens in English`() {
        assertEquals("en", speechLocale(AppLanguage.English).toLanguageTag())
    }

    @Test
    fun `pinned Indonesian listens in Indonesian`() {
        assertEquals("id", speechLocale(AppLanguage.Indonesian).toLanguageTag())
    }

    @Test
    fun `system language follows the device locale`() {
        assertEquals(
            java.util.Locale.getDefault(),
            speechLocale(AppLanguage.System),
        )
    }

    @Test
    fun `a transcript on an empty draft replaces it`() {
        assertEquals("perbaiki bug scroll", appendTranscript("", "perbaiki bug scroll"))
        assertEquals("perbaiki bug scroll", appendTranscript("   ", "  perbaiki bug scroll  "))
    }

    @Test
    fun `a transcript after typed text joins with a space`() {
        assertEquals(
            "tolong perbaiki bug scroll",
            appendTranscript("tolong", "perbaiki bug scroll"),
        )
    }

    @Test
    fun `a transcript after trailing whitespace does not double the space`() {
        assertEquals("tolong perbaiki", appendTranscript("tolong ", "perbaiki"))
        assertEquals("tolong\nperbaiki", appendTranscript("tolong\n", "perbaiki"))
    }

    @Test
    fun `a blank transcript leaves the draft untouched`() {
        assertEquals("tolong", appendTranscript("tolong", "   "))
        assertEquals("", appendTranscript("", ""))
    }

    @Test
    fun `elapsed time formats as minutes and zero-padded seconds`() {
        assertEquals("0:00", formatVoiceElapsed(0))
        assertEquals("0:07", formatVoiceElapsed(7))
        assertEquals("1:05", formatVoiceElapsed(65))
        assertEquals("10:00", formatVoiceElapsed(600))
    }

    @Test
    fun `negative elapsed time clamps to zero`() {
        assertEquals("0:00", formatVoiceElapsed(-5))
    }

    @Test
    fun `no match has its own honest message`() {
        assertEquals(
            R.string.voice_error_no_match,
            voiceErrorMessageRes(SpeechRecognizer.ERROR_NO_MATCH),
        )
    }

    @Test
    fun `a speech timeout has its own honest message`() {
        assertEquals(
            R.string.voice_error_timeout,
            voiceErrorMessageRes(SpeechRecognizer.ERROR_SPEECH_TIMEOUT),
        )
    }

    @Test
    fun `network, network timeout and server errors share the network message`() {
        listOf(
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER,
        ).forEach { code ->
            assertEquals(R.string.voice_error_network, voiceErrorMessageRes(code))
        }
    }

    @Test
    fun `missing microphone permission has its own message`() {
        assertEquals(
            R.string.voice_error_permission,
            voiceErrorMessageRes(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS),
        )
    }

    @Test
    fun `audio, client, busy and unknown errors fall back to generic`() {
        listOf(
            SpeechRecognizer.ERROR_AUDIO,
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            9999,
        ).forEach { code ->
            assertEquals(R.string.voice_error_generic, voiceErrorMessageRes(code))
        }
    }
}
