package com.example.kronos.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kronos.data.TaskRepository
import com.example.kronos.data.local.Subject
import com.example.kronos.data.local.Task
import com.example.kronos.data.local.TaskWithSubject
import com.example.kronos.data.local.Category
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(private val repository: TaskRepository) : ViewModel() {
    val tasks: StateFlow<List<TaskWithSubject>> = repository.taskFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val subjects: StateFlow<List<Subject>> = repository.subjectFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val categories: StateFlow<List<Category>> = repository.categoryFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    init {
        viewModelScope.launch { repository.ensureDefaultCategories() }
    }

    fun save(task: Task) = viewModelScope.launch {
        if (task.id == 0L) repository.insert(task) else repository.update(task)
    }
    fun delete(task: Task) = viewModelScope.launch { repository.delete(task) }
    fun insert(task: Task) = viewModelScope.launch { repository.insert(task) }
    fun setDone(id: Long, done: Boolean) = viewModelScope.launch { repository.setDone(id, done) }
    suspend fun find(id: Long): Task? = repository.find(id)
    fun saveCategory(category: Category) = viewModelScope.launch { repository.saveCategory(category) }
    fun deleteCategory(category: Category) = viewModelScope.launch { repository.deleteCategory(category) }

    class Factory(private val repository: TaskRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TaskViewModel(repository) as T
    }
}
