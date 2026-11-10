package dev.bidzz.opencode.ui.files

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import dev.bidzz.opencode.core.design.OpenCodeColors
import dev.bidzz.opencode.ui.chat.TokenKind
import dev.bidzz.opencode.ui.chat.highlight

/**
 * Builds an [AnnotatedString] with syntax highlighting applied based on [language].
 */
fun buildAnnotatedCode(language: String?, code: String, colors: OpenCodeColors): AnnotatedString {
    val tokens = highlight(language, code)
    if (tokens.isEmpty()) return AnnotatedString(code)

    return buildAnnotatedString {
        tokens.forEach { token ->
            withStyle(
                SpanStyle(
                    color = when (token.kind) {
                        TokenKind.Keyword -> colors.syntaxKeyword
                        TokenKind.String -> colors.syntaxString
                        TokenKind.Number -> colors.syntaxNumber
                        TokenKind.Comment -> colors.syntaxComment
                        TokenKind.Function -> colors.syntaxFunction
                        TokenKind.Type -> colors.syntaxType
                        TokenKind.Operator -> colors.onSurfaceMuted
                        TokenKind.Plain -> colors.onSurface
                    },
                    fontWeight = when (token.kind) {
                        TokenKind.Keyword -> FontWeight.SemiBold
                        else -> FontWeight.Normal
                    },
                    fontStyle = if (token.kind == TokenKind.Comment) FontStyle.Italic else FontStyle.Normal,
                ),
            ) {
                append(token.text)
            }
        }
    }
}

/**
 * Real-time syntax highlighting for editable code in [androidx.compose.foundation.text.BasicTextField].
 * Uses [OffsetMapping.Identity] so cursor navigation and selection are preserved exactly.
 */
class SyntaxHighlightVisualTransformation(
    private val language: String?,
    private val colors: OpenCodeColors,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = buildAnnotatedCode(language, text.text, colors)
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}
