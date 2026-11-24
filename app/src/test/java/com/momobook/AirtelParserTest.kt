package com.momobook

import com.momobook.data.Categorizer
import com.momobook.data.Direction
import com.momobook.data.Kind
import com.momobook.data.MomoParser
import com.momobook.data.ParsedTxn
import com.momobook.data.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class AirtelParserTest {
    private val sender = "AirtelMoney"

    private fun parse(body: String): ParsedTxn {
        val p = MomoParser.parse(body, sender)
        assertNotNull("not parsed: $body", p)
        assertEquals(Provider.AIRTEL, p!!.provider)
        // Pasted text (unknown sender) must give the same result.
        assertEquals(p, MomoParser.parse(body))
        return p
    }

    private fun at(s: String) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(s)!!.time

    @Test fun received() {
        val p = parse("RECEIVED UGX 105,000 from 256788810219,ROSE AKIROR,thank you. Balance UGX 105,001. TID:161829478904.")
        assertEquals("161829478904", p.txnId)
        assertEquals(Direction.CR, p.direction)
        assertEquals(Kind.RECEIVED, p.kind)
        assertEquals(105_000L, p.amount)
        assertEquals(105_001L, p.balance)
        assertEquals("ROSE AKIROR", p.counterparty)
        assertEquals("256788810219", p.phone)
        assertEquals("thank you", p.reason)
    }

    @Test fun loanCollected() {
        val p = parse("Quick Loans has collected UGX 9,510 from your account. TxnID: 161829480812. Bal UGX 95,491 Date: 20-November-2026 13:31")
        assertEquals("161829480812", p.txnId)
        assertEquals(Direction.DR, p.direction)
        assertEquals(9_510L, p.amount)
        assertEquals(95_491L, p.balance)
        assertEquals("Quick Loans", p.counterparty)
        assertEquals(at("2026-11-20 13:31"), p.occurredAt)
        assertEquals(Categorizer.LOANS, Categorizer.categorize(p))
    }

    @Test fun cashDeposit() {
        val p = parse("Cash deposit of UGX 255,000 from JOSH KA POOL FAMILY BUSINESS. Balance UGX 350,491. Trans ID:161835339488. Date 20-November-2026 14:47.")
        assertEquals("161835339488", p.txnId)
        assertEquals(Kind.DEPOSIT, p.kind)
        assertEquals(Direction.CR, p.direction)
        assertEquals(255_000L, p.amount)
        assertEquals(350_491L, p.balance)
        assertEquals("JOSH KA POOL FAMILY BUSINESS", p.counterparty)
        assertEquals(at("2026-11-20 14:47"), p.occurredAt)
        assertEquals(Categorizer.CASH_DEPOSIT, Categorizer.categorize(p))
    }

    @Test fun paid() {
        val p = parse("PAID.TID 161874076787. UGX 5,000 to BUSINESS Charge UGX 0. Bal UGX 345,491. 20-November-2026 21:32")
        assertEquals("161874076787", p.txnId)
        assertEquals(Kind.PAYMENT, p.kind)
        assertEquals(5_000L, p.amount)
        assertEquals(0L, p.fee)
        assertEquals(345_491L, p.balance)
        assertEquals("BUSINESS", p.counterparty)
        assertEquals(at("2026-11-20 21:32"), p.occurredAt)
    }

    @Test fun debited() {
        val p = parse("You have been debited UGX 92,000. Fee UGX 1,000. Bal UGX 808,991. TID 161937552960.Send using MyAirtel App https://bit.ly/3ZgpiNw")
        assertEquals("161937552960", p.txnId)
        assertEquals(Direction.DR, p.direction)
        assertEquals(92_000L, p.amount)
        assertEquals(1_000L, p.fee)
        assertEquals(808_991L, p.balance)
    }

    @Test fun loanDisbursed() {
        val p = parse("Success. Quickloan of UGX 3,000 disbursed. Please dial 185 to continue using Airtel Money.TID 161581666392")
        assertEquals("161581666392", p.txnId)
        assertEquals(Direction.CR, p.direction)
        assertEquals(3_000L, p.amount)
        assertNull(p.balance)
        assertEquals(Categorizer.LOANS, Categorizer.categorize(p))
    }

    @Test fun sentAbroad() {
        val p = parse("SENT UGX 50,000 equiv KES 1515.06 ( FX 0.03) to +254718745826 Esther Wakagio Njogu on 08/11/2026. Fee UGX 500 New bal UGX 253,001 TID 160937366031")
        assertEquals("160937366031", p.txnId)
        assertEquals(Kind.SENT, p.kind)
        assertEquals(50_000L, p.amount)
        assertEquals(500L, p.fee)
        assertEquals(253_001L, p.balance)
        assertEquals("Esther Wakagio Njogu", p.counterparty)
        assertEquals("+254718745826", p.phone)
        assertEquals("≈ KES 1515.06", p.reason)
    }

    @Test fun withdrawn() {
        val p = parse("WITHDRAWN. TID 159874512491. UGX15,000 with Agent ID: 244400.Fee UGX 700.Tax UGX 75.Bal UGX 412. 26-October-2026 20:46.")
        assertEquals("159874512491", p.txnId)
        assertEquals(Kind.WITHDRAW, p.kind)
        assertEquals(15_000L, p.amount)
        assertEquals(775L, p.fee)
        assertEquals(412L, p.balance)
        assertEquals("Agent 244400", p.counterparty)
        assertEquals(at("2026-10-26 20:46"), p.occurredAt)
    }

    @Test fun rejectsWithdrawalCode() {
        assertNull(MomoParser.parse("Withdrawal of UGX 15000 initiated. Secret Code: 646127. Expires on 26-October-2026 20:48.", sender))
    }

    @Test fun balanceChainHolds() {
        // Oldest first; each balance = previous balance + in − out − fees.
        val chain = listOf(
            "RECEIVED UGX 105,000 from 256788810219,ROSE AKIROR,thank you. Balance UGX 105,001. TID:161829478904.",
            "Quick Loans has collected UGX 9,510 from your account. TxnID: 161829480812. Bal UGX 95,491 Date: 20-November-2026 13:31",
            "Cash deposit of UGX 255,000 from JOSH KA POOL FAMILY BUSINESS. Balance UGX 350,491. Trans ID:161835339488. Date 20-November-2026 14:47.",
            "PAID.TID 161874076787. UGX 5,000 to BUSINESS Charge UGX 0. Bal UGX 345,491. 20-November-2026 21:32",
        ).map(::parse)
        chain.zipWithNext().forEach { (a, b) ->
            val delta = if (b.direction == Direction.CR) b.amount else -(b.amount + b.fee)
            assertEquals(b.counterparty, a.balance!! + delta, b.balance)
        }
    }

    @Test fun senders() {
        assertEquals(Provider.AIRTEL, MomoParser.senderProvider("AirtelMoney"))
        assertEquals(Provider.MTN, MomoParser.senderProvider("MTNMobMoney"))
        assertEquals(null, MomoParser.senderProvider("Pasted"))
        assertEquals(true, MomoParser.isMomoSender("AirtelMoney"))
        assertEquals(false, MomoParser.isMomoSender("SafeBoda"))
    }

    @Test fun mtnSenderNeverParsedAsAirtel() {
        val mtn = "You have received UGX 5000 from Airtel Money, 256770583820 on 2025-03-10 15:10:27. fee:0. Reason: x. " +
            "New balance: UGX 5184. ID: 31568062301."
        assertEquals(Provider.MTN, MomoParser.parse(mtn, "MTNMobMoney")!!.provider)
        assertEquals(Provider.MTN, MomoParser.parse(mtn)!!.provider)
    }

    @Test fun walletKeys() {
        assertEquals("2", Provider.walletKey(Provider.MTN, "2"))
        assertEquals("airtel:2", Provider.walletKey(Provider.AIRTEL, "2"))
        assertEquals("airtel", Provider.walletKey(Provider.AIRTEL, null))
        assertEquals(Provider.AIRTEL, Provider.ofWallet("airtel:2"))
        assertEquals("2", Provider.simOfWallet("airtel:2"))
        assertEquals(null, Provider.simOfWallet("airtel"))
        assertEquals("1", Provider.simOfWallet("1"))
    }
}
