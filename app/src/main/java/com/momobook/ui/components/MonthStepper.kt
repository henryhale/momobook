package com.momobook.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** "‹  November 2026  ›" month picker. [active] highlights it when it drives the screen. */
@Composable
fun MonthStepper(
    label: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    onLabelClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (active) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.Rounded.ChevronLeft, "Previous month") }
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                    .then(if (onLabelClick != null) Modifier.clickable(onClick = onLabelClick) else Modifier)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp),
                    tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
                    color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onNext, enabled = nextEnabled) {
                Icon(Icons.Rounded.ChevronRight, "Next month", tint = if (nextEnabled) LocalContentColor.current else Color.Gray.copy(alpha = .4f))
            }
        }
    }
}

/** "+12% vs Oct" style comparison, or null when there's nothing to compare. */
fun compareLabel(now: Long, before: Long, beforeName: String): String? {
    if (before <= 0L) return if (now > 0) "new vs $beforeName" else null
    val pct = ((now - before) * 100.0 / before)
    val sign = if (pct >= 0) "+" else "−"
    return "$sign${kotlin.math.abs(pct).toInt()}% vs $beforeName"
}
