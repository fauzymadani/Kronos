package com.example.kronos.lib

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Task workflow status. [code] is the only value written to the database;
 * ordinals and enum names are never persisted.
 */
enum class TaskStatus(val code: Int, val label: String) {
    NOT_STARTED(0, "Not started"),
    IN_PROGRESS(1, "In progress"),
    REVIEW(2, "Review"),
    REVISION(3, "Revision"),
    DONE(4, "Done");

    companion object {
        fun fromCode(code: Int): TaskStatus = entries.firstOrNull { it.code == code } ?: NOT_STARTED
    }
}

enum class AssignmentType(val code: Int, val label: String) {
    INDIVIDUAL(0, "Individual"),
    GROUP(1, "Group");

    companion object {
        fun fromCode(code: Int): AssignmentType =
            entries.firstOrNull { it.code == code } ?: INDIVIDUAL
    }
}

/** Status plus completion timestamp. [done] always follows [status]. */
data class TaskState(val status: TaskStatus = TaskStatus.NOT_STARTED, val completedAt: Long? = null) {
    val done: Boolean get() = status == TaskStatus.DONE
}

fun applyStatusChange(current: TaskState, newStatus: TaskStatus, nowMillis: Long): TaskState {
    val completedAt = when {
        newStatus != TaskStatus.DONE -> null
        current.status == TaskStatus.DONE -> current.completedAt
        else -> nowMillis
    }
    return TaskState(newStatus, completedAt)
}

fun isOverdue(dueDate: LocalDate?, status: TaskStatus, today: LocalDate): Boolean =
    dueDate != null && status != TaskStatus.DONE && dueDate.isBefore(today)

/** One task as the list needs it. Build it once per emission, not per bind. */
data class TaskListRow(
    val dueDate: LocalDate?,
    val status: TaskStatus,
    val priority: Int = 0,
    val completedAt: Long? = null,
    val title: String = ""
)

data class TaskSummary(val total: Int, val overdue: Int, val byStatus: Map<TaskStatus, Int>) {
    fun count(status: TaskStatus): Int = byStatus[status] ?: 0
}

fun summarize(tasks: List<TaskListRow>, today: LocalDate): TaskSummary {
    val counts = HashMap<TaskStatus, Int>(TaskStatus.entries.size)
    var overdue = 0
    tasks.forEach { task ->
        counts[task.status] = (counts[task.status] ?: 0) + 1
        if (isOverdue(task.dueDate, task.status, today)) overdue++
    }
    return TaskSummary(tasks.size, overdue, counts)
}

/** List filters. Statuses are grouped, not filtered, so only four are left. */
enum class TaskFilter {
    ACTIVE,
    ALL,
    OVERDUE,
    DONE;

    fun matches(task: TaskListRow, today: LocalDate): Boolean = when (this) {
        ALL -> true
        ACTIVE -> task.status != TaskStatus.DONE
        OVERDUE -> isOverdue(task.dueDate, task.status, today)
        DONE -> task.status == TaskStatus.DONE
    }
}

enum class TaskGroupMode { BY_DUE_DATE, BY_STATUS }

enum class TaskSectionKey {
    OVERDUE, TODAY, THIS_WEEK, LATER, NO_DATE, IN_PROGRESS, REVISION, REVIEW, NOT_STARTED, DONE
}

/** One labelled block of the list. */
data class TaskSection<T>(val key: TaskSectionKey, val tasks: List<T>)

/**
 * Splits [tasks] into the sections of [mode], in display order. Empty sections are dropped.
 * [row] is called once per task so callers can keep their own richer task type.
 */
fun <T> groupTasks(
    tasks: List<T>,
    today: LocalDate,
    mode: TaskGroupMode,
    row: (T) -> TaskListRow
): List<TaskSection<T>> {
    val order = when (mode) {
        TaskGroupMode.BY_DUE_DATE -> DUE_DATE_SECTIONS
        TaskGroupMode.BY_STATUS -> STATUS_SECTIONS
    }
    val buckets = LinkedHashMap<TaskSectionKey, MutableList<Pair<T, TaskListRow>>>(order.size)
    tasks.forEach { task ->
        val values = row(task)
        buckets.getOrPut(sectionOf(values, today, mode)) { ArrayList() }.add(task to values)
    }
    return order.mapNotNull { key ->
        val entries = buckets[key] ?: return@mapNotNull null
        val comparator = if (key == TaskSectionKey.DONE) newestFirst else dueThenPriority
        entries.sortWith(Comparator { a, b -> comparator.compare(a.second, b.second) })
        TaskSection(key, entries.map { it.first })
    }
}

private val DUE_DATE_SECTIONS = listOf(
    TaskSectionKey.OVERDUE,
    TaskSectionKey.TODAY,
    TaskSectionKey.THIS_WEEK,
    TaskSectionKey.LATER,
    TaskSectionKey.NO_DATE,
    TaskSectionKey.DONE
)

private val STATUS_SECTIONS = listOf(
    TaskSectionKey.IN_PROGRESS,
    TaskSectionKey.REVISION,
    TaskSectionKey.REVIEW,
    TaskSectionKey.NOT_STARTED,
    TaskSectionKey.DONE
)

private fun sectionOf(row: TaskListRow, today: LocalDate, mode: TaskGroupMode): TaskSectionKey =
    if (mode == TaskGroupMode.BY_STATUS) statusSection(row.status) else dueDateSection(row, today)

private fun statusSection(status: TaskStatus): TaskSectionKey = when (status) {
    TaskStatus.IN_PROGRESS -> TaskSectionKey.IN_PROGRESS
    TaskStatus.REVISION -> TaskSectionKey.REVISION
    TaskStatus.REVIEW -> TaskSectionKey.REVIEW
    TaskStatus.NOT_STARTED -> TaskSectionKey.NOT_STARTED
    TaskStatus.DONE -> TaskSectionKey.DONE
}

private fun dueDateSection(row: TaskListRow, today: LocalDate): TaskSectionKey {
    if (row.status == TaskStatus.DONE) return TaskSectionKey.DONE
    val due = row.dueDate ?: return TaskSectionKey.NO_DATE
    return when {
        due.isBefore(today) -> TaskSectionKey.OVERDUE
        due.isEqual(today) -> TaskSectionKey.TODAY
        ChronoUnit.DAYS.between(today, due) <= 7 -> TaskSectionKey.THIS_WEEK
        else -> TaskSectionKey.LATER
    }
}

private val dueDateAsc = Comparator<TaskListRow> { a, b ->
    val x = a.dueDate
    val y = b.dueDate
    if (x == null) {
        if (y == null) 0 else 1
    } else if (y == null) {
        -1
    } else {
        x.compareTo(y)
    }
}

private val completedDesc = Comparator<TaskListRow> { a, b ->
    val x = a.completedAt
    val y = b.completedAt
    if (x == null) {
        if (y == null) 0 else 1
    } else if (y == null) {
        -1
    } else {
        y.compareTo(x)
    }
}

private val priorityDesc = Comparator<TaskListRow> { a, b -> b.priority.compareTo(a.priority) }

private val titleAsc = Comparator<TaskListRow> { a, b -> a.title.compareTo(b.title, ignoreCase = true) }

/** Due date ascending (no due date last), then higher priority first, then title. */
private val dueThenPriority: Comparator<TaskListRow> = dueDateAsc.then(priorityDesc).then(titleAsc)

/** Newest completion first. */
private val newestFirst: Comparator<TaskListRow> = completedDesc.then(dueDateAsc)
