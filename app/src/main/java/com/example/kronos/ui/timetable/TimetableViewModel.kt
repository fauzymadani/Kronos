package com.example.kronos.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kronos.data.TimetableRepository
import com.example.kronos.data.local.ClassSession
import com.example.kronos.data.local.ClassSessionWithSubject
import com.example.kronos.lib.currentRotationWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimetableViewModel(
    private val repository: TimetableRepository,
    private val settings: com.example.kronos.data.SettingsStore
) : ViewModel() {
    private val selectedDay = MutableStateFlow(1)
    val day: StateFlow<Int> = selectedDay.asStateFlow()
    val sessions: StateFlow<List<ClassSessionWithSubject>> =
        selectedDay.flatMapLatest {
            repository.observeForDay(it, currentWeek())
        }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                emptyList()
            )

    fun selectDay(dayOfWeek: Int) {
        selectedDay.value = dayOfWeek
    }

    fun currentWeek(): Int = currentRotationWeek(
        java.time.LocalDate.now(),
        settings.semesterStart,
        settings.rotationLength
    )

    fun save(
        existing: ClassSession?,
        subject: String,
        color: String,
        dayOfWeek: Int,
        start: java.time.LocalTime,
        end: java.time.LocalTime,
        room: String?,
        teacher: String?,
        notes: String?
        , weekNumber: Int
    ) = viewModelScope.launch {
        repository.save(existing, subject, color, dayOfWeek, start, end, room, teacher, notes, weekNumber)
    }

    fun delete(session: ClassSession) = viewModelScope.launch {
        repository.delete(session)
    }

    suspend fun findSession(id: Long): ClassSession? = repository.findSession(id)

    class Factory(
        private val repository: TimetableRepository,
        private val settings: com.example.kronos.data.SettingsStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TimetableViewModel(repository, settings) as T
    }
}
