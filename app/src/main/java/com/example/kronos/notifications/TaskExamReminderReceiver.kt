package com.example.kronos.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.kronos.MainActivity
import com.example.kronos.R

class TaskExamReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        try {
            val id = intent.getLongExtra(EXTRA_ITEM_ID, 0L)
            val channel = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: return
            val title = intent.getStringExtra(EXTRA_TITLE)
                ?: context.getString(R.string.reminder_title)
            val notification = NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(context.getString(R.string.reminder_notification_text))
                .setContentIntent(
                    PendingIntent.getActivity(
                        context, id.toInt(), Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java)
                ?.notify(id.toInt(), notification)
        } finally {
            pending.finish()
        }
    }

    companion object {
        const val TASK_CHANNEL_ID = "task_reminders"
        const val EXAM_CHANNEL_ID = "exam_reminders"
        const val EXTRA_ITEM_ID = "reminder_item_id"
        const val EXTRA_TITLE = "reminder_title"
        const val EXTRA_CHANNEL_ID = "reminder_channel_id"
    }
}
