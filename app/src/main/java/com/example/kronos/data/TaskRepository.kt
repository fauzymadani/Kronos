package com.example.kronos.data

import com.example.kronos.data.local.Task
import com.example.kronos.data.local.TaskDao
import com.example.kronos.data.local.TaskWithSubject
import com.example.kronos.data.local.Category
import com.example.kronos.data.local.CategoryDao
import com.example.kronos.data.local.Subject
import com.example.kronos.data.local.SubjectDao
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

    suspend fun insert(task: Task) = tasks.insert(task)
    suspend fun update(task: Task) = tasks.update(task)
    suspend fun delete(task: Task) = tasks.delete(task)
    suspend fun find(id: Long): Task? = tasks.findById(id)
    suspend fun setDone(id: Long, done: Boolean) = tasks.setDone(id, done)
    suspend fun deleteCategory(category: Category) = categories.delete(category)
    suspend fun saveCategory(category: Category) {
        if (category.id == 0L) categories.insert(category) else categories.update(category)
    }
}
