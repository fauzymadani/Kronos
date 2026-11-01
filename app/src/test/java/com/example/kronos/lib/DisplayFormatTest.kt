package com.example.kronos.lib

import com.example.kronos.data.FirstDayOfWeek
import com.example.kronos.data.TimeFormat
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayFormatTest {
    @Test
    fun formatsTimeInBothModes() {
        Locale.setDefault(Locale.US)
        assertEquals("13:05", formatDisplayTime(LocalTime.of(13, 5), TimeFormat.HOUR_24))
        assertEquals("1:05 PM", formatDisplayTime(LocalTime.of(13, 5), TimeFormat.HOUR_12))
    }

    @Test
    fun ordersDaysFromConfiguredFirstDay() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), orderedIsoDays(FirstDayOfWeek.MONDAY))
        assertEquals(listOf(7, 1, 2, 3, 4, 5, 6), orderedIsoDays(FirstDayOfWeek.SUNDAY))
        assertEquals(0, dayColumnIndex(1, FirstDayOfWeek.MONDAY))
        assertEquals(6, dayColumnIndex(7, FirstDayOfWeek.MONDAY))
        assertEquals(0, dayColumnIndex(7, FirstDayOfWeek.SUNDAY))
        assertEquals(1, dayColumnIndex(1, FirstDayOfWeek.SUNDAY))
        assertEquals(6, dayColumnIndex(6, FirstDayOfWeek.SUNDAY))
    }

    @Test
    fun calculatesCalendarAndWeekStarts() {
        val date = LocalDate.of(2026, 11, 1)
        assertEquals(6, calendarLeadingDays(date, FirstDayOfWeek.MONDAY))
        assertEquals(0, calendarLeadingDays(date, FirstDayOfWeek.SUNDAY))
        assertEquals(date, startOfDisplayedWeek(date, FirstDayOfWeek.SUNDAY))
        assertEquals(date.minusDays(6), startOfDisplayedWeek(date, FirstDayOfWeek.MONDAY))
    }
}
