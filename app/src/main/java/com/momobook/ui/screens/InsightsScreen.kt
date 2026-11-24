package com.momobook.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.momobook.data.Analytics
import com.momobook.data.Entry
import com.momobook.data.Period
import com.momobook.data.Ranked
import com.momobook.ui.charts.*
import com.momobook.ui.components.*
import com.momobook.ui.theme.Brand
import com.momobook.ui.theme.LocalMoneyColors
import com.momobook.ui.theme.Tabular

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(entries: List<Entry>) {
    var period by rememberSaveable { mutableStateOf(Period.MONTH) }
    /** null = preset range; otherwise months from this month (0 = this month, -1 = last month…). */
    var monthOffset by rememberSaveable { mutableStateOf<Int?>(null) }
    val window = remember(entries, period, monthOffset) {
        monthOffset?.let { Analytics.monthWindow(it) } ?: Analytics.window(period, entries)
    }
    val ins = remember(entries, window) { Analytics.compute(entries, window) }
    // Month mode compares against the previous month.
    val prev = remember(entries, monthOffset) { monthOffset?.let { Analytics.compute(entries, Analytics.monthWindow(it - 1)) } }
    val prevName = remember(monthOffset) { monthOffset?.let { java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault()).format(Analytics.monthWindow(it - 1).from) } ?: "" }
    val mc = LocalMoneyColors.current
    val lineColor = if (MaterialTheme.colorScheme.primary == Brand.Ink) Color(0xFF2E90FA) else Brand.Yellow

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text("Insights", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "How money moves through your wallet",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Column {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Period.entries.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = monthOffset == null && period == p, onClick = { period = p; monthOffset = null },
                        shape = SegmentedButtonDefaults.itemShape(i, Period.entries.size),
                        icon = {},
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) { Text(p.label) }
                }
            }
            Spacer(Modifier.height(10.dp))
            MonthStepper(
                label = Fmt.month(Analytics.monthWindow(monthOffset ?: 0).from),
                active = monthOffset != null,
                onPrev = { monthOffset = (monthOffset ?: 0) - 1 },
                onNext = { monthOffset = ((monthOffset ?: 0) + 1).coerceAtMost(0) },
                nextEnabled = monthOffset != null && monthOffset!! < 0,
                onLabelClick = { if (monthOffset == null) monthOffset = 0 },
            )
        } }
        if (ins.count == 0) {
            item {
                EmptyState(Icons.Rounded.Insights, "Nothing to analyse yet", "No transactions in this period. Try a longer range.")
            }
            return@LazyColumn
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Money in", Fmt.ugx(ins.totalIn), Icons.Rounded.SouthWest, mc.moneyIn, Modifier.weight(1f),
                        caption = prev?.let { compareLabel(ins.totalIn, it.totalIn, prevName) })
                    StatTile("Money out", Fmt.ugx(ins.totalOut), Icons.Rounded.NorthEast, mc.moneyOut, Modifier.weight(1f),
                        caption = prev?.let { compareLabel(ins.totalOut, it.totalOut, prevName) } ?: "incl. fees")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        "Net flow", (if (ins.net >= 0) "+" else "−") + Fmt.ugx(kotlin.math.abs(ins.net)),
                        Icons.Rounded.SwapVert, if (ins.net >= 0) mc.moneyIn else mc.moneyOut, Modifier.weight(1f),
                    )
                    StatTile("Fees paid", Fmt.ugx(ins.fees), Icons.Rounded.Percent, mc.fee, Modifier.weight(1f),
                        caption = if (ins.totalOut > 0) "%.1f%% of spending".format(ins.fees * 100.0 / ins.totalOut) else null)
                }
            }
        }
        if (ins.flow.isNotEmpty()) item {
            SectionCard(title = "Cash flow", subtitle = "Money in vs money out") {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(mc.moneyIn, "In"); LegendDot(mc.moneyOut, "Out")
                }
                Spacer(Modifier.height(12.dp))
                CashFlowChart(ins.flow, mc.moneyIn, mc.moneyOut)
            }
        }
        if (ins.byCategory.isNotEmpty()) item {
            val slices = remember(ins) { categorySlices(ins.byCategory) }
            SectionCard(title = "Spending by category", subtitle = "Where the money went") {
                DonutWithLegend(slices, "Spent", Fmt.num(ins.totalOut))
            }
        }
        if (ins.topPayees.isNotEmpty()) item {
            SectionCard(title = "Top spending", subtitle = "Payees, merchants and services you paid most") {
                RankedBars(ins.topPayees, mc.moneyOut, "payment")
            }
        }
        if (ins.topSenders.isNotEmpty()) item {
            SectionCard(title = "Top sources", subtitle = "Agents, people and companies that sent you money") {
                RankedBars(ins.topSenders, mc.moneyIn, "deposit")
            }
        }
        if (ins.balance.size >= 2) item {
            val privacy = LocalBalancePrivacy.current
            SectionCard(
                title = "Balance trend", subtitle = "Wallet balance reported in each SMS",
                action = {
                    IconButton(onClick = privacy.toggle) {
                        Icon(if (privacy.hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Show or hide balances")
                    }
                },
            ) {
                if (privacy.hidden) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text("Balances hidden · tap the eye to show", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    return@SectionCard
                }
                TrendLine(ins.balance, lineColor, Modifier.fillMaxWidth().height(180.dp))
                Spacer(Modifier.height(8.dp))
                Row {
                    Text(Fmt.shortDate(ins.balance.first().first), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Fmt.shortDate(ins.balance.last().first), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SectionCard(title = "Highlights") {
                Highlight(Icons.Rounded.CalendarToday, "Average daily spend", Fmt.ugx(ins.avgDailySpend), null)
                ins.biggestOut?.let { Highlight(Icons.Rounded.TrendingUp, "Biggest expense", Fmt.ugx(it.outTotal), "${it.displayName} · ${Fmt.shortDate(it.occurredAt)}", mc.moneyOut) }
                ins.biggestIn?.let { Highlight(Icons.Rounded.Savings, "Biggest deposit", Fmt.ugx(it.amount), "${it.displayName} · ${Fmt.shortDate(it.occurredAt)}", mc.moneyIn) }
                ins.busiestWeekday?.let { Highlight(Icons.Rounded.Event, "Heaviest spending day", it, "Day of week with the most money out") }
                Highlight(Icons.Rounded.Receipt, "Transactions", ins.count.toString(), null)
            }
        }
    }
}

private fun categorySlices(ranked: List<Ranked>): List<Slice> {
    val top = ranked.take(6)
    val rest = ranked.drop(6).sumOf { it.amount }
    return top.map { Slice(it.name, it.amount, CategoryStyles.of(it.name).color) } +
        if (rest > 0) listOf(Slice("Everything else", rest, Color(0xFF85888E))) else emptyList()
}

@Composable
private fun Highlight(icon: ImageVector, label: String, value: String, caption: String?, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(12.dp), color = tint.copy(alpha = .12f), modifier = Modifier.size(38.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (caption != null) Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        FitText(value, Modifier.widthIn(max = 160.dp), style = MaterialTheme.typography.titleSmall.merge(Tabular), fontWeight = FontWeight.Bold)
    }
}
