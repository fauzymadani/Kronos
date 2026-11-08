package com.example.kronos

import android.os.Bundle
import android.content.res.Configuration
import androidx.core.view.WindowCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.kronos.data.SettingsStore
import com.example.kronos.widget.TimetableWidgetProvider

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val settings = SettingsStore(this)
        val isDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        if (isDark && settings.pureBlack) setTheme(R.style.Theme_Kronos_Amoled)
        super.onCreate(savedInstanceState)
        val overlayRes = accentOverlay(settings.accentColor)
        if (overlayRes != 0) theme.applyStyle(overlayRes, true)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isDark
            isAppearanceLightNavigationBars = !isDark
        }
        setContentView(R.layout.activity_main)
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
            .setupWithNavController(navHost.navController)
    }

    // Purple is the base theme, so it maps to 0 and no overlay is applied.
    private fun accentOverlay(accent: String): Int = when (accent) {
        "indigo" -> R.style.ThemeOverlay_Kronos_Accent_Indigo
        "blue" -> R.style.ThemeOverlay_Kronos_Accent_Blue
        "teal" -> R.style.ThemeOverlay_Kronos_Accent_Teal
        "green" -> R.style.ThemeOverlay_Kronos_Accent_Green
        "orange" -> R.style.ThemeOverlay_Kronos_Accent_Orange
        "rose" -> R.style.ThemeOverlay_Kronos_Accent_Rose
        "graphite" -> R.style.ThemeOverlay_Kronos_Accent_Graphite
        else -> 0
    }

    override fun onResume() {
        super.onResume()
        TimetableWidgetProvider.requestUpdate(applicationContext)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val day = intent.getIntExtra(EXTRA_REMINDER_DAY, 0)
        if (day in 1..7) {
            val navHost = supportFragmentManager.findFragmentById(R.id.nav_host)
                as? NavHostFragment ?: return
            if (navHost.navController.currentDestination?.id != R.id.timetableFragment) {
                navHost.navController.navigate(R.id.timetableFragment)
            }
            (navHost.childFragmentManager.fragments.firstOrNull() as?
                com.example.kronos.ui.timetable.TimetableFragment)?.selectReminderDay(day)
        }
    }

    companion object {
        const val EXTRA_REMINDER_DAY = "reminder_day_of_week"
    }
}