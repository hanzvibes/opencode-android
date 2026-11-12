package dev.bidzz.opencode.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import kotlinx.coroutines.delay

/**
 * Floating white card treatment (White B, light theme only).
 *
 * Light: 16dp radius (`tokens.radius.lg`) + 1dp #EBEBEB border
 * (`colors.cardBorder`) + the card shadow from `tokens.shadow.card`
 * (8dp elevation, ambient `rgba(0,0,0,0.06)`, spot `rgba(0,0,0,0.05)`)
 * on the white card surface, so the card lifts off the #F9F9FA page.
 * Dark: the flat grouped treatment instead — raised surface, hairline
 * edge, no shadow — so the same call site renders correctly in both
 * themes without branching on `colors.isDark` itself.
 *
 * Later phases apply this by name to the composer, session rows and
 * other surfaces; [CardGroup] already wires it through its `whiteCard`
 * parameter for grouped settings-style cards.
 *
 * [borderColor] overrides the 1dp edge (the connected-provider green
 * edge, for example); null keeps the default [OpenCodeColors.cardBorder].
 */
@Composable
fun Modifier.whiteCard(borderColor: Color? = null): Modifier {
    val colors = OpenCodeTheme.colors
    val tokens = OpenCodeTheme.tokens
    val shape = RoundedCornerShape(tokens.radius.lg)
    val edge = borderColor ?: colors.cardBorder
    return if (colors.isDark) {
        clip(shape)
            .background(colors.surface)
            .border(tokens.dimensions.hairline, edge, shape)
    } else {
        val shadow = tokens.shadow.card
        shadow(
            elevation = shadow.elevation,
            shape = shape,
            ambientColor = shadow.ambientColor,
            spotColor = shadow.spotColor,
        )
            .clip(shape)
            .background(colors.surface)
            .border(tokens.shadow.cardBorderWidth, edge, shape)
    }
}

/** Rows grouped on one rounded surface with a hairline edge (matching AppGroup in cross-platform-llm-client). */
@Composable
fun CardGroup(
    modifier: Modifier = Modifier,
    /**
     * When true (the default since Opsi B #41), the group renders as a
     * floating white card ([whiteCard]): 16dp radius, 1dp #EBEBEB border,
     * shadow tokens — instead of the flat surface + hairline. Light theme
     * only; in dark the treatment falls back to the flat surface, so the
     * parameter is safe to set unconditionally. Set false only for a
     * deliberately flat group.
     */
    whiteCard: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val shape = RoundedCornerShape(OpenCodeTheme.tokens.radius.lg)
    val baseModifier = if (whiteCard) {
        modifier.fillMaxWidth().whiteCard()
    } else {
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(0.5.dp, colors.hairline, shape)
    }
    Column(
        modifier = baseModifier,
        content = content,
    )
}

/** Rounded neutral surface card. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    outlined: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = OpenCodeTheme.colors
    val shape = RoundedCornerShape(OpenCodeTheme.tokens.radius.lg)
    val baseModifier = modifier
        .clip(shape)
        .background(if (outlined) Color.Transparent else colors.surface)
        .border(
            width = if (outlined) 1.dp else 0.5.dp,
            color = if (outlined) colors.outline else colors.hairline,
            shape = shape,
        )
    val clickableModifier = if (onClick != null) {
        baseModifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        baseModifier
    }
    Box(modifier = clickableModifier.padding(16.dp)) {
        content()
    }
}

/** Icon badge with customizable shape and background (Apple-style squircle or neutral circle). */
@Composable
fun IconBadge(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    background: Color? = null,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    size: Dp = 32.dp,
) {
    val colors = OpenCodeTheme.colors
    val fg = tint ?: if (background != null) Color.White else colors.onSurface.copy(alpha = 0.85f)
    val bg = background ?: colors.fill
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, tint = fg, size = size * 0.53f)
    }
}

/** Hairline between two rows of a card; [inset] lines it up under the text, not the icon. */
@Composable
fun RowDivider(inset: Dp = 0.dp) {
    Box(
        Modifier
            .padding(start = inset)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(OpenCodeTheme.colors.hairline),
    )
}

