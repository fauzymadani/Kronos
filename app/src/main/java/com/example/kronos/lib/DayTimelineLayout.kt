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

private const val DAY_START_MINUTE = 0
private const val DAY_END_MINUTE = 24 * 60
private const val DEFAULT_SCROLL_MINUTE = 7 * 60

data class DayTimelineLayout(
    val firstMinute: Int = DAY_START_MINUTE,
    val lastMinute: Int = DAY_END_MINUTE,
    val blocks: List<TimelineBlock>
) {
    val durationMinutes: Int get() = lastMinute - firstMinute
}

// The axis always covers 00:00 to 24:00, so the day scrolls like a real clock.
fun calculateDayTimelineLayout(
    sessions: List<ClassSessionWithSubject>
): DayTimelineLayout {
    if (sessions.isEmpty()) {
        return DayTimelineLayout(blocks = emptyList())
    }

    val intervals = sessions.map { session ->
        val start = LocalTime.parse(session.startTime).toSecondOfDay() / 60
        var end = LocalTime.parse(session.endTime).toSecondOfDay() / 60
        if (end <= start) end += 24 * 60
        Interval(session, start, end)
    }.sortedWith(compareBy<Interval> { it.start }.thenByDescending { it.end })

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
    return DayTimelineLayout(blocks = result)
}

fun initialScrollMinutes(
    classStartMinutes: List<Int>,
    isToday: Boolean,
    nowMinutes: Int
): Int {
    if (isToday) return max(0, nowMinutes - 60)
    val earliest = classStartMinutes.minOrNull() ?: return DEFAULT_SCROLL_MINUTE
    return max(0, earliest - 60)
}

private data class Interval(
    val session: ClassSessionWithSubject,
    val start: Int,
    val end: Int
)
