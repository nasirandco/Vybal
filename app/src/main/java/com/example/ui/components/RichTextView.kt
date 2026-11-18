package com.example.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.example.core.text.RichSocialText
import com.example.ui.theme.VybalLavender

@Suppress("DEPRECATION")
@Composable
fun RichSocialTextView(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    textColor: Color = MaterialTheme.colorScheme.onBackground,
    accentColor: Color = VybalLavender,
    onMentionClick: (String) -> Unit,
    onHashtagClick: (String) -> Unit
) {
    val annotated = remember(text, accentColor) {
        RichSocialText.buildAnnotated(text = text, accentColor = accentColor)
    }

    ClickableText(
        text = annotated,
        style = style.copy(color = textColor),
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations(RichSocialText.TAG_MENTION, offset, offset)
                .firstOrNull()
                ?.let { onMentionClick(it.item) }
                ?: annotated.getStringAnnotations(RichSocialText.TAG_HASHTAG, offset, offset)
                    .firstOrNull()
                    ?.let { onHashtagClick(it.item) }
        }
    )
}