/**
 * The one primary action button (Opsi B #42): an accent-fill button
 * with white label (+ optional glyph), 48dp minimum height, 12dp
 * radius — the same radius as [TonalButton], so the two button styles
 * align. Replaces the drifted private primary buttons:
 * SidebarPrimaryButton (ChatScreen), PrimaryButton (ProvidersScreen),
 * ChooseFolderButton (ProjectPickerScreen), and the inline accent-fill
 * boxes in ToolRow (Accept) and the status dialog (Diagnostics).
 * Drift it fixes: Providers had no 48dp minimum and no Button role;
 * ChooseFolder was 52dp with a 16dp radius; Accept was an 8dp radius;
 * the sidebar label was 15sp while the other four were 13sp.
 *
 * [enabled] dims the fill instead of hiding the action: a disabled
 * primary button keeps its place in the layout. None of the migrated
 * call sites passes disabled yet — the parameter exists so the next
 * form that needs it does not invent another button.
 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    enabled: Boolean = true,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = dims.minTouchTarget)
            .clip(RoundedCornerShape(radius.md))
            .background(if (enabled) colors.accentFill else colors.fill)
            .clickable(role = Role.Button, enabled = enabled, onClick = onClick)
            .padding(horizontal = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (glyph != null) {
            GlyphIcon(
                glyph,
                tint = if (enabled) colors.onAccent else colors.onSurfaceFaint,
                size = dims.iconMedium,
            )
        }
        Text(
            text = label,
            style = OpenCodeTypography.label,
            color = if (enabled) colors.onAccent else colors.onSurfaceFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (glyph != null) Modifier.padding(start = spacing.sm) else Modifier,
        )
    }
}

/** Tonal button: clean modern touch button matching cross-platform-llm-client. */
@Composable
fun TonalButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = OpenCodeTheme.colors.onSurface,
    fill: Color = OpenCodeTheme.colors.fill,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(OpenCodeTheme.tokens.radius.md))
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = OpenCodeTypography.label.copy(fontSize = 15.sp),
            color = color,
        )
    }
}

/**
 * The one empty / loading / error state for list screens (Opsi B #43):
 * a centered message, capped at the 720dp content width. Replaces the
 * 11 private `CenteredMessage` copies in Config, Export, Inbox,
 * Instructions, Mcp, Move, Plugins, SessionList, Stats, Usage and
 * Worktrees. Two of them (Mcp, SessionList) already used this shape —
 * text capped at 720dp with xxl inset; the other nine used a plain
 * full-width box with lg inset. This keeps the capped variant: on a
 * wide tablet a centered paragraph stays readable, and on a phone the
 * cap is a no-op. The only visual change for the nine is the inset
 * (lg → xxl) on centered text.
 *
 * Errors render in [OpenCodeColors.danger]; everything else in muted.
 */
@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = OpenCodeTypography.body,
            color = if (isError) colors.danger else colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 720.dp)
                .padding(spacing.xxl),
        )
    }
}

/**
 * The one loading state for list/detail screens (Opsi B #4c): a centered
 * spinner in the same style as the chat engine-loading spinner
 * (`dims.iconLarge`, 2dp stroke, `colors.accent`) with the loading text
 * below it, capped at the 720dp content width like [EmptyState].
 *
 * The retry button ([R.string.chat_retry]) appears only after
 * [retryAfterMs] (default 15s) and only when [onRetry] is given: engine
 * calls normally finish in seconds, so 15s means "stuck". The timer is a
 * `LaunchedEffect(Unit)` inside this composable, so it is cancelled
 * automatically when loading finishes and the composable leaves the
 * composition — it never leaks.
 *
 * Screens without a refresh callback (SessionList, Diff, SessionDiff)
 * pass `onRetry = null` and never show the button.
 */
@Composable
fun LoadingState(
    text: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryAfterMs: Long = 15_000,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val dims = OpenCodeTheme.tokens.dimensions
    var showRetry by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(retryAfterMs)
        showRetry = true
    }
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            modifier = Modifier
                .widthIn(max = 720.dp)
                .padding(spacing.xxl),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(dims.iconLarge),
                strokeWidth = 2.dp,
                color = colors.accent,
            )
            Text(
                text = text,
                style = OpenCodeTypography.body,
                color = colors.onSurfaceMuted,
                textAlign = TextAlign.Center,
            )
            if (showRetry && onRetry != null) {
                TonalButton(
                    label = stringResource(R.string.chat_retry),
                    onClick = onRetry,
                )
            }
        }
    }
}

