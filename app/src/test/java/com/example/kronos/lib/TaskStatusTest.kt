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
        completedAt: Long? = null,
        title: String = ""
    ) = TaskListRow(dueDate, status, priority, completedAt, title)

    private fun sections(tasks: List<TaskListRow>, mode: TaskGroupMode) =
        groupTasks(tasks, today, mode) { it }

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
            row(today.minusDays(3), TaskStatus.DONE)
        )
        fun filtered(filter: TaskFilter) = rows.filter { filter.matches(it, today) }

        assertEquals(rows, filtered(TaskFilter.ALL))
        assertEquals(rows.take(3), filtered(TaskFilter.ACTIVE))
        assertEquals(listOf(rows[0]), filtered(TaskFilter.OVERDUE))
        assertEquals(listOf(rows[3]), filtered(TaskFilter.DONE))
    }

    @Test
    fun dueDateBucketsRespectBoundaries() {
        val yesterday = row(today.minusDays(1), TaskStatus.IN_PROGRESS)
        val dueToday = row(today, TaskStatus.IN_PROGRESS)
        val inOneDay = row(today.plusDays(1), TaskStatus.IN_PROGRESS)
        val inSevenDays = row(today.plusDays(7), TaskStatus.IN_PROGRESS)
        val inEightDays = row(today.plusDays(8), TaskStatus.IN_PROGRESS)
        val noDate = row(null, TaskStatus.IN_PROGRESS)
        val doneYesterday = row(today.minusDays(1), TaskStatus.DONE, completedAt = 5L)

        val grouped = sections(
            listOf(yesterday, dueToday, inOneDay, inSevenDays, inEightDays, noDate, doneYesterday),
            TaskGroupMode.BY_DUE_DATE
        )

        assertEquals(
            listOf(
                TaskSectionKey.OVERDUE,
                TaskSectionKey.TODAY,
                TaskSectionKey.THIS_WEEK,
                TaskSectionKey.LATER,
                TaskSectionKey.NO_DATE,
                TaskSectionKey.DONE
            ),
            grouped.map { it.key }
        )
        assertEquals(listOf(yesterday), grouped[0].tasks)
        assertEquals(listOf(dueToday), grouped[1].tasks)
        assertEquals(listOf(inOneDay, inSevenDays), grouped[2].tasks)
        assertEquals(listOf(inEightDays), grouped[3].tasks)
        assertEquals(listOf(noDate), grouped[4].tasks)
        assertEquals(listOf(doneYesterday), grouped[5].tasks)
    }

    @Test
    fun emptySectionsAreOmitted() {
        val dueToday = row(today, TaskStatus.NOT_STARTED)
        val done = row(null, TaskStatus.DONE, completedAt = 1L)

        val grouped = sections(listOf(dueToday, done), TaskGroupMode.BY_DUE_DATE)

        assertEquals(listOf(TaskSectionKey.TODAY, TaskSectionKey.DONE), grouped.map { it.key })
        assertEquals(listOf(dueToday), grouped[0].tasks)
        assertEquals(listOf(done), grouped[1].tasks)
    }

    @Test
    fun sectionOrderIsDueDateThenPriorityThenTitle() {
        val highLater = row(today, TaskStatus.IN_PROGRESS, priority = 2, title = "b")
        val lowSoon = row(today, TaskStatus.IN_PROGRESS, priority = 0, title = "a")
        val highSoon = row(today, TaskStatus.IN_PROGRESS, priority = 2, title = "a")
        val tomorrow = row(today.plusDays(1), TaskStatus.IN_PROGRESS, priority = 0, title = "z")

        val grouped = sections(listOf(tomorrow, highLater, lowSoon, highSoon), TaskGroupMode.BY_DUE_DATE)

        assertEquals(
            listOf(TaskSectionKey.TODAY, TaskSectionKey.THIS_WEEK),
            grouped.map { it.key }
        )
        assertEquals(listOf(highSoon, highLater, lowSoon), grouped[0].tasks)
        assertEquals(listOf(tomorrow), grouped[1].tasks)
    }

    @Test
    fun doneSectionIsNewestFirst() {
        val older = row(today.minusDays(1), TaskStatus.DONE, completedAt = 100L)
        val newer = row(today.minusDays(3), TaskStatus.DONE, completedAt = 300L)
        val noStamp = row(today.minusDays(2), TaskStatus.DONE)

        val section = sections(listOf(older, noStamp, newer), TaskGroupMode.BY_DUE_DATE).single()

        assertEquals(TaskSectionKey.DONE, section.key)
        assertEquals(listOf(newer, older, noStamp), section.tasks)
    }

    @Test
    fun statusModeUsesFixedSectionOrder() {
        val done = row(today, TaskStatus.DONE, completedAt = 1L)
        val notStarted = row(today, TaskStatus.NOT_STARTED)
        val review = row(today, TaskStatus.REVIEW)
        val revision = row(today, TaskStatus.REVISION)
        val inProgress = row(today, TaskStatus.IN_PROGRESS)

        val grouped = sections(
            listOf(done, notStarted, review, revision, inProgress),
            TaskGroupMode.BY_STATUS
        )

        assertEquals(
            listOf(
                TaskSectionKey.IN_PROGRESS,
                TaskSectionKey.REVISION,
                TaskSectionKey.REVIEW,
                TaskSectionKey.NOT_STARTED,
                TaskSectionKey.DONE
            ),
            grouped.map { it.key }
        )
        assertEquals(listOf(inProgress), grouped[0].tasks)
        assertEquals(listOf(revision), grouped[1].tasks)
        assertEquals(listOf(review), grouped[2].tasks)
        assertEquals(listOf(notStarted), grouped[3].tasks)
        assertEquals(listOf(done), grouped[4].tasks)
    }

    @Test
    fun doneFilterGivesOneFlatSection() {
        val older = row(today, TaskStatus.DONE, completedAt = 1L)
        val newer = row(today.minusDays(3), TaskStatus.DONE, completedAt = 9L)
        val active = row(today, TaskStatus.IN_PROGRESS)

        val visible = listOf(older, newer, active).filter { TaskFilter.DONE.matches(it, today) }
        val grouped = sections(visible, TaskGroupMode.BY_DUE_DATE)

        assertEquals(1, grouped.size)
        assertEquals(TaskSectionKey.DONE, grouped[0].key)
        assertEquals(listOf(newer, older), grouped[0].tasks)
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
