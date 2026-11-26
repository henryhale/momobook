package com.momobook.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.momobook.data.LockGrace
import com.momobook.data.ThemeMode
import com.momobook.security.AppLock
import com.momobook.ui.components.MomoLogo

private const val AUTHOR = "Henry Hale"
private const val AUTHOR_SITE = "henryhale.github.io"
private const val PRIVACY_URL = "https://henryhale.github.io/momobook/privacy.html"

private enum class Page(val title: String) {
    MAIN("Settings"), APPEARANCE("Appearance"), DATA("Your data"), PRIVACY("Privacy"), SECURITY("Security"), ABOUT("About"),
}

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

    // Re-check on resume: the user may have just set up a screen lock or fingerprint in phone settings.
    var lockAvailable by remember { mutableStateOf(AppLock.canAuthenticate(ctx)) }
    var biometrics by remember { mutableStateOf(AppLock.hasBiometrics(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        lockAvailable = AppLock.canAuthenticate(ctx)
        biometrics = AppLock.hasBiometrics(ctx)
    }

    var page by rememberSaveable { mutableStateOf(Page.MAIN) }
    BackHandler(enabled = page != Page.MAIN) { page = Page.MAIN }

    AnimatedContent(
        page,
        transitionSpec = {
            val forward = targetState != Page.MAIN
            (slideInHorizontally { w -> if (forward) w / 4 else -w / 4 } + fadeIn()) togetherWith
                (slideOutHorizontally { w -> if (forward) -w / 4 else w / 4 } + fadeOut())
        },
        label = "settings",
    ) { p ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (p == Page.MAIN) {
                Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 4.dp))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(x = (-12).dp)) {
                    IconButton(onClick = { page = Page.MAIN }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to settings") }
                    Text(p.title, style = MaterialTheme.typography.titleLarge)
                }
            }
            when (p) {
                Page.MAIN -> {
                    Group({ NavRow(Icons.Outlined.Palette, "Appearance", theme.label) { page = Page.APPEARANCE } })
                    Group({ NavRow(Icons.Outlined.Storage, "Your data", "$entryCount entries") { page = Page.DATA } })
                    Group(
                        { NavRow(Icons.Outlined.Shield, "Privacy") { page = Page.PRIVACY } },
                        { NavRow(Icons.Outlined.Lock, "Security", if (appLock) "On" else "Off") { page = Page.SECURITY } },
                    )
                    Group(
                        { LinkRow(Icons.Outlined.Policy, "Privacy policy") { openUrl(PRIVACY_URL) } },
                        { NavRow(Icons.Outlined.Info, "About MomoBook") { page = Page.ABOUT } },
                    )
                }

                Page.APPEARANCE -> Group(
                    *ThemeMode.entries.map { m ->
                        @Composable {
                            ChoiceRow(
                                when (m) {
                                    ThemeMode.SYSTEM -> Icons.Outlined.PhoneAndroid
                                    ThemeMode.LIGHT -> Icons.Outlined.LightMode
                                    ThemeMode.DARK -> Icons.Outlined.DarkMode
                                },
                                if (m == ThemeMode.SYSTEM) "Same as phone" else m.label, theme == m,
                            ) { onTheme(m) }
                        }
                    }.toTypedArray(),
                )

                Page.DATA -> Group(
                    {
                        ActionRow(Icons.Outlined.Sync, "Scan SMS inbox now", "Import new mobile money and bank SMS", onSync) {
                            if (syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    },
                    { ActionRow(Icons.Outlined.ContentPaste, "Add from SMS text", "Paste a mobile money or bank SMS", onPaste) },
                    { ActionRow(Icons.Outlined.IosShare, "Export journal", "Share all entries as a CSV spreadsheet", onExport) },
                    footer = "$entryCount journal entries · $walletCount wallet${if (walletCount == 1) "" else "s"}. " +
                        "Stored only on this phone and never uploaded.",
                )

                Page.PRIVACY -> {
                    Group(
                        {
                            SwitchRow(Icons.Outlined.VisibilityOff, "Hide balances when app opens",
                                "Balances show as **** until you tap the eye", hideOnLaunch, onHideOnLaunch)
                        },
                        {
                            SwitchRow(Icons.Outlined.Screenshot, "Allow screenshots", null, allowScreenshots, onAllowScreenshots)
                        },
                        footer = if (allowScreenshots) "Turn off screenshots to also hide MomoBook in recent apps."
                        else "Screenshots and the recent-apps preview are blocked.",
                    )
                }

                Page.SECURITY -> {
                    Group(
                        {
                            SwitchRow(
                                if (biometrics) Icons.Outlined.Fingerprint else Icons.Outlined.Lock, "Lock app",
                                if (biometrics) "Fingerprint or screen lock" else "Screen lock",
                                appLock, onAppLock, enabled = lockAvailable || appLock,
                            )
                        },
                        footer = when {
                            !lockAvailable && !appLock -> "Set a screen lock in your phone's settings first."
                            appLock -> "MomoBook asks to unlock when it opens, and is hidden in recent apps."
                            else -> "Ask for your fingerprint or screen lock each time MomoBook opens."
                        },
                    )
                    if (appLock) {
                        Group(
                            *LockGrace.entries.map { g ->
                                @Composable { ChoiceRow(null, g.label, lockGrace == g) { onLockGrace(g) } }
                            }.toTypedArray(),
                            header = "Lock again after leaving the app",
                        )
                    }
                }

                Page.ABOUT -> AboutPage(openUrl)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AboutPage(openUrl: (String) -> Unit) {
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "1.0"
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        MomoLogo(size = 72.dp)
        Spacer(Modifier.height(12.dp))
        Text("MomoBook", style = MaterialTheme.typography.titleLarge)
        Text("Version $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(
            "Offline journal and money tracker, built from your messages. Deposits, spending, fees and insights, all on your " +
                "phone. Each message is copied into the journal so your history stays even if the SMS is deleted.\n\n" +
                "Currently reads mobile money and bank SMS from Uganda, with more to come.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
    Group(
        { LinkRow(Icons.Outlined.Policy, "Privacy policy") { openUrl(PRIVACY_URL) } },
        { LinkRow(Icons.Outlined.Language, "Built by $AUTHOR", AUTHOR_SITE) { openUrl("https://$AUTHOR_SITE") } },
    )
}

/** A rounded block of rows with hairline dividers, with an optional caption above and note underneath. */
@Composable
private fun Group(vararg rows: @Composable () -> Unit, header: String? = null, footer: String? = null) {
    Column {
        if (header != null) Text(
            header, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
        )
        Surface(
            Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column {
                rows.forEachIndexed { i, row ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
                    row()
                }
            }
        }
        if (footer != null) Text(
            footer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
        )
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector?,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        if (icon != null) {
            Icon(icon, null, Modifier.size(22.dp), tint = tint)
            Spacer(Modifier.width(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = tint)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

/** Opens a sub-page. */
@Composable
private fun NavRow(icon: ImageVector, title: String, value: String? = null, onClick: () -> Unit) =
    SettingRow(icon, title, onClick = onClick) {
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }

/** Opens something outside the app. */
@Composable
private fun LinkRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) =
    SettingRow(icon, title, subtitle, onClick) {
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }

@Composable
private fun ActionRow(
    icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) = SettingRow(icon, title, subtitle, onClick, trailing = trailing)

@Composable
private fun SwitchRow(
    icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) = SettingRow(icon, title, subtitle, { onChange(!checked) }, enabled) {
    Spacer(Modifier.width(8.dp))
    Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
}

/** One option of a single choice; the chosen one is ticked. */
@Composable
private fun ChoiceRow(icon: ImageVector?, title: String, selected: Boolean, onClick: () -> Unit) =
    SettingRow(icon, title, onClick = onClick) {
        if (selected) Icon(Icons.Rounded.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
    }
