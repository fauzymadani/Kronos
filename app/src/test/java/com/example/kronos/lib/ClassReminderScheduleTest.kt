package com.example.kronos.lib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ClassReminderScheduleTest {
    private val semesterStart = LocalDate.of(2026, 11, 2)
    private val now = LocalDateTime.of(2026, 11, 2, 8, 0)

    @Test
    fun schedulesNormalWeeklyClass() {
        val result = schedule(ReminderClass(1, LocalTime.of(9, 0), 0))
        assertEquals(LocalDateTime.of(2026, 11, 2, 8, 50), result?.reminderTime)
    }

    @Test
    fun respectsTwoWeekRotation() {
        val result = schedule(ReminderClass(1, LocalTime.of(9, 0), 2), rotationLength = 2)
        assertEquals(LocalDateTime.of(2026, 11, 9, 8, 50), result?.reminderTime)
    }

    @Test
    fun supportsClassShortlyAfterMidnight() {
        val result = schedule(ReminderClass(2, LocalTime.of(0, 5), 0))
        assertEquals(LocalDateTime.of(2026, 11, 3, 0, 5), result?.occurrence)
    }

    @Test
    fun skipsHolidayDates() {
        val result = nextClassReminder(
            afterOccurrence = LocalDateTime.of(2026, 11, 2, 9, 0),
            now = LocalDateTime.of(2026, 11, 2, 9, 1),
            classInfo = ReminderClass(1, LocalTime.of(9, 0), 0),
            semesterStart = semesterStart,
            rotationLength = 1,
            holidays = listOf(
                HolidayRange(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 9))
            ),
            offsetMinutes = 10
        )
        assertEquals(LocalDateTime.of(2026, 11, 16, 8, 50), result?.reminderTime)
    }

    @Test
    fun skipsReminderAlreadyInThePast() {
        val result = nextClassReminder(
            afterOccurrence = LocalDateTime.of(2026, 11, 1, 0, 0),
            now = LocalDateTime.of(2026, 11, 2, 8, 55),
            classInfo = ReminderClass(1, LocalTime.of(9, 0), 0),
            semesterStart = semesterStart,
            rotationLength = 1,
            holidays = emptyList(),
            offsetMinutes = 10
        )
        assertNotNull(result)
        assertEquals(LocalDateTime.of(2026, 11, 9, 8, 50), result?.reminderTime)
    }

    @Test
    fun offsetCanCrossMidnight() {
        val result = schedule(ReminderClass(2, LocalTime.of(0, 5), 0), offsetMinutes = 10)
        assertEquals(LocalDateTime.of(2026, 11, 2, 23, 55), result?.reminderTime)
    }

    private fun schedule(
        classInfo: ReminderClass,
        rotationLength: Int = 1,
        holidays: List<HolidayRange> = emptyList(),
        offsetMinutes: Int = 10
    ) = nextClassReminder(
        afterOccurrence = now,
        now = now,
        classInfo = classInfo,
        semesterStart = semesterStart,
        rotationLength = rotationLength,
        holidays = holidays,
        offsetMinutes = offsetMinutes
    )
}
