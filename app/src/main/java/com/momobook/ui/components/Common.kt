package com.momobook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.momobook.data.Entry
import com.momobook.ui.theme.LocalMoneyColors
import com.momobook.ui.theme.Tabular

/** Wallet labels to tag rows with; empty when only one wallet is in view. */
val LocalWalletLabels = staticCompositionLocalOf<Map<String, String>> { emptyMap() }

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        if (subtitle != null) Text(
                            subtitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    action?.invoke()
                }
                Spacer(Modifier.height(16.dp))
            }
            content()
        }
    }
}

@Composable
fun CategoryIcon(category: String, size: Int = 44) {
    val s = CategoryStyles.of(category)
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.32f).dp)).background(s.color.copy(alpha = .14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(s.icon, null, tint = s.color, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
fun DirectionPill(isIn: Boolean) {
    val mc = LocalMoneyColors.current
    val (fg, bg) = if (isIn) mc.moneyIn to mc.inContainer else mc.moneyOut to mc.outContainer
    Text(
        if (isIn) "CR" else "DR",
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 6.dp, vertical = 1.dp),
        color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp,
    )
}

/** A colour-coded journal line: a coloured rail, category icon, payee, and signed amount. */
@Composable
fun EntryRow(e: Entry, onClick: () -> Unit, showDate: Boolean = false) {
    val mc = LocalMoneyColors.current
    val accent = if (e.isIn) mc.moneyIn else mc.moneyOut
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(36.dp).clip(CircleShape).background(accent.copy(alpha = .85f)))
        Spacer(Modifier.width(10.dp))
        CategoryIcon(e.category)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                buildString {
                    LocalWalletLabels.current[e.wallet]?.let { append(it); append(" · ") }
                    append(e.category); append(" · ")
                    append(if (showDate) Fmt.shortDate(e.occurredAt) + " " + Fmt.time(e.occurredAt) else Fmt.time(e.occurredAt))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Fmt.signed(e.amount, e.isIn),
                style = MaterialTheme.typography.bodyLarge.merge(Tabular),
                fontWeight = FontWeight.Bold, color = accent,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (e.fee > 0) {
                    Text(
                        "fee ${Fmt.num(e.fee)}  ", style = MaterialTheme.typography.labelSmall,
                        color = LocalMoneyColors.current.fee,
                    )
                }
                DirectionPill(e.isIn)
            }
        }
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = .14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FitText(value, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge.merge(Tabular))
            if (caption != null) Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
