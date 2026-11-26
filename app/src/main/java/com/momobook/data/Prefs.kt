package com.momobook.data

import android.content.Context

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

/** How long the app may sit in the background before app lock asks again. */
enum class LockGrace(val label: String, val millis: Long) {
    IMMEDIATE("Instantly", 0), SEC_30("30 sec", 30_000), MIN_1("1 min", 60_000), MIN_5("5 min", 300_000),
}

/** Small user settings, stored on the device. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var theme: ThemeMode
        get() = runCatching { ThemeMode.valueOf(sp.getString("theme", null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
        set(v) = sp.edit().putString("theme", v.name).apply()

    /** Balances start hidden ("****") each time the app opens. */
    var hideBalancesOnLaunch: Boolean
        get() = sp.getBoolean("hide_on_launch", true)
        set(v) = sp.edit().putBoolean("hide_on_launch", v).apply()

    /** Ask for fingerprint / screen lock before showing the app. */
    var appLock: Boolean
        get() = sp.getBoolean("app_lock", false)
        set(v) = sp.edit().putBoolean("app_lock", v).apply()

    var lockGrace: LockGrace
        get() = runCatching { LockGrace.valueOf(sp.getString("lock_grace", null) ?: "SEC_30") }.getOrDefault(LockGrace.SEC_30)
        set(v) = sp.edit().putString("lock_grace", v.name).apply()

    /** When off, the window is marked secure: no screenshots, and blank in recent apps. */
    var allowScreenshots: Boolean
        get() = sp.getBoolean("allow_screenshots", true)
        set(v) = sp.edit().putBoolean("allow_screenshots", v).apply()
}
