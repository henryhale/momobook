package com.momobook.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.momobook.data.Analytics
import com.momobook.data.Entry
import com.momobook.ui.charts.DonutWithLegend
import com.momobook.ui.charts.Slice
import com.momobook.ui.components.*
import com.momobook.ui.theme.LocalMoneyColors
import com.momobook.ui.theme.Tabular
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

/** Summary of one day: totals, end-of-day balance, spending mix and every transaction. */
@Composable
fun DayScreen(
    day: Long,
    entries: List<Entry>,
    onBack: () -> Unit,
    onDay: (Long) -> Unit,
    onOpen: (Entry) -> Unit,
) {
    val mc = LocalMoneyColors.current
    val privacy = LocalBalancePrivacy.current
    val today = remember { Analytics.startOfDay(System.currentTimeMillis()) }
    val w = remember(day) { Analytics.dayWindow(day) }
    val dayEntries = remember(entries, w) { Analytics.inWindow(entries, w).sortedBy { it.occurredAt } }
    val ins = remember(entries, w) { Analytics.compute(entries, w) }
    // Balance at the end of the day: each wallet's last balance on or before this day.
    val closing = remember(entries, w) { Analytics.latestBalances(entries.filter { it.occurredAt < w.to }).values.sumOf { it.balance!! } }
    val title = remember(day) { SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(day) }
    val subtitle = remember(day, today, dayEntries.size) {
        val rel = when (day) { today -> "Today"; Analytics.addDays(today, -1) -> "Yesterday"; else -> null }
        val n = dayEntries.size
        val count = when (n) { 0 -> "no transactions"; 1 -> "1 transaction"; else -> "$n transactions" }
        if (rel != null) "$rel · $count" else count.replaceFirstChar { it.uppercase() }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to calendar") }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onDay(Analytics.addDays(day, -1)) }) { Icon(Icons.Rounded.ChevronLeft, "Previous day") }
                IconButton(onClick = { onDay(Analytics.addDays(day, 1)) }, enabled = day < today) {
                    Icon(Icons.Rounded.ChevronRight, "Next day")
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Money in", Fmt.ugx(ins.totalIn), Icons.Rounded.SouthWest, mc.moneyIn, Modifier.weight(1f),
                        caption = "${dayEntries.count { it.isIn }} received")
                    StatTile("Money out", Fmt.ugx(ins.totalOut), Icons.Rounded.NorthEast, mc.moneyOut, Modifier.weight(1f),
                        caption = "${dayEntries.count { !it.isIn }} spent")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Net", (if (ins.net >= 0) "+" else "−") + Fmt.ugx(abs(ins.net)), Icons.Rounded.SwapVert,
                        if (ins.net >= 0) mc.moneyIn else mc.moneyOut, Modifier.weight(1f))
                    StatTile("Fees paid", Fmt.ugx(ins.fees), Icons.Rounded.Percent, mc.fee, Modifier.weight(1f))
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("End-of-day balance", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FitText(if (privacy.hidden) "UGX $MASK" else Fmt.ugx(closing), style = MaterialTheme.typography.titleMedium.merge(Tabular))
                    }
                    IconButton(onClick = privacy.toggle) {
                        Icon(if (privacy.hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show or hide balances")
                    }
                }
            }
        }
        if (dayEntries.isEmpty()) {
            item { EmptyState(Icons.Rounded.EventBusy, "No transactions", "Nothing moved in your wallet on this day.") }
            return@LazyColumn
        }
        if (ins.byCategory.isNotEmpty()) item {
            SectionCard(title = "Where it went") {
                DonutWithLegend(
                    ins.byCategory.map { Slice(it.name, it.amount, CategoryStyles.of(it.name).color) },
                    "Spent", Fmt.num(ins.totalOut),
                )
            }
        }
        if (ins.topSenders.isNotEmpty()) item {
            SectionCard(title = "Money came from") {
                ins.topSenders.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        FitText("+" + Fmt.ugx(r.amount), Modifier.widthIn(max = 170.dp),
                            style = MaterialTheme.typography.titleSmall.merge(Tabular), color = mc.moneyIn)
                    }
                }
            }
        }
        item {
            SectionCard(title = "Transactions", subtitle = "${dayEntries.size} on this day · oldest first") {
                dayEntries.forEach { e -> EntryRow(e, onClick = { onOpen(e) }) }
            }
        }
    }
}
