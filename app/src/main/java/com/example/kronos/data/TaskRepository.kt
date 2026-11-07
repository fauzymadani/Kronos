package com.example.kronos.data

import com.example.kronos.data.local.Task
import com.example.kronos.data.local.TaskDao
import com.example.kronos.data.local.TaskWithSubject
import com.example.kronos.data.local.Category
import com.example.kronos.data.local.CategoryDao
import com.example.kronos.data.local.Subject
import com.example.kronos.data.local.SubjectDao
import com.example.kronos.lib.TaskState
import com.example.kronos.lib.TaskStatus
import com.example.kronos.lib.applyStatusChange
import com.example.kronos.lib.normalizeName
import kotlinx.coroutines.flow.Flow

class TaskRepository(
    private val tasks: TaskDao,
    private val subjects: SubjectDao,
    private val categories: CategoryDao
) {
    val taskFlow: Flow<List<TaskWithSubject>> = tasks.observeAll()
    val subjectFlow: Flow<List<Subject>> = subjects.observeAll()
    val categoryFlow: Flow<List<Category>> = categories.observeAll()

    suspend fun ensureDefaultCategories() {
        if (categories.count() == 0) {
            listOf(
                Category(name = "Homework", color = "#3F51B5"),
                Category(name = "Assignment", color = "#008577"),
                Category(name = "Project", color = "#E91E63")
            ).forEach { categories.insert(it) }
        }
    }

    suspend fun insert(task: Task, nowMillis: Long = System.currentTimeMillis()): Long =
        tasks.insert(task.consistentStatus(task.state(), nowMillis))

    suspend fun update(task: Task, nowMillis: Long = System.currentTimeMillis()) =
        tasks.update(task.consistentStatus(tasks.findById(task.id)?.state() ?: task.state(), nowMillis))

    suspend fun delete(task: Task) = tasks.delete(task)
    suspend fun find(id: Long): Task? = tasks.findById(id)

    /** Writes status, the derived done flag and completedAt in one statement. */
    suspend fun setStatus(id: Long, newStatus: TaskStatus, nowMillis: Long = System.currentTimeMillis()) {
        val task = tasks.findById(id) ?: return
        val next = applyStatusChange(task.state(), newStatus, nowMillis)
        tasks.setStatus(id, next.status.code, next.done, next.completedAt)
    }

    suspend fun deleteCategory(category: Category) = categories.delete(category)
    suspend fun saveCategory(category: Category) {
        val normalized = normalizeName(category.name)
        require(normalized.isNotEmpty())
        val existing = categories.findByNormalizedName(normalized)
        if (existing == null) {
            categories.insert(category.copy(name = category.name.trim()))
        } else if (existing.id != category.id) {
            throw IllegalArgumentException("A category with this name already exists")
        } else {
            categories.update(category.copy(name = category.name.trim()))
        }
    }

    private fun Task.state() = TaskState(TaskStatus.fromCode(status), completedAt)

    /** `done` is still set by older call sites; when it is true the task is DONE. */
    private fun Task.consistentStatus(stored: TaskState, nowMillis: Long): Task {
        val requested = if (done) TaskStatus.DONE else TaskStatus.fromCode(status)
        val next = applyStatusChange(stored, requested, nowMillis)
        return copy(status = next.status.code, done = next.done, completedAt = next.completedAt)
    }
}
