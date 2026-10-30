package com.example.kronos.lib

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekRotationTest {
    private val start = LocalDate.of(2026, 9, 1)

    @Test
    fun firstDayOfSemesterIsWeekOne() {
        assertEquals(1, currentRotationWeek(start, start, 4))
    }

    @Test
    fun weekChangesAtSevenDayBoundary() {
        assertEquals(1, currentRotationWeek(start.plusDays(6), start, 4))
        assertEquals(2, currentRotationWeek(start.plusDays(7), start, 4))
    }

    @Test
    fun datesBeforeStartAreComputedWithFloorDivision() {
        assertEquals(4, currentRotationWeek(start.minusDays(1), start, 4))
    }

    @Test
    fun rotationWrapsAfterConfiguredLength() {
        assertEquals(1, currentRotationWeek(start.plusWeeks(4), start, 4))
        assertEquals(2, currentRotationWeek(start.plusWeeks(5), start, 4))
    }
}
