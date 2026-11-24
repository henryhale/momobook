package com.momobook.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/**
 * Single-line text that shrinks its font until the whole value fits, so figures are never
 * cut off with "…". Drawing waits until the final size is known to avoid a flash.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    minScale: Float = 0.45f,
) {
    var scale by remember(text, style) { mutableFloatStateOf(1f) }
    var ready by remember(text, style) { mutableStateOf(false) }
    Text(
        text = text,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        style = style.copy(fontSize = style.fontSize * scale),
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { r ->
            if (r.didOverflowWidth && scale > minScale) scale = (scale * 0.9f).coerceAtLeast(minScale) else ready = true
        },
    )
}

/** Whether balances are hidden, and how to toggle that. */
data class BalancePrivacy(val hidden: Boolean, val toggle: () -> Unit)

val LocalBalancePrivacy = staticCompositionLocalOf { BalancePrivacy(false) {} }

const val MASK = "****"
