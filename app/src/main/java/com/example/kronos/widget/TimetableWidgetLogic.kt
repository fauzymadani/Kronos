package com.example.kronos.widget

import com.example.kronos.data.local.ClassSessionWithSubject
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class WidgetRow(
    val session: ClassSessionWithSubject,
    val highlighted: Boolean,
    val ended: Boolean
)

data class WidgetContent(
    val rows: List<WidgetRow>,
    val moreCount: Int,
    val holidayName: String?,
    val empty: Boolean
)

fun buildWidgetContent(
    sessions: List<ClassSessionWithSubject>,
    holidayName: String?,
    clock: Clock,
    maxRows: Int = 5
): WidgetContent {
    if (holidayName != null) return WidgetContent(emptyList(), 0, holidayName, false)
    val now = LocalDateTime.now(clock)
    val sorted = sessions.sortedBy { LocalTime.parse(it.startTime) }
    val states = sorted.map { session ->
        val start = LocalDateTime.of(now.toLocalDate(), LocalTime.parse(session.startTime))
        var end = LocalDateTime.of(now.toLocalDate(), LocalTime.parse(session.endTime))
        if (!end.isAfter(start)) end = end.plusDays(1)
        WidgetRow(session, highlighted = !now.isBefore(start) && now.isBefore(end), ended = !end.isAfter(now))
    }
    val highlightedIndex = states.indexOfFirst { it.highlighted }.takeIf { it >= 0 }
        ?: states.indexOfFirst { !it.ended }.takeIf { it >= 0 }
    val rows = states.mapIndexed { index, row -> row.copy(highlighted = index == highlightedIndex) }
        .take(maxRows)
    return WidgetContent(rows, (states.size - rows.size).coerceAtLeast(0), null, states.isEmpty())
}

fun nextWidgetBoundary(
    sessions: List<ClassSessionWithSubject>,
    clock: Clock
): LocalDateTime {
    val now = LocalDateTime.now(clock)
    val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
    val candidates = ArrayList<LocalDateTime>(sessions.size * 2 + 1)
    candidates += midnight
    sessions.forEach { session ->
        val start = LocalDateTime.of(now.toLocalDate(), LocalTime.parse(session.startTime))
        var end = LocalDateTime.of(now.toLocalDate(), LocalTime.parse(session.endTime))
        if (!end.isAfter(start)) end = end.plusDays(1)
        if (start.isAfter(now)) candidates += start
        if (end.isAfter(now)) candidates += end
    }
    return candidates.minOrNull() ?: midnight
}
