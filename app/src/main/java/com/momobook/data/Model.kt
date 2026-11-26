package com.momobook.data

/**
 * Statement convention (what you see on a bank/MoMo statement):
 *  CR = credit = money IN to your wallet, DR = debit = money OUT of your wallet.
 */
enum class Direction { DR, CR }

enum class Kind { RECEIVED, DEPOSIT, PAYMENT, SENT, WITHDRAW, OTHER }

/**
 * Mobile money network or bank. Mobile money keeps a wallet per SIM; a bank's wallet is the account,
 * whichever SIM the SMS arrives on.
 */
enum class Provider(val label: String, private val prefix: String, val isBank: Boolean) {
    MTN("MTN", "", false),
    AIRTEL("Airtel", "airtel", false),
    CENTENARY("Centenary", "centenary", true),
    DTB("DTB", "dtb", true),
    STANBIC("Stanbic", "stanbic", true);

    companion object {
        /**
         * Wallet key: the SIM subscription id for MTN (kept for existing data), "airtel[:sim]" for Airtel,
         * "<bank>[:account digits]" for banks (e.g. "centenary:260").
         */
        fun walletKey(provider: Provider, simId: String?, account: String? = null): String = when {
            provider == MTN -> simId.orEmpty()
            provider.isBank -> if (account.isNullOrEmpty()) provider.prefix else "${provider.prefix}:$account"
            else -> if (simId.isNullOrEmpty()) provider.prefix else "${provider.prefix}:$simId"
        }

        /** Key used when the SIM is unknown (pasted messages). Bank wallets don't depend on the SIM. */
        fun unknownWallet(p: ParsedTxn) = walletKey(p.provider, null, p.account)

        fun ofWallet(key: String): Provider =
            entries.firstOrNull { it != MTN && (key == it.prefix || key.startsWith("${it.prefix}:")) } ?: MTN

        /** SIM subscription id behind a wallet key; null for unknown SIMs and bank accounts. */
        fun simOfWallet(key: String): String? {
            val p = ofWallet(key)
            return when {
                p == MTN -> key.ifEmpty { null }
                p.isBank -> null
                else -> key.substringAfter(':', "").ifEmpty { null }
            }
        }

        /** Account digits of a bank wallet key ("centenary:260" -> "260"). */
        fun accountOfWallet(key: String): String? =
            if (ofWallet(key).isBank) key.substringAfter(':', "").ifEmpty { null } else null
    }
}

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
    val provider: Provider = Provider.MTN,
    /** Bank account digits shown in the SMS (e.g. "260" from "A/C **260"). */
    val account: String? = null,
    /**
     * A bare bank alert (no reference, no time) that may repeat a confirmation SMS for the same
     * transaction. The journal drops it when a matching confirmed entry exists.
     */
    val alert: Boolean = false,
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
    /** Wallet key, see [Provider.walletKey]. MTN: SIM subscription id, "" when unknown (e.g. pasted). */
    val wallet: String,
) {
    val isIn: Boolean get() = direction == Direction.CR

    /** Total effect on the wallet: amount plus fee for outflows, amount for inflows. */
    val outTotal: Long get() = if (isIn) 0 else amount + fee
    val inTotal: Long get() = if (isIn) amount else 0
    val displayName: String get() = Categorizer.displayName(counterparty)
}
