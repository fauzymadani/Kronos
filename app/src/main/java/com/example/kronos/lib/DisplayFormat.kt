package com.example.kronos.lib

import com.example.kronos.R
import com.example.kronos.data.FirstDayOfWeek
import com.example.kronos.data.TimeFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatDisplayTime(value: LocalTime, format: TimeFormat): String =
    value.format(
        if (format == TimeFormat.HOUR_12) {
            DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        } else {
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        }
    )

fun formatDisplayDate(value: LocalDate): String =
    value.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))

/** Compact "Fri 14 Nov" form used by the task list. */
fun formatShortDate(value: LocalDate): String =
    value.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))

fun statusLabelRes(status: TaskStatus): Int = when (status) {
    TaskStatus.NOT_STARTED -> R.string.status_not_started
    TaskStatus.IN_PROGRESS -> R.string.status_in_progress
    TaskStatus.REVIEW -> R.string.status_review
    TaskStatus.REVISION -> R.string.status_revision
    TaskStatus.DONE -> R.string.done
}

fun assignmentLabelRes(type: AssignmentType): Int = when (type) {
    AssignmentType.INDIVIDUAL -> R.string.assignment_individual
    AssignmentType.GROUP -> R.string.assignment_group
}

fun orderedIsoDays(firstDay: FirstDayOfWeek): List<Int> =
    if (firstDay == FirstDayOfWeek.MONDAY) (1..7).toList() else listOf(7, 1, 2, 3, 4, 5, 6)

fun dayColumnIndex(isoDay: Int, firstDay: FirstDayOfWeek): Int =
    orderedIsoDays(firstDay).indexOf(isoDay).coerceAtLeast(0)

fun calendarLeadingDays(month: LocalDate, firstDay: FirstDayOfWeek): Int {
    val first = month.withDayOfMonth(1).dayOfWeek.value
    return (first - firstDay.isoDay + 7) % 7
}

fun startOfDisplayedWeek(date: LocalDate, firstDay: FirstDayOfWeek): LocalDate {
    val offset = (date.dayOfWeek.value - firstDay.isoDay + 7) % 7
    return date.minusDays(offset.toLong())
}

fun displayWeekdayLabels(firstDay: FirstDayOfWeek): List<String> =
    orderedIsoDays(firstDay).map {
        DayOfWeek.of(it).name.take(3).lowercase().replaceFirstChar { character ->
            character.uppercase()
        }
    }
