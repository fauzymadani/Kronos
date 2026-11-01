package com.example.kronos.notifications

import com.example.kronos.data.local.Exam
import com.example.kronos.data.local.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class TaskExamScheduleCoordinatorTest {
    private val zone = ZoneId.of("Asia/Bangkok")
    private val clock = Clock.fixed(
        LocalDateTime.of(2026, 11, 1, 12, 0).atZone(zone).toInstant(), zone
    )
    private val gateway = FakeAlarmGateway()
    private val coordinator = TaskExamScheduleCoordinator(
        gateway, clock, ReminderSettings(examReminderHour = LocalTime.of(9, 0))
    )

    @Test
    fun taskInPastIsNotScheduled() {
        coordinator.saveTask(Task(id = 1, title = "Past", reminderTime = LocalDateTime.of(2026, 11, 1, 11, 0)))
        assertTrue(gateway.active.isEmpty())
    }

    @Test
    fun completingTaskCancelsAndUndoReschedules() {
        val task = Task(id = 1, title = "Task", reminderTime = LocalDateTime.of(2026, 11, 2, 9, 0))
        coordinator.saveTask(task)
        coordinator.saveTask(task.copy(done = true))
        assertTrue(gateway.active.isEmpty())
        coordinator.saveTask(task.copy(done = false))
        assertEquals(1, gateway.active.size)
    }

    @Test
    fun editingTaskTimeReplacesAlarm() {
        coordinator.saveTask(Task(id = 1, title = "Task", reminderTime = LocalDateTime.of(2026, 11, 2, 9, 0)))
        coordinator.saveTask(Task(id = 1, title = "Task", reminderTime = LocalDateTime.of(2026, 11, 2, 10, 0)))
        assertEquals(1, gateway.active.size)
        assertEquals(millis(LocalDateTime.of(2026, 11, 2, 10, 0)), gateway.active.values.single().triggerAtMillis)
    }

    @Test
    fun deletingTaskOrExamCancels() {
        coordinator.saveTask(Task(id = 1, title = "Task", reminderTime = LocalDateTime.of(2026, 11, 2, 9, 0)))
        coordinator.deleteTask(1)
        coordinator.saveExam(Exam(id = 2, title = "Exam", date = LocalDate.of(2026, 11, 3), startTime = LocalTime.of(10, 0)))
        coordinator.cancelExam(2)
        assertTrue(gateway.active.isEmpty())
    }

    @Test
    fun examSchedulesConfiguredLeadsAndSkipsPastOnes() {
        coordinator.saveExam(Exam(id = 2, title = "Exam", date = LocalDate.of(2026, 11, 2), startTime = LocalTime.of(12, 30)))
        assertEquals(1, gateway.active.size)
        assertEquals(
            millis(LocalDateTime.of(2026, 11, 2, 11, 30)),
            gateway.active.values.single().triggerAtMillis
        )
    }

    @Test
    fun requestCodesAreNamespaced() {
        val codes = setOf(
            ClassReminderScheduler.requestCode(5, ReminderItemType.CLASS),
            ClassReminderScheduler.requestCode(5, ReminderItemType.TASK),
            ClassReminderScheduler.requestCode(5, ReminderItemType.EXAM)
        )
        assertEquals(3, codes.size)
    }

    @Test
    fun rescheduleAllPlanRecreatesClassTaskAndExamAlarms() {
        val classCoordinator = ReminderScheduleCoordinator(
            gateway, clock, LocalDate.of(2026, 11, 1), 1, 10
        )
        classCoordinator.rescheduleAll(
            listOf(
                ReminderScheduleClass(
                    5, "Class", true, null,
                    com.example.kronos.lib.ReminderClass(2, LocalTime.of(9, 0), 0)
                )
            ),
            emptyList()
        )
        coordinator.rescheduleTasks(
            listOf(Task(id = 6, title = "Task", reminderTime = LocalDateTime.of(2026, 11, 2, 9, 0)))
        )
        coordinator.rescheduleExams(
            listOf(Exam(id = 7, title = "Exam", date = LocalDate.of(2026, 11, 3)))
        )
        assertEquals(4, gateway.active.size)
    }

    private fun millis(value: LocalDateTime) =
        value.atZone(zone).toInstant().toEpochMilli()

    private class FakeAlarmGateway : AlarmGateway {
        val active = linkedMapOf<Int, AlarmCall>()
        override fun schedule(
            requestCode: Int, triggerAtMillis: Long, itemId: Long,
            occurrenceAtMillis: Long, title: String, channelId: String
        ) {
            active[requestCode] = AlarmCall(triggerAtMillis)
        }
        override fun cancel(requestCode: Int) {
            active.remove(requestCode)
        }
    }

    private data class AlarmCall(val triggerAtMillis: Long)
}
