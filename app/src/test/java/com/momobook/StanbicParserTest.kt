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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class StanbicParserTest {
    private val sender = "Stanbic"

    private val pos = "Stanbic: Debit of UGX 85,000 from Acct **1234 at POS/Card **5678 (CITY SUPERMARKET) on 26/11/26 19:42. " +
        "Avail Bal: UGX 1,415,000. Ref: TXN984712."
    private val credit = "Stanbic: Credit of UGX 500,000 to Acct **1234 (SALARY/TRANSFER IN) on 26/11/26 09:15. " +
        "Avail Bal: UGX 1,500,000. Ref: TXN442190."
    private val mobile = "Stanbic: Debit of UGX 120,000 from Acct **1234 to 25677XXXXXXX on 26/11/26 14:05. " +
        "Avail Bal: UGX 1,295,000. Ref: TXN771034."

    private fun parse(body: String): ParsedTxn {
        val p = MomoParser.parse(body, sender)
        assertNotNull("not parsed: $body", p)
        assertEquals(Provider.STANBIC, p!!.provider)
        assertEquals("1234", p.account)
        // Pasted, or from a numeric sender: "Stanbic:" identifies the bank.
        assertEquals(p, MomoParser.parse(body))
        assertEquals(p, MomoParser.parse(body, "8000"))
        assertTrue(MomoParser.accepts("8000", body))
        return p
    }

    private fun at(s: String) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(s)!!.time

    @Test fun cardPurchase() {
        val p = parse(pos)
        assertEquals(Direction.DR, p.direction)
        assertEquals(Kind.PAYMENT, p.kind)
        assertEquals(85_000L, p.amount)
        assertEquals(1_415_000L, p.balance)
        assertEquals("CITY SUPERMARKET", p.counterparty)
        assertEquals("TXN984712", p.txnId)
        assertEquals(at("2026-11-26 19:42"), p.occurredAt)
        assertEquals("Food & Shopping", Categorizer.categorize(p))
    }

    @Test fun salaryCredit() {
        val p = parse(credit)
        assertEquals(Direction.CR, p.direction)
        assertEquals(Kind.RECEIVED, p.kind)
        assertEquals(500_000L, p.amount)
        assertEquals(1_500_000L, p.balance)
        assertEquals("SALARY/TRANSFER IN", p.counterparty)
        assertEquals("Salary", Categorizer.categorize(p))
    }

    @Test fun mobileMoneyTransfer() {
        val p = parse(mobile)
        assertEquals(Direction.DR, p.direction)
        assertEquals(Kind.SENT, p.kind)
        assertEquals(120_000L, p.amount)
        assertEquals(1_295_000L, p.balance)
        assertEquals("25677XXXXXXX", p.counterparty)
        assertNull(p.phone) // masked
        assertEquals("Transfers", Categorizer.categorize(p))
    }

    @Test fun walletLabelKey() {
        assertEquals("stanbic:1234", Provider.walletKey(Provider.STANBIC, "1", "1234"))
        assertEquals(Provider.STANBIC, Provider.ofWallet("stanbic:1234"))
        assertEquals("1234", Provider.accountOfWallet("stanbic:1234"))
    }
}
