package com.example.kronos.ui.exams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kronos.data.ExamRepository
import com.example.kronos.data.local.Exam
import com.example.kronos.data.local.ExamWithSubject
import com.example.kronos.data.local.Subject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExamViewModel(private val repository: ExamRepository) : ViewModel() {
    val exams: StateFlow<List<ExamWithSubject>> = repository.examFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val subjects: StateFlow<List<Subject>> = repository.subjectFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    fun save(exam: Exam) = viewModelScope.launch {
        if (exam.id == 0L) repository.insert(exam) else repository.update(exam)
    }

    fun delete(exam: Exam) = viewModelScope.launch { repository.delete(exam) }
    suspend fun find(id: Long): Exam? = repository.find(id)

    class Factory(private val repository: ExamRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ExamViewModel(repository) as T
    }
}
