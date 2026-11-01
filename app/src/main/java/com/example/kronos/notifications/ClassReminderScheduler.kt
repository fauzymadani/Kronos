package com.example.kronos.notifications

import android.content.Context
import com.example.kronos.data.SettingsStore
import com.example.kronos.data.local.ClassSession
import com.example.kronos.data.local.ClassSessionWithSubject
import com.example.kronos.data.local.DatabaseProvider
import com.example.kronos.data.local.Holiday
import com.example.kronos.lib.HolidayRange
import com.example.kronos.lib.ReminderClass
import java.time.Clock
import java.time.LocalDateTime
import java.time.LocalTime

class ClassReminderScheduler(
    context: Context,
    clock: Clock = Clock.systemDefaultZone()
) {
    private val appContext = context.applicationContext
    private val clock = clock
    private val gateway: AlarmGateway = AlarmManagerGateway(appContext)

    suspend fun rescheduleAll() {
        val database = DatabaseProvider.get(appContext)
        val settings = SettingsStore(appContext)
        ReminderScheduleCoordinator(
            gateway,
            clock,
            settings.semesterStart,
            settings.rotationLength,
            settings.defaultReminderOffsetMinutes,
            settings.classRemindersEnabled
        ).rescheduleAll(
            database.classSessionDao().getAllWithSubjects().map(::toScheduleClass),
            database.holidayDao().getAll().map(::toHolidayRange)
        )
    }

    suspend fun rescheduleClass(sessionId: Long) {
        val database = DatabaseProvider.get(appContext)
        val settings = SettingsStore(appContext)
        val coordinator = coordinator(settings)
        val session = database.classSessionDao().getById(sessionId)
        if (session == null) {
            coordinator.deleteClass(sessionId)
            return
        }
        coordinator.saveClass(
            toScheduleClass(session, database.subjectDao().findById(session.subjectId)?.name.orEmpty()),
            database.holidayDao().getAll().map(::toHolidayRange)
        )
    }

    suspend fun scheduleNextAfter(sessionId: Long, firedOccurrence: LocalDateTime) {
        val database = DatabaseProvider.get(appContext)
        val settings = SettingsStore(appContext)
        val coordinator = coordinator(settings)
        val session = database.classSessionDao().getById(sessionId)
        if (session == null) {
            coordinator.deleteClass(sessionId)
            return
        }
        coordinator.scheduleNextAfter(
            toScheduleClass(
                session,
                database.subjectDao().findById(session.subjectId)?.name.orEmpty()
            ),
            firedOccurrence,
            database.holidayDao().getAll().map(::toHolidayRange)
        )
    }

    fun cancel(sessionId: Long) {
        gateway.cancel(requestCode(sessionId))
    }

    private fun coordinator(settings: SettingsStore) = ReminderScheduleCoordinator(
        gateway,
        clock,
        settings.semesterStart,
        settings.rotationLength,
        settings.defaultReminderOffsetMinutes,
        settings.classRemindersEnabled
    )

    private fun toScheduleClass(session: ClassSessionWithSubject) =
        toScheduleClass(
            session.id,
            session.subjectName,
            session.reminderEnabled,
            session.reminderOffsetMinutes,
            session.dayOfWeek,
            LocalTime.parse(session.startTime),
            session.weekNumber
        )

    private fun toScheduleClass(session: ClassSession, subject: String) =
        toScheduleClass(
            session.id,
            subject,
            session.reminderEnabled,
            session.reminderOffsetMinutes,
            session.dayOfWeek,
            session.startTime,
            session.weekNumber
        )

    private fun toScheduleClass(
        id: Long,
        subject: String,
        reminderEnabled: Boolean,
        reminderOffsetMinutes: Int?,
        dayOfWeek: Int,
        startTime: LocalTime,
        weekNumber: Int
    ) = ReminderScheduleClass(
        id = id,
        subject = subject,
        reminderEnabled = reminderEnabled,
        reminderOffsetMinutes = reminderOffsetMinutes,
        reminderClass = ReminderClass(dayOfWeek, startTime, weekNumber)
    )

    private fun toHolidayRange(holiday: Holiday) =
        HolidayRange(holiday.startDate, holiday.endDate)

    companion object {
        const val CHANNEL_ID = "class_reminders"
        const val CHANNEL_NAME = "Class reminders"
        const val TASK_REQUEST_OFFSET = 600_000_000
        const val EXAM_REQUEST_OFFSET = 1_200_000_000

        fun requestCode(sessionId: Long): Int =
            requestCode(sessionId, ReminderItemType.CLASS)

        fun requestCode(itemId: Long, type: ReminderItemType): Int =
            type.requestOffset + ((itemId xor (itemId ushr 32)).toInt() and 0x1fffffff)
    }
}
