package com.momobook.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.momobook.data.Entry
import com.momobook.security.AppLock
import com.momobook.security.findFragmentActivity
import com.momobook.sms.SmsInbox
import com.momobook.ui.components.BalancePrivacy
import com.momobook.ui.components.LocalBalancePrivacy
import com.momobook.ui.components.LocalWalletLabels
import com.momobook.ui.screens.*

enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val iconSel: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Home", Icons.Outlined.Home, Icons.Rounded.Home),
    JOURNAL("Journal", Icons.Outlined.Receipt, Icons.Rounded.Receipt),
    CALENDAR("Calendar", Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
    INSIGHTS("Insights", Icons.Outlined.Insights, Icons.Rounded.Insights),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MomoRoot(vm: MainViewModel = viewModel()) {
    val ctx = LocalContext.current
    val entries by vm.entries.collectAsStateWithLifecycle()
    val allEntries by vm.allEntries.collectAsStateWithLifecycle()
    val wallets by vm.wallets.collectAsStateWithLifecycle()
    val wallet by vm.wallet.collectAsStateWithLifecycle()
    val walletLabels = remember(wallets) { wallets.associate { it.key to it.label } }
    val balancesHidden by vm.balancesHidden.collectAsStateWithLifecycle()
    val themeMode by vm.theme.collectAsStateWithLifecycle()
    val hideOnLaunch by vm.hideOnLaunch.collectAsStateWithLifecycle()
    val appLock by vm.appLock.collectAsStateWithLifecycle()
    val lockGrace by vm.lockGrace.collectAsStateWithLifecycle()
    val allowScreenshots by vm.allowScreenshots.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()

    var hasPerm by remember { mutableStateOf(SmsInbox.hasPermission(ctx)) }
    var skippedOnboarding by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var selected by remember { mutableStateOf<Entry?>(null) }
    var showPaste by rememberSaveable { mutableStateOf(false) }
    var calendarMonth by rememberSaveable { mutableStateOf(0) }
    var openDay by rememberSaveable { mutableStateOf<Long?>(null) }
    androidx.activity.compose.BackHandler(enabled = openDay != null && tab == Tab.CALENDAR) { openDay = null }
    val snackbar = remember { SnackbarHostState() }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        hasPerm = res[Manifest.permission.READ_SMS] == true
        if (hasPerm) vm.sync()
    }
    val requestPerm = { permLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) }

    // Re-scan quietly whenever the app comes to the foreground.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPerm = SmsInbox.hasPermission(ctx)
        if (hasPerm) vm.sync(quiet = true)
    }
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    // Keep the open detail sheet in sync with edits (e.g. category change).
    LaunchedEffect(allEntries) { selected = selected?.let { s -> allEntries.firstOrNull { it.id == s.id } } }

    if (!loaded) return
    if (!hasPerm && allEntries.isEmpty() && !skippedOnboarding) {
        OnboardingScreen(onGrant = requestPerm, onSkip = { skippedOnboarding = true; showPaste = true })
        if (showPaste) PasteSmsDialog(onDismiss = { showPaste = false }, onSubmit = vm::importPasted)
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { if (tab == t) openDay = null; tab = t },
                        icon = { Icon(if (tab == t) t.iconSel else t.icon, null) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).consumeWindowInsets(pad)) {
            if (wallets.size > 1 && tab != Tab.SETTINGS) WalletSwitcher(wallets, wallet, vm::selectWallet)
            CompositionLocalProvider(
                LocalWalletLabels provides if (wallet == null && wallets.size > 1) walletLabels else emptyMap(),
                LocalBalancePrivacy provides BalancePrivacy(balancesHidden, vm::toggleBalances),
            ) {
            AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { t ->
                when (t) {
                    Tab.HOME -> HomeScreen(
                        entries = entries, syncing = syncing, hasPermission = hasPerm,
                        walletBalances = remember(entries, wallets) {
                            val latest = com.momobook.data.Analytics.latestBalances(entries)
                            wallets.mapNotNull { w -> latest[w.key]?.let { w.label to it.balance!! } }
                        },
                        onSync = { if (hasPerm) vm.sync() else requestPerm() },
                        onOpen = { selected = it }, onSeeAll = { tab = Tab.JOURNAL },
                        onInsights = { tab = Tab.INSIGHTS }, onPaste = { showPaste = true },
                    )
                    Tab.JOURNAL -> JournalScreen(
                        entries = entries, syncing = syncing,
                        onSync = { if (hasPerm) vm.sync() else requestPerm() },
                        onOpen = { selected = it }, onPaste = { showPaste = true },
                        onExport = { vm.exportCsv { ctx.startActivity(it) } },
                    )
                    Tab.CALENDAR -> openDay?.let { d ->
                        DayScreen(
                            day = d, entries = entries,
                            onBack = { openDay = null },
                            onDay = { openDay = it },
                            onOpen = { selected = it },
                        )
                    } ?: CalendarScreen(
                        entries = entries, monthOffset = calendarMonth,
                        onMonthOffset = { calendarMonth = it },
                        onOpenDay = { openDay = it },
                    )
                    Tab.INSIGHTS -> InsightsScreen(entries = entries, onOpen = { selected = it })
                    Tab.SETTINGS -> SettingsScreen(
                        theme = themeMode, onTheme = vm::setTheme,
                        hideOnLaunch = hideOnLaunch, onHideOnLaunch = vm::setHideOnLaunch,
                        appLock = appLock,
                        onAppLock = { on ->
                            val activity = ctx.findFragmentActivity()
                            // Only turn the lock on after a successful unlock, so nobody locks themselves out.
                            if (!on) vm.setAppLock(false)
                            else if (activity != null) AppLock.authenticate(activity, "Turn on app lock") { r ->
                                when (r) {
                                    AppLock.Result.Success -> vm.setAppLock(true)
                                    AppLock.Result.Cancelled -> Unit
                                    is AppLock.Result.Error -> vm.notify(r.message)
                                }
                            }
                        },
                        lockGrace = lockGrace, onLockGrace = vm::setLockGrace,
                        allowScreenshots = allowScreenshots, onAllowScreenshots = vm::setAllowScreenshots,
                        entryCount = allEntries.size, walletCount = wallets.size, syncing = syncing,
                        onSync = { if (hasPerm) vm.sync() else requestPerm() },
                        onPaste = { showPaste = true },
                        onExport = { vm.exportCsv { ctx.startActivity(it) } },
                    )
                }
            }
            }
        }
    }

    selected?.let { e ->
        EntryDetailSheet(
            entry = e,
            walletLabel = walletLabels[e.wallet],
            onDismiss = { selected = null },
            onCategory = { vm.setCategory(e, it) },
            onDelete = { vm.delete(e); selected = null },
        )
    }
    if (showPaste) PasteSmsDialog(onDismiss = { showPaste = false }, onSubmit = vm::importPasted)
}


@Composable
private fun WalletSwitcher(wallets: List<Wallet>, selected: String?, onSelect: (String?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null, onClick = { onSelect(null) },
            label = { Text("All wallets") },
            leadingIcon = { Icon(Icons.Rounded.AccountBalanceWallet, null, Modifier.size(16.dp)) },
            shape = RoundedCornerShape(12.dp),
        )
        wallets.forEach { w ->
            FilterChip(
                selected = selected == w.key, onClick = { onSelect(w.key) },
                label = { Text(w.label) },
                leadingIcon = { Icon(Icons.Rounded.SimCard, null, Modifier.size(16.dp)) },
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}
