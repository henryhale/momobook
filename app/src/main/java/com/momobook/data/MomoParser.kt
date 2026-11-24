package com.momobook.data

import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToLong

/** A "requested a withdrawal" notice. It is not a transaction, but it is the only place MTN states the fee. */
data class WithdrawRequest(val agent: String, val amount: Long, val fee: Long, val txnId: String)

/**
 * Parses MTN Uganda Mobile Money SMS.
 *
 * Every known template has its own pattern. A lenient fallback only accepts messages that carry
 * an amount, a transaction ID and a clear direction. Pending approvals, withdrawal requests,
 * failed transactions, OTPs and promos are rejected so they never reach the journal.
 */
object MomoParser {
    private const val AMT = """([\d,]+(?:\.\d{1,2})?)"""
    private const val CUR = """(?:UGX\s*)+"""
    private const val OCUR = """(?:UGX\s*)*"""
    private const val DATE = """(\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}(?::\d{2})?)"""
    private val OPTS = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private fun rx(s: String) = Regex(s, OPTS)

    /** Only MTN MoMo senders are journaled (other wallets would corrupt the balance). */
    private val MOMO_SENDER = Regex("""mtn|momo|mobile\s*money|mobmoney""", RegexOption.IGNORE_CASE)

    fun isMomoSender(address: String?): Boolean =
        address == null || address == "Pasted" || MOMO_SENDER.containsMatchIn(address)

    private val NOT_A_TXN = rx(
        """My\s+Approvals|requested\s+a\s+withdrawal|\bfailed\b|\bOTP\b|will\s+be\s+valid\s+for|""" +
            """to\s+opt\s*in|\bDND\b|complete\s+your\s+login|not\s+registered\s+for"""
    )

    // Y'ello. MTN UGANDA LIMITED PAKABUN50 has deducted UGX 5,000 at a fee of UGX 0 Transaction ID: 1 from your
    // mobile money account. Message: Bundle Purchase. New balance is:UGX 1,198.
    private val DEDUCTED = rx(
        """(?:Y[’'`]?ello\.?\s*)?(.+?)\s+has\s+deducted\s+$CUR$AMT\s+at\s+a\s+fee\s+of\s+$CUR$AMT""" +
            """.*?Transaction\s+ID:?\s*(\d+)(?:.*?Message:\s*([^.]*)\.)?.*?New\s+balance\s+is:?\s*$OCUR$AMT"""
    )

    // You have received UGX 80000 from NAME, 256700000000 on 2026-11-16 08:59:54. fee:0. Reason: x.
    // New balance: UGX 52345. ID: 1.
    private val RECEIVED = rx(
        """You\s+have\s+received\s+$CUR$AMT\s+from\s+(.+?)(?:,\s*(\+?\d{9,13}))?\s+on\s+$DATE\.?\s*""" +
            """(?:fee:?\s*$OCUR$AMT\.?\s*)?(?:Reason:\s*(.*?)\.?\s*)?""" +
            """New\s+balance:?\s*$OCUR$AMT\.?\s*(?:Transaction\s+)?ID:?\s*(\d+)"""
    )

    // You have deposited UGX 50000 from AGENT on 2026-11-20 10:00:00. New balance: UGX 51198. ID: 1.
    private val DEPOSITED = rx(
        """You\s+have\s+deposited\s+$CUR$AMT\s+from\s+(.+?)\s+on\s+$DATE\.?\s*""" +
            """New\s+balance:?\s*$OCUR$AMT\.?\s*(?:Transaction\s+)?ID:?\s*(\d+)"""
    )

    // Old: You have withdrawn UGX UGX 20000 from AGENT on 2026-11-20 10:00:00. New balance: UGX 1198. ID -.
    private val WITHDRAWN_AGENT = rx(
        """You\s+have\s+withdrawn\s+$CUR$AMT\s+from\s+(.+?)\s+on\s+$DATE\.?\s*""" +
            """New\s+balance:?\s*$OCUR$AMT\.?(?:\s*(?:Transaction\s+)?ID\s*:?\s*(\d+))?"""
    )

