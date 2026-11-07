package com.example.kronos.lib

import com.example.kronos.data.FirstDayOfWeek
import com.example.kronos.data.local.ExamWithSubject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

const val MONTH_CELL_COUNT = 42
const val MAX_DOTS_PER_DAY = 3

private const val THIS_WEEK_DAYS = 7L

/** Sentinel for "this exam has no subject color"; the UI falls back to the theme primary. */
const val NO_EXAM_COLOR = 0

enum class CountdownKind { TODAY, TOMORROW, DAYS, PAST }

data class ExamCountdown(val kind: CountdownKind, val days: Long = 0)

fun examCountdown(today: LocalDate, examDate: LocalDate): ExamCountdown {
    val days = ChronoUnit.DAYS.between(today, examDate)
    return when {
        days < 0 -> ExamCountdown(CountdownKind.PAST)
        days == 0L -> ExamCountdown(CountdownKind.TODAY)
        days == 1L -> ExamCountdown(CountdownKind.TOMORROW)
        else -> ExamCountdown(CountdownKind.DAYS, days)
    }
}

enum class ExamSection { TODAY, THIS_WEEK, LATER, PAST }

data class ExamSectionGroup(val section: ExamSection, val exams: List<ExamWithSubject>)

data class DayCell(
    val date: LocalDate,
    val inMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    val dotColors: List<Int>,
    val isPast: Boolean
)

// Exam dates are stored as ISO strings, so comparing them as text keeps date order.
private val examOrder = compareBy<ExamWithSubject>({ it.date }, { it.startTime.orEmpty() })

fun groupExams(exams: List<ExamWithSubject>, today: LocalDate): List<ExamSectionGroup> {
    val todayIso = today.toString()
    val weekEndIso = today.plusDays(THIS_WEEK_DAYS).toString()
    val todayExams = ArrayList<ExamWithSubject>()
    val weekExams = ArrayList<ExamWithSubject>()
    val laterExams = ArrayList<ExamWithSubject>()
    val pastExams = ArrayList<ExamWithSubject>()
    for (exam in exams) {
        when {
            exam.date == todayIso -> todayExams += exam
            exam.date <= weekEndIso && exam.date > todayIso -> weekExams += exam
            exam.date > weekEndIso -> laterExams += exam
            else -> pastExams += exam
        }
    }
    val groups = ArrayList<ExamSectionGroup>(4)
    if (todayExams.isNotEmpty()) {
        groups += ExamSectionGroup(ExamSection.TODAY, todayExams.sortedWith(examOrder))
    }
    if (weekExams.isNotEmpty()) {
        groups += ExamSectionGroup(ExamSection.THIS_WEEK, weekExams.sortedWith(examOrder))
    }
    if (laterExams.isNotEmpty()) {
        groups += ExamSectionGroup(ExamSection.LATER, laterExams.sortedWith(examOrder))
    }
    if (pastExams.isNotEmpty()) {
        groups += ExamSectionGroup(ExamSection.PAST, pastExams.sortedWith(examOrder.reversed()))
    }
    return groups
}

fun nextExam(exams: List<ExamWithSubject>, today: LocalDate): ExamWithSubject? {
    val todayIso = today.toString()
    var best: ExamWithSubject? = null
    for (exam in exams) {
        if (exam.date < todayIso) continue
        val current = best
        if (current == null || examOrder.compare(exam, current) < 0) best = exam
    }
    return best
}

fun examDotsByDate(exams: List<ExamWithSubject>): Map<LocalDate, List<Int>> {
    val dots = LinkedHashMap<LocalDate, MutableList<Int>>()
    for (exam in exams) {
        val date = LocalDate.parse(exam.date)
        val colors = dots.getOrPut(date) { ArrayList(MAX_DOTS_PER_DAY) }
        if (colors.size < MAX_DOTS_PER_DAY) {
            colors += parseUserColor(exam.subjectColor, NO_EXAM_COLOR)
        }
    }
    return dots
}

fun buildMonthCells(
    month: YearMonth,
    firstDayOfWeek: FirstDayOfWeek,
    today: LocalDate,
    selected: LocalDate,
    dots: Map<LocalDate, List<Int>>
): List<DayCell> {
    val firstOfMonth = month.atDay(1)
    val leadingDays = calendarLeadingDays(firstOfMonth, firstDayOfWeek)
    val start = firstOfMonth.minusDays(leadingDays.toLong())
    val cells = ArrayList<DayCell>(MONTH_CELL_COUNT)
    for (offset in 0 until MONTH_CELL_COUNT) {
        val date = start.plusDays(offset.toLong())
        cells += DayCell(
            date = date,
            inMonth = YearMonth.from(date) == month,
            isToday = date == today,
            isSelected = date == selected,
            dotColors = dots[date].orEmpty(),
            isPast = date.isBefore(today)
        )
    }
    return cells
}

private val monthTitleFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
private val dayTitleFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

fun formatMonthTitle(month: YearMonth): String = month.format(monthTitleFormatter)

fun formatDayTitle(date: LocalDate): String = date.format(dayTitleFormatter)
