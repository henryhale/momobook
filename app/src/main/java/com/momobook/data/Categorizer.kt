package com.momobook.data

import java.util.Locale

object Categorizer {
    const val INCOME = "Income"
    const val OTHER = "Other"

    /** Spending categories a user can assign, in display order. */
    val spendCategories = listOf(
        "Data & Airtime", "Transfers", "Merchants & Bills", "Cash Withdrawal", "Food & Shopping",
        "Transport", "Electricity", "Water", "TV & Internet", "Education", "Health",
        "Taxes & Govt", "Bank & Savings", "Betting", OTHER,
    )
    const val CASH_DEPOSIT = "Cash Deposit"
    val incomeCategories = listOf(INCOME, CASH_DEPOSIT, "Interest", "Salary", "Business", "Family & Friends", "Refund", OTHER)

    private fun rx(vararg words: String) =
        Regex(words.joinToString("|") { """\b${Regex.escape(it)}""" }, RegexOption.IGNORE_CASE)

    private val rules: List<Pair<Regex, String>> = listOf(
        rx("DATABUN", "DATA BUN", "BUNDLE", "AIRTIME", "INTERNET BUN", "CISDATA", "MTN DATA") to "Data & Airtime",
        rx("UMEME", "YAKA", "ELECTRIC", "UEDCL") to "Electricity",
        rx("NWSC", "WATER") to "Water",
        rx("DSTV", "GOTV", "STARTIMES", "AZAM", "ZUKU", "NETFLIX", "SHOWMAX", "ROKE", "AIRTEL INTERNET") to "TV & Internet",
        rx("SCHOOL", "UNIVERSITY", "COLLEGE", "ACADEMY", "SCHOOLPAY", "TUITION") to "Education",
        rx("URA", "REVENUE AUTHORITY", "KCCA", "GOVERNMENT", "MINISTRY", "NIRA", "URSB") to "Taxes & Govt",
        rx("AGENT", "WITHDRAW", "CASH OUT") to "Cash Withdrawal",
        rx("BANK", "SACCO", "MOKASH", "XTRA", "YINVESTA", "SAVINGS") to "Bank & Savings",
        rx("SUPERMARKET", "FOODS", "RESTAURANT", "CAFE", "GLOVO", "JUMIA", "SHOPRITE", "CARREFOUR", "CAPITAL SHOPPERS", "MAJID", "BAKERY", "KFC", "CAFE JAVAS") to "Food & Shopping",
        rx("SAFEBODA", "UBER", "BOLT", "FUEL", "SHELL", "STABEX", "TOTALENERGIES", "TAXI", "BUS") to "Transport",
        rx("HOSPITAL", "CLINIC", "PHARMACY", "MEDICAL", "HEALTH") to "Health",
        rx("BET", "SPORTPESA", "BETWAY", "FORTEBET", "GAL SPORT", "BETPAWA", "LOTTO") to "Betting",
    )

    fun categorize(p: ParsedTxn): String {
        if (p.direction == Direction.CR) return when {
            p.kind == Kind.DEPOSIT -> CASH_DEPOSIT
            p.counterparty.contains("INTEREST", ignoreCase = true) -> "Interest"
            else -> INCOME
        }
        if (p.kind == Kind.WITHDRAW) return "Cash Withdrawal"
        if ((p.counterparty.startsWith("MTN", ignoreCase = true) && BUNDLE_CODE.containsMatchIn(p.counterparty.uppercase())) || p.reason?.contains("bundle", ignoreCase = true) == true) {
            return "Data & Airtime"
        }
        rules.firstOrNull { it.first.containsMatchIn(p.counterparty) }?.let { return it.second }
        return if (p.kind == Kind.SENT || p.phone != null) "Transfers" else "Merchants & Bills"
    }

    /** MTN product codes such as CISDATABUN, PAKABUN50 — all bundle purchases. */
    private val BUNDLE_CODE = Regex("""[A-Z]*BUN\d*\b""")

    private val ACRONYMS = setOf("MTN", "URA", "NWSC", "KCCA", "UGX", "DSTV", "GOTV", "SACCO", "KFC", "NIRA", "UEDCL", "AVS", "GTB", "II", "III")

    /** Human-friendly name for raw SMS counterparties ("MTN UGANDA LIMITED CISDATABUN" -> "MTN Data Bundles"). */
    fun displayName(raw: String): String {
        val up = raw.uppercase(Locale.ROOT)
        when {
            "DATABUN" in up || "CISDATA" in up -> return "MTN Data Bundles"
            "PAKABUN" in up -> return "MTN Paka Bundles"
            "AIRTIME" in up -> return "MTN Airtime"
            BUNDLE_CODE.containsMatchIn(up) && up.startsWith("MTN") -> return "MTN Bundles"
            up == "MTN UGANDA LIMITED" -> return "MTN Uganda"
        }
        return raw.split(' ').filter { it.isNotBlank() }.joinToString(" ") { w ->
            val u = w.uppercase(Locale.ROOT)
            if (u in ACRONYMS || !w.all { it.isLetter() }) u
            else w.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
    }
}
