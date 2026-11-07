package com.example.kronos.lib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskStatusTest {
    private val today = LocalDate.of(2026, 11, 7)

    private fun row(
        dueDate: LocalDate? = null,
        status: TaskStatus = TaskStatus.NOT_STARTED,
        priority: Int = 0,
        subject: String? = null,
        completedAt: Long? = null
    ) = TaskListRow(dueDate, status, priority, subject, completedAt)

    @Test
    fun codesAreStableAndUnknownFallsBack() {
        assertEquals(
            listOf(0, 1, 2, 3, 4),
            TaskStatus.entries.map { it.code }
        )
        assertEquals(TaskStatus.NOT_STARTED, TaskStatus.fromCode(99))
        assertEquals(TaskStatus.DONE, TaskStatus.fromCode(4))
        assertEquals(AssignmentType.INDIVIDUAL, AssignmentType.fromCode(-1))
        assertEquals(AssignmentType.GROUP, AssignmentType.fromCode(1))
    }

    @Test
    fun enteringDoneStampsCompletedAt() {
        val next = applyStatusChange(TaskState(TaskStatus.IN_PROGRESS), TaskStatus.DONE, 1_000L)
        assertEquals(TaskStatus.DONE, next.status)
        assertEquals(1_000L, next.completedAt)
        assertTrue(next.done)
    }

    @Test
    fun editingAnAlreadyDoneTaskKeepsCompletedAt() {
        val next = applyStatusChange(TaskState(TaskStatus.DONE, 1_000L), TaskStatus.DONE, 2_000L)
        assertEquals(TaskStatus.DONE, next.status)
        assertEquals(1_000L, next.completedAt)
        assertTrue(next.done)
    }

    @Test
    fun leavingDoneClearsCompletedAt() {
        val next = applyStatusChange(TaskState(TaskStatus.DONE, 1_000L), TaskStatus.REVIEW, 2_000L)
        assertEquals(TaskStatus.REVIEW, next.status)
        assertNull(next.completedAt)
        assertFalse(next.done)
    }

    @Test
    fun doneFlagAlwaysFollowsStatus() {
        TaskStatus.entries.forEach { status ->
            assertEquals(status == TaskStatus.DONE, TaskState(status, 5L).done)
        }
    }

    @Test
    fun overdueExcludesDoneAndToday() {
        assertTrue(isOverdue(today.minusDays(1), TaskStatus.IN_PROGRESS, today))
        assertFalse(isOverdue(today, TaskStatus.IN_PROGRESS, today))
        assertFalse(isOverdue(today.minusDays(1), TaskStatus.DONE, today))
        assertFalse(isOverdue(null, TaskStatus.NOT_STARTED, today))
    }

    @Test
    fun filtersSelectTheRightTasks() {
        val rows = listOf(
            row(today.minusDays(2), TaskStatus.IN_PROGRESS),
            row(today, TaskStatus.NOT_STARTED),
            row(today.plusDays(1), TaskStatus.REVIEW),
            row(today.plusDays(2), TaskStatus.REVISION),
            row(today.minusDays(3), TaskStatus.DONE)
        )
        fun filtered(filter: TaskFilter) = rows.filter { filter.matches(it, today) }

        assertEquals(rows, filtered(TaskFilter.ALL))
        assertEquals(4, filtered(TaskFilter.ACTIVE).size)
        assertEquals(listOf(rows[0]), filtered(TaskFilter.OVERDUE))
        assertEquals(listOf(rows[1]), filtered(TaskFilter.NOT_STARTED))
        assertEquals(listOf(rows[0]), filtered(TaskFilter.IN_PROGRESS))
        assertEquals(listOf(rows[2]), filtered(TaskFilter.REVIEW))
        assertEquals(listOf(rows[3]), filtered(TaskFilter.REVISION))
        assertEquals(listOf(rows[4]), filtered(TaskFilter.DONE))
    }

    @Test
    fun defaultSortPutsActiveByDueDateThenDoneByCompletion() {
        val late = row(today.plusDays(3), TaskStatus.IN_PROGRESS)
        val noDate = row(null, TaskStatus.IN_PROGRESS)
        val soon = row(today.plusDays(1), TaskStatus.IN_PROGRESS)
        val olderDone = row(null, TaskStatus.DONE, completedAt = 500L)
        val newerDone = row(today.minusDays(5), TaskStatus.DONE, completedAt = 900L)

        val sorted = listOf(olderDone, late, newerDone, noDate, soon)
            .sortedWith(TaskSort.DUE_DATE.comparator())

        assertEquals(listOf(soon, late, noDate, newerDone, olderDone), sorted)
    }

    @Test
    fun otherSortsAreDeterministic() {
        val a = row(today.plusDays(2), TaskStatus.REVISION, priority = 3, subject = "Math")
        val b = row(today.plusDays(1), TaskStatus.DONE, priority = 3, subject = "art", completedAt = 1L)
        val c = row(null, TaskStatus.NOT_STARTED, priority = 1, subject = null)

        assertEquals(
            listOf(b, a, c),
            listOf(c, a, b).sortedWith(TaskSort.PRIORITY.comparator())
        )
        assertEquals(
            listOf(b, a, c),
            listOf(b, a, c).sortedWith(TaskSort.SUBJECT.comparator())
        )
        assertEquals(
            listOf(c, a, b),
            listOf(a, b, c).sortedWith(TaskSort.STATUS.comparator())
        )
    }

    @Test
    fun summaryCountsTotalsOverdueAndPerStatus() {
        val rows = listOf(
            row(today.minusDays(1), TaskStatus.NOT_STARTED),
            row(today.minusDays(1), TaskStatus.IN_PROGRESS),
            row(today, TaskStatus.REVIEW),
            row(null, TaskStatus.REVISION),
            row(today.minusDays(4), TaskStatus.DONE),
            row(null, TaskStatus.DONE)
        )
        val summary = summarize(rows, today)

        assertEquals(6, summary.total)
        assertEquals(2, summary.overdue)
        assertEquals(1, summary.count(TaskStatus.NOT_STARTED))
        assertEquals(1, summary.count(TaskStatus.IN_PROGRESS))
        assertEquals(1, summary.count(TaskStatus.REVIEW))
        assertEquals(1, summary.count(TaskStatus.REVISION))
        assertEquals(2, summary.count(TaskStatus.DONE))
    }
}
