package com.momobook.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.momobook.data.Ledger
import com.momobook.data.MomoParser
import com.momobook.data.SmsRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Journals MTN MoMo, Airtel Money and Centenary Bank SMS the moment they arrive, before anyone has a chance to delete them. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val sub = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", intent.getIntExtra("subscription", -1))
        val simId = if (sub >= 0) sub.toString() else null
        val messages = parts.filterNotNull()
            .groupBy { it.originatingAddress }
            .map { (address, ps) ->
                SmsRef(
                    smsId = null, threadId = null, address = address,
                    body = ps.joinToString("") { it.messageBody ?: "" },
                    date = ps.firstOrNull()?.timestampMillis ?: System.currentTimeMillis(),
                    simId = simId,
                )
            }
            .filter { MomoParser.accepts(it.address, it.body) }
        if (messages.isEmpty()) return

        val pending = goAsync()
        Ledger.init(context)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                Ledger.ingest(messages)
            } finally {
                pending.finish()
            }
        }
    }
}
