package com.momobook.data

import android.content.Context

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

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
}
