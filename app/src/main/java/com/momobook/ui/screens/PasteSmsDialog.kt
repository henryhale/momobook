package com.momobook.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp

@Composable
fun PasteSmsDialog(onDismiss: () -> Unit, onSubmit: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.ContentPaste, null) },
        title = { Text("Add from SMS text") },
        text = {
            Column {
                Text(
                    "Paste a Mobile Money message. It will be parsed and saved to your journal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = false },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("You have received UGX 80000 from …") },
                    isError = error,
                    supportingText = if (error) {{ Text("That doesn't look like a MoMo transaction message.") }} else null,
                )
                TextButton(onClick = { clipboard.getText()?.text?.let { text = it; error = false } }) {
                    Text("Paste from clipboard")
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (onSubmit(text)) onDismiss() else error = true }, enabled = text.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
