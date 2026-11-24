package com.momobook.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local, append-only journal. Each MoMo transaction ID is stored once; the original SMS
 * body is copied into the row so the history survives even if the SMS is deleted.
 *
 * Bumping [VERSION] re-parses every stored SMS body with the current parser, so parser
 * fixes also correct history whose SMS have since been deleted.
 */
class LedgerDb(context: Context) : SQLiteOpenHelper(context, "momo_ledger.db", null, VERSION) {
    private val appContext = context.applicationContext

    companion object {
        const val VERSION = 2
        private const val FIVE_MIN = 5 * 60_000L
        private const val TWO_DAYS = 2 * 86_400_000L
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE entries (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                txn_id TEXT NOT NULL UNIQUE,
                direction TEXT NOT NULL,
                kind TEXT NOT NULL,
                amount INTEGER NOT NULL,
                fee INTEGER NOT NULL DEFAULT 0,
                balance INTEGER,
                counterparty TEXT NOT NULL,
                phone TEXT,
                reason TEXT,
                category TEXT NOT NULL,
                occurred_at INTEGER NOT NULL,
                sms_id INTEGER,
                sms_thread_id INTEGER,
                sms_address TEXT,
                sms_body TEXT NOT NULL,
                imported_at INTEGER NOT NULL,
                wallet TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_entries_time ON entries(occurred_at)")
        createRequests(db)
    }

    private fun createRequests(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS withdraw_requests (
                txn_id TEXT PRIMARY KEY,
                agent TEXT NOT NULL,
                amount INTEGER NOT NULL,
                fee INTEGER NOT NULL,
                requested_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE entries ADD COLUMN wallet TEXT NOT NULL DEFAULT ''")
        createRequests(db)
        reparseAll(db)
    }

    /** Rebuilds the journal from the SMS bodies it already holds, using the current parser. */
    private fun reparseAll(db: SQLiteDatabase) {
        val saved = db.rawQuery(
            "SELECT sms_id, sms_thread_id, sms_address, sms_body, occurred_at, wallet FROM entries ORDER BY occurred_at, _id",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    SmsRef(
                        smsId = if (c.isNull(0)) null else c.getLong(0),
                        threadId = if (c.isNull(1)) null else c.getLong(1),
                        address = c.getString(2),
                        body = c.getString(3),
                        date = c.getLong(4),
                        simId = c.getString(5).ifBlank { null },
                    )
                )
            }
        }
        // Rows journaled before wallets existed: recover the SIM from the inbox where the SMS still exists.
        val withSim = saved.map { r ->
            if (r.simId != null || r.smsId == null) r
            else r.copy(simId = com.momobook.sms.SmsInbox.simOf(appContext, r.smsId))
        }
        db.execSQL("DELETE FROM entries")
        ingest(db, withSim)
    }

    /** Journals messages (oldest first). Returns how many new transactions were added. */
    fun ingest(db: SQLiteDatabase, messages: List<SmsRef>): Int {
        var added = 0
        for (m in messages.sortedBy { it.date }) {
            if (!MomoParser.isMomoSender(m.address)) continue
            MomoParser.parseWithdrawRequest(m.body)?.let { recordRequest(db, it, m.date); continue }
            val p = MomoParser.parse(m.body) ?: continue
            if (insert(db, p, m)) added++
        }
        return added
    }

