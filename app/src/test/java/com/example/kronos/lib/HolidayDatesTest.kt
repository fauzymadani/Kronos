package com.example.kronos.lib

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HolidayDatesTest {
    @Test
    fun rangeIncludesBothEndpoints() {
        val start = LocalDate.of(2026, 10, 10)
        val end = LocalDate.of(2026, 10, 12)
        assertTrue(isDateInHolidayRange(start, start, end))
        assertTrue(isDateInHolidayRange(end, start, end))
        assertFalse(isDateInHolidayRange(start.minusDays(1), start, end))
        assertFalse(isDateInHolidayRange(end.plusDays(1), start, end))
    }

    @Test
    fun singleDayHolidayOnlyMatchesThatDay() {
        val day = LocalDate.of(2026, 11, 5)
        assertTrue(isDateInHolidayRange(day, day, day))
        assertFalse(isDateInHolidayRange(day.minusDays(1), day, day))
        assertFalse(isDateInHolidayRange(day.plusDays(1), day, day))
    }

    @Test
    fun rangeCanCrossMonthAndYearBoundary() {
        val start = LocalDate.of(2026, 12, 30)
        val end = LocalDate.of(2027, 1, 2)
        assertTrue(isDateInHolidayRange(LocalDate.of(2027, 1, 1), start, end))
        assertFalse(isDateInHolidayRange(LocalDate.of(2027, 1, 3), start, end))
    }
}
