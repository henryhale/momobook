package com.momobook.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.momobook.data.Analytics
import com.momobook.data.DayTotal
import com.momobook.data.Entry
import com.momobook.ui.components.*
import com.momobook.ui.theme.Brand
import com.momobook.ui.theme.LocalMoneyColors
import com.momobook.ui.theme.Tabular
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/** Month grid: each day shows its money in (green) and out (red). Tap a day to open its summary. */
@Composable
fun CalendarScreen(
    entries: List<Entry>,
    monthOffset: Int,
    onMonthOffset: (Int) -> Unit,
    onOpenDay: (Long) -> Unit,
) {
    val mc = LocalMoneyColors.current
    val window = remember(monthOffset) { Analytics.monthWindow(monthOffset) }
    val totals = remember(entries, window) { Analytics.dayTotals(entries, window) }
    val monthIn = totals.values.sumOf { it.inAmt }
    val monthOut = totals.values.sumOf { it.outAmt }
    val maxOut = totals.values.maxOfOrNull { it.outAmt }?.coerceAtLeast(1) ?: 1
    val today = remember { Analytics.startOfDay(System.currentTimeMillis()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)
            // Swipe left/right to change month.
            .pointerInput(monthOffset) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        if (drag > 120) onMonthOffset(monthOffset - 1)
                        else if (drag < -120 && monthOffset < 0) onMonthOffset(monthOffset + 1)
                    },
                ) { _, dx -> drag += dx }
            },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Calendar", style = MaterialTheme.typography.headlineSmall)
                Text("Tap a day to see what happened", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (monthOffset != 0) {
                AssistChip(
                    onClick = { onMonthOffset(0) }, label = { Text("Today") },
                    leadingIcon = { Icon(Icons.Rounded.Today, null, Modifier.size(18.dp)) },
                    shape = RoundedCornerShape(12.dp),
                )
            }
        }

        MonthStepper(
            label = Fmt.month(window.from),
            onPrev = { onMonthOffset(monthOffset - 1) },
            onNext = { onMonthOffset((monthOffset + 1).coerceAtMost(0)) },
            nextEnabled = monthOffset < 0,
        )

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            MonthSum("Money in", "+" + Fmt.num(monthIn), mc.moneyIn, Modifier.weight(1f))
            MonthSum("Money out", "−" + Fmt.num(monthOut), mc.moneyOut, Modifier.weight(1f))
            val net = monthIn - monthOut
            MonthSum("Net", (if (net >= 0) "+" else "−") + Fmt.num(abs(net)), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        }

        SectionCard {
            MonthGrid(window.from, totals, maxOut, today, onOpenDay)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(mc.moneyIn, "Money in")
                LegendDot(mc.moneyOut, "Money out")
            }
            Text(
                "Darker days mean more spending · swipe to change month",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        val topDays = remember(totals) { totals.entries.filter { it.value.outAmt > 0 }.sortedByDescending { it.value.outAmt }.take(3) }
        if (topDays.isNotEmpty()) {
            SectionCard(title = "Biggest spending days", subtitle = "This month") {
                topDays.forEach { (day, t) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onOpenDay(day) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(Fmt.dayHeader(day), style = MaterialTheme.typography.bodyLarge)
                            Text("${t.count} transaction${if (t.count == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FitText("−" + Fmt.ugx(t.outAmt), Modifier.widthIn(max = 170.dp),
                            style = MaterialTheme.typography.titleSmall.merge(Tabular), color = mc.moneyOut)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MonthSum(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FitText(value, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall.merge(Tabular), color = color)
    }
}

@Composable
private fun MonthGrid(monthStart: Long, totals: Map<Long, DayTotal>, maxOut: Long, today: Long, onOpenDay: (Long) -> Unit) {
    val mc = LocalMoneyColors.current
    val cal = remember(monthStart) { Calendar.getInstance().apply { timeInMillis = monthStart } }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val lead = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Monday-first
    val weekdays = remember {
        val f = SimpleDateFormat("EEEEE", Locale.getDefault())
        // 2024-01-01 was a Monday.
        (0 until 7).map { f.format(Calendar.getInstance().apply { set(2024, 0, 1 + it) }.time) }
    }

    Row(Modifier.fillMaxWidth()) {
        weekdays.forEach {
            Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(6.dp))
    val cells = List(lead) { null } + (1..daysInMonth).toList()
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until 7) {
                val dayNum = week.getOrNull(i)
                Box(Modifier.weight(1f).height(66.dp).padding(vertical = 2.dp)) {
                    if (dayNum != null) {
                        val day = Analytics.addDays(monthStart, dayNum - 1)
                        DayCell(dayNum, totals[day], maxOut, day == today, day > today) { onOpenDay(day) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(num: Int, t: DayTotal?, maxOut: Long, isToday: Boolean, isFuture: Boolean, onClick: () -> Unit) {
    val mc = LocalMoneyColors.current
    // Heat: tint by how much was spent relative to the month's biggest day.
    val heat = if (t != null && t.outAmt > 0) 0.07f + 0.23f * (t.outAmt.toFloat() / maxOut) else 0f
    val bg = when {
        t == null -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isFuture) .25f else .55f)
        heat > 0f -> mc.moneyOut.copy(alpha = heat)
        else -> mc.moneyIn.copy(alpha = .12f)
    }
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(bg)
            .then(if (isToday) Modifier.border(BorderStroke(2.dp, Brand.Yellow), RoundedCornerShape(12.dp)) else Modifier)
            .then(if (!isFuture) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 3.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            num.toString(), style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (isFuture) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f) else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        if (t != null && t.inAmt > 0) {
            FitText("+" + Fmt.compact(t.inAmt), style = MaterialTheme.typography.labelSmall.merge(Tabular), color = mc.moneyIn,
                fontWeight = FontWeight.Bold)
        }
        if (t != null && t.outAmt > 0) {
            FitText("−" + Fmt.compact(t.outAmt), style = MaterialTheme.typography.labelSmall.merge(Tabular), color = mc.moneyOut,
                fontWeight = FontWeight.Bold)
        }
    }
}
