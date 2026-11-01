package com.example.kronos.notifications

import com.example.kronos.lib.HolidayRange
import com.example.kronos.lib.ReminderClass
import com.example.kronos.lib.nextClassReminder
import java.time.Clock
import java.time.LocalDateTime

data class ReminderScheduleClass(
    val id: Long,
    val subject: String,
    val reminderEnabled: Boolean,
    val reminderOffsetMinutes: Int?,
    val reminderClass: ReminderClass
)

enum class ReminderItemType(val requestOffset: Int, val channelId: String) {
    CLASS(0, ClassReminderScheduler.CHANNEL_ID),
    TASK(ClassReminderScheduler.TASK_REQUEST_OFFSET, TaskExamReminderReceiver.TASK_CHANNEL_ID),
    EXAM(ClassReminderScheduler.EXAM_REQUEST_OFFSET, TaskExamReminderReceiver.EXAM_CHANNEL_ID)
}

class ReminderScheduleCoordinator(
    private val gateway: AlarmGateway,
    private val clock: Clock,
    private val semesterStart: java.time.LocalDate,
    private val rotationLength: Int,
    private val defaultOffsetMinutes: Int,
    private val classRemindersEnabled: Boolean = true
) {
    fun saveClass(session: ReminderScheduleClass, holidays: List<HolidayRange>) {
        gateway.cancel(ClassReminderScheduler.requestCode(session.id, ReminderItemType.CLASS))
        if (classRemindersEnabled && session.reminderEnabled) {
            schedule(session, holidays, now(), now().minusNanos(1))
        }
    }

    fun deleteClass(sessionId: Long) {
        gateway.cancel(ClassReminderScheduler.requestCode(sessionId, ReminderItemType.CLASS))
    }

    fun rescheduleAll(
        sessions: List<ReminderScheduleClass>,
        holidays: List<HolidayRange>
    ) {
        sessions.forEach { session ->
            gateway.cancel(ClassReminderScheduler.requestCode(session.id, ReminderItemType.CLASS))
            if (classRemindersEnabled && session.reminderEnabled) {
                schedule(session, holidays, now(), now().minusNanos(1))
            }
        }
    }

    fun scheduleNextAfter(
        session: ReminderScheduleClass,
        firedOccurrence: LocalDateTime,
        holidays: List<HolidayRange>
    ) {
        gateway.cancel(ClassReminderScheduler.requestCode(session.id, ReminderItemType.CLASS))
        if (classRemindersEnabled && session.reminderEnabled) {
            schedule(session, holidays, now(), firedOccurrence)
        }
    }

    private fun schedule(
        session: ReminderScheduleClass,
        holidays: List<HolidayRange>,
        now: LocalDateTime,
        afterOccurrence: LocalDateTime
    ) {
        val occurrence = nextClassReminder(
            afterOccurrence = afterOccurrence,
            now = now,
            classInfo = session.reminderClass,
            semesterStart = semesterStart,
            rotationLength = rotationLength,
            holidays = holidays,
            offsetMinutes = session.reminderOffsetMinutes ?: defaultOffsetMinutes
        ) ?: return
        val zone = clock.zone
        gateway.schedule(
            requestCode = ClassReminderScheduler.requestCode(session.id, ReminderItemType.CLASS),
            triggerAtMillis = occurrence.reminderTime.atZone(zone).toInstant().toEpochMilli(),
            itemId = session.id,
            occurrenceAtMillis = occurrence.occurrence.atZone(zone).toInstant().toEpochMilli(),
            title = session.subject,
            channelId = ReminderItemType.CLASS.channelId
        )
    }

    private fun now(): LocalDateTime =
        LocalDateTime.ofInstant(clock.instant(), clock.zone)
}
