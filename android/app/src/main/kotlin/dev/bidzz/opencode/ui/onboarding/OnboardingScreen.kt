package dev.bidzz.opencode.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.TonalButton

/**
 * First-run flow: project → provider → permissions.
 *
 * Shown once, while the workspace is empty (see [OnboardingStore]); every
 * step is skippable and leaving the flow — Skip on any step, or finishing
 * it — marks it completed so it never appears again. Dismissing it returns
 * to chat, where Phase 2's contextual starters still greet an empty
 * workspace; this flow does not replace them, it just orients first.
 *
 * Navigation out of the flow (e.g. "Add a provider key") goes through
 * [onOpenProviders], which the host wires to the Providers screen and to
 * marking the flow completed — the user has engaged with that step, so
 * there is nothing left to force.
 *
 * @param workspacePath path shown on the project step, so the user sees
 *   where the default workspace (and a sample project) will live.
 * @param hasAllFilesAccess whether the permission step can show its
 *   already-granted state instead of the grant button.
 * @param onCreateSample seeds the sample project; returns the directory it
 *   was seeded into, or null when seeding failed (the button stays, so the
 *   user can retry).
 */
@Composable
fun OnboardingScreen(
    workspacePath: String,
    hasAllFilesAccess: Boolean,
    onChooseFolder: () -> Unit,
    onGrantAllFilesAccess: () -> Unit,
    onOpenProviders: () -> Unit,
    onCreateSample: () -> String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    var step by remember { mutableIntStateOf(0) }
    var samplePath by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // Top row: progress + Skip on every step. Skip is completing, not
        // abandoning — see OnboardingStore.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.onboarding_step_of, step + 1, 3),
                style = OpenCodeTypography.label,
                color = colors.onSurfaceFaint,
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    style = OpenCodeTypography.label,
                    color = colors.onSurfaceMuted,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .widthIn(max = 720.dp)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            when (step) {
                0 -> ProjectStep(
                    workspacePath = workspacePath,
                    samplePath = samplePath,
                    onCreateSample = { samplePath = onCreateSample() },
                    onSampleContinue = { step = 1 },
                    onChooseFolder = {
                        onChooseFolder()
                        step = 1
                    },
                    onUseDefault = { step = 1 },
                )

                1 -> ProviderStep(
                    onOpenProviders = onOpenProviders,
                    onContinue = { step = 2 },
                    onBack = { step = 0 },
                )

                else -> PermissionsStep(
                    hasAllFilesAccess = hasAllFilesAccess,
                    onGrantAllFilesAccess = onGrantAllFilesAccess,
                    onFinish = onDismiss,
                    onBack = { step = 1 },
                )
            }
        }
    }
}

/** Step 1 — project: sample, own folder, or the built-in workspace. */
@Composable
private fun ProjectStep(
    workspacePath: String,
    samplePath: String?,
    onCreateSample: () -> Unit,
    onSampleContinue: () -> Unit,
    onChooseFolder: () -> Unit,
    onUseDefault: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    StepHeader(
        titleRes = R.string.onboarding_project_title,
        bodyRes = R.string.onboarding_project_body,
    )
    Text(
        text = workspacePath,
        style = OpenCodeTypography.code,
        color = colors.onSurfaceFaint,
    )

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        if (samplePath != null) {
            Text(
                text = stringResource(R.string.onboarding_sample_created, samplePath),
                style = OpenCodeTypography.bodySmall,
                color = colors.success,
            )
        }
        TonalButton(
            label = stringResource(
                if (samplePath == null) R.string.onboarding_create_sample
                else R.string.onboarding_continue,
            ),
            onClick = if (samplePath == null) onCreateSample else onSampleContinue,
            fill = colors.accent,
            color = colors.onAccent,
            modifier = Modifier.fillMaxWidth(),
        )
        TonalButton(
            label = stringResource(R.string.onboarding_choose_folder),
            onClick = onChooseFolder,
            modifier = Modifier.fillMaxWidth(),
        )
        TonalButton(
            label = stringResource(R.string.onboarding_use_default),
            onClick = onUseDefault,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Step 2 — provider: explain the free tier, offer the Providers screen. */
@Composable
private fun ProviderStep(
    onOpenProviders: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    StepHeader(
        titleRes = R.string.onboarding_provider_title,
        bodyRes = R.string.onboarding_provider_body,
    )
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        TonalButton(
            label = stringResource(R.string.onboarding_open_providers),
            onClick = onOpenProviders,
            fill = colors.accent,
            color = colors.onAccent,
            modifier = Modifier.fillMaxWidth(),
        )
        TonalButton(
            label = stringResource(R.string.onboarding_continue),
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
        BackButton(onBack = onBack)
    }
}

/** Step 3 — permissions: all-files access, then into chat. */
@Composable
private fun PermissionsStep(
    hasAllFilesAccess: Boolean,
    onGrantAllFilesAccess: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    StepHeader(
        titleRes = R.string.onboarding_permissions_title,
        bodyRes = R.string.onboarding_permissions_body,
    )
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        if (hasAllFilesAccess) {
            Text(
                text = stringResource(R.string.onboarding_access_granted),
                style = OpenCodeTypography.bodySmall,
                color = colors.success,
            )
        } else {
            TonalButton(
                label = stringResource(R.string.onboarding_grant_access),
                onClick = onGrantAllFilesAccess,
                fill = colors.accent,
                color = colors.onAccent,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TonalButton(
            label = stringResource(R.string.onboarding_finish),
            onClick = onFinish,
            fill = if (hasAllFilesAccess) colors.accent else colors.fill,
            color = if (hasAllFilesAccess) colors.onAccent else colors.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
        BackButton(onBack = onBack)
    }
}

/** Step title + body, shared by all three steps. */
@Composable
private fun StepHeader(titleRes: Int, bodyRes: Int) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(
            text = stringResource(titleRes),
            style = OpenCodeTypography.title,
            color = colors.onSurface,
        )
        Text(
            text = stringResource(bodyRes),
            style = OpenCodeTypography.body,
            color = colors.onSurfaceMuted,
        )
    }
}

/** Quiet back link under a step's buttons. */
@Composable
private fun BackButton(onBack: () -> Unit) {
    val colors = OpenCodeTheme.colors

    TextButton(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.common_back),
            style = OpenCodeTypography.label,
            color = colors.onSurfaceMuted,
        )
    }
}
