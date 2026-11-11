package dev.bidzz.opencode.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type scale for a developer tool.
 *
 * Two families, each with a job: a UI sans for chrome and prose, and a monospace
 * for anything the user might copy — code, paths, ports, identifiers. Mixing them
 * is meaningful rather than decorative, which is why inline code inside a message
 * is monospace too.
 *
 * Sizes are small by default. A coding tool shows a lot of text; oversized headings
 * waste vertical space on a phone, and `plan.md` §12 explicitly forbids them.
 */
object OpenCodeTypography {

    /**
     * Prose and UI chrome. Bundled Inter (SIL OFL 1.1, see
     * `assets/fonts/OFL.txt`); weights 400/500/600/700 in `res/font/inter.xml`.
     */
    val ui: FontFamily = FontFamily(Font(R.font.inter))

    /**
     * Code, paths, and anything copyable. Bundled JetBrains Mono (SIL OFL 1.1,
     * see `assets/fonts/OFL.txt`); weights 400/500/700 in
     * `res/font/jetbrains_mono.xml`.
     */
    val mono: FontFamily = FontFamily(Font(R.font.jetbrains_mono))

    /** Base body text for chat messages. */
    val body: TextStyle = TextStyle(
        fontFamily = ui,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.15).sp,
    )

    /** Body bold / strong. */
    val bodyStrong: TextStyle = TextStyle(
        fontFamily = ui,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.15).sp,
    )

    /** Slightly denser, for secondary labels and metadata. */
    val bodySmall: TextStyle = TextStyle(
        fontFamily = ui,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.08).sp,
    )

    /** Message author, section labels. */
    val label: TextStyle = TextStyle(
        fontFamily = ui,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.08).sp,
    )

    /**
     * Small semibold label: pill labels (Copy / Reply / Edit / Resend),
     * the thinking pill, badges. The scale had no 11sp semibold — call
     * sites copied `label` down to 11sp or 11.5sp by hand, which is how
     * the size drifted. Anything at 11sp semibold reads this, never a
     * one-off copy.
     */
    val labelSmall: TextStyle = TextStyle(
        fontFamily = ui,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.05.sp,
    )

    /** Micro text, badge labels, timestamps. Subtle open tracking for small-scale readability. */
    val caption: TextStyle = TextStyle(
        fontFamily = ui,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.05.sp,
    )

    /** Screen titles. Apple style modest title. */
    val title: TextStyle = TextStyle(
        fontFamily = ui,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
    )

    /** Large header. */
    val titleLarge: TextStyle = TextStyle(
        fontFamily = ui,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
    )

    /** Code inside a fenced block. */
    val code: TextStyle = TextStyle(
        fontFamily = mono,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    )

    /** Inline code inside prose: same family, slightly smaller to sit on the baseline. */
    val codeInline: TextStyle = TextStyle(
        fontFamily = mono,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    )

    /** Composer input. Matches body so the text does not jump on send. */
    val input: TextStyle = TextStyle(
        fontFamily = ui,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.15).sp,
    )

    /**
     * Markdown heading scale, moved here from MarkdownText so the sizes live
     * in one place. Same values the chat renderer used before: each level
     * steps down so the hierarchy still reads, and H4–H6 are no longer
     * identical. Top padding stays with the caller (first block gets none).
     */
    val heading1: TextStyle = TextStyle(
        fontFamily = ui,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
    )

    val heading2: TextStyle = TextStyle(
        fontFamily = ui,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.25).sp,
    )

    val heading3: TextStyle = TextStyle(
        fontFamily = ui,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.15).sp,
    )

    val heading4: TextStyle = TextStyle(
        fontFamily = ui,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
    )

    val heading5: TextStyle = TextStyle(
        fontFamily = ui,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
    )

    val heading6: TextStyle = TextStyle(
        fontFamily = ui,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.1).sp,
    )

    /** Material3 typography, mapped so Material components match the scale above. */
    fun material(): Typography = Typography(
        titleLarge = titleLarge,
        titleMedium = title,
        titleSmall = title.copy(fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.15).sp),
        bodyLarge = body,
        bodyMedium = body,
        bodySmall = bodySmall,
        labelLarge = label.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.1).sp),
        labelMedium = label,
        labelSmall = caption,
    )
}
