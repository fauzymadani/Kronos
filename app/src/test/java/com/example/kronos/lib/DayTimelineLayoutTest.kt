package com.example.kronos.lib

import com.example.kronos.data.local.ClassSessionWithSubject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DayTimelineLayoutTest {
    @Test
    fun singleClassGetsOneColumnAndOneHourMargins() {
        val layout = calculateDayTimelineLayout(listOf(session(9, 10)))
        assertEquals(8 * 60, layout.firstMinute)
        assertEquals(18 * 60, layout.lastMinute)
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
    fun shortClassKeepsItsDurationAndRange() {
        val block = calculateDayTimelineLayout(listOf(session(9, 9, 15))).blocks.single()
        assertEquals(15, block.endMinute - block.startMinute)
    }

    @Test
    fun overnightClassExtendsPastMidnightWithoutClipping() {
        val layout = calculateDayTimelineLayout(listOf(session(23, 1)))
        assertEquals(23 * 60, layout.blocks.single().startMinute)
        assertEquals(25 * 60, layout.blocks.single().endMinute)
        assertTrue(layout.lastMinute >= 26 * 60)
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
