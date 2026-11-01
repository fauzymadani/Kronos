package com.example.kronos

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.example.kronos.data.SettingsStore
import com.example.kronos.data.ThemeMode
import com.example.kronos.notifications.ClassReminderScheduler
import com.example.kronos.notifications.TaskExamReminderReceiver

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    ClassReminderScheduler.CHANNEL_ID,
                    ClassReminderScheduler.CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    TaskExamReminderReceiver.TASK_CHANNEL_ID,
                    getString(R.string.task_reminder_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    TaskExamReminderReceiver.EXAM_CHANNEL_ID,
                    getString(R.string.exam_reminder_channel),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }
}
