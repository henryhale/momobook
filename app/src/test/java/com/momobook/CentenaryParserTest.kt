package com.momobook

import com.momobook.data.Categorizer
import com.momobook.data.Direction
import com.momobook.data.Kind
import com.momobook.data.MomoParser
import com.momobook.data.ParsedTxn
import com.momobook.data.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class CentenaryParserTest {
    private val sender = "CENTENARY"

    // Truncated by the phone after "Call 080", as delivered.
    private val eft = "CENTENARY: Dear JOHN, a trxn of 1,000,000 on your A/C **260 on 29-09-2026 at 12:18. " +
        "Bal:1,958,033 ( EFT-ORDER ACME COMPANY LIMITED/Head Office). Call 080"
    private val atm1 = "CENTENARY: Dear JOHN, a trxn of -50,000 on your A/C **260 on 27-09-2026 at 12:15. " +
        "Bal:962,633 (ATM WITHDRAWAL VISA /Ebanking). Call 0800200555."
    private val atm2 = "CENTENARY: Dear JOHN, a trxn of -45,000 on your A/C **260 on 02-10-2026 at 09:00. " +
        "Bal:1,909,316 (ATM WITHDRAWAL VISA /Ebanking). Call 0800200555."

    private fun parse(body: String): ParsedTxn {
        val p = MomoParser.parse(body, sender)
        assertNotNull("not parsed: $body", p)
        assertEquals(Provider.CENTENARY, p!!.provider)
        assertEquals("260", p.account)
        // Pasted text, or a numeric sender, gives the same result.
        assertEquals(p, MomoParser.parse(body))
        assertEquals(p, MomoParser.parse(body, "8338"))
        return p
    }

    private fun at(s: String) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(s)!!.time

    @Test fun eftCredit() {
        val p = parse(eft)
        assertEquals(Direction.CR, p.direction)
        assertEquals(Kind.RECEIVED, p.kind)
        assertEquals(1_000_000L, p.amount)
        assertEquals(0L, p.fee)
        assertEquals(1_958_033L, p.balance)
        assertEquals("ACME COMPANY LIMITED", p.counterparty)
        assertEquals("EFT-ORDER ACME COMPANY LIMITED/Head Office", p.reason)
        assertEquals(at("2026-09-29 12:18"), p.occurredAt)
        assertEquals(Categorizer.INCOME, Categorizer.categorize(p))
    }

    @Test fun depositWithoutClosingBracket() {
        val base = "CENTENARY: Dear JOHN, a trxn of 1,000,000 on your A/C **260 on 29-09-2026 at 12:18. Bal:1,958,033 "
        for (tail in listOf(
            "( EFT-ORDER ACME COMPANY LIMITED/Head Office. Call 0800200555.",
            "( EFT-ORDER ACME COMPANY LIMITED/Head Office Call 080",
            "(EFT-ORDER ACME COMPANY LIMITED/Head Off",
            "( EFT-ORDER ACME COMPANY LIMITED",
        )) {
            val p = parse(base + tail)
            assertEquals(tail, "ACME COMPANY LIMITED", p.counterparty)
            assertEquals(tail, Direction.CR, p.direction)
            assertEquals(tail, 1_958_033L, p.balance)
            assertTrue(tail, p.reason!!.startsWith("EFT-ORDER ACME COMPANY LIMITED") && "Call" !in p.reason!!)
        }
    }

    @Test fun depositWithoutDescription() {
        val p = parse("CENTENARY: Dear JOHN, a trxn of 20,000 on your A/C **260 on 29-09-2026 at 12:18. Bal:20,500. Call 0800200555.")
        assertEquals("Centenary Bank", p.counterparty)
        assertNull(p.reason)
    }

    @Test fun atmWithdrawal() {
        val p = parse(atm1)
        assertEquals(Direction.DR, p.direction)
        assertEquals(Kind.WITHDRAW, p.kind)
        assertEquals(50_000L, p.amount)
        assertEquals(962_633L, p.balance)
        assertEquals("Centenary ATM", p.counterparty)
        assertEquals(at("2026-09-27 12:15"), p.occurredAt)
        assertEquals("Cash Withdrawal", Categorizer.categorize(p))
    }

    @Test fun idsAreStableAndDistinct() {
        assertEquals(parse(atm2).txnId, parse(atm2).txnId)
        assertNotEquals(parse(atm1).txnId, parse(atm2).txnId)
        assertTrue(parse(atm2).txnId.startsWith("CB-260-"))
    }

    @Test fun balanceChainHolds() {
        // 27 Sep 962,633 → (+1,000,000 and other activity) → 29 Sep 1,958,033 → −45,000 … 1,909,316 on 2 Oct.
        val a = parse(eft); val b = parse(atm2)
        assertTrue(b.balance!! <= a.balance!! - b.amount)
    }

    @Test fun walletIsTheAccount() {
        val p = parse(atm1)
        assertEquals("centenary:260", Provider.walletKey(p.provider, "2", p.account))
        assertEquals("centenary:260", Provider.unknownWallet(p))
        assertEquals(Provider.CENTENARY, Provider.ofWallet("centenary:260"))
        assertEquals("260", Provider.accountOfWallet("centenary:260"))
        assertNull(Provider.simOfWallet("centenary:260"))
    }

    @Test fun senders() {
        assertEquals(Provider.CENTENARY, MomoParser.senderProvider("CENTENARY"))
        assertEquals(Provider.CENTENARY, MomoParser.senderProvider("CenteBank"))
        assertTrue(MomoParser.accepts("8338", atm1))
        assertEquals(false, MomoParser.accepts("8338", "Dear customer, your loan is due."))
        assertNull(MomoParser.parse("CENTENARY: Dear JOHN, your statement is ready.", sender))
    }
}
