package dev.bidzz.opencode.ui.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.GlyphIcon
import dev.bidzz.opencode.ui.PrimaryButton

/**
 * App lock (Batch 7, Native C batch 2).
 *
 * When enabled in Settings, the app shows [LockScreen] instead of the chat
 * until the user passes a biometric prompt (or the device PIN/pattern as a
 * fallback). The lock re-arms whenever the activity returns from the
 * background (see MainActivity); the engine keeps running behind it — only
 * the UI is gated, so a run in progress is never interrupted by locking.
 *
 * The setting is one boolean in SharedPreferences (same pattern as
 * [dev.bidzz.opencode.AppLanguage]): the lock is a local device preference,
 * not engine state, so it needs no server round-trip and survives restarts.
 */
object AppLockPrefs {
    private const val PREFS = "opencode_lock"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}

/** What this device can offer as an unlock, from BiometricManager. */
enum class LockAvailability {
    Available,
    NoHardware,
    HardwareUnavailable,
    NoneEnrolled,
    SecurityUpdateRequired,
}

/**
 * Pure mapping from [BiometricManager.canAuthenticate] codes to
 * [LockAvailability]. Pure so the rule is unit-tested here rather than
 * through the activity; unknown codes degrade to [LockAvailability.HardwareUnavailable].
 */
fun lockAvailabilityFrom(code: Int): LockAvailability = when (code) {
    BiometricManager.BIOMETRIC_SUCCESS -> LockAvailability.Available
    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> LockAvailability.NoHardware
    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> LockAvailability.HardwareUnavailable
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> LockAvailability.NoneEnrolled
    BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> LockAvailability.SecurityUpdateRequired
    else -> LockAvailability.HardwareUnavailable
}

/**
 * Whether this device can unlock the app at all. Strong biometrics are
 * accepted, and the device credential (PIN/pattern/password) is allowed as
 * a fallback, so the lock works on a device with a screen lock but no
 * enrolled biometrics.
 */
fun lockAvailability(context: Context): LockAvailability =
    lockAvailabilityFrom(
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        ),
    )

/**
 * Runs one [BiometricPrompt] authentication.
 *
 * Backing out (user-cancelled, negative button, system-cancelled) is silent:
 * the caller stays locked and may prompt again. A real failure — a sensor
 * error, or a biometric that was not recognized — reaches [onError].
 * No negative button is set: it conflicts with the DEVICE_CREDENTIAL
 * authenticator and the prompt throws.
 */
fun authenticateWithBiometrics(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    failedMessage: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
) {
    // Activity.mainExecutor (API 28+, minSdk 29) instead of
    // ContextCompat: the app module has no androidx.core dependency, and
    // this avoids relying on it arriving transitively.
    val prompt = BiometricPrompt(
        activity,
        activity.mainExecutor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult,
            ) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                onError(failedMessage)
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )
        .build()
    prompt.authenticate(info)
}

/**
 * Full-screen lock. Prompts automatically once when it appears; the Unlock
 * button re-prompts after a failure or a back-out. Renders inside its own
 * [OpenCodeTheme] because it replaces the whole app content (and therefore
 * the theme OpenCodeApp would otherwise provide).
 */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val activity = LocalContext.current as? FragmentActivity
    val title = stringResource(R.string.lock_unlock_title)
    val subtitle = stringResource(R.string.lock_unlock_subtitle)
    val failedMessage = stringResource(R.string.lock_failed)
    var error by remember { mutableStateOf<String?>(null) }
    var prompted by remember { mutableStateOf(false) }

    fun prompt() {
        val act = activity ?: return
        authenticateWithBiometrics(act, title, subtitle, failedMessage, onUnlocked) {
            error = it
        }
    }

    LaunchedEffect(Unit) {
        if (!prompted) {
            prompted = true
            prompt()
        }
    }

    OpenCodeTheme {
        val colors = OpenCodeTheme.colors
        val spacing = OpenCodeTheme.tokens.spacing
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.xxl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GlyphIcon(
                glyph = Glyph.Lock,
                tint = colors.onSurfaceMuted,
                size = 48.dp,
                contentDescription = null,
            )
            Spacer(Modifier.height(spacing.lg))
            Text(
                text = title,
                style = OpenCodeTypography.body.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(spacing.sm))
            Text(
                text = subtitle,
                style = OpenCodeTypography.bodySmall,
                color = colors.onSurfaceMuted,
                textAlign = TextAlign.Center,
            )
            error?.let {
                Spacer(Modifier.height(spacing.md))
                Text(
                    text = it,
                    style = OpenCodeTypography.bodySmall,
                    color = colors.danger,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(spacing.xl))
            PrimaryButton(
                label = stringResource(R.string.lock_unlock_button),
                onClick = ::prompt,
                modifier = Modifier.fillMaxWidth(),
                glyph = Glyph.Lock,
            )
        }
    }
}
