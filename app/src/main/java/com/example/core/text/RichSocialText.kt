package com.example.core.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

object RichSocialText {
    const val TAG_MENTION = "MENTION"
    const val TAG_HASHTAG = "HASHTAG"

    private val TOKEN_REGEX = Regex("([@#][A-Za-z0-9_]{2,32})")
    private val HASHTAG_REGEX = Regex("#([A-Za-z0-9_]{2,32})")
    private val MENTION_REGEX = Regex("@([A-Za-z0-9_]{2,32})")

    fun extractHashtags(text: String): List<String> =
        HASHTAG_REGEX.findAll(text)
            .map { it.groupValues[1].lowercase() }
            .distinct()
            .toList()

    fun extractMentions(text: String): List<String> =
        MENTION_REGEX.findAll(text)
            .map { it.groupValues[1].lowercase() }
            .distinct()
            .toList()

    /**
     * Returns the partial hashtag currently being typed at the end of a caption (without '#'),
     * or null if the user is not currently typing a hashtag token.
     */
    fun activeHashtagPrefix(text: String): String? {
        val lastToken = text.substringAfterLast(' ').substringAfterLast('\n')
        if (!lastToken.startsWith("#")) return null
        val body = lastToken.removePrefix("#")
        return if (body.all { it.isLetterOrDigit() || it == '_' }) body.lowercase() else null
    }

    fun applyHashtagSuggestion(currentText: String, selectedTag: String): String {
        val cleanTag = selectedTag.removePrefix("#").lowercase()
        val lastBreak = maxOf(currentText.lastIndexOf(' '), currentText.lastIndexOf('\n'))
        val prefix = if (lastBreak >= 0) currentText.substring(0, lastBreak + 1) else ""
        return "$prefix#$cleanTag "
    }

    fun buildAnnotated(
        text: String,
        accentColor: Color
    ): AnnotatedString = buildAnnotatedString {
        append(text)
        TOKEN_REGEX.findAll(text).forEach { match ->
            val token = match.value
            val start = match.range.first
            val end = match.range.last + 1
            addStyle(
                style = SpanStyle(
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold
                ),
                start = start,
                end = end
            )
            if (token.startsWith("@")) {
                addStringAnnotation(
                    tag = TAG_MENTION,
                    annotation = token.removePrefix("@").lowercase(),
                    start = start,
                    end = end
                )
            } else if (token.startsWith("#")) {
                addStringAnnotation(
                    tag = TAG_HASHTAG,
                    annotation = token.removePrefix("#").lowercase(),
                    start = start,
                    end = end
                )
            }
        }
    }
}
