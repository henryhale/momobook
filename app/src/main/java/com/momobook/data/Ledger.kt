package com.momobook.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Process-wide journal repository. Exposes all entries as a StateFlow. */
object Ledger {
    private lateinit var db: LedgerDb
    private val mutex = Mutex()

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    fun init(context: Context) {
        if (!::db.isInitialized) db = LedgerDb(context.applicationContext)
    }

    suspend fun reload() = withContext(Dispatchers.IO) {
        _entries.value = db.all()
        _loaded.value = true
    }

    /** Journals every parseable message; returns how many were new. */
    suspend fun ingest(messages: List<SmsRef>): Int = withContext(Dispatchers.IO) {
        val added = mutex.withLock {
            val w = db.writableDatabase
            w.beginTransaction()
            try {
                db.ingest(w, messages).also { w.setTransactionSuccessful() }
            } finally {
                w.endTransaction()
            }
        }
        reload()
        added
    }

    suspend fun setCategory(entry: Entry, category: String) = withContext(Dispatchers.IO) {
        db.setCategory(entry.id, category); reload()
    }

    suspend fun delete(entry: Entry) = withContext(Dispatchers.IO) {
        db.delete(entry.id); reload()
    }
}
