package com.momobook.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.momobook.data.Analytics
import com.momobook.data.Entry
import com.momobook.ui.components.*
import com.momobook.ui.theme.LocalMoneyColors
import com.momobook.ui.theme.Tabular

private enum class Flow(val label: String) { ALL("All"), IN("Money in"), OUT("Money out") }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun JournalScreen(
    entries: List<Entry>,
    syncing: Boolean,
    onSync: () -> Unit,
    onOpen: (Entry) -> Unit,
    onPaste: () -> Unit,
    onExport: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var flow by rememberSaveable { mutableStateOf(Flow.ALL) }
    var ledgerView by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }

    val categories = remember(entries) { entries.map { it.category }.distinct().sorted() }
    val shown = remember(entries, query, flow, category) {
        val q = query.trim().lowercase()
        entries.filter { e ->
            (flow == Flow.ALL || (flow == Flow.IN) == e.isIn) &&
                (category == null || e.category == category) &&
                (q.isEmpty() || listOf(e.displayName, e.counterparty, e.txnId, e.category, e.reason ?: "", e.phone ?: "", e.amount.toString())
                    .any { it.lowercase().contains(q) })
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Journal", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${entries.size} entries · saved on this phone",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { ledgerView = !ledgerView }) {
                Icon(if (ledgerView) Icons.Rounded.ViewAgenda else Icons.Rounded.TableRows, if (ledgerView) "List view" else "Ledger view")
            }
            IconButton(onClick = onPaste) { Icon(Icons.Rounded.ContentPaste, "Add from SMS text") }
            IconButton(onClick = onExport, enabled = entries.isNotEmpty()) { Icon(Icons.Rounded.IosShare, "Export CSV") }
        }

        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search...", maxLines = 1) },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") } },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedBorderColor = Color.Transparent,
            ),
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val mc = LocalMoneyColors.current
            Flow.entries.forEach { f ->
                FilterChip(
                    selected = flow == f, onClick = { flow = f },
                    label = { Text(f.label) },
                    leadingIcon = when (f) {
                        Flow.IN -> {{ Icon(Icons.Rounded.SouthWest, null, Modifier.size(16.dp), tint = mc.moneyIn) }}
                        Flow.OUT -> {{ Icon(Icons.Rounded.NorthEast, null, Modifier.size(16.dp), tint = mc.moneyOut) }}
                        else -> null
                    },
                    shape = RoundedCornerShape(12.dp),
                )
            }
            if (categories.size > 1) {
                VerticalDivider(Modifier.height(32.dp).align(Alignment.CenterVertically))
                categories.forEach { c ->
                    FilterChip(
                        selected = category == c, onClick = { category = if (category == c) null else c },
                        label = { Text(c) },
                        leadingIcon = { Icon(CategoryStyles.of(c).icon, null, Modifier.size(16.dp), tint = CategoryStyles.of(c).color) },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
        }

        SummaryStrip(shown)

        PullToRefreshBox(isRefreshing = syncing, onRefresh = onSync, modifier = Modifier.fillMaxSize()) {
            if (shown.isEmpty()) {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        EmptyState(
                            Icons.Rounded.SearchOff,
                            if (entries.isEmpty()) "Your journal is empty" else "No matching entries",
                            if (entries.isEmpty()) "Pull down to scan your SMS inbox for mobile money and bank SMS." else "Try a different search or filter.",
                        )
                    }
                }
            } else if (ledgerView) {
                LedgerTable(shown, onOpen)
            } else {
                val grouped = remember(shown) { shown.groupBy { Analytics.startOfDay(it.occurredAt) } }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp),
                ) {
                    grouped.forEach { (day, list) ->
                        stickyHeader(key = "h$day") { DayHeader(day, list) }
                        items(list, key = { it.id }) { e -> EntryRow(e, onClick = { onOpen(e) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStrip(shown: List<Entry>) {
    val mc = LocalMoneyColors.current
    val inSum = shown.sumOf { it.inTotal }
    val outSum = shown.sumOf { it.outTotal }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SumCell("Credits (in)", "+" + Fmt.num(inSum), mc.moneyIn, Modifier.weight(1f))
        SumCell("Debits (out)", "−" + Fmt.num(outSum), mc.moneyOut, Modifier.weight(1f))
        SumCell("Net", (if (inSum - outSum >= 0) "+" else "−") + Fmt.num(kotlin.math.abs(inSum - outSum)), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
    }
}

@Composable
private fun SumCell(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FitText(value, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall.merge(Tabular), color = color)
    }
}

@Composable
private fun DayHeader(day: Long, list: List<Entry>) {
    val mc = LocalMoneyColors.current
    val inSum = list.sumOf { it.inTotal }
    val outSum = list.sumOf { it.outTotal }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(Fmt.dayHeader(day), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (inSum > 0) Text("+${Fmt.compact(inSum)}  ", style = MaterialTheme.typography.labelMedium.merge(Tabular), color = mc.moneyIn)
        if (outSum > 0) Text("−${Fmt.compact(outSum)}", style = MaterialTheme.typography.labelMedium.merge(Tabular), color = mc.moneyOut)
    }
}

/** Classic statement-style ledger: Date | Particulars | Debit | Credit | Balance. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LedgerTable(rows: List<Entry>, onOpen: (Entry) -> Unit) {
    val mc = LocalMoneyColors.current
    val hidden = LocalBalancePrivacy.current.hidden
    val labelStyle = MaterialTheme.typography.labelSmall
    val cell = MaterialTheme.typography.bodySmall.merge(Tabular)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        stickyHeader {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(MaterialTheme.colorScheme.primary).padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                val c = MaterialTheme.colorScheme.onPrimary
                Text("Date", Modifier.weight(.9f), style = labelStyle, color = c, fontWeight = FontWeight.Bold)
                Text("Particulars", Modifier.weight(1.8f), style = labelStyle, color = c, fontWeight = FontWeight.Bold)
                Text("Dr (out)", Modifier.weight(1f), style = labelStyle, color = c, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                Text("Cr (in)", Modifier.weight(1f), style = labelStyle, color = c, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                Text("Balance", Modifier.weight(1.1f), style = labelStyle, color = c, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
            }
        }
        items(rows.size, key = { rows[it].id }) { i ->
            val e = rows[i]
            val bg = if (e.isIn) mc.inContainer.copy(alpha = .45f) else if (i % 2 == 0) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer
            Row(
                Modifier.fillMaxWidth().background(bg).clickable { onOpen(e) }.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(Fmt.ledgerDate(e.occurredAt), Modifier.weight(.9f), style = cell, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1.8f)) {
                    Text(e.displayName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("#${e.txnId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                FitText(if (!e.isIn) Fmt.num(e.outTotal) else "", Modifier.weight(1f), style = cell, color = mc.moneyOut, textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold)
                FitText(if (e.isIn) Fmt.num(e.amount) else "", Modifier.weight(1f), style = cell, color = mc.moneyIn, textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold)
                FitText(if (hidden) MASK else e.balance?.let { Fmt.num(it) } ?: "—", Modifier.weight(1.1f), style = cell, textAlign = TextAlign.End)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 10.dp, vertical = 12.dp)
            ) {
                Text("Totals", Modifier.weight(2.7f), style = labelStyle, fontWeight = FontWeight.Bold)
                FitText(Fmt.num(rows.sumOf { it.outTotal }), Modifier.weight(1f), style = cell, color = mc.moneyOut, textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
                FitText(Fmt.num(rows.sumOf { it.inTotal }), Modifier.weight(1f), style = cell, color = mc.moneyIn, textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1.1f))
            }
        }
    }
}