/** Status indicator dot. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 6.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

/** Compact label badge / tag. */
@Composable
fun AppTag(
    label: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    glyph: Glyph? = null,
) {
    val colors = OpenCodeTheme.colors
    val radius = OpenCodeTheme.tokens.radius
    val fg = color ?: colors.onSurfaceMuted
    val bg = if (color == null) colors.fill else color.copy(alpha = 0.12f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(radius.sm))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (glyph != null) {
            GlyphIcon(glyph, tint = fg, size = 11.dp)
        }
        Text(
            text = label,
            style = OpenCodeTypography.label.copy(fontSize = 11.sp),
            color = fg,
        )
    }
}

/** Inline notice row (Opsi B #4e): the shared form of the chat error banner. */
enum class NoticeKind { Error, Warning, Info, Success }

/**
 * Notice row (Opsi B #4e): glyph + message + optional retry / dismiss, in
 * the kind's colour. Generalises the chat's private PinnedErrorBanner
 * (danger 12% bg, 0.5dp danger 35% border, Warning glyph, bodySmall text,
 * accentFill retry pill, 48dp dismiss) — the banner's call site and the
 * Providers key-validation messages are the same component now. Dismiss
 * and retry behaviour live at the call site, not here.
 */
@Composable
fun NoticeRow(
    message: String,
    kind: NoticeKind,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val dims = OpenCodeTheme.tokens.dimensions
    val kindColor = when (kind) {
        NoticeKind.Error -> colors.danger
        NoticeKind.Warning -> colors.warning
        NoticeKind.Info -> colors.accent
        NoticeKind.Success -> colors.success
    }
    val glyph = when (kind) {
        NoticeKind.Error, NoticeKind.Warning -> Glyph.Warning
        NoticeKind.Info -> Glyph.Info
        NoticeKind.Success -> Glyph.Check
    }
    val retryLabel = stringResource(R.string.chat_retry)
    val closeDescription = stringResource(R.string.chat_status_dialog_close)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius.md))
            .background(kindColor.copy(alpha = 0.12f))
            .border(0.5.dp, kindColor.copy(alpha = 0.35f), RoundedCornerShape(radius.md))
            .padding(start = spacing.md, top = spacing.xs, bottom = spacing.xs, end = spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(
            glyph = glyph,
            tint = kindColor,
            size = 16.dp,
        )
        Text(
            text = message,
            style = OpenCodeTypography.bodySmall,
            color = colors.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = spacing.sm),
        )
        if (onRetry != null) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minHeight = dims.minTouchTarget)
                    .clip(RoundedCornerShape(radius.full))
                    .background(colors.accentFill)
                    .semantics {
                        role = Role.Button
                        contentDescription = retryLabel
                    }
                    .clickable(role = Role.Button, onClick = onRetry)
                    .padding(horizontal = spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    GlyphIcon(
                        glyph = Glyph.Refresh,
                        tint = colors.onAccent,
                        size = 16.dp,
                    )
                    Text(
                        text = retryLabel,
                        style = OpenCodeTypography.label,
                        color = colors.onAccent,
                    )
                }
            }
            Spacer(Modifier.width(spacing.xs))
        }
        if (onDismiss != null) {
            // Dismiss: a full 48dp hit area around the 12dp cross.
            Box(
                modifier = Modifier
                    .size(dims.minTouchTarget)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .semantics { contentDescription = closeDescription },
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(
                    glyph = Glyph.Close,
                    tint = colors.onSurfaceMuted,
                    size = 12.dp,
                )
            }
        }
    }
}

/**
 * Shared search field (Opsi B #5): one shape for the six search bars that
 * were each hand-rolled — sidebar, explorer, providers, settings, session
 * list, diff filter. Radius.md + fill bg + hairline border + 48dp minimum
 * height + 16dp Search glyph + clear button while text is present. The
 * capsule (explorer / settings / diff) and whiteCard (providers) shapes
 * are the documented deltas; the AttachFilePicker field is NOT migrated
 * (its field owns focus + key handling for the picker).
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Default,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val clearDescription = stringResource(R.string.search_clear)
    // A11y (#8d): a visible focus ring (accent border while focused —
    // the same pattern as the composer) plus the Search/Done key
    // dismissing the keyboard, so the field is usable without touch.
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(radius.md))
            .background(colors.fill)
            .border(
                if (isFocused) 1.dp else 0.5.dp,
                if (isFocused) colors.accent.copy(alpha = 0.70f) else colors.hairline,
                RoundedCornerShape(radius.md),
            )
            .padding(horizontal = spacing.md, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.Search, tint = colors.onSurfaceMuted, size = 16.dp)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = OpenCodeTypography.body.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onSearch = { focusManager.clearFocus() },
                onDone = { focusManager.clearFocus() },
            ),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .padding(start = spacing.sm)
                .onFocusChanged { isFocused = it.isFocused },
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = OpenCodeTypography.body,
                        color = colors.onSurfaceFaint,
                    )
                }
                inner()
            },
        )
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(radius.sm))
                    .clickable(role = Role.Button) { onQueryChange("") }
                    .semantics { contentDescription = clearDescription },
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(
                    glyph = Glyph.Close,
                    tint = colors.onSurfaceMuted,
                    size = 14.dp,
                )
            }
        }
    }
}

/**
 * Shared turn-action pill (Opsi B #5): the one shell for Reply / Share /
 * Copy / Edit / Resend — capsule, 48dp minimum hit area, surfaceVariant +
 * hairline, 12dp glyph + 11sp semibold label. Copy keeps its own
 * clipboard / "Copied" state and passes its animated colours in; the
 * label crossfade is gone (instant swap), the animated pill colours and
 * the TalkBack announcement survive.
 */
