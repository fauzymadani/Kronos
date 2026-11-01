package com.example.kronos.lib

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class ReminderClass(
    val dayOfWeek: Int,
    val startTime: LocalTime,
    val weekNumber: Int
)

data class HolidayRange(val startDate: LocalDate, val endDate: LocalDate)

data class ClassReminderOccurrence(
    val occurrence: LocalDateTime,
    val reminderTime: LocalDateTime
)

fun nextClassReminder(
    afterOccurrence: LocalDateTime,
    now: LocalDateTime,
    classInfo: ReminderClass,
    semesterStart: LocalDate,
    rotationLength: Int,
    holidays: List<HolidayRange>,
    offsetMinutes: Int
): ClassReminderOccurrence? {
    require(classInfo.dayOfWeek in 1..7)
    require(offsetMinutes >= 0)
    var date = afterOccurrence.toLocalDate()
    repeat(366 * rotationLength + 14) {
        val occurrence = LocalDateTime.of(date, classInfo.startTime)
        val rotationMatches = classInfo.weekNumber == 0 ||
            currentRotationWeek(date, semesterStart, rotationLength) == classInfo.weekNumber
        val holiday = holidays.any { !date.isBefore(it.startDate) && !date.isAfter(it.endDate) }
        if (date.dayOfWeek.value == classInfo.dayOfWeek &&
            rotationMatches &&
            !holiday &&
            occurrence.isAfter(afterOccurrence)
        ) {
            val reminderTime = occurrence.minusMinutes(offsetMinutes.toLong())
            if (reminderTime.isAfter(now)) {
                return ClassReminderOccurrence(occurrence, reminderTime)
            }
        }
        date = date.plusDays(1)
    }
    return null
}