    // New: You have withdrawn UGX 9,000 on 2025-01-01 10:00:00. Fee: UGX 375, Tax: UGX 45. New balance: UGX 12.50.
    private val WITHDRAWN_FEE = rx(
        """You\s+have\s+withdrawn\s+$CUR$AMT\s+on\s+$DATE\.?\s*Fee:?\s*$OCUR$AMT,?\s*""" +
            """(?:Tax:?\s*$OCUR$AMT\.?\s*)?New\s+balance:?\s*$OCUR$AMT"""
    )

    // Y'ello. You have requested a withdrawal of UGX 90,000 from AGENT . Dial *165# ... The total fee is  UGX 2,375
    // inclusive of 0.5 percent tax.Transaction ID 1.
    private val WITHDRAW_REQUEST = rx(
        """requested\s+a\s+withdrawal\s+of\s+$CUR$AMT\s+from\s+(.+?)\s*\.\s+Dial.*?total\s+fee\s+is\s*$OCUR$AMT""" +
            """.*?Transaction\s+ID:?\s*(\d+)"""
    )

    // Y'ello. You have sent UGX 10,000 to 256700000000, NAME. Fee:UGX 500.00.  Transaction ID:1.
    // Your Mobile Money balance is now UGX 1198.
    private val SENT = rx(
        """You\s+have\s+sent\s+$CUR$AMT\s+to\s+(?:(\+?\d{9,13}),?\s*)?(.*?)\.?\s*Fee:?\s*$OCUR$AMT""" +
            """.*?Transaction\s+ID:?\s*(\d+).*?balance\s+is(?:\s+now)?:?\s*$OCUR$AMT"""
    )

    // Y'ello. You have transferred UGX UGX 50,000 to Centenary Bank. TX Charge  UGX 1000.00.
    // Your new balance: UGX UGX 100,000.5. Transaction ID:1.
    private val TRANSFERRED = rx(
        """You\s+have\s+transferred\s+$CUR$AMT\s+to\s+(.+?)\.\s*(?:TX\s+)?Charge:?\s*$OCUR$AMT\.?\s*""" +
            """.*?balance:?\s*$OCUR$AMT.*?Transaction\s+ID:?\s*(\d+)"""
    )

    // You have paid UGX 500 to  MERCHANT on 2025-01-01 10:00:00. New balance is: 12345. ID: 1.
    private val PAID_TO = rx(
        """You\s+have\s+paid\s+$CUR$AMT\s+to\s+(.+?)\s+on\s+$DATE\.?\s*New\s+balance\s+is:?\s*$OCUR$AMT""" +
            """\.?\s*(?:Transaction\s+)?ID:?\s*(\d+)"""
    )

    // You have paid UGX 20000 for UEDCL at a Fee of UGX 0 Account No.: 123 New balance is UGX 500 Transaction ID: 1
    private val PAID_FOR = rx(
        """You\s+have\s+paid\s+$CUR$AMT\s+for\s+(.+?)\s+at\s+a\s+fee\s+of\s+$CUR$AMT""" +
            """.*?New\s+balance\s+is:?\s*$OCUR$AMT.*?Transaction\s+ID:?\s*(\d+)"""
    )

    // You have bought UGX 500 airtime. New MoMo balance: UGX 1.23.
    private val AIRTIME = rx(
        """You\s+have\s+bought\s+$CUR$AMT\s+airtime\.?\s*New\s+(?:MoMo\s+)?balance:?\s*$OCUR$AMT"""
    )

    // Bundle loaded worth UGX 1,000/- . New balance: UGX 123.45. Transaction ID: 1.
    private val BUNDLE_LOADED = rx(
        """Bundle\s+loaded\s+worth\s+$CUR$AMT.*?New\s+balance:?\s*$OCUR$AMT.*?Transaction\s+ID:?\s*(\d+)"""
    )

