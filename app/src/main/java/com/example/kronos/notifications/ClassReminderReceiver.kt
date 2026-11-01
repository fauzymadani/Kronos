package com.example.kronos.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.kronos.MainActivity
import com.example.kronos.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ClassReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, 0L)
                val occurrence = Instant.ofEpochMilli(
                    intent.getLongExtra(EXTRA_OCCURRENCE, 0L)
                ).atZone(ZoneId.systemDefault()).toLocalDateTime()
                val subject = intent.getStringExtra(EXTRA_SUBJECT)
                    ?: appContext.getString(R.string.class_reminder_title)
                val day = occurrence.dayOfWeek.value
                withContext(Dispatchers.Main) {
                    val openIntent = Intent(appContext, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_REMINDER_DAY, day)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    val contentIntent = PendingIntent.getActivity(
                        appContext,
                        ClassReminderScheduler.requestCode(sessionId),
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val notification = NotificationCompat.Builder(
                        appContext, ClassReminderScheduler.CHANNEL_ID
                    )
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle(appContext.getString(R.string.class_reminder_title))
                        .setContentText(
                            appContext.getString(R.string.class_reminder_text, subject)
                        )
                        .setContentIntent(contentIntent)
                        .setAutoCancel(true)
                        .build()
                    ContextCompat.getSystemService(
                        appContext, NotificationManager::class.java
                    )?.notify(sessionId.toInt(), notification)
                }
                ClassReminderScheduler(appContext).scheduleNextAfter(sessionId, occurrence)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "class_reminder_session_id"
        const val EXTRA_OCCURRENCE = "class_reminder_occurrence"
        const val EXTRA_SUBJECT = "class_reminder_subject"
    }
}
