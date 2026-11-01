package com.example.kronos

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.example.kronos.data.SettingsStore
import com.example.kronos.data.ThemeMode
import com.example.kronos.notifications.ClassReminderScheduler
import com.example.kronos.notifications.TaskExamReminderScheduler
import com.example.kronos.notifications.TaskExamReminderReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
            val sound = Uri.parse("android.resource://$packageName/${R.raw.notification_7}")
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val notificationManager = getSystemService(NotificationManager::class.java)
            listOf(
                ClassReminderScheduler.LEGACY_CHANNEL_ID,
                TaskExamReminderReceiver.LEGACY_TASK_CHANNEL_ID,
                TaskExamReminderReceiver.LEGACY_EXAM_CHANNEL_ID
            ).forEach(notificationManager::deleteNotificationChannel)
            listOf(
                ClassReminderScheduler.CHANNEL_ID to ClassReminderScheduler.CHANNEL_NAME,
                TaskExamReminderReceiver.TASK_CHANNEL_ID to getString(R.string.task_reminder_channel),
                TaskExamReminderReceiver.EXAM_CHANNEL_ID to getString(R.string.exam_reminder_channel)
            ).forEach { (id, name) ->
                notificationManager.createNotificationChannel(
                    NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                        setSound(sound, audioAttributes)
                    }
                )
            }
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                TaskExamReminderScheduler(this@KronosApplication).rescheduleAll()
            }
        }
    }
}
