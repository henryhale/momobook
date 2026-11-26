package com.momobook

import com.momobook.data.Categorizer
import com.momobook.data.Direction
import com.momobook.data.Kind
import com.momobook.data.MomoParser
import com.momobook.data.Provider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class DtbParserTest {
    private val sender = "DTB"

    private val transfer = "Confirmed. You have transferred UGX 150,000 from A/C 012456 to A/C 098321 (Ref: DTB12345678) " +
        "on 26/11/2026 at 20:55."
    private val fixedDeposit = "Your Fixed Deposit of UGX 2,000,000 has been booked. Ref: FD98765432."
    private val alert = "Alert: Debit of UGX 50,000 on A/C 012***456 on 26/11/2026. If unauthorized, call DTB immediately."

    private fun at(s: String) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(s)!!.time

    @Test fun transfer() {
        val p = MomoParser.parse(transfer, sender)!!
        assertEquals(Provider.DTB, p.provider)
        assertEquals(Direction.DR, p.direction)
        assertEquals(Kind.SENT, p.kind)
        assertEquals(150_000L, p.amount)
        assertNull(p.balance)
        assertEquals("456", p.account)
        assertEquals("DTB12345678", p.txnId)
        assertEquals("A/C 098321", p.counterparty)
        assertEquals(at("2026-11-26 20:55"), p.occurredAt)
        assertFalse(p.alert)
        // Pasted, or from a numeric sender: the "Ref: DTB…" identifies the bank.
        assertEquals(p, MomoParser.parse(transfer))
        assertEquals(p, MomoParser.parse(transfer, "8228"))
        assertTrue(MomoParser.accepts("8228", transfer))
    }

    @Test fun fixedDeposit() {
        val p = MomoParser.parse(fixedDeposit, sender)!!
        assertEquals(Provider.DTB, p.provider)
        assertEquals(Direction.DR, p.direction)
        assertEquals(2_000_000L, p.amount)
        assertEquals("FD98765432", p.txnId)
        assertNull(p.account)
        assertEquals("Bank & Savings", Categorizer.categorize(p))
        assertEquals(p, MomoParser.parse(fixedDeposit))
        // Doesn't name the bank, so an unknown sender isn't trusted with it.
        assertFalse(MomoParser.accepts("8228", fixedDeposit))
    }

    @Test fun alertSharesTheAccountWallet() {
        val p = MomoParser.parse(alert, sender)!!
        assertEquals(Provider.DTB, p.provider)
        assertEquals(Direction.DR, p.direction)
        assertEquals(50_000L, p.amount)
        assertEquals("456", p.account)
        assertTrue(p.alert)
        assertTrue(p.txnId.startsWith(MomoParser.ALERT_PREFIX))
        assertEquals(at("2026-11-26 00:00"), p.occurredAt)
        assertEquals(p, MomoParser.parse(alert))
        assertTrue(MomoParser.accepts("8228", alert))
        // Masked in alerts, full in confirmations: both land in the same wallet.
        assertEquals(
            Provider.walletKey(Provider.DTB, "1", MomoParser.parse(transfer, sender)!!.account),
            Provider.walletKey(Provider.DTB, "2", p.account),
        )
    }

    @Test fun walletKeys() {
        assertEquals("dtb:456", Provider.walletKey(Provider.DTB, "3", "456"))
        assertEquals(Provider.DTB, Provider.ofWallet("dtb:456"))
        assertEquals(Provider.DTB, Provider.ofWallet("dtb"))
        assertEquals("456", Provider.accountOfWallet("dtb:456"))
        assertNull(Provider.simOfWallet("dtb:456"))
        // Unchanged for existing data.
        assertEquals(Provider.MTN, Provider.ofWallet("3"))
        assertEquals("3", Provider.simOfWallet("3"))
        assertEquals("3", Provider.simOfWallet("airtel:3"))
        assertEquals(Provider.CENTENARY, Provider.ofWallet("centenary:260"))
    }

    @Test fun otherSendersCantUseDtbTemplates() {
        assertNull(MomoParser.parse(alert, "MTNMobMoney"))
        assertNotNull(MomoParser.parse(alert, "DTB-UG"))
    }
}
