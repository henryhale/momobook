package com.momobook.data

/**
 * Statement convention (what you see on a bank/MoMo statement):
 *  CR = credit = money IN to your wallet, DR = debit = money OUT of your wallet.
 */
enum class Direction { DR, CR }

enum class Kind { RECEIVED, DEPOSIT, PAYMENT, SENT, WITHDRAW, OTHER }

/** Result of parsing a single SMS body. */
data class ParsedTxn(
    val txnId: String,
    val direction: Direction,
    val kind: Kind,
    val amount: Long,
    val fee: Long,
    val balance: Long?,
    val counterparty: String,
    val phone: String?,
    val reason: String?,
    val occurredAt: Long?,
)

/** Where a parsed transaction came from. smsId is null for pasted / just-received messages. */
data class SmsRef(
    val smsId: Long?,
    val threadId: Long?,
    val address: String?,
    val body: String,
    val date: Long,
    /** SIM subscription the SMS arrived on. Each SIM has its own MoMo wallet. */
    val simId: String? = null,
)

/** A journal row as persisted locally. The SMS body is copied so history survives SMS deletion. */
data class Entry(
    val id: Long,
    val txnId: String,
    val direction: Direction,
    val kind: Kind,
    val amount: Long,
    val fee: Long,
    val balance: Long?,
    val counterparty: String,
    val phone: String?,
    val reason: String?,
    val category: String,
    val occurredAt: Long,
    val smsId: Long?,
    val threadId: Long?,
    val smsAddress: String?,
    val smsBody: String,
    val importedAt: Long,
    /** Wallet key (SIM subscription id), "" when unknown (e.g. pasted). */
    val wallet: String,
) {
    val isIn: Boolean get() = direction == Direction.CR

    /** Total effect on the wallet: amount plus fee for outflows, amount for inflows. */
    val outTotal: Long get() = if (isIn) 0 else amount + fee
    val inTotal: Long get() = if (isIn) amount else 0
    val displayName: String get() = Categorizer.displayName(counterparty)
}
