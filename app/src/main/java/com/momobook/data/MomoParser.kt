package com.momobook.data

import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToLong

/** A "requested a withdrawal" notice. It is not a transaction, but it is the only place MTN states the fee. */
data class WithdrawRequest(val agent: String, val amount: Long, val fee: Long, val txnId: String)

/**
 * Parses MTN Uganda Mobile Money and Airtel Money Uganda SMS.
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

    /** Only MTN MoMo and Airtel Money senders are journaled (anything else would corrupt the balance). */
    private val MTN_SENDER = Regex("""mtn|momo|mobile\s*money|mobmoney""", RegexOption.IGNORE_CASE)
    private val AIRTEL_SENDER = Regex("""airtel""", RegexOption.IGNORE_CASE)

    fun isMomoSender(address: String?): Boolean =
        address == null || address == "Pasted" || senderProvider(address) != null

    /** Provider named by the SMS sender, or null when unknown (pasted text). Airtel is checked first. */
    fun senderProvider(address: String?): Provider? = when {
        address == null -> null
        AIRTEL_SENDER.containsMatchIn(address) -> Provider.AIRTEL
        MTN_SENDER.containsMatchIn(address) -> Provider.MTN
        else -> null
    }

    /** Wording only Airtel uses (MTN never writes "TID", "TxnID", "Trans ID" or "Bal UGX"). */
    private val AIRTEL_HINT = Regex("""\bTID\b|TxnID|Trans\s+ID|\bBal\s+UGX|MyAirtel""", RegexOption.IGNORE_CASE)

    private val NOT_A_TXN = rx(
        """My\s+Approvals|requested\s+a\s+withdrawal|\bfailed\b|\bOTP\b|will\s+be\s+valid\s+for|""" +
            """to\s+opt\s*in|\bDND\b|complete\s+your\s+login|not\s+registered\s+for|Secret\s+Code"""
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

    // ---- Airtel Money Uganda ----
    private const val ADATE = """(\d{1,2}-[A-Za-z]+-\d{4}\s+\d{1,2}:\d{2})"""
    private const val ABAL = """Bal(?:ance)?:?\s*$OCUR$AMT"""

    // RECEIVED UGX 105,000 from 256700000000,NAME,thank you. Balance UGX 105,001. TID:1.
    private val A_RECEIVED = rx(
        """^RECEIVED\s+$CUR$AMT\s+from\s+(?:(\+?\d{9,13})\s*,?\s*)?(.*?)\.\s*$ABAL.*?TID:?\s*(\d+)"""
    )

    // Quick Loans has collected UGX 9,510 from your account. TxnID: 1. Bal UGX 95,491 Date: 20-November-2026 13:31
    private val A_COLLECTED = rx(
        """^(.+?)\s+has\s+collected\s+$CUR$AMT\s+from\s+your\s+account\.?\s*(?:TxnID|TID|Trans\s*ID):?\s*(\d+)""" +
            """\.?\s*$ABAL(?:.*?Date:?\s*$ADATE)?"""
    )

    // Cash deposit of UGX 255,000 from NAME. Balance UGX 350,491. Trans ID:1. Date 20-November-2026 14:47.
    private val A_DEPOSIT = rx(
        """Cash\s+deposit\s+of\s+$CUR$AMT\s+from\s+(.+?)\.\s*$ABAL\.?\s*(?:Trans\s*ID|TxnID|TID):?\s*(\d+)""" +
            """(?:.*?Date:?\s*$ADATE)?"""
    )

    // PAID.TID 1. UGX 5,000 to BUSINESS Charge UGX 0. Bal UGX 345,491. 20-November-2026 21:32
    private val A_PAID = rx(
        """^PAID\.?\s*TID:?\s*(\d+)\.?\s*$CUR$AMT\s+to\s+(.+?)\.?\s+Charge:?\s*$OCUR$AMT\.?\s*$ABAL(?:\.?\s*$ADATE)?"""
    )

    // You have been debited UGX 92,000. Fee UGX 1,000. Bal UGX 808,991. TID 1.Send using MyAirtel App
    private val A_DEBITED = rx(
        """You\s+have\s+been\s+debited\s+$CUR$AMT\.?\s*Fee:?\s*$OCUR$AMT\.?\s*$ABAL\.?\s*TID:?\s*(\d+)"""
    )

    // Success. Quickloan of UGX 3,000 disbursed. Please dial 185 to continue using Airtel Money.TID 1
    private val A_LOAN = rx("""Quick\s*loans?\s+of\s+$CUR$AMT\s+disbursed.*?TID:?\s*(\d+)""")

    // SENT UGX 50,000 equiv KES 1515.06 ( FX 0.03) to +254700000000 NAME on 08/11/2026. Fee UGX 500
    // New bal UGX 253,001 TID 1
    private val A_SENT = rx(
        """^SENT\s+$CUR$AMT(?:\s+equiv\s+([A-Z]{3}\s*[\d,.]+))?(?:\s*\([^)]*\))?\s+to\s+(?:(\+?\d{9,13})\s*,?\s*)?""" +
            """(.*?)(?:\s+on\s+[\d/]+)?\.?\s*Fee:?\s*$OCUR$AMT\.?\s*(?:New\s+)?$ABAL.*?TID:?\s*(\d+)"""
    )

    // WITHDRAWN. TID 1. UGX15,000 with Agent ID: 244400.Fee UGX 700.Tax UGX 75.Bal UGX 412. 26-October-2026 20:46.
    private val A_WITHDRAWN = rx(
        """^WITHDRAWN\.?\s*TID:?\s*(\d+)\.?\s*$CUR$AMT\s+with\s+(?:Agent\s+ID:?\s*)?(.+?)\.?\s*Fee:?\s*$OCUR$AMT""" +
            """\.?\s*(?:Tax:?\s*$OCUR$AMT\.?\s*)?$ABAL(?:\.?\s*$ADATE)?"""
    )

    // ---- Lenient fallback pieces ----
    private val G_AMOUNT = rx("""UGX\s*(?:UGX\s*)*$AMT""")
    private val G_FEE = rx("""fee(?:\s+of)?\s*(?:is)?:?\s*$OCUR$AMT""")
    private val G_BAL = rx("""(?:balance|\bbal\b)(?:\s+is)?(?:\s+now)?:?\s*$OCUR$AMT""")
    private val G_ID = rx("""(?:Transaction\s+ID|TxnID|Trans\s+ID|TID|\bID)\s*:?\s*(\d{6,})""")
    private val G_PARTY = rx("""\b(?:to|from)\s+([A-Za-z][A-Za-z0-9 &.'\-]{1,48}?)(?:\s*,|\s+on\s|\s+\(|\s+\d|\.\s)""")
    private val G_PHONE = Regex("""\b(256\d{9}|0[37]\d{8})\b""")

    /**
     * [address] is the SMS sender. A known sender only gets its own provider's templates, so an MTN
     * message can never be journaled as Airtel or the other way round. Pasted text tries both.
     */
    fun parse(body: String, address: String? = null): ParsedTxn? {
        val t = normalize(body)
        if (!t.contains("UGX", ignoreCase = true)) return null
        if (NOT_A_TXN.containsMatchIn(t)) return null
        return when (senderProvider(address)) {
            Provider.MTN -> mtn(t) ?: generic(t, Provider.MTN)
            Provider.AIRTEL -> airtel(t) ?: generic(t, Provider.AIRTEL)
            null -> mtn(t) ?: airtel(t) ?: generic(t, if (AIRTEL_HINT.containsMatchIn(t)) Provider.AIRTEL else Provider.MTN)
        }
    }

    private fun mtn(t: String) = deducted(t) ?: received(t) ?: deposited(t) ?: withdrawnWithFee(t) ?: withdrawnAgent(t)
        ?: sent(t) ?: transferred(t) ?: paidTo(t) ?: paidFor(t) ?: airtime(t) ?: bundleLoaded(t) ?: electricity(t)

    private fun airtel(t: String) = (aReceived(t) ?: aCollected(t) ?: aDeposit(t) ?: aPaid(t) ?: aDebited(t)
        ?: aLoan(t) ?: aSent(t) ?: aWithdrawn(t))?.copy(provider = Provider.AIRTEL)

    fun parseWithdrawRequest(body: String): WithdrawRequest? {
        val m = WITHDRAW_REQUEST.find(normalize(body)) ?: return null
        val (amt, agent, fee, id) = m.destructured
        return WithdrawRequest(clean(agent), money(amt), money(fee), id)
    }

    private fun normalize(s: String) = s.replace(' ', ' ').trim()

    private fun txn(
        id: String, dir: Direction, kind: Kind, amount: Long, fee: Long, balance: Long?,
        party: String, phone: String? = null, reason: String? = null, date: String? = null, at: Long? = null,
    ) = ParsedTxn(
        txnId = id, direction = dir, kind = kind, amount = amount, fee = fee, balance = balance,
        counterparty = clean(party).ifBlank { phone ?: "Unknown" }, phone = phone?.ifBlank { null },
        reason = reason?.trim()?.trimEnd('.', ',')?.trim()?.ifBlank { null },
        occurredAt = at ?: date?.let(::parseDate),
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

    private fun aReceived(t: String) = A_RECEIVED.find(t)?.destructured?.let { (amt, phone, rest, bal, id) ->
        // "NAME,reason": the sender may add a note after the name.
        val name = rest.substringBefore(',')
        txn(id, Direction.CR, Kind.RECEIVED, money(amt), 0, money(bal), name, phone = phone,
            reason = rest.substringAfter(',', "").ifBlank { null })
    }

    private fun aCollected(t: String) = A_COLLECTED.find(t)?.destructured?.let { (party, amt, id, bal, date) ->
        txn(id, Direction.DR, Kind.PAYMENT, money(amt), 0, money(bal), party, reason = "Loan repayment",
            at = airtelDate(date))
    }

    private fun aDeposit(t: String) = A_DEPOSIT.find(t)?.destructured?.let { (amt, party, bal, id, date) ->
        txn(id, Direction.CR, Kind.DEPOSIT, money(amt), 0, money(bal), party, at = airtelDate(date))
    }

    private fun aPaid(t: String) = A_PAID.find(t)?.destructured?.let { (id, amt, party, fee, bal, date) ->
        txn(id, Direction.DR, Kind.PAYMENT, money(amt), money(fee), money(bal), party, at = airtelDate(date))
    }

    private fun aDebited(t: String) = A_DEBITED.find(t)?.destructured?.let { (amt, fee, bal, id) ->
        // The SMS doesn't say where the money went.
        txn(id, Direction.DR, Kind.SENT, money(amt), money(fee), money(bal), "Airtel Money debit")
    }

    private fun aLoan(t: String) = A_LOAN.find(t)?.destructured?.let { (amt, id) ->
        txn(id, Direction.CR, Kind.RECEIVED, money(amt), 0, null, "Quick Loans", reason = "Loan disbursed")
    }

    private fun aSent(t: String) = A_SENT.find(t)?.destructured?.let { (amt, fx, phone, party, fee, bal, id) ->
        txn(id, Direction.DR, Kind.SENT, money(amt), money(fee), money(bal), party, phone = phone,
            reason = fx.ifBlank { null }?.let { "≈ $it" })
    }

    private fun aWithdrawn(t: String) = A_WITHDRAWN.find(t)?.destructured?.let { (id, amt, agent, fee, tax, bal, date) ->
        val party = if (agent.all(Char::isDigit)) "Agent $agent" else agent
        txn(id, Direction.DR, Kind.WITHDRAW, money(amt), money(fee) + money(tax.ifBlank { "0" }), money(bal), party,
            at = airtelDate(date))
    }

    private fun generic(t: String, provider: Provider): ParsedTxn? {
        val id = G_ID.find(t)?.groupValues?.get(1) ?: return null
        val amount = G_AMOUNT.find(t)?.groupValues?.get(1)?.let(::money) ?: return null
        if (amount <= 0) return null
        val lower = t.lowercase(Locale.ROOT)
        val kind = when {
            "received" in lower || "deposited" in lower || "credited" in lower -> Kind.RECEIVED
            "withdrawn" in lower -> Kind.WITHDRAW
            "sent" in lower || "transferred" in lower -> Kind.SENT
            "paid" in lower || "deducted" in lower || "debited" in lower || "collected" in lower -> Kind.PAYMENT
            else -> return null // can't tell direction: don't guess
        }
        return txn(
            id, if (kind == Kind.RECEIVED) Direction.CR else Direction.DR, kind, amount,
            G_FEE.find(t)?.groupValues?.get(1)?.let(::money) ?: 0,
            G_BAL.find(t)?.groupValues?.get(1)?.let(::money),
            G_PARTY.find(t)?.groupValues?.get(1) ?: "Unknown",
            phone = G_PHONE.find(t)?.groupValues?.get(1),
        ).copy(provider = provider)
    }

    private fun syntheticId(prefix: String, date: String, amount: Long) = "$prefix-" + date.filter(Char::isDigit) + "-" + amount

    private fun clean(s: String) = s.trim().trim(',', '.', ' ').replace(Regex("""\s+"""), " ")

    internal fun money(s: String): Long = s.replace(",", "").trim().toDoubleOrNull()?.roundToLong() ?: 0

    /** "20-November-2026 13:31" (Airtel). Blank or unreadable → null, so the SMS time is used. */
    private fun airtelDate(s: String): Long? {
        if (s.isBlank()) return null
        for (p in listOf("d-MMMM-yyyy HH:mm", "d-MMM-yyyy HH:mm")) {
            runCatching { return SimpleDateFormat(p, Locale.US).apply { isLenient = false }.parse(s.trim())!!.time }
        }
        return null
    }

    private fun parseDate(s: String): Long? {
        val norm = s.replace('T', ' ')
        for (p in listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm")) {
            runCatching { return SimpleDateFormat(p, Locale.US).apply { isLenient = false }.parse(norm)!!.time }
        }
        return null
    }
}
