package com.example.kronos.data

import android.content.Context
import java.time.LocalDate

class SettingsStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var rotationLength: Int
        get() = preferences.getInt(KEY_ROTATION_LENGTH, 1)
        set(value) {
            require(value in 1..4)
            preferences.edit().putInt(KEY_ROTATION_LENGTH, value).apply()
        }

    var semesterStart: LocalDate
        get() = LocalDate.parse(
            preferences.getString(KEY_SEMESTER_START, LocalDate.now().toString())!!
        )
        set(value) {
            preferences.edit().putString(KEY_SEMESTER_START, value.toString()).apply()
        }

    companion object {
        private const val NAME = "kronos_settings"
        private const val KEY_ROTATION_LENGTH = "rotation_length"
        private const val KEY_SEMESTER_START = "semester_start"
    }
}
