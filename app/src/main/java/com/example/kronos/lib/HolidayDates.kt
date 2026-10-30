package com.example.kronos.lib

import java.time.LocalDate

fun isDateInHolidayRange(date: LocalDate, startDate: LocalDate, endDate: LocalDate): Boolean =
    !date.isBefore(startDate) && !date.isAfter(endDate)