@Composable
fun TurnActionPill(
    label: String,
    glyph: Glyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color? = null,
    border: Color? = null,
    contentColor: Color? = null,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    val pillShape = RoundedCornerShape(radius.full)
    // 48dp minimum hit area (Phase 2, finding 14): the pill keeps its
    // visual size, centred in a touch target that is at least 48dp tall
    // and grows to fit its label — a fixed 48dp box squeezed the label
    // and wrapped it ("Co py", "Re ply") on tablet.
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            // A11y (#8b): the pill is a button — the label text inside is
            // its name, so no contentDescription is needed here.
            .clickable(role = Role.Button) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(pillShape)
                .background(background ?: colors.surfaceVariant)
                .border(0.5.dp, border ?: colors.hairline, pillShape)
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                GlyphIcon(
                    glyph = glyph,
                    tint = contentColor ?: colors.onSurfaceMuted,
                    size = 12.dp,
                )
                Text(
                    text = label,
                    style = OpenCodeTypography.labelSmall,
                    color = contentColor ?: colors.onSurfaceMuted,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Shared radio button (Opsi B #5): the M3 RadioButton in the app's colours
 * (selected = accent, unselected = muted) — the three call sites (move
 * session, theme, language) all set the same colours by hand, which is how
 * the drift would have started. The rows keep owning their click handling
 * (onClick is null at two sites); the hand-drawn circle in
 * InlineQuestionCard is a different control (radio-or-checkbox by
 * isMultiple) and stays as-is.
 */
@Composable
fun AppRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = OpenCodeTheme.colors
    RadioButton(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = RadioButtonDefaults.colors(
            selectedColor = colors.accent,
            unselectedColor = colors.onSurfaceMuted,
        ),
    )
}

/**
 * Shared filter chip (Opsi B #5): the ToolRow trace filter chip — capsule,
 * selected = accentFill bg + border + white text, unselected = fill bg +
 * hairline border + muted text. StatusChip is a different component
 * (dot/pulse + mono 11sp status text) and is NOT merged into this one.
 */
@Composable
fun SelectorChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    val radius = OpenCodeTheme.tokens.radius
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(radius.full))
            .background(if (selected) colors.accentFill else colors.fill)
            .border(
                0.5.dp,
                if (selected) colors.accentFill else colors.hairline,
                RoundedCornerShape(radius.full),
            )
            // A11y (#8c): a filter chip is a toggle, not a plain button —
            // toggleable announces "selected / not selected" with the label.
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
            .padding(horizontal = spacing.sm, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = OpenCodeTypography.caption.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = if (selected) colors.onAccent else colors.onSurfaceMuted,
        )
    }
}

/**
 * Per-corner shape for a row in a grouped card (Opsi B #5, GroupedCard):
 * only the group's first/last rows round their outer corners. A container
 * that takes the rows cannot own LazyColumn items — the animateItem
 * modifier and the shared-element scopes live on the items themselves —
 * so the shared unit is the shape; the rows keep their own clip, surface
 * and divider because the selected state, the light-mode card shadow and
 * the divider insets differ between the two lists.
 */
fun groupedCardShape(isFirst: Boolean, isLast: Boolean, radius: Dp): RoundedCornerShape =
    RoundedCornerShape(
        topStart = if (isFirst) radius else 0.dp,
        topEnd = if (isFirst) radius else 0.dp,
        bottomStart = if (isLast) radius else 0.dp,
        bottomEnd = if (isLast) radius else 0.dp,
    )
