package com.example.kronos.ui.exams

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kronos.R
import com.example.kronos.data.ExamRepository
import com.example.kronos.data.SettingsStore
import com.example.kronos.data.TimeFormat
import com.example.kronos.data.local.DatabaseProvider
import com.example.kronos.data.local.Exam
import com.example.kronos.data.local.ExamWithSubject
import com.example.kronos.databinding.FragmentExamsBinding
import com.example.kronos.lib.CountdownKind
import com.example.kronos.lib.ExamSection
import com.example.kronos.lib.examCountdown
import com.example.kronos.lib.formatDayTitle
import com.example.kronos.lib.formatDisplayDate
import com.example.kronos.lib.formatDisplayTime
import com.example.kronos.lib.formatMonthTitle
import com.example.kronos.notifications.TaskExamReminderScheduler
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

class ExamsFragment : Fragment() {
    private var _binding: FragmentExamsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ExamViewModel by viewModels {
        val db = DatabaseProvider.get(requireContext())
        ExamViewModel.Factory(
            ExamRepository(db.examDao(), db.subjectDao()),
            TaskExamReminderScheduler(requireContext()),
            SettingsStore(requireContext())
        )
    }
    private val monthAdapter = ExamMonthAdapter(::selectDate)
    private val dayAdapter = ExamAdapter(::editExam)
    private val listAdapter = ExamAdapter(::editExam, ::toggleSection)
    private var pastExpanded = false
    private var timeFormat = TimeFormat.HOUR_24

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentExamsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.monthGrid.layoutManager = GridLayoutManager(requireContext(), CALENDAR_COLUMNS)
        binding.monthGrid.adapter = monthAdapter
        binding.dayList.layoutManager = LinearLayoutManager(requireContext())
        binding.dayList.adapter = dayAdapter
        binding.listMode.layoutManager = LinearLayoutManager(requireContext())
        binding.listMode.adapter = listAdapter
        binding.prevMonth.setOnClickListener { viewModel.onPrevMonth() }
        binding.nextMonth.setOnClickListener { viewModel.onNextMonth() }
        binding.addExam.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch { openEditor(null) }
        }
        binding.modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.onSetMode(
                if (checkedId == R.id.modeCalendar) ExamViewMode.CALENDAR else ExamViewMode.LIST
            )
        }

        parentFragmentManager.setFragmentResultListener(
            ExamEditorBottomSheet.REQUEST, viewLifecycleOwner
        ) { _, result -> handleResult(result) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        timeFormat = SettingsStore(requireContext()).timeFormat
        dayAdapter.timeFormat = timeFormat
        listAdapter.timeFormat = timeFormat
        viewModel.refreshToday()
    }

    private fun render(state: ExamUiState) {
        val calendarMode = state.mode == ExamViewMode.CALENDAR
        binding.modeToggle.check(if (calendarMode) R.id.modeCalendar else R.id.modeList)
        binding.monthTitle.text = formatMonthTitle(state.month)
        state.weekdayLabels.forEachIndexed { index, label ->
            (binding.weekdayRow.getChildAt(index) as? TextView)?.text = label
        }
        monthAdapter.submitList(state.cells)
        binding.calendarGroup.isVisible = calendarMode && state.hasExams
        binding.listMode.isVisible = !calendarMode && state.hasExams
        binding.emptyExams.isVisible = !state.hasExams

        binding.dayTitle.text = formatDayTitle(state.selectedDate)
        dayAdapter.submitExams(state.selectedDateExams, state.today)
        binding.dayList.isVisible = state.selectedDateExams.isNotEmpty()
        binding.dayEmpty.isVisible = state.selectedDateExams.isEmpty()

        listAdapter.submitSections(state.sections, pastExpanded, state.today)

        val next = state.nextExam
        binding.nextExamTitle.isVisible = next != null
        if (next == null) {
            binding.nextExamSubtitle.text = getString(R.string.no_upcoming_exams)
        } else {
            binding.nextExamTitle.text = getString(
                R.string.next_exam_label, next.subjectName ?: next.title
            )
            binding.nextExamSubtitle.text = getString(
                R.string.next_exam_summary,
                countdownText(state.today, LocalDate.parse(next.date)),
                dateWithTime(next)
            )
        }
    }

    private fun countdownText(today: LocalDate, date: LocalDate): String {
        val countdown = examCountdown(today, date)
        return when (countdown.kind) {
            CountdownKind.TODAY -> getString(R.string.today)
            CountdownKind.TOMORROW -> getString(R.string.countdown_tomorrow)
            CountdownKind.DAYS -> getString(R.string.countdown_in_days, countdown.days)
            CountdownKind.PAST -> getString(R.string.countdown_past)
        }
    }

    private fun dateWithTime(exam: ExamWithSubject): String {
        val date = formatDisplayDate(LocalDate.parse(exam.date))
        val time = exam.startTime?.let { formatDisplayTime(LocalTime.parse(it), timeFormat) }
        return if (time == null) date else getString(R.string.date_with_time, date, time)
    }

    private fun selectDate(date: LocalDate) {
        viewModel.onSelectDate(date)
    }

    private fun toggleSection(section: ExamSection) {
        if (section != ExamSection.PAST) return
        pastExpanded = !pastExpanded
        val state = viewModel.uiState.value
        listAdapter.submitSections(state.sections, pastExpanded, state.today)
    }

    private suspend fun openEditor(exam: Exam?) {
        ExamEditorBottomSheet.new(exam, viewModel.getSubjects())
            .show(parentFragmentManager, "exam_editor")
    }

    private fun editExam(item: ExamWithSubject) {
        viewLifecycleOwner.lifecycleScope.launch { openEditor(viewModel.find(item.id)) }
    }

    private fun handleResult(result: Bundle) {
        val id = result.getLong(ExamEditorBottomSheet.RESULT_ID)
        if (result.getBoolean(ExamEditorBottomSheet.RESULT_DELETE)) {
            viewLifecycleOwner.lifecycleScope.launch { viewModel.find(id)?.let(viewModel::delete) }
            return
        }
        viewModel.save(
            Exam(
                id = id,
                title = viewModel.subjects.value.firstOrNull {
                    it.id == result.getLong(ExamEditorBottomSheet.RESULT_SUBJECT_ID)
                }?.name ?: "Exam",
                subjectId = result.getLong(ExamEditorBottomSheet.RESULT_SUBJECT_ID).takeUnless { it == 0L },
                date = LocalDate.parse(result.getString(ExamEditorBottomSheet.RESULT_DATE)),
                startTime = result.getString(ExamEditorBottomSheet.RESULT_TIME)?.takeIf { it.isNotBlank() }
                    ?.let(LocalTime::parse),
                room = result.getString(ExamEditorBottomSheet.RESULT_ROOM)?.takeIf { it.isNotBlank() },
                seat = result.getString(ExamEditorBottomSheet.RESULT_SEAT)?.takeIf { it.isNotBlank() },
                notes = result.getString(ExamEditorBottomSheet.RESULT_NOTES)?.takeIf { it.isNotBlank() }
            )
        )
    }

    override fun onDestroyView() {
        binding.monthGrid.adapter = null
        binding.dayList.adapter = null
        binding.listMode.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val CALENDAR_COLUMNS = 7
    }
}
