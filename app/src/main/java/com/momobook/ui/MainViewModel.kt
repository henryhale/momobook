package com.momobook.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.momobook.data.Entry
import com.momobook.data.Ledger
import com.momobook.data.MomoParser
import com.momobook.data.Prefs
import com.momobook.data.ThemeMode
import com.momobook.data.SmsRef
import com.momobook.sms.SmsInbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Wallet(val key: String, val label: String)

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    val allEntries: StateFlow<List<Entry>> = Ledger.entries

    /** Selected wallet (SIM) or null for all wallets. */
    private val _wallet = MutableStateFlow<String?>(null)
    val wallet: StateFlow<String?> = _wallet.asStateFlow()
    fun selectWallet(w: String?) { _wallet.value = w }

    /** Entries for the selected wallet. */
    val entries: StateFlow<List<Entry>> = combine(Ledger.entries, _wallet) { es, w ->
        if (w == null) es else es.filter { it.wallet == w }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Known wallets in a stable order, labelled "SIM 1", "SIM 2", … */
    val wallets: StateFlow<List<Wallet>> = Ledger.entries.map { es ->
        es.map { it.wallet }.filter { it.isNotEmpty() }.distinct()
            .sortedBy { it.toLongOrNull() ?: Long.MAX_VALUE }
            .mapIndexed { i, key -> Wallet(key, "SIM ${i + 1}") }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val loaded: StateFlow<Boolean> = Ledger.loaded

    private val prefs = Prefs(app)

    private val _theme = MutableStateFlow(prefs.theme)
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()
    fun setTheme(m: ThemeMode) { prefs.theme = m; _theme.value = m }

    private val _hideOnLaunch = MutableStateFlow(prefs.hideBalancesOnLaunch)
    val hideOnLaunch: StateFlow<Boolean> = _hideOnLaunch.asStateFlow()
    fun setHideOnLaunch(v: Boolean) { prefs.hideBalancesOnLaunch = v; _hideOnLaunch.value = v }

    private val _balancesHidden = MutableStateFlow(prefs.hideBalancesOnLaunch)
    val balancesHidden: StateFlow<Boolean> = _balancesHidden.asStateFlow()
    fun toggleBalances() { _balancesHidden.value = !_balancesHidden.value }

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events

    init {
        viewModelScope.launch { Ledger.reload() }
    }

    fun sync(quiet: Boolean = false) {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            try {
                val msgs = withContext(Dispatchers.IO) { SmsInbox.scan(app) }
                val added = Ledger.ingest(msgs)
                if (!quiet || added > 0) {
                    _events.emit(if (added > 0) "$added new transaction${if (added == 1) "" else "s"} journaled" else "Journal is up to date")
                }
            } finally {
                _syncing.value = false
            }
        }
    }

    /** Adds a message pasted by the user. Returns false when it isn't a recognised MoMo SMS. */
    fun importPasted(text: String): Boolean {
        if (MomoParser.parse(text) == null) return false
        viewModelScope.launch {
            val added = Ledger.ingest(listOf(SmsRef(null, null, "Pasted", text.trim(), System.currentTimeMillis())))
            _events.emit(if (added > 0) "Transaction added to journal" else "That transaction is already in your journal")
        }
        return true
    }

    fun setCategory(e: Entry, category: String) = viewModelScope.launch { Ledger.setCategory(e, category) }

    fun delete(e: Entry) = viewModelScope.launch {
        Ledger.delete(e)
        _events.emit("Entry removed")
    }

    fun exportCsv(onReady: (Intent) -> Unit) = viewModelScope.launch {
        val uri: Uri = withContext(Dispatchers.IO) {
            val dir = File(app.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "momo-journal.csv")
            val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            fun q(s: String?) = "\"" + (s ?: "").replace("\"", "\"\"") + "\""
            file.bufferedWriter().use { w ->
                val labels = wallets.value.associate { it.key to it.label }
                w.appendLine("Date,Wallet,Transaction ID,Type,Particulars,Category,Debit (out),Credit (in),Fee,Balance,Phone,Reason,SMS")
                entries.value.sortedBy { it.occurredAt }.forEach { e ->
                    w.appendLine(
                        listOf(
                            df.format(Date(e.occurredAt)), labels[e.wallet] ?: "", e.txnId, e.direction.name, q(e.displayName), q(e.category),
                            if (e.isIn) "" else e.amount.toString(), if (e.isIn) e.amount.toString() else "",
                            e.fee.toString(), e.balance?.toString() ?: "", e.phone ?: "", q(e.reason), q(e.smsBody),
                        ).joinToString(",")
                    )
                }
            }
            FileProvider.getUriForFile(app, "${app.packageName}.files", file)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "MoMo journal")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        onReady(Intent.createChooser(send, "Export journal"))
    }
}
