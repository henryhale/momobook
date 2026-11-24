package com.momobook.sms

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.momobook.data.Entry
import com.momobook.data.SmsRef

object SmsInbox {

    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** Reads inbox SMS that mention UGX. The ledger keeps only MTN MoMo / Airtel Money senders and real transactions. */
    fun scan(ctx: Context): List<SmsRef> {
        if (!hasPermission(ctx)) return emptyList()
        val cols = arrayOf(
            Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.SUBSCRIPTION_ID,
        )
        return runCatching {
            ctx.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI, cols,
                "${Telephony.Sms.BODY} LIKE ?", arrayOf("%UGX%"),
                "${Telephony.Sms.DATE} ASC",
            )?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            SmsRef(
                                smsId = c.getLong(0),
                                threadId = c.getLong(1),
                                address = c.getString(2),
                                body = c.getString(3) ?: continue,
                                date = c.getLong(4),
                                simId = if (c.isNull(5)) null else c.getLong(5).toString(),
                            )
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /** SIM subscription id of a stored SMS, or null if it's gone or unreadable. */
    fun simOf(ctx: Context, smsId: Long): String? {
        if (!hasPermission(ctx)) return null
        return runCatching {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms.SUBSCRIPTION_ID),
                "${Telephony.Sms._ID} = ?", arrayOf(smsId.toString()), null,
            )?.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0).toString() else null }
        }.getOrNull()
    }

    /** Whether the linked SMS still exists in the phone's inbox. Null = unknown (no link/permission). */
    fun stillExists(ctx: Context, entry: Entry): Boolean? {
        val id = entry.smsId ?: return null
        if (!hasPermission(ctx)) return null
        return runCatching {
            ctx.contentResolver.query(
                Telephony.Sms.CONTENT_URI, arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms._ID} = ?", arrayOf(id.toString()), null,
            )?.use { it.count > 0 }
        }.getOrNull()
    }

    /** Opens the SMS conversation in the default messaging app. */
    fun open(ctx: Context, entry: Entry): Boolean {
        val intents = buildList {
            entry.threadId?.let { add(Intent(Intent.ACTION_VIEW, Uri.parse("content://mms-sms/conversations/$it"))) }
            entry.smsAddress?.let { add(Intent(Intent.ACTION_VIEW, Uri.parse("sms:" + Uri.encode(it)))) }
        }
        for (i in intents) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }
}
