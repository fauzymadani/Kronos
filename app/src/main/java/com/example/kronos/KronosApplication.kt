package com.example.kronos

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.kronos.data.SettingsStore
import com.example.kronos.data.ThemeMode

class KronosApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val settings = SettingsStore(this)
        AppCompatDelegate.setDefaultNightMode(
            when (settings.themeMode) {
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                ThemeMode.AUTO_BATTERY -> AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
            }
        )
    }
}