    private fun insert(db: SQLiteDatabase, raw: ParsedTxn, sms: SmsRef): Boolean {
        val wallet = sms.simId.orEmpty()
        // MTN transaction IDs are global; synthetic ones (airtime, withdrawals without ID) are scoped to the wallet.
        val parsed = if (wallet.isNotEmpty() && !raw.txnId.all(Char::isDigit)) raw.copy(txnId = "$wallet:${raw.txnId}") else raw
        val occurredAt = parsed.occurredAt ?: sms.date
        val p = if (parsed.kind == Kind.WITHDRAW && parsed.fee == 0L) {
            parsed.copy(fee = requestedFee(db, parsed, occurredAt) ?: 0)
        } else parsed
        val cv = ContentValues().apply {
            put("txn_id", p.txnId)
            put("direction", p.direction.name)
            put("kind", p.kind.name)
            put("amount", p.amount)
            put("fee", p.fee)
            if (p.balance != null) put("balance", p.balance) else putNull("balance")
            put("counterparty", p.counterparty)
            put("phone", p.phone)
            put("reason", p.reason)
            put("category", Categorizer.categorize(p))
            put("occurred_at", occurredAt)
            if (sms.smsId != null) put("sms_id", sms.smsId) else putNull("sms_id")
            if (sms.threadId != null) put("sms_thread_id", sms.threadId) else putNull("sms_thread_id")
            put("sms_address", sms.address)
            put("sms_body", sms.body)
            put("imported_at", System.currentTimeMillis())
            put("wallet", wallet)
        }
        val rowId = db.insertWithOnConflict("entries", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        if (rowId != -1L) return true

        // MTN often sends two SMS for one transaction: a generic "MTN UGANDA LIMITED has deducted…" and a
        // specific one naming the product (e.g. "MTN UGANDA LIMITED PAKABUN50 …"). Keep the more specific one.
        db.execSQL(
            """UPDATE entries SET counterparty = ?, reason = COALESCE(?, reason), category = ?
               WHERE txn_id = ? AND length(counterparty) < length(?) AND substr(?, 1, length(counterparty)) = counterparty""",
            arrayOf<Any?>(p.counterparty, p.reason, Categorizer.categorize(p), p.txnId, p.counterparty, p.counterparty),
        )
        if (wallet.isNotEmpty()) {
            db.execSQL("UPDATE entries SET wallet = ? WHERE txn_id = ? AND wallet = ''", arrayOf<Any?>(wallet, p.txnId))
        }
        // Already journaled (e.g. captured live by the receiver): just attach the inbox link.
        if (sms.smsId != null) {
            db.execSQL(
                "UPDATE entries SET sms_id = ?, sms_thread_id = ? WHERE txn_id = ? AND sms_id IS NULL",
                arrayOf<Any?>(sms.smsId, sms.threadId, p.txnId),
            )
        }
        return false
    }

    /** The fee from the matching "requested a withdrawal" SMS (same agent and amount, shortly before). */
    private fun requestedFee(db: SQLiteDatabase, p: ParsedTxn, at: Long): Long? =
        db.rawQuery(
            """SELECT fee FROM withdraw_requests WHERE amount = ? AND upper(agent) = upper(?)
               AND requested_at BETWEEN ? AND ? ORDER BY requested_at DESC LIMIT 1""",
            arrayOf(p.amount.toString(), p.counterparty, (at - TWO_DAYS).toString(), (at + FIVE_MIN).toString()),
        ).use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun recordRequest(db: SQLiteDatabase, r: WithdrawRequest, at: Long) {
        db.insertWithOnConflict(
            "withdraw_requests", null,
            ContentValues().apply {
                put("txn_id", r.txnId); put("agent", r.agent); put("amount", r.amount)
                put("fee", r.fee); put("requested_at", at)
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
        // The confirmation may have been journaled first (SMS order isn't guaranteed).
        db.execSQL(
            """UPDATE entries SET fee = ? WHERE kind = 'WITHDRAW' AND fee = 0 AND amount = ? AND upper(counterparty) = upper(?)
               AND occurred_at BETWEEN ? AND ?""",
            arrayOf<Any?>(r.fee, r.amount, r.agent, at - FIVE_MIN, at + TWO_DAYS),
        )
    }

    fun all(): List<Entry> =
        readableDatabase.rawQuery("SELECT * FROM entries ORDER BY occurred_at DESC, _id DESC", null).use { c ->
            buildList { while (c.moveToNext()) add(c.toEntry()) }
        }

    fun setCategory(id: Long, category: String) {
        writableDatabase.execSQL("UPDATE entries SET category = ? WHERE _id = ?", arrayOf<Any?>(category, id))
    }

    fun delete(id: Long) {
        writableDatabase.delete("entries", "_id = ?", arrayOf(id.toString()))
    }

    private fun Cursor.str(col: String): String? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.long(col: String): Long? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getLong(it) }

    private fun Cursor.toEntry() = Entry(
        id = long("_id")!!,
        txnId = str("txn_id")!!,
        direction = Direction.valueOf(str("direction")!!),
        kind = runCatching { Kind.valueOf(str("kind")!!) }.getOrDefault(Kind.OTHER),
        amount = long("amount") ?: 0,
        fee = long("fee") ?: 0,
        balance = long("balance"),
        counterparty = str("counterparty") ?: "",
        phone = str("phone"),
        reason = str("reason"),
        category = str("category") ?: Categorizer.OTHER,
        occurredAt = long("occurred_at") ?: 0,
        smsId = long("sms_id"),
        threadId = long("sms_thread_id"),
        smsAddress = str("sms_address"),
        smsBody = str("sms_body") ?: "",
        importedAt = long("imported_at") ?: 0,
        wallet = str("wallet").orEmpty(),
    )
}
