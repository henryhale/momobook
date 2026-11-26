package com.momobook.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.momobook.security.AppLock
import com.momobook.security.findFragmentActivity
import com.momobook.ui.components.MomoLogo

/** Shown instead of the app while it is locked. Nothing of the journal is composed behind it. */
@Composable
fun LockScreen() {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findFragmentActivity() }
    val biometrics = remember(ctx) { AppLock.hasBiometrics(ctx) }
    var message by remember { mutableStateOf<String?>(null) }

    val unlock: () -> Unit = {
        message = null
        if (activity == null || !AppLock.canAuthenticate(ctx)) AppLock.unlock()
        else AppLock.authenticate(activity, "Unlock MomoBook") { r ->
            when (r) {
                AppLock.Result.Success -> AppLock.unlock()
                AppLock.Result.Cancelled -> Unit
                is AppLock.Result.Error -> if (!AppLock.canAuthenticate(ctx)) AppLock.unlock() else message = r.message
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (AppLock.consumeAutoPrompt()) unlock() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MomoLogo(size = 80.dp)
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text("MomoBook is locked", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (biometrics) "Use your fingerprint or screen lock to open" else "Use your screen lock to open",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(32.dp))
            Button(onClick = unlock, contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)) {
                Icon(if (biometrics) Icons.Rounded.Fingerprint else Icons.Rounded.Lock, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Unlock")
            }
        }
    }
}
