package com.momobook.ui.components

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Fmt {
    private val nf = NumberFormat.getIntegerInstance(Locale.US)

    fun num(v: Long): String = nf.format(v)
    fun ugx(v: Long): String = "UGX " + nf.format(v)
    fun signed(v: Long, isIn: Boolean) = (if (isIn) "+" else "−") + nf.format(v)

    fun compact(v: Long): String {
        val a = abs(v.toDouble())
        val s = when {
            a >= 1e9 -> trim(a / 1e9) + "B"
            a >= 1e6 -> trim(a / 1e6) + "M"
            a >= 1e3 -> trim(a / 1e3) + "K"
            else -> nf.format(a.toLong())
        }
        return if (v < 0) "-$s" else s
    }

    private fun trim(d: Double): String {
        val s = if (d >= 100) "%.0f".format(Locale.US, d) else "%.1f".format(Locale.US, d)
        return s.removeSuffix(".0")
    }

    fun time(t: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))
    fun dateTime(t: Long): String = SimpleDateFormat("EEE d MMM yyyy · HH:mm", Locale.getDefault()).format(Date(t))
    fun shortDate(t: Long): String = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(t))
    fun ledgerDate(t: Long): String = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(t))
    fun month(t: Long): String = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(t))

    fun dayHeader(t: Long, now: Long = System.currentTimeMillis()): String {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        val n = Calendar.getInstance().apply { timeInMillis = now }
        val sameYear = c.get(Calendar.YEAR) == n.get(Calendar.YEAR)
        val dayDiff = if (sameYear) n.get(Calendar.DAY_OF_YEAR) - c.get(Calendar.DAY_OF_YEAR) else -1
        return when (dayDiff) {
            0 -> "Today"
            1 -> "Yesterday"
            else -> SimpleDateFormat(if (sameYear) "EEEE, d MMM" else "EEE, d MMM yyyy", Locale.getDefault()).format(Date(t))
        }
    }

    fun relative(t: Long, now: Long = System.currentTimeMillis()): String {
        val m = (now - t) / 60_000
        return when {
            m < 1 -> "just now"
            m < 60 -> "${m}m ago"
            m < 60 * 24 -> "${m / 60}h ago"
            m < 60 * 24 * 7 -> "${m / (60 * 24)}d ago"
            else -> shortDate(t)
        }
    }
}
