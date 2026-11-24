package com.momobook.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.momobook.data.Categorizer
import com.momobook.data.Entry
import com.momobook.sms.SmsInbox
import com.momobook.ui.components.*
import com.momobook.ui.theme.LocalMoneyColors
import com.momobook.ui.theme.Tabular
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EntryDetailSheet(entry: Entry, walletLabel: String?, onDismiss: () -> Unit, onCategory: (String) -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val mc = LocalMoneyColors.current
    val accent = if (entry.isIn) mc.moneyIn else mc.moneyOut
    var inInbox by remember(entry.id) { mutableStateOf<Boolean?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(entry.id) { inInbox = withContext(Dispatchers.IO) { SmsInbox.stillExists(ctx, entry) } }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                CategoryIcon(entry.category, 60)
                Spacer(Modifier.height(12.dp))
                Text(entry.displayName, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                FitText(
                    (if (entry.isIn) "+" else "−") + Fmt.ugx(entry.amount),
                    style = MaterialTheme.typography.headlineMedium.merge(Tabular), color = accent,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DirectionPill(entry.isIn)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (entry.isIn) "Credit · money received" else "Debit · money spent",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Field("Date", Fmt.dateTime(entry.occurredAt))
                    walletLabel?.let { Field("Wallet", it) }
                    Field("Transaction ID", entry.txnId, mono = true)
                    Field("Counterparty", entry.counterparty)
                    entry.phone?.let { Field("Phone", it, mono = true) }
                    Field("Fee", Fmt.ugx(entry.fee))
                    if (!entry.isIn) Field("Total out", Fmt.ugx(entry.outTotal))
                    entry.balance?.let { Field("Balance after", if (LocalBalancePrivacy.current.hidden) "UGX $MASK" else Fmt.ugx(it)) }
                    entry.reason?.let { Field("Reason", it) }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Category", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (if (entry.isIn) Categorizer.incomeCategories else Categorizer.spendCategories).forEach { c ->
                    val st = CategoryStyles.of(c)
                    FilterChip(
                        selected = entry.category == c, onClick = { onCategory(c) },
                        label = { Text(c) },
                        leadingIcon = { Icon(st.icon, null, Modifier.size(16.dp), tint = st.color) },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Original SMS", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                SmsStatus(entry, inInbox)
            }
            Spacer(Modifier.height(8.dp))
            Surface(shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Column(Modifier.padding(16.dp)) {
                    entry.smsAddress?.let {
                        Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(entry.smsBody, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = {
                        if (!SmsInbox.open(ctx, entry)) Toast.makeText(ctx, "Couldn't open your messaging app", Toast.LENGTH_SHORT).show()
                    },
                    enabled = inInbox != false && (entry.threadId != null || (entry.smsAddress != null && entry.smsAddress != "Pasted")),
                    modifier = Modifier.weight(1f),
                ) { Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Open in Messages") }
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(entry.smsBody))
                    Toast.makeText(ctx, "SMS copied", Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Copy") }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = mc.moneyOut)) {
                Icon(Icons.Rounded.DeleteOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Remove from journal")
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove this entry?") },
            text = {
                Text(
                    if (inInbox == true) "It will come back the next time your inbox is scanned while the SMS still exists."
                    else "The original SMS is no longer in your inbox, so this record can't be recovered."
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun SmsStatus(entry: Entry, inInbox: Boolean?) {
    val mc = LocalMoneyColors.current
    val (label, color) = when {
        entry.smsAddress == "Pasted" -> "Added manually" to MaterialTheme.colorScheme.onSurfaceVariant
        inInbox == true -> "In your inbox" to mc.moneyIn
        inInbox == false -> "Deleted · copy kept" to mc.fee
        else -> "Saved copy" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .12f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Field(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.width(120.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.merge(Tabular),
            fontWeight = FontWeight.Medium, textAlign = TextAlign.End,
            fontFamily = if (mono) FontFamily.Monospace else null,
        )
    }
}
