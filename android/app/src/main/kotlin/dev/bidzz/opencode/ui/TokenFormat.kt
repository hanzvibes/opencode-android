package dev.bidzz.opencode.ui

/**
 * Compact token counts, rounded — not truncated — to one decimal:
 * 842 → "842", 1299 → "1.3k", 1999 → "2.0k", 1_250_000 → "1.3M".
 *
 * One shared implementation (audit-100 Fase 6a, 66-F5/19-F4): the turn
 * footer, the session list, the drawer label and the status dialog all
 * used to carry their own truncating copies, so every surface understated
 * by up to 99 tokens (k scale) or 99,999 (M scale) — always downwards,
 * on top of the other undercounts. Rounding can push a value over the
 * next unit (999_999 → "1.0M", never "1000.0k"), so the k branch promotes
 * when the rounded figure reaches 1000.
 */
internal fun formatCompactTokens(tokens: Long): String = when {
    tokens < 1_000 -> tokens.toString()
    tokens < 1_000_000 -> {
        val inK = tokens / 1000.0
        if (inK >= 999.95) {
            "${String.format(java.util.Locale.US, "%.1f", tokens / 1_000_000.0)}M"
        } else {
            "${String.format(java.util.Locale.US, "%.1f", inK)}k"
        }
    }
    else -> "${String.format(java.util.Locale.US, "%.1f", tokens / 1_000_000.0)}M"
}
