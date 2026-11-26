package com.momobook.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.momobook.data.LockGrace
import com.momobook.data.ThemeMode
import com.momobook.security.AppLock
import com.momobook.ui.components.MomoLogo
import com.momobook.ui.components.SectionCard

private const val AUTHOR = "Henry Hale"
private const val AUTHOR_SITE = "henryhale.github.io"
private const val PRIVACY_URL = "https://henryhale.github.io/momobook/privacy.html"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    theme: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
    hideOnLaunch: Boolean,
    onHideOnLaunch: (Boolean) -> Unit,
    appLock: Boolean,
    onAppLock: (Boolean) -> Unit,
    lockGrace: LockGrace,
    onLockGrace: (LockGrace) -> Unit,
    allowScreenshots: Boolean,
    onAllowScreenshots: (Boolean) -> Unit,
    entryCount: Int,
    walletCount: Int,
    syncing: Boolean,
    onSync: () -> Unit,
    onPaste: () -> Unit,
    onExport: () -> Unit,
) {
    val ctx = LocalContext.current
    val openUrl: (String) -> Unit = { url ->
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(ctx, "No browser found", Toast.LENGTH_SHORT).show() }
    }
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0"
    }

    // Re-check on resume: the user may have just set up a screen lock or fingerprint in phone settings.
    var lockAvailable by remember { mutableStateOf(AppLock.canAuthenticate(ctx)) }
    var biometrics by remember { mutableStateOf(AppLock.hasBiometrics(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        lockAvailable = AppLock.canAuthenticate(ctx)
        biometrics = AppLock.hasBiometrics(ctx)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.padding(horizontal = 4.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            Text("Appearance, privacy and your data", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard(title = "Appearance", subtitle = "Choose how MomoBook looks") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = theme == m, onClick = { onTheme(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        icon = {
                            Icon(
                                when (m) {
                                    ThemeMode.SYSTEM -> Icons.Rounded.PhoneAndroid
                                    ThemeMode.LIGHT -> Icons.Rounded.LightMode
                                    ThemeMode.DARK -> Icons.Rounded.DarkMode
                                }, null, Modifier.size(18.dp),
                            )
                        },
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) { Text(m.label, maxLines = 1) }
                }
            }
        }

        SectionCard(title = "Privacy") {
            ToggleRow(
                Icons.Rounded.VisibilityOff, "Hide balances when app opens", "Balances show as **** until you tap the eye",
                hideOnLaunch, onHideOnLaunch,
            )
            ToggleRow(
                if (biometrics) Icons.Rounded.Fingerprint else Icons.Rounded.Lock, "Lock app",
                when {
                    !lockAvailable && !appLock -> "Set a screen lock in phone settings first"
                    biometrics -> "Ask for fingerprint or screen lock to open"
                    else -> "Ask for your screen lock to open"
                },
                appLock, onAppLock, enabled = lockAvailable || appLock,
            )
            if (appLock) {
                Text("Lock again after leaving the app", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    LockGrace.entries.forEachIndexed { i, g ->
                        SegmentedButton(
                            selected = lockGrace == g, onClick = { onLockGrace(g) },
                            shape = SegmentedButtonDefaults.itemShape(i, LockGrace.entries.size),
                            icon = {},
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        ) { Text(g.label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            ToggleRow(
                Icons.Rounded.Screenshot, "Allow screenshots",
                if (allowScreenshots) "Turn off to also hide MomoBook in recent apps" else "Screenshots and recent-apps preview are blocked",
                allowScreenshots, onAllowScreenshots,
            )
            Spacer(Modifier.height(8.dp))
            InfoRow(Icons.Rounded.Lock, "Stored only on this phone", "Your journal is never uploaded anywhere")
        }

        SectionCard(title = "Your data", subtitle = "$entryCount journal entries · $walletCount wallet${if (walletCount == 1) "" else "s"}") {
            ActionRow(Icons.Rounded.Sync, "Scan SMS inbox now", "Import new mobile money and bank SMS", onSync, trailing = {
                if (syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            })
            ActionRow(Icons.Rounded.ContentPaste, "Add from SMS text", "Paste a mobile money or bank SMS", onPaste)
            ActionRow(Icons.Rounded.IosShare, "Export journal", "Share all entries as a CSV spreadsheet", onExport)
        }

        SectionCard(title = "About") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MomoLogo(size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("MomoBook", style = MaterialTheme.typography.titleMedium)
                    Text("Version $version", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Offline journal and money tracker, built from your messages. Deposits, spending, fees and insights, all on your " +
                    "phone. Each message is copied into the journal so your history stays even if the SMS is deleted.\n\n" +
                    "Currently reads mobile money and bank SMS from Uganda, with more to come.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            ActionRow(Icons.Rounded.Policy, "Privacy policy", "How MomoBook handles your data", { openUrl(PRIVACY_URL) })
            ActionRow(Icons.Rounded.Language, "Built by $AUTHOR", AUTHOR_SITE, { openUrl("https://$AUTHOR_SITE") })
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun IconBadge(icon: ImageVector, tint: Color) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = .14f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    body: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing() else Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
