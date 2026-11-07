package com.example.kronos.lib

import java.time.LocalDate

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
    val subject: String? = null,
    val completedAt: Long? = null
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

enum class TaskFilter {
    ALL,
    ACTIVE,
    OVERDUE,
    NOT_STARTED,
    IN_PROGRESS,
    REVIEW,
    REVISION,
    DONE;

    fun matches(task: TaskListRow, today: LocalDate): Boolean = when (this) {
        ALL -> true
        ACTIVE -> task.status != TaskStatus.DONE
        OVERDUE -> isOverdue(task.dueDate, task.status, today)
        NOT_STARTED -> task.status == TaskStatus.NOT_STARTED
        IN_PROGRESS -> task.status == TaskStatus.IN_PROGRESS
        REVIEW -> task.status == TaskStatus.REVIEW
        REVISION -> task.status == TaskStatus.REVISION
        DONE -> task.status == TaskStatus.DONE
    }
}

enum class TaskSort {
    DUE_DATE,
    PRIORITY,
    SUBJECT,
    STATUS;

    fun comparator(): Comparator<TaskListRow> = when (this) {
        DUE_DATE -> dueDateOrder
        PRIORITY -> priorityOrder
        SUBJECT -> subjectOrder
        STATUS -> statusOrder
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

private val subjectAsc = Comparator<TaskListRow> { a, b ->
    val x = a.subject
    val y = b.subject
    if (x == null) {
        if (y == null) 0 else 1
    } else if (y == null) {
        -1
    } else {
        x.compareTo(y, ignoreCase = true)
    }
}

private val statusAsc = Comparator<TaskListRow> { a, b -> a.status.code.compareTo(b.status.code) }

private val activeOrder: Comparator<TaskListRow> = dueDateAsc.then(priorityDesc)
private val doneOrder: Comparator<TaskListRow> = completedDesc.then(dueDateAsc)

private val dueDateOrder = Comparator<TaskListRow> { a, b ->
    val aDone = a.status == TaskStatus.DONE
    val bDone = b.status == TaskStatus.DONE
    when {
        aDone != bDone -> if (aDone) 1 else -1
        aDone -> doneOrder.compare(a, b)
        else -> activeOrder.compare(a, b)
    }
}

private val priorityOrder: Comparator<TaskListRow> = priorityDesc.then(dueDateAsc)
private val subjectOrder: Comparator<TaskListRow> = subjectAsc.then(dueDateAsc)
private val statusOrder: Comparator<TaskListRow> = statusAsc.then(dueDateAsc)
