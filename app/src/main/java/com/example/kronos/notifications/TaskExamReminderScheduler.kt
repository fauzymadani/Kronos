package com.example.kronos.notifications

import android.content.Context
import com.example.kronos.data.SettingsStore
import com.example.kronos.data.local.DatabaseProvider
import com.example.kronos.data.local.Exam
import com.example.kronos.data.local.Task
import java.time.Clock
import java.time.LocalDateTime
import java.time.LocalTime

class TaskExamReminderScheduler(
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val gateway: AlarmGateway = AlarmManagerGateway(context.applicationContext)
) {
    private val appContext = context.applicationContext

    suspend fun rescheduleAll() {
        val db = DatabaseProvider.get(appContext)
        val settings = SettingsStore(appContext)
        ClassReminderScheduler(appContext, clock).rescheduleAll()
        val coordinator = TaskExamScheduleCoordinator(gateway, clock, settings.reminderSettings())
        coordinator.rescheduleTasks(db.taskDao().getAll())
        coordinator.rescheduleExams(db.examDao().getAll())
    }

    suspend fun saveTask(task: Task) {
        TaskExamScheduleCoordinator(
            gateway, clock, SettingsStore(appContext).reminderSettings()
        ).saveTask(task)
    }

    fun cancelTask(id: Long) =
        gateway.cancel(ClassReminderScheduler.requestCode(id, ReminderItemType.TASK))

    suspend fun saveExam(exam: Exam) {
        TaskExamScheduleCoordinator(
            gateway, clock, SettingsStore(appContext).reminderSettings()
        ).saveExam(exam)
    }

    fun cancelExam(id: Long) {
        TaskExamScheduleCoordinator(
            gateway, clock, SettingsStore(appContext).reminderSettings()
        ).cancelExam(id)
    }
}

data class ReminderSettings(
    val taskRemindersEnabled: Boolean = true,
    val examRemindersEnabled: Boolean = true,
    val examDayBeforeEnabled: Boolean = true,
    val examHourBeforeEnabled: Boolean = true,
    val examReminderHour: LocalTime = LocalTime.of(9, 0)
)

private fun SettingsStore.reminderSettings() = ReminderSettings(
    taskRemindersEnabled = taskRemindersEnabled,
    examRemindersEnabled = examRemindersEnabled,
    examDayBeforeEnabled = examDayBeforeEnabled,
    examHourBeforeEnabled = examHourBeforeEnabled,
    examReminderHour = examReminderHour
)

class TaskExamScheduleCoordinator(
    private val gateway: AlarmGateway,
    private val clock: Clock,
    private val settings: ReminderSettings
) {
    fun saveTask(task: Task) {
        val requestCode = ClassReminderScheduler.requestCode(task.id, ReminderItemType.TASK)
        gateway.cancel(requestCode)
        if (settings.taskRemindersEnabled && !task.done) {
            task.reminderTime?.takeIf { it.isAfter(now()) }?.let {
                gateway.schedule(
                    requestCode, millis(it), task.id, millis(it), task.title,
                    ReminderItemType.TASK.channelId
                )
            }
        }
    }

    fun deleteTask(id: Long) =
        gateway.cancel(ClassReminderScheduler.requestCode(id, ReminderItemType.TASK))

    fun rescheduleTasks(tasks: List<Task>) = tasks.forEach(::saveTask)

    fun saveExam(exam: Exam) {
        cancelExam(exam.id)
        if (!settings.examRemindersEnabled) return
        val examTime = LocalDateTime.of(exam.date, exam.startTime ?: LocalTime.of(9, 0))
        val leads = buildList {
            if (settings.examDayBeforeEnabled) {
                add(examTime.toLocalDate().minusDays(1).atTime(settings.examReminderHour))
            }
            if (settings.examHourBeforeEnabled) add(examTime.minusHours(1))
        }
        leads.forEachIndexed { index, reminder ->
            if (reminder.isAfter(now())) {
                val requestCode = ClassReminderScheduler.requestCode(
                    exam.id * 2 + index, ReminderItemType.EXAM
                )
                gateway.schedule(
                    requestCode, millis(reminder), exam.id, millis(examTime), exam.title,
                    ReminderItemType.EXAM.channelId
                )
            }
        }
    }

    fun cancelExam(id: Long) {
        repeat(2) { index ->
            gateway.cancel(
                ClassReminderScheduler.requestCode(id * 2 + index, ReminderItemType.EXAM)
            )
        }
    }

    fun rescheduleExams(exams: List<Exam>) = exams.forEach(::saveExam)

    private fun now() = LocalDateTime.ofInstant(clock.instant(), clock.zone)
    private fun millis(time: LocalDateTime) =
        time.atZone(clock.zone).toInstant().toEpochMilli()
}
