package com.example.kronos.lib

import com.example.kronos.data.local.ClassSessionWithSubject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DayTimelineLayoutTest {
    @Test
    fun axisAlwaysCoversTheFullDay() {
        val layout = calculateDayTimelineLayout(listOf(session(13, 14)))
        assertEquals(0, layout.firstMinute)
        assertEquals(24 * 60, layout.lastMinute)
        assertEquals(24 * 60, layout.durationMinutes)
    }

    @Test
    fun emptyDayStillCoversTheFullDay() {
        val layout = calculateDayTimelineLayout(emptyList())
        assertEquals(24 * 60, layout.durationMinutes)
        assertTrue(layout.blocks.isEmpty())
    }

    @Test
    fun singleClassGetsOneColumn() {
        val layout = calculateDayTimelineLayout(listOf(session(9, 10)))
        assertEquals(0, layout.blocks.single().column)
        assertEquals(1, layout.blocks.single().columnCount)
    }

    @Test
    fun backToBackClassesShareAColumn() {
        val layout = calculateDayTimelineLayout(listOf(session(9, 10), session(10, 11)))
        assertTrue(layout.blocks.all { it.columnCount == 1 })
    }

    @Test
    fun overlappingClassesSplitIntoColumns() {
        val layout = calculateDayTimelineLayout(listOf(session(9, 11), session(10, 12)))
        assertEquals(setOf(0, 1), layout.blocks.map { it.column }.toSet())
        assertTrue(layout.blocks.all { it.columnCount == 2 })
    }

    @Test
    fun threeOverlappingClassesUseThreeColumns() {
        val layout = calculateDayTimelineLayout(
            listOf(session(9, 12), session(10, 11), session(10, 12))
        )
        assertTrue(layout.blocks.all { it.columnCount == 3 })
    }

    @Test
    fun shortClassKeepsItsDuration() {
        val block = calculateDayTimelineLayout(listOf(session(9, 9, 15))).blocks.single()
        assertEquals(15, block.endMinute - block.startMinute)
    }

    @Test
    fun overnightClassKeepsItsDurationPastMidnight() {
        val block = calculateDayTimelineLayout(listOf(session(23, 1))).blocks.single()
        assertEquals(23 * 60, block.startMinute)
        assertEquals(25 * 60, block.endMinute)
    }

    @Test
    fun noClassesAndNotTodayScrollsToSevenAm() {
        assertEquals(420, initialScrollMinutes(emptyList(), isToday = false, nowMinutes = 0))
    }

    @Test
    fun singleClassAtThirteenScrollsOneHourEarlier() {
        assertEquals(
            12 * 60,
            initialScrollMinutes(listOf(13 * 60), isToday = false, nowMinutes = 0)
        )
    }

    @Test
    fun earlyClassClampsToMidnight() {
        assertEquals(0, initialScrollMinutes(listOf(30), isToday = false, nowMinutes = 0))
    }

    @Test
    fun lateClassKeepsScrollInsideTheDay() {
        assertEquals(
            22 * 60,
            initialScrollMinutes(listOf(23 * 60), isToday = false, nowMinutes = 0)
        )
    }

    @Test
    fun todayFollowsTheCurrentTime() {
        assertEquals(
            13 * 60 + 20,
            initialScrollMinutes(emptyList(), isToday = true, nowMinutes = 14 * 60 + 20)
        )
        assertEquals(0, initialScrollMinutes(listOf(8 * 60), isToday = true, nowMinutes = 20))
    }

    private fun session(startHour: Int, endHour: Int, endMinute: Int = 0) =
        ClassSessionWithSubject(
            id = startHour.toLong() * 100 + endHour,
            subjectId = 1,
            subjectName = "Subject",
            subjectColor = "#3F51B5",
            dayOfWeek = 1,
            startTime = "%02d:00".format(startHour),
            endTime = "%02d:%02d".format(endHour, endMinute),
            room = "Room",
            teacher = null,
            weekNumber = 0,
            notes = null,
            reminderEnabled = false,
            reminderOffsetMinutes = null
        )
}
