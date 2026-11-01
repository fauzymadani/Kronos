package com.example.kronos.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

class AlarmManagerGateway(context: Context) : AlarmGateway {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    override fun schedule(
        requestCode: Int,
        triggerAtMillis: Long,
        itemId: Long,
        occurrenceAtMillis: Long,
        title: String,
        channelId: String
    ) {
        val intent = Intent(
            appContext,
            if (channelId == ClassReminderScheduler.CHANNEL_ID) {
                ClassReminderReceiver::class.java
            } else {
                TaskExamReminderReceiver::class.java
            }
        ).apply {
            putExtra(ClassReminderReceiver.EXTRA_OCCURRENCE, occurrenceAtMillis)
            if (channelId == ClassReminderScheduler.CHANNEL_ID) {
                putExtra(ClassReminderReceiver.EXTRA_SESSION_ID, itemId)
                putExtra(ClassReminderReceiver.EXTRA_SUBJECT, title)
            } else {
                putExtra(TaskExamReminderReceiver.EXTRA_ITEM_ID, itemId)
                putExtra(TaskExamReminderReceiver.EXTRA_TITLE, title)
                putExtra(TaskExamReminderReceiver.EXTRA_CHANNEL_ID, channelId)
            }
        }
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            PendingIntent.getBroadcast(
                appContext,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    override fun cancel(requestCode: Int) {
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                appContext,
                requestCode,
                Intent(
                    appContext,
                    if (requestCode >= ClassReminderScheduler.TASK_REQUEST_OFFSET) {
                        TaskExamReminderReceiver::class.java
                    } else {
                        ClassReminderReceiver::class.java
                    }
                ),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    override fun scheduleWidget(requestCode: Int, triggerAtMillis: Long) {
        val intent = Intent(appContext, com.example.kronos.widget.TimetableWidgetProvider::class.java)
            .setAction(com.example.kronos.widget.TimetableWidgetProvider.ACTION_ALARM)
        alarmManager.set(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            PendingIntent.getBroadcast(
                appContext,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    override fun cancelWidget(requestCode: Int) {
        alarmManager.cancel(
            PendingIntent.getBroadcast(
                appContext,
                requestCode,
                Intent(appContext, com.example.kronos.widget.TimetableWidgetProvider::class.java)
                    .setAction(com.example.kronos.widget.TimetableWidgetProvider.ACTION_ALARM),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }
}
