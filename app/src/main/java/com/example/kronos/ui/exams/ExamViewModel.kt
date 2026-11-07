package com.example.kronos.ui.exams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kronos.data.ExamRepository
import com.example.kronos.data.FirstDayOfWeek
import com.example.kronos.data.SettingsStore
import com.example.kronos.data.local.Exam
import com.example.kronos.data.local.ExamWithSubject
import com.example.kronos.data.local.Subject
import com.example.kronos.lib.DayCell
import com.example.kronos.lib.ExamSection
import com.example.kronos.lib.ExamSectionGroup
import com.example.kronos.lib.buildMonthCells
import com.example.kronos.lib.displayWeekdayLabels
import com.example.kronos.lib.examDotsByDate
import com.example.kronos.lib.groupExams
import com.example.kronos.lib.nextExam
import com.example.kronos.notifications.TaskExamReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class ExamViewMode { CALENDAR, LIST }

data class ExamUiState(
    val mode: ExamViewMode = ExamViewMode.CALENDAR,
    val month: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val weekdayLabels: List<String> = emptyList(),
    val cells: List<DayCell> = emptyList(),
    val sections: List<ExamSectionGroup> = emptyList(),
    val selectedDateExams: List<ExamWithSubject> = emptyList(),
    val nextExam: ExamWithSubject? = null,
    val pastCount: Int = 0,
    val hasExams: Boolean = false
)

class ExamViewModel(
    private val repository: ExamRepository,
    private val reminderScheduler: TaskExamReminderScheduler,
    private val settings: SettingsStore
) : ViewModel() {
    private data class Clock(val today: LocalDate, val firstDayOfWeek: FirstDayOfWeek)

    val mode = MutableStateFlow(ExamViewMode.CALENDAR)
    val month = MutableStateFlow(YearMonth.now())
    val selectedDate = MutableStateFlow(LocalDate.now())
    private val clock = MutableStateFlow(Clock(LocalDate.now(), settings.firstDayOfWeek))

    val exams: StateFlow<List<ExamWithSubject>> = repository.examFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    val subjects: StateFlow<List<Subject>> = repository.subjectFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val uiState: StateFlow<ExamUiState> = combine(
        exams, mode, month, selectedDate, clock
    ) { list, viewMode, monthValue, selected, clockValue ->
        buildState(list, viewMode, monthValue, selected, clockValue)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        buildState(emptyList(), mode.value, month.value, selectedDate.value, clock.value)
    )

    fun onPrevMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun onNextMonth() {
        month.value = month.value.plusMonths(1)
    }

    fun onSelectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun onSetMode(value: ExamViewMode) {
        mode.value = value
    }

    /** Keeps "today" and the first day of week right after midnight or a settings change. */
    fun refreshToday() {
        clock.value = Clock(LocalDate.now(), settings.firstDayOfWeek)
    }

    private fun buildState(
        exams: List<ExamWithSubject>,
        mode: ExamViewMode,
        month: YearMonth,
        selected: LocalDate,
        clock: Clock
    ): ExamUiState {
        val today = clock.today
        val sections = groupExams(exams, today)
        val selectedIso = selected.toString()
        return ExamUiState(
            mode = mode,
            month = month,
            selectedDate = selected,
            today = today,
            weekdayLabels = displayWeekdayLabels(clock.firstDayOfWeek),
            cells = buildMonthCells(
                month, clock.firstDayOfWeek, today, selected, examDotsByDate(exams)
            ),
            sections = sections,
            selectedDateExams = exams.filter { it.date == selectedIso }
                .sortedBy { it.startTime.orEmpty() },
            nextExam = nextExam(exams, today),
            pastCount = sections.firstOrNull { it.section == ExamSection.PAST }
                ?.exams?.size ?: 0,
            hasExams = exams.isNotEmpty()
        )
    }

    fun save(exam: Exam) = viewModelScope.launch {
        val saved = if (exam.id == 0L) {
            exam.copy(id = repository.insert(exam))
        } else {
            repository.update(exam)
            exam
        }
        reminderScheduler.saveExam(saved)
    }

    fun delete(exam: Exam) = viewModelScope.launch {
        repository.delete(exam)
        reminderScheduler.cancelExam(exam.id)
    }
    suspend fun find(id: Long): Exam? = repository.find(id)
    suspend fun getSubjects(): List<Subject> = repository.getSubjects()

    class Factory(
        private val repository: ExamRepository,
        private val reminderScheduler: TaskExamReminderScheduler,
        private val settings: SettingsStore
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ExamViewModel(repository, reminderScheduler, settings) as T
    }
}
