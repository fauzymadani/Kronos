package com.example.kronos.data

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate
import java.time.LocalTime

data class ClassPeriod(
    val name: String,
    val start: LocalTime,
    val end: LocalTime
)

class SettingsStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var rotationLength: Int
        get() = preferences.getInt(KEY_ROTATION_LENGTH, 1)
        set(value) {
            require(value in 1..4)
            preferences.edit { putInt(KEY_ROTATION_LENGTH, value) }
        }

    var semesterStart: LocalDate
        get() = LocalDate.parse(
            preferences.getString(KEY_SEMESTER_START, LocalDate.now().toString())!!
        )
        set(value) {
            preferences.edit { putString(KEY_SEMESTER_START, value.toString()) }
        }

    var periods: List<ClassPeriod>
        get() = preferences.getString(KEY_PERIODS, null)
            ?.split('|')
            ?.mapNotNull { encoded ->
                val parts = encoded.split('~')
                if (parts.size != 3) return@mapNotNull null
                runCatching {
                    ClassPeriod(parts[0], LocalTime.parse(parts[1]), LocalTime.parse(parts[2]))
                }.getOrNull()
            }
            .orEmpty()
        set(value) {
            val encoded = value.joinToString("|") {
                "${it.name.replace("~", "").replace("|", "")}~${it.start}~${it.end}"
            }
            preferences.edit { putString(KEY_PERIODS, encoded) }
        }

    var themeMode: ThemeMode
        get() = ThemeMode.fromStoredValue(preferences.getString(KEY_THEME_MODE, null))
        set(value) = preferences.edit { putString(KEY_THEME_MODE, value.value) }

    var pureBlack: Boolean
        get() = preferences.getBoolean(KEY_PURE_BLACK, false)
        set(value) = preferences.edit { putBoolean(KEY_PURE_BLACK, value) }

    companion object {
        private const val NAME = "kronos_settings"
        private const val KEY_ROTATION_LENGTH = "rotation_length"
        private const val KEY_SEMESTER_START = "semester_start"
        private const val KEY_PERIODS = "class_periods"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PURE_BLACK = "pure_black"
    }
}

enum class ThemeMode(val value: String) {
    LIGHT("light"),
    DARK("dark"),
    AUTO_BATTERY("auto_battery");

    companion object {
        fun fromStoredValue(value: String?): ThemeMode =
            entries.firstOrNull { it.value == value } ?: AUTO_BATTERY
    }
}
