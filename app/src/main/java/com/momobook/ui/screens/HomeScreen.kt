package com.momobook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.momobook.data.Analytics
import com.momobook.data.Entry
import com.momobook.ui.charts.RankedBars
import com.momobook.ui.charts.TrendLine
import com.momobook.ui.components.*
import com.momobook.ui.theme.Brand
import com.momobook.ui.theme.LocalMoneyColors
import com.momobook.ui.theme.Tabular

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    entries: List<Entry>,
    syncing: Boolean,
    hasPermission: Boolean,
    walletBalances: List<Pair<String, Long>>,
    onSync: () -> Unit,
    onOpen: (Entry) -> Unit,
    onSeeAll: () -> Unit,
    onInsights: () -> Unit,
    onPaste: () -> Unit,
) {
    val mc = LocalMoneyColors.current
    val monthStart = remember(entries) { Analytics.monthStart() }
    val month = remember(entries) { entries.filter { it.occurredAt >= monthStart } }
    val monthIn = month.sumOf { it.inTotal }
    val monthOut = month.sumOf { it.outTotal }
    val monthFees = month.sumOf { it.fee }
    val latest = entries.filter { it.balance != null }.maxByOrNull { it.occurredAt }
    val total = remember(entries) { Analytics.latestBalances(entries).values.sumOf { it.balance!! } }
    val spark = remember(entries) { Analytics.combinedBalance(entries).takeLast(30) }
    val topCats = remember(month) {
        Analytics.rank(month.filter { !it.isIn }, { it.category }, { it.outTotal }).take(4)
    }

    PullToRefreshBox(isRefreshing = syncing, onRefresh = onSync, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    MomoLogo(size = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("MoMoBook", style = MaterialTheme.typography.titleLarge)
                        Text(
                            Fmt.month(System.currentTimeMillis()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalIconButton(onClick = onPaste) { Icon(Icons.Rounded.ContentPaste, "Add from SMS text") }
                    FilledTonalIconButton(onClick = onSync) { Icon(Icons.Rounded.Sync, "Sync SMS") }
                }
            }
            item { BalanceHero(latest, total, walletBalances, spark, monthIn, monthOut) }
            if (!hasPermission) item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer, onClick = onSync) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Sms, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Allow SMS access to import MoMo messages automatically",
                            Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        "Fees this month", Fmt.ugx(monthFees), Icons.Rounded.Percent, mc.fee, Modifier.weight(1f),
                        caption = "${month.count { it.fee > 0 }} charged",
                    )
                    StatTile(
                        "Transactions", month.size.toString(), Icons.Rounded.Receipt, MaterialTheme.colorScheme.secondary,
                        Modifier.weight(1f), caption = "${month.count { it.isIn }} in · ${month.count { !it.isIn }} out",
                    )
                }
            }
            if (topCats.isNotEmpty()) item {
                SectionCard(
                    title = "Where your money went",
                    subtitle = "This month by category",
                    action = { TextButton(onClick = onInsights) { Text("Insights") } },
                ) {
                    RankedBarsByCategory(topCats)
                }
            }
            item {
                SectionCard(
                    title = "Recent activity",
                    action = { if (entries.isNotEmpty()) TextButton(onClick = onSeeAll) { Text("See all") } },
                ) {
                    if (entries.isEmpty()) {
                        EmptyState(
                            Icons.Rounded.Inbox, "No transactions yet",
                            "Pull down to scan your SMS inbox, or paste a MoMo message.",
                        )
                    } else {
                        entries.take(8).forEach { EntryRow(it, onClick = { onOpen(it) }, showDate = true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RankedBarsByCategory(items: List<com.momobook.data.Ranked>) {
    // One bar list per category colour would be noisy; colour each row by its category instead.
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val max = items.maxOf { it.amount }.coerceAtLeast(1)
        items.forEach { r ->
            val style = CategoryStyles.of(r.name)
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(r.name, 36)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(Fmt.ugx(r.amount), style = MaterialTheme.typography.bodyMedium.merge(Tabular), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        Box(Modifier.fillMaxWidth(r.amount.toFloat() / max).fillMaxHeight().clip(CircleShape).background(style.color))
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceHero(
    latest: Entry?,
    total: Long,
    walletBalances: List<Pair<String, Long>>,
    spark: List<Pair<Long, Long>>,
    monthIn: Long,
    monthOut: Long,
) {
    val privacy = LocalBalancePrivacy.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Brand.Navy, Brand.Ink)))
            .drawBehind {
                // Decorative coin glow in the corner, echoing the logo.
                drawCircle(Brand.Yellow.copy(alpha = .16f), size.width * .42f, Offset(size.width * 1.02f, -size.height * .05f))
                drawCircle(Brand.Yellow.copy(alpha = .10f), size.width * .28f, Offset(size.width * 1.02f, -size.height * .05f))
            }
            .padding(22.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(Brand.Yellow))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (walletBalances.size > 1) "Mobile Money · all wallets" else "Mobile Money balance",
                    Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .75f),
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                IconButton(onClick = privacy.toggle, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (privacy.hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        if (privacy.hidden) "Show balances" else "Hide balances",
                        tint = Brand.Yellow,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            FitText(
                when {
                    latest == null -> "UGX —"
                    privacy.hidden -> "UGX $MASK"
                    else -> Fmt.ugx(total)
                },
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.displaySmall.merge(Tabular), color = Color.White,
            )
            Text(
                latest?.let { "As of ${Fmt.relative(it.occurredAt)} · from your last MoMo SMS" } ?: "Waiting for your first MoMo SMS",
                style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .55f),
            )
            if (walletBalances.size > 1) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    walletBalances.forEach { (label, bal) ->
                        FitText(
                            "$label  " + if (privacy.hidden) "UGX $MASK" else Fmt.ugx(bal),
                            modifier = Modifier.weight(1f, fill = false).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = .08f))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium.merge(Tabular), color = Color.White.copy(alpha = .85f),
                        )
                    }
                }
            }
            if (spark.size >= 2) {
                Spacer(Modifier.height(14.dp))
                TrendLine(spark, Brand.Yellow, Modifier.fillMaxWidth().height(56.dp), showAxis = false, fillAlpha = .22f)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroChip(Icons.Rounded.SouthWest, "In · this month", monthIn, Color(0xFF47CD89), Modifier.weight(1f))
                HeroChip(Icons.Rounded.NorthEast, "Out · this month", monthOut, Color(0xFFF97066), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroChip(icon: ImageVector, label: String, value: Long, tint: Color, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .07f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(tint.copy(alpha = .18f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            FitText(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .6f))
            FitText(Fmt.num(value), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium.merge(Tabular), color = Color.White)
        }
    }
}
