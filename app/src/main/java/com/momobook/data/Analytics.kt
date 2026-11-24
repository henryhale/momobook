package com.momobook.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class Period(val label: String, val days: Int?) {
    WEEK("7D", 7), MONTH("30D", 30), QUARTER("90D", 90), YEAR("1Y", 365), ALL("All", null)
}

enum class Gran { DAY, WEEK, MONTH }

/** A time window [from, to) and how to bucket its cash-flow chart. */
data class Window(val from: Long, val to: Long, val gran: Gran)

data class Ranked(val name: String, val amount: Long, val count: Int)
data class FlowBucket(val label: String, val inAmt: Long, val outAmt: Long)
data class DayTotal(val inAmt: Long, val outAmt: Long, val count: Int)

data class Insights(
    val totalIn: Long,
    val totalOut: Long,
    val fees: Long,
    val count: Int,
    val byCategory: List<Ranked>,
    val topPayees: List<Ranked>,
    val topSenders: List<Ranked>,
    val flow: List<FlowBucket>,
    val balance: List<Pair<Long, Long>>,
    val avgDailySpend: Long,
    val biggestOut: Entry?,
    val biggestIn: Entry?,
    val busiestWeekday: String?,
) {
    val net get() = totalIn - totalOut
}

object Analytics {
    const val DAY = 86_400_000L

    private fun cal(t: Long) = Calendar.getInstance().apply { timeInMillis = t }

    fun startOfDay(t: Long): Long = cal(t).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun monthStart(now: Long = System.currentTimeMillis()): Long =
        cal(startOfDay(now)).apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis

    /** Adds whole days using the calendar (safe across DST changes). */
    fun addDays(day: Long, n: Int): Long = cal(day).apply { add(Calendar.DAY_OF_MONTH, n) }.timeInMillis

    private fun addMonths(t: Long, n: Int): Long = cal(t).apply { add(Calendar.MONTH, n) }.timeInMillis

    /** Window for a preset range ending today. */
    fun window(period: Period, all: List<Entry>, now: Long = System.currentTimeMillis()): Window {
        val end = addDays(startOfDay(now), 1)
        return when (period) {
            Period.WEEK -> Window(addDays(end, -7), end, Gran.DAY)
            Period.MONTH -> Window(addDays(end, -30), end, Gran.WEEK)
            Period.QUARTER -> Window(addDays(end, -90), end, Gran.WEEK)
            Period.YEAR -> Window(addDays(end, -365), end, Gran.MONTH)
            Period.ALL -> Window(startOfDay(all.minOfOrNull { it.occurredAt } ?: now), end, Gran.MONTH)
        }
    }

    /** Calendar month window; offset 0 = this month, -1 = last month, … */
    fun monthWindow(offset: Int, now: Long = System.currentTimeMillis()): Window {
        val from = addMonths(monthStart(now), offset)
        return Window(from, addMonths(from, 1), Gran.DAY)
    }

    fun dayWindow(day: Long): Window = startOfDay(day).let { Window(it, addDays(it, 1), Gran.DAY) }

    fun inWindow(entries: List<Entry>, w: Window) = entries.filter { it.occurredAt >= w.from && it.occurredAt < w.to }

    /** Money in / out per day (keyed by start of day). */
    fun dayTotals(entries: List<Entry>, w: Window): Map<Long, DayTotal> =
        inWindow(entries, w).groupBy { startOfDay(it.occurredAt) }
            .mapValues { (_, v) -> DayTotal(v.sumOf { it.inTotal }, v.sumOf { it.outTotal }, v.size) }

    fun rank(entries: List<Entry>, key: (Entry) -> String, value: (Entry) -> Long): List<Ranked> =
        entries.groupBy(key)
            .map { (k, v) -> Ranked(k, v.sumOf(value), v.size) }
            .filter { it.amount > 0 }
            .sortedByDescending { it.amount }

    fun compute(all: List<Entry>, w: Window, now: Long = System.currentTimeMillis()): Insights {
        val es = inWindow(all, w)
        val outs = es.filter { !it.isIn }
        val ins = es.filter { it.isIn }
        val effectiveEnd = minOf(w.to, addDays(startOfDay(now), 1))
        val spanDays = ((effectiveEnd - w.from + DAY / 2) / DAY).coerceAtLeast(1)
        val weekdayFmt = SimpleDateFormat("EEEE", Locale.getDefault())
        return Insights(
            totalIn = ins.sumOf { it.amount },
            totalOut = outs.sumOf { it.outTotal },
            fees = es.sumOf { it.fee },
            count = es.size,
            byCategory = rank(outs, { it.category }, { it.outTotal }),
            topPayees = rank(outs, { it.displayName }, { it.outTotal }).take(6),
            topSenders = rank(ins, { it.displayName }, { it.amount }).take(6),
            flow = flow(es, w, effectiveEnd),
            balance = combinedBalance(es),
            avgDailySpend = outs.sumOf { it.outTotal } / spanDays,
            biggestOut = outs.maxByOrNull { it.outTotal },
            biggestIn = ins.maxByOrNull { it.amount },
            busiestWeekday = outs.groupBy { weekdayFmt.format(it.occurredAt) }
                .maxByOrNull { (_, v) -> v.sumOf { it.outTotal } }?.key,
        )
    }

    /** Total balance over time: the sum of each wallet's last known balance. */
    fun combinedBalance(es: List<Entry>): List<Pair<Long, Long>> {
        val last = HashMap<String, Long>()
        return es.filter { it.balance != null }.sortedBy { it.occurredAt }.map { e ->
            last[e.wallet] = e.balance!!
            e.occurredAt to last.values.sum()
        }
    }

    /** Latest reported balance per wallet. */
    fun latestBalances(es: List<Entry>): Map<String, Entry> =
        es.filter { it.balance != null }.groupBy { it.wallet }.mapValues { (_, v) -> v.maxBy { it.occurredAt } }

    private fun flow(es: List<Entry>, w: Window, end: Long): List<FlowBucket> {
        if (es.isEmpty() || end <= w.from) return emptyList()
        val starts = mutableListOf<Long>()
        when (w.gran) {
            Gran.DAY -> { var d = w.from; while (d < end) { starts += d; d = addDays(d, 1) } }
            Gran.WEEK -> { var d = end; while (d > w.from) { d = addDays(d, -7); starts.add(0, maxOf(d, w.from)) } }
            Gran.MONTH -> { var m = monthStart(w.from); while (m < end) { starts += maxOf(m, w.from); m = addMonths(m, 1) } }
        }
        val fmt = SimpleDateFormat(
            when (w.gran) {
                Gran.DAY -> if (starts.size <= 7) "EEE" else "d"
                Gran.WEEK -> "d MMM"
                Gran.MONTH -> if (starts.size > 12) "MMM yy" else "MMM"
            },
            Locale.getDefault(),
        )
        return starts.mapIndexed { i, s ->
            val e = starts.getOrNull(i + 1) ?: end
            val b = es.filter { it.occurredAt >= s && it.occurredAt < e }
            FlowBucket(fmt.format(s), b.sumOf { it.inTotal }, b.sumOf { it.outTotal })
        }
    }
}
