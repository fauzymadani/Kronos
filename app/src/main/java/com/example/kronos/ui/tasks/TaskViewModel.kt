package com.example.kronos.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kronos.data.TaskRepository
import com.example.kronos.data.local.Subject
import com.example.kronos.data.local.Task
import com.example.kronos.data.local.TaskWithSubject
import com.example.kronos.data.local.Category
import com.example.kronos.lib.TaskFilter
import com.example.kronos.lib.TaskGroupMode
import com.example.kronos.lib.TaskListRow
import com.example.kronos.lib.TaskSectionKey
import com.example.kronos.lib.TaskStatus
import com.example.kronos.lib.TaskSummary
import com.example.kronos.lib.groupTasks
import com.example.kronos.lib.summarize
import com.example.kronos.notifications.TaskExamReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** What the list screen renders: summary, chips, menu state and the flat list of rows. */
data class TasksUiState(
    val filter: TaskFilter = TaskFilter.ACTIVE,
    val mode: TaskGroupMode = TaskGroupMode.BY_DUE_DATE,
    val doneExpanded: Boolean = false,
    val summary: TaskSummary = TaskSummary(0, 0, emptyMap()),
    val items: List<TaskListItem> = emptyList(),
    val hasTasks: Boolean = false
)

class TaskViewModel(
    private val repository: TaskRepository,
    private val reminderScheduler: TaskExamReminderScheduler
) : ViewModel() {
    val tasks: StateFlow<List<TaskWithSubject>> = repository.taskFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val subjects: StateFlow<List<Subject>> = repository.subjectFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val categories: StateFlow<List<Category>> = repository.categoryFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    private val filter = MutableStateFlow(TaskFilter.ACTIVE)
    private val mode = MutableStateFlow(TaskGroupMode.BY_DUE_DATE)
    private val doneExpanded = MutableStateFlow(false)

    val state: StateFlow<TasksUiState> = combine(
        tasks, subjects, filter, mode, doneExpanded
    ) { tasks, subjects, filter, mode, doneExpanded ->
        buildState(tasks, subjects, filter, mode, doneExpanded)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    init {
        viewModelScope.launch { repository.ensureDefaultCategories() }
    }

    fun setFilter(value: TaskFilter) {
        filter.value = value
    }

    fun setGroupMode(value: TaskGroupMode) {
        mode.value = value
    }

    fun toggleDoneExpanded() {
        doneExpanded.value = !doneExpanded.value
    }

    private fun buildState(
        tasks: List<TaskWithSubject>,
        subjects: List<Subject>,
        filter: TaskFilter,
        mode: TaskGroupMode,
        doneExpanded: Boolean
    ): TasksUiState {
        val today = LocalDate.now()
        val rows = tasks.map { it.toRow() }
        val subjectColors = HashMap<Long, String?>(subjects.size)
        subjects.forEach { subjectColors[it.id] = it.color }
        val visible = tasks.indices.filter { filter.matches(rows[it], today) }
        val sections = groupTasks(visible, today, mode) { rows[it] }
        val items = ArrayList<TaskListItem>(visible.size + sections.size)
        sections.forEach { section ->
            val collapsible = section.key == TaskSectionKey.DONE && filter == TaskFilter.ALL
            if (filter != TaskFilter.DONE) {
                items.add(TaskListItem.Header(section.key, section.tasks.size, collapsible, doneExpanded))
            }
            if (collapsible && !doneExpanded) return@forEach
            section.tasks.forEach { index ->
                val task = tasks[index]
                items.add(
                    TaskListItem.Row(
                        task = task,
                        showStatus = mode == TaskGroupMode.BY_DUE_DATE,
                        subjectColor = task.subjectId?.let { subjectColors[it] }
                    )
                )
            }
        }
        return TasksUiState(
            filter = filter,
            mode = mode,
            doneExpanded = doneExpanded,
            summary = summarize(rows, today),
            items = items,
            hasTasks = tasks.isNotEmpty()
        )
    }

    private fun TaskWithSubject.toRow() = TaskListRow(
        dueDate = dueDate?.let(LocalDate::parse),
        status = TaskStatus.fromCode(status),
        priority = priority,
        completedAt = completedAt,
        title = title
    )

    fun save(task: Task) = viewModelScope.launch {
        val id = if (task.id == 0L) {
            repository.insert(task)
        } else {
            repository.update(task)
            task.id
        }
        repository.find(id)?.let { reminderScheduler.saveTask(it) }
    }
    fun delete(task: Task) = viewModelScope.launch {
        repository.delete(task)
        reminderScheduler.cancelTask(task.id)
    }
    fun insert(task: Task) = viewModelScope.launch {
        val id = repository.insert(task)
        repository.find(id)?.let { reminderScheduler.saveTask(it) }
    }
    fun setStatus(id: Long, status: TaskStatus, nowMillis: Long = System.currentTimeMillis()) =
        viewModelScope.launch {
            repository.setStatus(id, status, nowMillis)
            repository.find(id)?.let { reminderScheduler.saveTask(it) }
        }
    suspend fun find(id: Long): Task? = repository.find(id)
    fun saveCategory(category: Category) = viewModelScope.launch { repository.saveCategory(category) }
    fun deleteCategory(category: Category) = viewModelScope.launch { repository.deleteCategory(category) }

    class Factory(
        private val repository: TaskRepository,
        private val reminderScheduler: TaskExamReminderScheduler
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TaskViewModel(repository, reminderScheduler) as T
    }
}
