package dev.bidzz.opencode.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.api.dto.PendingPermission
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.AppTag
import dev.bidzz.opencode.ui.StatusDot

/** How dangerous the requested tool call looks, from its name and arguments alone. */
enum class PermissionRisk { LOW, MEDIUM, HIGH }

/**
 * Classifies a permission request from the tool name / action text and its
 * arguments. Pure function: no Android, no I/O, so it can be unit-tested.
 *
 * - HIGH: destructive — can delete data or change things outside the request
 *   (e.g. `rm -rf`, `sudo`, `delete`, `drop`).
 * - MEDIUM: changes files or runs a command, but deletes nothing outright
 *   (e.g. `write`, `edit`, `bash`).
 * - LOW: only reads (e.g. `read`, `grep`, `glob`) or nothing recognisable.
 */
internal fun classifyPermissionRisk(
    action: String,
    message: String? = null,
    resources: Collection<String> = emptyList(),
): PermissionRisk {
    // Padded with spaces so "rm" does not match inside "format" or "firm".
    val haystack = (" $action ${message.orEmpty()} ${resources.joinToString(" ")} ")
        .lowercase()
    fun has(vararg tokens: String): Boolean = tokens.any { haystack.contains(" $it ") }
    fun hasLoose(vararg fragments: String): Boolean = fragments.any { haystack.contains(it) }

    if (
        hasLoose("rm -rf", "rm -r", "rm --recursive", "git reset --hard", "git clean -f")
        || has(
            "rm", "rmdir", "sudo", "su", "delete", "drop", "format", "shred",
            "kill", "shutdown", "reboot", "chmod", "chown", "truncate", "mkfs",
        )
    ) {
        return PermissionRisk.HIGH
    }
    if (
        has(
            "write", "edit", "bash", "shell", "create", "move", "rename", "copy",
            "install", "patch", "apply", "run", "execute", "terminal",
        )
    ) {
        return PermissionRisk.MEDIUM
    }
    return PermissionRisk.LOW
}

/**
 * Shown while the engine is blocked waiting for permission.
 *
 * The agent cannot continue until this is answered, so the card sits above the
 * composer rather than inside the scrolling transcript: scrolling away must not
 * be able to hide the only control that unblocks the conversation.
 *
 * Three answers mirror the engine's contract:
 * - `once` allows this request only.
 * - `always` writes a saved permission for the project, so the same request is
 *   not asked again.
 * - `reject` refuses and lets the agent continue without the tool.
 *
 * A fourth affordance, "Answer later", defers the decision: [onAnswerLater]
 * is the callback the integration layer wires to the defer behaviour. This
 * composable only reports the tap; it does not dismiss the prompt or clear
 * the pending request itself.
 *
 * The risk chip is computed from the request's tool name and arguments by
 * [classifyPermissionRisk]: `rm -rf` reads as high risk, `read` as low risk.
 */
