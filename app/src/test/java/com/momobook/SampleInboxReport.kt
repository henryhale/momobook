package com.momobook

import com.momobook.data.Categorizer
import com.momobook.data.Direction
import com.momobook.data.MomoParser
import com.momobook.data.ParsedTxn
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Local-only check against real messages pulled with adb into sms-samples/ (git-ignored).
 * Replays the inbox like the app does and verifies the balance chain: every reported balance
 * must equal the previous balance + money in − money out − fees. Writes app/build/sample-report.txt.
 */
class SampleInboxReport {
    private data class Sms(val date: Long, val address: String, val body: String, val sim: String)

    @Test fun report() {
        val f = listOf("../sms-samples/momo-inbox-full.txt", "../sms-samples/momo-inbox-sample.txt").map(::File).firstOrNull { it.exists() }
        assumeTrue(f != null)
        val sms = f!!.readText().split(Regex("""(?m)^Row: \d+ """)).filter { it.isNotBlank() }.mapNotNull { r ->
            val date = Regex("""date=(\d+)""").find(r)?.groupValues?.get(1)?.toLong() ?: return@mapNotNull null
            val addr = Regex("""address=([^,]*)""").find(r)?.groupValues?.get(1) ?: ""
            val sim = Regex("""sub_id=(-?\d+)""").find(r)?.groupValues?.get(1) ?: "?"
            Sms(date, addr, r.substringAfter("body=").trim(), sim)
        }.sortedBy { it.date }

        val out = StringBuilder()
        val byId = LinkedHashMap<String, Pair<Sms, ParsedTxn>>()
        val requests = mutableListOf<Pair<Long, com.momobook.data.WithdrawRequest>>()
        val unparsed = mutableListOf<String>()
        for (s in sms) {
            if (!MomoParser.isMomoSender(s.address)) continue
            MomoParser.parseWithdrawRequest(s.body)?.let { requests += s.date to it; continue }
            var p = MomoParser.parse(s.body)
            if (p == null) {
                if (s.body.contains("UGX") && Regex("(?i)balance").containsMatchIn(s.body)) unparsed += s.body
                continue
            }
            if (p.kind == com.momobook.data.Kind.WITHDRAW && p.fee == 0L) {
                val at = p.occurredAt ?: s.date
                requests.lastOrNull { (t, r) -> r.amount == p!!.amount && r.agent.equals(p!!.counterparty, true) && t in (at - 172_800_000)..(at + 300_000) }
                    ?.let { p = p!!.copy(fee = it.second.fee) }
            }
            p = p!!.copy(txnId = s.sim + ":" + p!!.txnId)
            val existing = byId[p!!.txnId]
            if (existing == null || p!!.counterparty.length > existing.second.counterparty.length) byId[p!!.txnId] = (existing?.first ?: s) to p!!
        }

        val bySms = byId.values.sortedBy { it.first.date }
        val byOccurred = byId.values.sortedBy { it.second.occurredAt ?: it.first.date }
        fun chain(list: List<Pair<Sms, ParsedTxn>>): Pair<Int, Long> {
            var pr: Long? = null; var n = 0; var net = 0L
            list.forEach { (_, p) ->
                val d = if (p.direction == Direction.CR) p.amount - p.fee else -(p.amount + p.fee)
                if (p.balance != null && pr != null && abs(pr!! + d - p.balance!!) > 1) { n++; net += p.balance!! - (pr!! + d) }
                if (p.balance != null) pr = p.balance
            }
            return n to net
        }
        out.appendLine("Chain by SMS date: " + chain(bySms) + "   by occurredAt: " + chain(byOccurred))
        bySms.groupBy { it.first.sim }.forEach { (sim, l) ->
            out.appendLine("  SIM $sim: ${l.size} txns, chain breaks/net=${chain(l)}, final balance=${l.lastOrNull { it.second.balance != null }?.second?.balance}, " +
                "first=${java.util.Date(l.first().first.date)}")
        }
        val txns = bySms
        var prev: Long? = null
        var breaks = 0
        txns.forEach { (s, p) ->
            val bal = p.balance
            val delta = if (p.direction == Direction.CR) p.amount - p.fee else -(p.amount + p.fee)
            if (bal != null && prev != null && abs(prev!! + delta - bal) > 1) {
                breaks++
                if (breaks <= 25) out.appendLine("BREAK expected=${prev!! + delta} got=$bal diff=${bal - (prev!! + delta)} | ${s.body.take(150)}")
            }
            if (bal != null) prev = bal
        }
        out.appendLine()
        out.appendLine("MoMo SMS: ${sms.count { MomoParser.isMomoSender(it.address) }}  transactions: ${txns.size}  withdrawal requests: ${requests.size}")
        out.appendLine("Balance-chain breaks: $breaks")
        out.appendLine("Final balance: ${txns.lastOrNull { it.second.balance != null }?.second?.balance}")
        out.appendLine("By kind: " + txns.groupBy { it.second.kind }.mapValues { it.value.size })
        out.appendLine("By category: " + txns.groupBy { Categorizer.categorize(it.second) }.mapValues { it.value.size })
        out.appendLine("Withdrawals with fee: ${txns.count { it.second.kind == com.momobook.data.Kind.WITHDRAW && it.second.fee > 0 }}/" +
            "${txns.count { it.second.kind == com.momobook.data.Kind.WITHDRAW }}")
        out.appendLine("\nUnparsed SMS mentioning a balance (${unparsed.size}):")
        unparsed.distinctBy { it.replace(Regex("\\d"), "").take(60) }.forEach { out.appendLine("  " + it.take(200)) }
        File("build/sample-report.txt").writeText(out.toString())
    }
}
