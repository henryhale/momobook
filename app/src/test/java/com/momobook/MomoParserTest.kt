package com.momobook

import com.momobook.data.Categorizer
import com.momobook.data.Direction
import com.momobook.data.Kind
import com.momobook.data.MomoParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MomoParserTest {
    private val deducted = "Y'ello. MTN UGANDA LIMITED CISDATABUN has deducted UGX UGX 500 at a fee of UGX UGX 0 " +
        "Transaction ID: 45000000101 from your mobile money account. New balance is:UGX UGX 12,345. Be safe. " +
        "Do NOT share your Mobile Money PIN"

    private val received = "You have received UGX 50000 from JOHN DOE, 256700000001 on 2026-01-01 10:00:00. " +
        "fee:0. Reason: test. New balance: UGX 52345. ID: 45000000102. Download MoMo App http://bit.ly/x to get 500MBs."

    @Test fun parsesDeduction() {
        val p = MomoParser.parse(deducted)!!
        assertEquals("45000000101", p.txnId)
        assertEquals(Direction.DR, p.direction)
        assertEquals(500L, p.amount)
        assertEquals(0L, p.fee)
        assertEquals(12345L, p.balance)
        assertEquals("MTN UGANDA LIMITED CISDATABUN", p.counterparty)
        assertEquals("Data & Airtime", Categorizer.categorize(p))
        assertEquals("MTN Data Bundles", Categorizer.displayName(p.counterparty))
    }

    @Test fun parsesReceipt() {
        val p = MomoParser.parse(received)!!
        assertEquals("45000000102", p.txnId)
        assertEquals(Direction.CR, p.direction)
        assertEquals(Kind.RECEIVED, p.kind)
        assertEquals(50000L, p.amount)
        assertEquals(52345L, p.balance)
        assertEquals("JOHN DOE", p.counterparty)
        assertEquals("256700000001", p.phone)
        assertEquals("test", p.reason)
        assertNotNull(p.occurredAt)
        assertEquals("John Doe", Categorizer.displayName(p.counterparty))
    }

    @Test fun parsesGenericSent() {
        val p = MomoParser.parse(
            "You have sent UGX 15,000 to JOHN DOE, 256772000000 on 2026-11-20 10:00:00. Fee UGX 500. " +
                "Your new balance: UGX 70,000. Transaction ID: 99887766554."
        )!!
        assertEquals(Direction.DR, p.direction)
        assertEquals(Kind.SENT, p.kind)
        assertEquals(15000L, p.amount)
        assertEquals(500L, p.fee)
        assertEquals(70000L, p.balance)
        assertEquals("JOHN DOE", p.counterparty)
    }

    @Test fun ignoresNonTransactions() {
        assertNull(MomoParser.parse("Hello, your data bundle expires tomorrow."))
        assertNull(MomoParser.parse("Get 10GB for UGX 20,000 today! Dial *150#"))
    }

    // Formats below mirror real MTN Uganda messages; names and numbers are made up.

    @Test fun parsesPakaBundleWithMessage() {
        val p = MomoParser.parse(
            "Y'ello. MTN UGANDA LIMITED PAKABUN50 has deducted UGX 5,000 at a fee of UGX 0 Transaction ID: 45000000010 " +
                "from your mobile money account. Message: Bundle Purchase. New balance is:UGX 1,198. Your Bonus was: 0. Be safe."
        )!!
        assertEquals(5000L, p.amount)
        assertEquals(1198L, p.balance)
        assertEquals("Bundle Purchase", p.reason)
        assertEquals("Data & Airtime", Categorizer.categorize(p))
    }

    @Test fun parsesAgentDeposit() {
        val p = MomoParser.parse(
            "You have deposited UGX 50000 from JANE DOE on 2026-11-20 10:00:00. New balance: UGX 51198. ID: 45000000011. " +
                "Do NOT share your Mobile Money PIN."
        )!!
        assertEquals(Direction.CR, p.direction)
        assertEquals(Kind.DEPOSIT, p.kind)
        assertEquals("JANE DOE", p.counterparty)
        assertEquals("Cash Deposit", Categorizer.categorize(p))
    }

    @Test fun parsesWithdrawalWithoutId() {
        val body = "You have withdrawn UGX UGX 20000 from AGENT SHOP on 2026-11-21 09:15:00. New balance: UGX 1198. ID -. " +
            "Download MoMo App http://bit.ly/x to get 500MBs."
        val p = MomoParser.parse(body)!!
        assertEquals(Direction.DR, p.direction)
        assertEquals(Kind.WITHDRAW, p.kind)
        assertEquals(20000L, p.amount)
        assertEquals("WD-20261121091500-20000", p.txnId)
        assertEquals(p.txnId, MomoParser.parse(body)!!.txnId) // stable across re-scans
    }

    @Test fun parsesSentToNumber() {
        val p = MomoParser.parse(
            "Y'ello. You have sent UGX 10000 to 256770000000 JOHN DOE. Fee:UGX 500.00.  Transaction ID:45000000012. " +
                "Your Mobile Money balance is now UGX 1198.Thank you for using MTN Mobile Money."
        )!!
        assertEquals(Kind.SENT, p.kind)
        assertEquals("JOHN DOE", p.counterparty)
        assertEquals("256770000000", p.phone)
        assertEquals(500L, p.fee)
        assertEquals(1198L, p.balance)
    }

    @Test fun skipsRequestsApprovalsAndOtps() {
        assertNull(MomoParser.parse("Dial *165# and select My Approvals to allow payment of UGX 5000 to MTN UGANDA LIMITED."))
        assertNull(MomoParser.parse(
            "Y'ello. You have requested a withdrawal of UGX 20000 from AGENT SHOP . Dial *165# and select My Approvals to " +
                "authorize the transaction.The total fee is  UGX 700 inclusive of 0.5 percent tax.Transaction ID 45000000013."
        ))
        assertNull(MomoParser.parse("Y'ello. Please use the OTP 123456 to complete the transaction on Shop with UGX EUR 9.99."))
    }

    @Test fun parsesWithdrawalWithFeeAndTax() {
        val p = MomoParser.parse(
            "You have withdrawn UGX 8,000 on 2025-02-28 18:39:05. Fee: UGX 700, Tax: UGX 40. New balance: UGX 1,260.5. " +
                "Download MoMo App https://bit.ly/x to get 500MBs"
        )!!
        assertEquals(Kind.WITHDRAW, p.kind)
        assertEquals(8000L, p.amount)
        assertEquals(740L, p.fee)
        assertEquals(1261L, p.balance)
    }

    @Test fun parsesAirtimeMerchantBankAndUtility() {
        MomoParser.parse("You have bought UGX 1,200 airtime. New MoMo balance: UGX 60.5. Do NOT share your Mobile Money PIN.")!!.let {
            assertEquals(1200L, it.amount); assertEquals("Data & Airtime", Categorizer.categorize(it))
        }
        MomoParser.parse(
            "You have paid UGX 500 to  SOME SUPERMARKET LIMITED on 2025-01-01 10:00:00. New balance is: 12345. ID: 31000000001. " +
                "Discount received: UGX 0."
        )!!.let { assertEquals(Direction.DR, it.direction); assertEquals(12345L, it.balance); assertEquals("SOME SUPERMARKET LIMITED", it.counterparty) }
        MomoParser.parse(
            "Y'ello. You have transferred UGX UGX 50,000 to Centenary Bank. TX Charge  UGX 1000.00. Your new balance: UGX UGX 100,000.5. " +
                "Transaction ID:31000000002."
        )!!.let { assertEquals(1000L, it.fee); assertEquals("Centenary Bank", it.counterparty) }
        MomoParser.parse(
            "You have paid UGX 20000 for UEDCL at a Fee of UGX 0 Account No.: 123 New balance is UGX 500 Transaction ID: 31000000003"
        )!!.let { assertEquals("Electricity", Categorizer.categorize(it)) }
    }

    @Test fun skipsFailedTransactions() {
        assertNull(MomoParser.parse(
            "Hello, the transaction with amount 1000 UGX for MTN UGANDA LIMITED KIRABO1 (256700000000) with message: Bundle Purchase " +
                "failed at 2025-02-28 21:14:01, reason: Expired .Please check your balance. Financial Transaction Id: 31000000004"
        ))
    }

    @Test fun onlyMomoSenders() {
        assertEquals(true, MomoParser.isMomoSender("MTNMobMoney"))
        assertEquals(false, MomoParser.isMomoSender("AirtelMoney"))
    }
}
