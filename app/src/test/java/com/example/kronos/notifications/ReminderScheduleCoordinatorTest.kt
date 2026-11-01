package com.example.kronos.notifications

import com.example.kronos.lib.HolidayRange
import com.example.kronos.lib.ReminderClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderScheduleCoordinatorTest {
    private val zone = ZoneId.of("Asia/Bangkok")
    private val now = LocalDateTime.of(2026, 11, 2, 8, 55)
    private val clock = Clock.fixed(now.atZone(zone).toInstant(), zone)
    private val gateway = FakeAlarmGateway()
    private val coordinator = ReminderScheduleCoordinator(
        gateway = gateway,
        clock = clock,
        semesterStart = LocalDate.of(2026, 11, 2),
        rotationLength = 1,
        defaultOffsetMinutes = 10
    )

    @Test
    fun pastReminderIsSkippedToNextValidOccurrence() {
        coordinator.saveClass(classInfo(), emptyList())

        assertEquals(
            millis(LocalDateTime.of(2026, 11, 9, 8, 50)),
            gateway.single().triggerAtMillis
        )
    }

    @Test
    fun savingEnabledClassSchedulesExactlyOneAlarm() {
        coordinator.saveClass(classInfo(), emptyList())

        assertEquals(1, gateway.active.size)
        assertEquals(ClassReminderScheduler.requestCode(42), gateway.single().requestCode)
    }

    @Test
    fun turningReminderOffCancelsAlarm() {
        coordinator.saveClass(classInfo(), emptyList())
        coordinator.saveClass(classInfo(reminderEnabled = false), emptyList())

        assertTrue(gateway.active.isEmpty())
        assertEquals(
            listOf(ClassReminderScheduler.requestCode(42), ClassReminderScheduler.requestCode(42)),
            gateway.cancelled
        )
    }

    @Test
    fun deletingClassCancelsAlarm() {
        coordinator.saveClass(classInfo(), emptyList())
        coordinator.deleteClass(42)

        assertTrue(gateway.active.isEmpty())
        assertEquals(
            listOf(ClassReminderScheduler.requestCode(42), ClassReminderScheduler.requestCode(42)),
            gateway.cancelled
        )
    }

    @Test
    fun editingClassTimeReplacesOldAlarmWithOneUsingSameRequestCode() {
        coordinator.saveClass(classInfo(), emptyList())
        coordinator.saveClass(
            classInfo(startTime = LocalTime.of(10, 0)),
            emptyList()
        )

        assertEquals(1, gateway.active.size)
        assertEquals(ClassReminderScheduler.requestCode(42), gateway.single().requestCode)
        assertEquals(millis(LocalDateTime.of(2026, 11, 2, 9, 50)), gateway.single().triggerAtMillis)
        assertEquals(2, gateway.cancelled.size)
    }

    @Test
    fun rescheduleAllRecreatesAllEnabledAlarms() {
        val second = classInfo(id = 43, startTime = LocalTime.of(10, 0))
        coordinator.rescheduleAll(
            sessions = listOf(classInfo(), second, classInfo(id = 44, reminderEnabled = false)),
            holidays = emptyList()
        )

        assertEquals(setOf(42L, 43L).map(ClassReminderScheduler::requestCode).toSet(),
            gateway.active.keys)
        assertEquals(3, gateway.cancelled.size)
    }

    @Test
    fun holidayOnNextOccurrenceIsSkipped() {
        coordinator.saveClass(
            classInfo(),
            listOf(HolidayRange(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 9)))
        )

        assertEquals(
            millis(LocalDateTime.of(2026, 11, 16, 8, 50)),
            gateway.single().triggerAtMillis
        )
    }

    @Test
    fun afterAlarmFiresNextAlarmIsStrictlyAfterFiredOccurrence() {
        coordinator.scheduleNextAfter(
            session = classInfo(),
            firedOccurrence = LocalDateTime.of(2026, 11, 2, 9, 0),
            holidays = emptyList()
        )

        assertEquals(
            millis(LocalDateTime.of(2026, 11, 9, 9, 0)),
            gateway.single().occurrenceAtMillis
        )
    }

    private fun classInfo(
        id: Long = 42,
        startTime: LocalTime = LocalTime.of(9, 0),
        reminderEnabled: Boolean = true
    ) = ReminderScheduleClass(
        id = id,
        subject = "Mathematics",
        reminderEnabled = reminderEnabled,
        reminderOffsetMinutes = null,
        reminderClass = ReminderClass(1, startTime, 0)
    )

    private fun millis(value: LocalDateTime): Long =
        value.atZone(zone).toInstant().toEpochMilli()

    private class FakeAlarmGateway : AlarmGateway {
        val active = linkedMapOf<Int, AlarmCall>()
        val cancelled = mutableListOf<Int>()

        override fun schedule(
            requestCode: Int,
            triggerAtMillis: Long,
            itemId: Long,
            occurrenceAtMillis: Long,
            title: String,
            channelId: String
        ) {
            active[requestCode] = AlarmCall(
                requestCode,
                triggerAtMillis,
                itemId,
                occurrenceAtMillis,
                title
            )
        }

        override fun cancel(requestCode: Int) {
            cancelled += requestCode
            active.remove(requestCode)
        }

        fun single(): AlarmCall = active.values.single()
    }

    private data class AlarmCall(
        val requestCode: Int,
        val triggerAtMillis: Long,
        val sessionId: Long,
        val occurrenceAtMillis: Long,
        val subject: String
    )
}
