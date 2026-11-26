package com.momobook

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.momobook.data.Prefs
import com.momobook.data.ThemeMode
import com.momobook.security.AppLock
import com.momobook.ui.MainViewModel
import com.momobook.ui.MomoRoot
import com.momobook.ui.screens.LockScreen
import com.momobook.ui.theme.MomoTheme

// FragmentActivity (a ComponentActivity) is required by BiometricPrompt.
class MainActivity : FragmentActivity() {
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        AppLock.init(this)
        applyWindowPrivacy()
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            val mode by vm.theme.collectAsStateWithLifecycle()
            val appLock by vm.appLock.collectAsStateWithLifecycle()
            val allowScreenshots by vm.allowScreenshots.collectAsStateWithLifecycle()
            val locked by AppLock.locked.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LaunchedEffect(appLock, allowScreenshots) { applyWindowPrivacy() }
            // Keeps tab/scroll state across a lock, while drawing nothing of the app behind the lock screen.
            val saved = rememberSaveableStateHolder()
            MomoTheme(dark = dark) {
                if (locked) LockScreen() else saved.SaveableStateProvider("app") { MomoRoot(vm) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppLock.onStart(this)
    }

    override fun onStop() {
        AppLock.onStop(isChangingConfigurations)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        applyWindowPrivacy()
    }

    override fun onPause() {
        // Android 12 and older can't blank just the recent-apps image, so mark the window
        // secure while we're leaving; onResume lifts it again so screenshots keep working.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && prefs.appLock) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        super.onPause()
    }

    /** FLAG_SECURE when screenshots are off; with app lock on, blank the recent-apps preview. */
    private fun applyWindowPrivacy() {
        if (prefs.allowScreenshots) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!prefs.appLock)
    }
}
