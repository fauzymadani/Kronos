package com.example.kronos.lib

import com.example.kronos.data.FirstDayOfWeek
import com.example.kronos.data.local.ExamWithSubject
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExamCalendarTest {
    private val today = LocalDate.of(2026, 11, 10)

    @Test
    fun countdownToday() {
        assertEquals(CountdownKind.TODAY, examCountdown(today, today).kind)
    }

    @Test
    fun countdownTomorrow() {
        assertEquals(CountdownKind.TOMORROW, examCountdown(today, today.plusDays(1)).kind)
    }

    @Test
    fun countdownInFiveDays() {
        val countdown = examCountdown(today, today.plusDays(5))
        assertEquals(CountdownKind.DAYS, countdown.kind)
        assertEquals(5L, countdown.days)
    }

    @Test
    fun countdownYesterday() {
        assertEquals(CountdownKind.PAST, examCountdown(today, today.minusDays(1)).kind)
    }

    @Test
    fun sectionsRespectTheSevenDayBoundary() {
        val groups = groupExams(
            listOf(
                exam("2026-11-10"),
                exam("2026-11-11"),
                exam("2026-11-17"),
                exam("2026-11-18"),
                exam("2026-11-09")
            ),
            today
        )
        assertEquals(
            listOf(ExamSection.TODAY, ExamSection.THIS_WEEK, ExamSection.LATER, ExamSection.PAST),
            groups.map { it.section }
        )
        assertEquals(listOf("2026-11-11", "2026-11-17"), groups[1].exams.map { it.date })
        assertEquals(listOf("2026-11-18"), groups[2].exams.map { it.date })
        assertEquals(listOf("2026-11-09"), groups[3].exams.map { it.date })
    }

    @Test
    fun emptySectionsAreOmitted() {
        assertEquals(
            listOf(ExamSection.LATER),
            groupExams(listOf(exam("2026-11-18")), today).map { it.section }
        )
        assertTrue(groupExams(emptyList(), today).isEmpty())
    }

    @Test
    fun pastSectionIsNewestFirst() {
        val groups = groupExams(
            listOf(exam("2026-11-01"), exam("2026-11-08"), exam("2026-11-05")),
            today
        )
        assertEquals(
            listOf("2026-11-08", "2026-11-05", "2026-11-01"),
            groups.single().exams.map { it.date }
        )
    }

    @Test
    fun sectionsSortByTimeWithinOneDay() {
        val groups = groupExams(
            listOf(exam("2026-11-12", "14:00"), exam("2026-11-12", "09:00")),
            today
        )
        assertEquals(listOf("09:00", "14:00"), groups.single().exams.map { it.startTime })
    }

    @Test
    fun nextExamStartsAtToday() {
        val exams = listOf(exam("2026-11-01"), exam("2026-11-20"), exam("2026-11-10", "09:00"))
        assertEquals("2026-11-10", nextExam(exams, today)?.date)
    }

    @Test
    fun nextExamIsNullWhenEverythingIsPast() {
        assertNull(nextExam(listOf(exam("2026-11-01"), exam("2026-11-09")), today))
        assertNull(nextExam(emptyList(), today))
    }

    @Test
    fun dotsAreCappedAtThreePerDay() {
        val exams = (1..5).map { exam("2026-11-12", id = it.toLong()) }
        val dots = examDotsByDate(exams)
        assertEquals(1, dots.size)
        assertEquals(MAX_DOTS_PER_DAY, dots.getValue(LocalDate.of(2026, 11, 12)).size)
    }

    @Test
    fun dotsUseTheExamDate() {
        val dots = examDotsByDate(
            listOf(exam("2026-11-12"), exam("2026-11-12"), exam("2026-11-13"))
        )
        assertEquals(2, dots.getValue(LocalDate.of(2026, 11, 12)).size)
        assertEquals(1, dots.getValue(LocalDate.of(2026, 11, 13)).size)
    }

    @Test
    fun shortAndLongMonthsAlwaysFillFortyTwoCells() {
        listOf(YearMonth.of(2026, 2), YearMonth.of(2026, 7)).forEach { month ->
            val cells = buildMonthCells(month, FirstDayOfWeek.MONDAY, today, today, emptyMap())
            assertEquals(MONTH_CELL_COUNT, cells.size)
            assertEquals(month.lengthOfMonth(), cells.count { it.inMonth })
        }
    }

    @Test
    fun mondayFirstStartsOnThePreviousMonday() {
        // 1 November 2026 is a Sunday, so a Monday-first grid starts on 26 October.
        val cells = buildMonthCells(
            YearMonth.of(2026, 11), FirstDayOfWeek.MONDAY, today, today, emptyMap()
        )
        assertEquals(LocalDate.of(2026, 10, 26), cells.first().date)
        assertFalse(cells.first().inMonth)
        assertEquals(LocalDate.of(2026, 11, 1), cells[6].date)
        assertTrue(cells[6].inMonth)
    }

    @Test
    fun sundayFirstStartsOnTheFirstOfTheMonth() {
        val cells = buildMonthCells(
            YearMonth.of(2026, 11), FirstDayOfWeek.SUNDAY, today, today, emptyMap()
        )
        assertEquals(LocalDate.of(2026, 11, 1), cells.first().date)
        assertTrue(cells.first().inMonth)
    }

    @Test
    fun todaySelectedAndPastAreFlagged() {
        val selected = LocalDate.of(2026, 11, 12)
        val cells = buildMonthCells(
            YearMonth.of(2026, 11), FirstDayOfWeek.MONDAY, today, selected, emptyMap()
        )
        assertEquals(1, cells.count { it.isToday })
        assertEquals(today, cells.first { it.isToday }.date)
        assertEquals(1, cells.count { it.isSelected })
        assertEquals(selected, cells.first { it.isSelected }.date)
        assertTrue(cells.first { it.date == LocalDate.of(2026, 11, 9) }.isPast)
        assertFalse(cells.first { it.isToday }.isPast)
    }

    @Test
    fun dotsReachTheMatchingCellsOnly() {
        val dots = examDotsByDate(listOf(exam("2026-11-12"), exam("2026-11-12")))
        val cells = buildMonthCells(
            YearMonth.of(2026, 11), FirstDayOfWeek.MONDAY, today, today, dots
        )
        assertEquals(2, cells.first { it.date == LocalDate.of(2026, 11, 12) }.dotColors.size)
        assertEquals(1, cells.count { it.dotColors.isNotEmpty() })
    }

    private fun exam(date: String, time: String? = null, id: Long = 0) = ExamWithSubject(
        id = id,
        title = "Exam",
        subjectId = 1,
        subjectName = "Math",
        subjectColor = "#3F51B5",
        date = date,
        startTime = time,
        endTime = null,
        room = null,
        seat = null,
        notes = null
    )
}
