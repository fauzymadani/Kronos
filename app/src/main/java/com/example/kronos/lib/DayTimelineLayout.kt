package com.example.kronos.lib

import com.example.kronos.data.local.ClassSessionWithSubject
import java.time.LocalTime
import kotlin.math.max

data class TimelineBlock(
    val session: ClassSessionWithSubject,
    val startMinute: Int,
    val endMinute: Int,
    val column: Int,
    val columnCount: Int
)

data class DayTimelineLayout(
    val firstMinute: Int,
    val lastMinute: Int,
    val blocks: List<TimelineBlock>
) {
    val durationMinutes: Int get() = lastMinute - firstMinute
}

fun calculateDayTimelineLayout(
    sessions: List<ClassSessionWithSubject>,
    minimumFirstMinute: Int = 7 * 60,
    minimumLastMinute: Int = 18 * 60
): DayTimelineLayout {
    if (sessions.isEmpty()) {
        return DayTimelineLayout(minimumFirstMinute, minimumLastMinute, emptyList())
    }

    val intervals = sessions.map { session ->
        val start = LocalTime.parse(session.startTime).toSecondOfDay() / 60
        var end = LocalTime.parse(session.endTime).toSecondOfDay() / 60
        if (end <= start) end += 24 * 60
        Interval(session, start, end)
    }.sortedWith(compareBy<Interval> { it.start }.thenByDescending { it.end })

    val first = max(minimumFirstMinute, intervals.minOf { it.start } - 60)
    val last = max(minimumLastMinute, intervals.maxOf { it.end } + 60)
    val result = ArrayList<TimelineBlock>(intervals.size)
    var groupStart = 0
    while (groupStart < intervals.size) {
        var groupEnd = groupStart + 1
        var groupLast = intervals[groupStart].end
        while (groupEnd < intervals.size && intervals[groupEnd].start < groupLast) {
            groupLast = max(groupLast, intervals[groupEnd].end)
            groupEnd++
        }

        val columns = ArrayList<Int>(groupEnd - groupStart)
        val columnEnds = ArrayList<Int>()
        var index = groupStart
        while (index < groupEnd) {
            val interval = intervals[index]
            var column = 0
            while (column < columnEnds.size && columnEnds[column] > interval.start) column++
            if (column == columnEnds.size) columnEnds += interval.end
            else columnEnds[column] = interval.end
            columns += column
            index++
        }
        val columnCount = columnEnds.size
        index = groupStart
        while (index < groupEnd) {
            val interval = intervals[index]
            result += TimelineBlock(
                interval.session, interval.start, interval.end, columns[index - groupStart], columnCount
            )
            index++
        }
        groupStart = groupEnd
    }
    return DayTimelineLayout(first, last, result)
}

private data class Interval(
    val session: ClassSessionWithSubject,
    val start: Int,
    val end: Int
)
