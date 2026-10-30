package com.example.kronos.lib

import java.time.LocalDate
import java.time.temporal.ChronoUnit

fun currentRotationWeek(
    date: LocalDate,
    semesterStart: LocalDate,
    rotationLength: Int
): Int {
    require(rotationLength in 1..4) { "rotationLength must be between 1 and 4" }
    val elapsedWeeks = ChronoUnit.DAYS.between(semesterStart, date).floorDiv(7)
    return Math.floorMod(elapsedWeeks, rotationLength).toInt() + 1
}