    // You've purchased Electricity for Meter: 123, Amount: 20000.00 UGX, Token: 1234, Units: 25.1, Txn ID: 1
    private val ELECTRICITY = rx(
        """purchased\s+Electricity\s+for\s+Meter:?\s*(\d+),?\s*Amount:?\s*$AMT\s*UGX.*?Token:?\s*([\d-]+)""" +
            """.*?Txn\s+ID:?\s*(\d+)"""
    )

    // ---- Lenient fallback pieces ----
    private val G_AMOUNT = rx("""UGX\s*(?:UGX\s*)*$AMT""")
    private val G_FEE = rx("""fee(?:\s+of)?\s*(?:is)?:?\s*$OCUR$AMT""")
    private val G_BAL = rx("""balance(?:\s+is)?(?:\s+now)?:?\s*$OCUR$AMT""")
    private val G_ID = rx("""(?:Transaction\s+ID|TID|\bID)\s*:?\s*(\d{6,})""")
    private val G_PARTY = rx("""\b(?:to|from)\s+([A-Za-z][A-Za-z0-9 &.'\-]{1,48}?)(?:\s*,|\s+on\s|\s+\(|\s+\d|\.\s)""")
    private val G_PHONE = Regex("""\b(256\d{9}|0[37]\d{8})\b""")

    fun parse(body: String): ParsedTxn? {
        val t = normalize(body)
        if (!t.contains("UGX", ignoreCase = true)) return null
        if (NOT_A_TXN.containsMatchIn(t)) return null
        return deducted(t) ?: received(t) ?: deposited(t) ?: withdrawnWithFee(t) ?: withdrawnAgent(t) ?: sent(t)
            ?: transferred(t) ?: paidTo(t) ?: paidFor(t) ?: airtime(t) ?: bundleLoaded(t) ?: electricity(t)
            ?: generic(t)
    }

    fun parseWithdrawRequest(body: String): WithdrawRequest? {
        val m = WITHDRAW_REQUEST.find(normalize(body)) ?: return null
        val (amt, agent, fee, id) = m.destructured
        return WithdrawRequest(clean(agent), money(amt), money(fee), id)
    }

    private fun normalize(s: String) = s.replace(' ', ' ').trim()

    private fun txn(
        id: String, dir: Direction, kind: Kind, amount: Long, fee: Long, balance: Long?,
        party: String, phone: String? = null, reason: String? = null, date: String? = null,
    ) = ParsedTxn(
        txnId = id, direction = dir, kind = kind, amount = amount, fee = fee, balance = balance,
        counterparty = clean(party).ifBlank { phone ?: "Unknown" }, phone = phone?.ifBlank { null },
        reason = reason?.trim()?.trimEnd('.', ',')?.trim()?.ifBlank { null },
        occurredAt = date?.let(::parseDate),
    )

    private fun deducted(t: String) = DEDUCTED.find(t)?.destructured?.let { (party, amt, fee, id, msg, bal) ->
        txn(id, Direction.DR, Kind.PAYMENT, money(amt), money(fee), money(bal), party.removePrefix("Y'ello."), reason = msg)
    }

    private fun received(t: String) = RECEIVED.find(t)?.groupValues?.let { g ->
        txn(g[8], Direction.CR, Kind.RECEIVED, money(g[1]), g[5].let { if (it.isBlank()) 0 else money(it) }, money(g[7]),
            g[2], phone = g[3], reason = g[6], date = g[4])
    }

    private fun deposited(t: String) = DEPOSITED.find(t)?.destructured?.let { (amt, party, date, bal, id) ->
        txn(id, Direction.CR, Kind.DEPOSIT, money(amt), 0, money(bal), party, date = date)
    }

    private fun withdrawnAgent(t: String) = WITHDRAWN_AGENT.find(t)?.destructured?.let { (amt, party, date, bal, id) ->
        val amount = money(amt)
        txn(id.ifBlank { syntheticId("WD", date, amount) }, Direction.DR, Kind.WITHDRAW, amount, 0, money(bal), party, date = date)
    }

