package com.example.kronos.widget

import com.example.kronos.data.local.ClassSessionWithSubject
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class TimetableWidgetLogicTest {
    private val zone = ZoneId.of("Asia/Bangkok")
    private val date = LocalDateTime.of(2026, 11, 1, 10, 0)
    private val clock = Clock.fixed(date.atZone(zone).toInstant(), zone)

    @Test fun noClassesIsEmpty() {
        assertTrue(buildWidgetContent(emptyList(), null, clock).empty)
    }

    @Test fun allEndedAreDimmedAndNextIsNotHighlighted() {
        val content = buildWidgetContent(listOf(session("08:00", "09:00")), null, clock)
        assertTrue(content.rows.single().ended)
        assertTrue(!content.rows.single().highlighted)
    }

    @Test fun runningClassIsHighlighted() {
        val row = buildWidgetContent(listOf(session("09:00", "11:00")), null, clock).rows.single()
        assertTrue(row.highlighted)
        assertTrue(!row.ended)
    }

    @Test fun laterClassIsHighlightedAsNext() {
        val rows = buildWidgetContent(
            listOf(session("08:00", "09:00"), session("12:00", "13:00")), null, clock
        ).rows
        assertTrue(!rows[0].highlighted)
        assertTrue(rows[1].highlighted)
    }

    @Test fun limitsRowsAndReportsMore() {
        val content = buildWidgetContent((8..14).map { session("%02d:00".format(it), "%02d:30".format(it)) }, null, clock)
        assertEquals(5, content.rows.size)
        assertEquals(2, content.moreCount)
    }

    @Test fun holidayReplacesClasses() {
        val content = buildWidgetContent(listOf(session("09:00", "10:00")), "Holiday", clock)
        assertEquals("Holiday", content.holidayName)
        assertTrue(content.rows.isEmpty())
    }

    @Test fun classEndingExactlyNowIsEnded() {
        val row = buildWidgetContent(listOf(session("09:00", "10:00")), null, clock).rows.single()
        assertTrue(row.ended)
        assertTrue(!row.highlighted)
    }

    @Test fun nextBoundaryAfterLastClassIsMidnight() {
        assertEquals(
            date.toLocalDate().plusDays(1).atStartOfDay(),
            nextWidgetBoundary(listOf(session("08:00", "09:00")), clock)
        )
    }

    private fun session(start: String, end: String) = ClassSessionWithSubject(
        id = start.hashCode().toLong(),
        subjectId = 1,
        subjectName = "Subject",
        subjectColor = "#3F51B5",
        dayOfWeek = 7,
        startTime = start,
        endTime = end,
        room = "Room",
        teacher = null,
        weekNumber = 0,
        notes = null,
        reminderEnabled = false,
        reminderOffsetMinutes = null
    )
}