@Composable
fun PermissionPrompt(
    request: PendingPermission,
    onReply: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAnswerLater: () -> Unit = {},
    /** True when the last reply failed: the spent buttons re-arm so the
     * operator can retry instead of staring at a dead prompt. */
    replyFailed: Boolean = false,
    /** Focused by the screen when the request arrives, so TalkBack lands on
     * the prompt that blocks the agent. Null when no focus move is wanted. */
    focusRequester: FocusRequester? = null,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val cardShape = RoundedCornerShape(radius.lg)

    // Once any answer is tapped the card is spent: a double-tap (or a
    // tap while the reply is still in flight) must not answer twice.
    // Keyed on the request id: a new request is a new card, and a stale
    // `true` from the previous request must never disable its buttons.
    // A failed reply resets it (see the effect below) so the operator can
    // retry — the engine still waits, and the prompt must stay answerable.
    // Saveable, not plain remember (item 22): a rotation recreates the
    // composable, and a reset `replied` would let the operator answer the
    // same request twice while the first reply is still in flight.
    var replied by rememberSaveable(request.id) { mutableStateOf(false) }
    LaunchedEffect(replyFailed) {
        if (replyFailed) replied = false
    }

    val risk = remember(request.action, request.message, request.resources) {
        classifyPermissionRisk(request.action, request.message, request.resources)
    }
    val riskColor = when (risk) {
        PermissionRisk.HIGH -> colors.danger
        PermissionRisk.MEDIUM -> colors.warning
        PermissionRisk.LOW -> colors.success
    }
    val riskLabel = stringResource(
        when (risk) {
            PermissionRisk.HIGH -> R.string.permission_risk_high
            PermissionRisk.MEDIUM -> R.string.permission_risk_medium
            PermissionRisk.LOW -> R.string.permission_risk_low
        },
    )

    // The card itself is focusable so the screen can move TalkBack focus to
    // it when the request arrives; its description names the request, and the
    // answer buttons stay separately focusable below it.
    val permissionDescription = "${stringResource(R.string.permission_title)}: ${request.action}"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .semantics { contentDescription = permissionDescription }
            .clip(cardShape)
            .background(colors.surface)
            .border(0.5.dp, colors.warning.copy(alpha = 0.35f), cardShape)
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        // Warning card header with badge or subtle status dot
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(colors.warning.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                StatusDot(
                    color = colors.warning,
                    size = 6.dp,
                )
            }
            Text(
                text = stringResource(R.string.permission_title),
                style = OpenCodeTypography.label,
                color = colors.warning,
            )
            Spacer(Modifier.weight(1f))
            AppTag(label = riskLabel, color = riskColor)
        }

        // What the risk level means in practice.
        Text(
            text = stringResource(
                when (risk) {
                    PermissionRisk.HIGH -> R.string.permission_risk_high_note
                    PermissionRisk.MEDIUM -> R.string.permission_risk_medium_note
                    PermissionRisk.LOW -> R.string.permission_risk_low_note
                },
            ),
            style = OpenCodeTypography.caption,
            color = colors.onSurfaceMuted,
        )

        // Action description
        if (request.action.isNotBlank()) {
            Text(
                text = request.action,
                style = OpenCodeTypography.body,
                color = colors.onSurface,
            )
        }

        // What each answer actually does — it only lived in a code
        // comment before, so "Always" vs "Allow once" was a guess.
        Text(
            text = stringResource(R.string.permission_explainer),
            style = OpenCodeTypography.caption,
            color = colors.onSurfaceMuted,
        )

        // What "Always" would save, when the engine says so.
        if (request.save.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.permission_save_note,
                    request.save.filter { it.isNotBlank() }.joinToString(", "),
                ),
                style = OpenCodeTypography.caption,
                color = colors.onSurfaceMuted,
            )
        }

        // Detail code box styling (monospace, hairline border, rounded corners)
        val detail = remember(request.message, request.resources, request.action) {
            val items = mutableListOf<String>()
            val msg = request.message
            if (!msg.isNullOrBlank() && msg != request.action) {
                items.add(msg)
            }
            if (request.resources.isNotEmpty()) {
                items.addAll(request.resources.filter { it.isNotBlank() })
            }
            if (items.isEmpty() && !msg.isNullOrBlank()) {
                items.add(msg)
            }
            items.joinToString("\n").takeIf { it.isNotBlank() }
        }

        if (detail != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(radius.sm))
                    .background(colors.surfaceSunken)
                    .border(0.5.dp, colors.hairline, RoundedCornerShape(radius.sm))
                    .heightIn(max = 140.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                ) {
                    // Wraps instead of scrolling sideways: a nested
                    // vertical+horizontal scroll hid the detail behind two
                    // gestures, and the muted grey made it read as disabled.
                    Text(
                        text = detail,
                        style = OpenCodeTypography.code,
                        color = colors.onSurface,
                        softWrap = true,
                    )
                }
            }
        }

        // Action buttons: "Allow once" (Primary accent Apple Blue), "Always" (Neutral tonal fill), "Reject" (Soft red destructive tint)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            PermissionAction(
                label = stringResource(R.string.permission_allow_once),
                backgroundColor = colors.accent,
                contentColor = colors.onAccent,
                enabled = !replied,
                onClick = {
                    replied = true
                    onReply("once")
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            PermissionAction(
                label = stringResource(R.string.permission_always),
                backgroundColor = colors.fill,
                contentColor = colors.onSurface,
                borderColor = colors.hairline,
                enabled = !replied,
                onClick = {
                    replied = true
                    onReply("always")
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            PermissionAction(
                label = stringResource(R.string.permission_reject),
                backgroundColor = colors.danger.copy(alpha = 0.14f),
                contentColor = colors.danger,
                borderColor = colors.danger.copy(alpha = 0.22f),
                enabled = !replied,
                onClick = {
                    replied = true
                    onReply("reject")
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }

        // Defer: not an engine answer, so it goes through onAnswerLater
        // instead of onReply. The integration layer owns what "later"
        // means; this only reports the tap, once, like the other answers.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clip(RoundedCornerShape(radius.sm))
                .clickable(
                    enabled = !replied,
                    role = Role.Button,
                    onClick = {
                        replied = true
                        onAnswerLater()
                    },
                )
                .padding(horizontal = spacing.xs, vertical = spacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.permission_answer_later),
                style = OpenCodeTypography.label,
                color = if (replied) colors.onSurfaceFaint else colors.accentText,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PermissionAction(
    label: String,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color? = null,
    enabled: Boolean = true,
) {
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val shape = RoundedCornerShape(radius.sm)

    val borderModifier = if (borderColor != null) {
        Modifier.border(0.5.dp, borderColor, shape)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(shape)
            .background(backgroundColor)
            .then(borderModifier)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = spacing.xs, vertical = spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = OpenCodeTypography.label,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