    private fun withdrawnWithFee(t: String) = WITHDRAWN_FEE.find(t)?.destructured?.let { (amt, date, fee, tax, bal) ->
        val amount = money(amt)
        txn(syntheticId("WD", date, amount), Direction.DR, Kind.WITHDRAW, amount, money(fee) + money(tax.ifBlank { "0" }),
            money(bal), "Agent withdrawal", date = date)
    }

    private fun sent(t: String) = SENT.find(t)?.destructured?.let { (amt, phone, party, fee, id, bal) ->
        txn(id, Direction.DR, Kind.SENT, money(amt), money(fee), money(bal), party, phone = phone)
    }

    private fun transferred(t: String) = TRANSFERRED.find(t)?.destructured?.let { (amt, party, fee, bal, id) ->
        txn(id, Direction.DR, Kind.SENT, money(amt), money(fee), money(bal), party)
    }

    private fun paidTo(t: String) = PAID_TO.find(t)?.destructured?.let { (amt, party, date, bal, id) ->
        txn(id, Direction.DR, Kind.PAYMENT, money(amt), 0, money(bal), party, date = date)
    }

    private fun paidFor(t: String) = PAID_FOR.find(t)?.destructured?.let { (amt, party, fee, bal, id) ->
        txn(id, Direction.DR, Kind.PAYMENT, money(amt), money(fee), money(bal), party)
    }

    private fun airtime(t: String) = AIRTIME.find(t)?.destructured?.let { (amt, bal) ->
        // No ID or date in this SMS: amount + resulting balance (with cents) is stable across re-scans.
        val amount = money(amt)
        txn("AT-$amount-" + bal.filter(Char::isDigit), Direction.DR, Kind.PAYMENT, amount, 0, money(bal), "MTN Airtime")
    }

    private fun bundleLoaded(t: String) = BUNDLE_LOADED.find(t)?.destructured?.let { (amt, bal, id) ->
        txn(id, Direction.DR, Kind.PAYMENT, money(amt), 0, money(bal), "MTN Bundles", reason = "Bundle Purchase")
    }

    private fun electricity(t: String) = ELECTRICITY.find(t)?.destructured?.let { (meter, amt, token, id) ->
        txn(id, Direction.DR, Kind.PAYMENT, money(amt), 0, null, "UEDCL Yaka", reason = "Meter $meter · Token $token")
    }

    private fun generic(t: String): ParsedTxn? {
        val id = G_ID.find(t)?.groupValues?.get(1) ?: return null
        val amount = G_AMOUNT.find(t)?.groupValues?.get(1)?.let(::money) ?: return null
        if (amount <= 0) return null
        val lower = t.lowercase(Locale.ROOT)
        val kind = when {
            "received" in lower || "deposited" in lower || "credited" in lower -> Kind.RECEIVED
            "withdrawn" in lower -> Kind.WITHDRAW
            "sent" in lower || "transferred" in lower -> Kind.SENT
            "paid" in lower || "deducted" in lower || "debited" in lower -> Kind.PAYMENT
            else -> return null // can't tell direction: don't guess
        }
        return txn(
            id, if (kind == Kind.RECEIVED) Direction.CR else Direction.DR, kind, amount,
            G_FEE.find(t)?.groupValues?.get(1)?.let(::money) ?: 0,
            G_BAL.find(t)?.groupValues?.get(1)?.let(::money),
            G_PARTY.find(t)?.groupValues?.get(1) ?: "Unknown",
            phone = G_PHONE.find(t)?.groupValues?.get(1),
        )
    }

    private fun syntheticId(prefix: String, date: String, amount: Long) = "$prefix-" + date.filter(Char::isDigit) + "-" + amount

    private fun clean(s: String) = s.trim().trim(',', '.', ' ').replace(Regex("""\s+"""), " ")

    internal fun money(s: String): Long = s.replace(",", "").trim().toDoubleOrNull()?.roundToLong() ?: 0

    private fun parseDate(s: String): Long? {
        val norm = s.replace('T', ' ')
        for (p in listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm")) {
            runCatching { return SimpleDateFormat(p, Locale.US).apply { isLenient = false }.parse(norm)!!.time }
        }
        return null
    }
}
