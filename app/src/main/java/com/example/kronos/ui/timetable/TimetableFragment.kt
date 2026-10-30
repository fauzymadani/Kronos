package com.example.kronos.ui.timetable

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kronos.R
import com.example.kronos.data.SettingsStore
import com.example.kronos.data.ClassPeriod
import com.example.kronos.data.TimetableRepository
import com.example.kronos.data.local.ClassSessionWithSubject
import com.example.kronos.data.local.DatabaseProvider
import com.example.kronos.data.local.ExamWithSubject
import com.example.kronos.data.local.Holiday
import com.example.kronos.lib.isDateInHolidayRange
import com.example.kronos.ui.exams.ExamAdapter
import com.example.kronos.databinding.FragmentTimetableBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.LocalDate
import java.time.YearMonth
import android.widget.EditText
import android.widget.LinearLayout
import android.text.InputType
import android.widget.TextView

class TimetableFragment : Fragment() {
    private var _binding: FragmentTimetableBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TimetableViewModel by viewModels {
        val database = DatabaseProvider.get(requireContext())
        TimetableViewModel.Factory(
            TimetableRepository(database.classSessionDao(), database.subjectDao(), database.holidayDao()),
            SettingsStore(requireContext())
        )
    }
    private val adapter = ClassSessionAdapter(::editClass)
    private val monthAdapter = MonthDayAdapter(::selectMonthDay)
    private var selectedMonthDay = LocalDate.now()
    private var monthSessions: List<ClassSessionWithSubject> = emptyList()
    private var monthExams: List<ExamWithSubject> = emptyList()
    private var holidays: List<Holiday> = emptyList()
    private var daySessions: List<ClassSessionWithSubject> = emptyList()
    private val examAdapter = ExamAdapter {}

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentTimetableBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.title = "${getString(R.string.timetable_title)} · Week ${viewModel.currentWeek()}"
        binding.toolbar.inflateMenu(R.menu.timetable_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.day_view -> showDayView()
                R.id.week_view -> showWeekView()
                R.id.month_view -> showMonthView()
                R.id.settings -> showSettings()
                else -> false
            }
        }
        binding.classList.layoutManager = LinearLayoutManager(requireContext())
        binding.classList.adapter = adapter
        applyHolidayState()
        binding.monthGrid.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 7)
        binding.monthGrid.adapter = monthAdapter
        binding.monthClasses.layoutManager = LinearLayoutManager(requireContext())
        binding.monthExams.layoutManager = LinearLayoutManager(requireContext())
        binding.monthExams.adapter = examAdapter
        DAYS.forEach { binding.dayTabs.addTab(binding.dayTabs.newTab().setText(it)) }
        binding.dayTabs.getTabAt(0)?.select()
        binding.dayTabs.addOnTabSelectedListener(object :
            com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                viewModel.selectDay(tab.position + 1)
                applyHolidayState()
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
        })
        binding.addClass.setOnClickListener {
            ClassEditorBottomSheet.new(viewModel.day.value).show(parentFragmentManager, "classEditor")
        }
        binding.weekGrid.onClassClick = ::editClass
        parentFragmentManager.setFragmentResultListener(
            ClassEditorBottomSheet.REQUEST,
            viewLifecycleOwner
        ) { _, result -> handleResult(result) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.sessions.collect {
                        daySessions = it
                        applyHolidayState()
                    }
                }
                launch {
                    viewModel.holidays.collect {
                        holidays = it
                        applyHolidayState()
                        refreshMonth()
                    }
                }
                launch {
                    viewModel.weekSessions.collect {
                        monthSessions = it
                        binding.weekGrid.setSessions(it)
                        refreshMonth()
                    }
                    launch {
                        DatabaseProvider.get(requireContext()).examDao().observeAll().collect {
                            monthExams = it
                            refreshMonth()
                        }
                    }
                }
                launch {
                    viewModel.day.collect { binding.dayTabs.getTabAt(it - 1)?.select() }
                }
            }
        }
        showDayView()
    }

    private fun showDayView(): Boolean {
        binding.dayTabs.visibility = View.VISIBLE
        binding.classList.visibility = View.VISIBLE
        binding.weekGrid.visibility = View.GONE
        binding.monthGrid.visibility = View.GONE
        binding.monthClasses.visibility = View.GONE
        binding.monthExams.visibility = View.GONE
        binding.classList.adapter = adapter
        applyHolidayState()
        binding.toolbar.menu.findItem(R.id.day_view)?.isChecked = true
        binding.toolbar.menu.findItem(R.id.week_view)?.isChecked = false
        binding.toolbar.menu.findItem(R.id.month_view)?.isChecked = false
        return true
    }

    private fun showMonthView(): Boolean {
        binding.dayTabs.visibility = View.GONE
        binding.classList.visibility = View.GONE
        binding.weekGrid.visibility = View.GONE
        binding.monthGrid.visibility = View.VISIBLE
        binding.monthClasses.visibility = View.VISIBLE
        binding.monthExams.visibility = View.VISIBLE
        binding.monthClasses.adapter = adapter
        applyHolidayState()
        binding.holidayBanner.visibility = View.GONE
        binding.toolbar.menu.findItem(R.id.day_view)?.isChecked = false
        binding.toolbar.menu.findItem(R.id.week_view)?.isChecked = false
        binding.toolbar.menu.findItem(R.id.month_view)?.isChecked = true
        binding.toolbar.title = "${getString(R.string.timetable_title)} · ${selectedMonthDay.month}"
        refreshMonth()
        return true
    }

    private fun refreshMonth() {
        if (_binding == null) return
        val month = YearMonth.from(selectedMonthDay)
        val classDayNumbers = monthSessions.map { it.dayOfWeek }.toSet()
        val classDays = (1..month.lengthOfMonth())
            .map { month.atDay(it) }
            .filter { it.dayOfWeek.value in classDayNumbers }
            .toSet()
        val examDays = monthExams.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.toSet()
        val holidayDays = holidays.flatMap { holiday ->
            generateSequence(holiday.startDate) { current ->
                current.plusDays(1).takeIf { !it.isAfter(holiday.endDate) }
            }.toList()
        }.toSet()
        monthAdapter.submitMonth(month.atDay(1), classDays, examDays, holidayDays, selectedMonthDay)
        if (binding.monthGrid.visibility == View.VISIBLE) {
            adapter.submitList(monthSessions.filter {
                it.dayOfWeek == selectedMonthDay.dayOfWeek.value
            }.takeUnless { isHoliday(selectedMonthDay) } ?: emptyList())
            examAdapter.submitList(monthExams.filter { it.date == selectedMonthDay.toString() })
        }
    }

    private fun isHoliday(date: LocalDate): Boolean =
        holidays.any { isDateInHolidayRange(date, it.startDate, it.endDate) }

    private fun applyHolidayState() {
        if (_binding == null) return
        val dayDate = LocalDate.now().with(java.time.DayOfWeek.MONDAY)
                .plusDays((viewModel.day.value - 1).toLong())
        val holiday = holidays.firstOrNull { isDateInHolidayRange(
            dayDate,
            it.startDate,
            it.endDate
        ) }
        binding.holidayBanner.visibility = if (holiday != null) View.VISIBLE else View.GONE
        binding.holidayBanner.text = holiday?.name.orEmpty()
        val holidayDates = holidays.flatMap { holiday ->
            generateSequence(holiday.startDate) { current ->
                current.plusDays(1).takeIf { !it.isAfter(holiday.endDate) }
            }.toList()
        }.toSet()
        binding.weekGrid.setHolidays(holidayDates)
        if (binding.classList.visibility == View.VISIBLE) {
            adapter.submitList(if (holiday != null) emptyList() else daySessions)
        }
    }

    private fun selectMonthDay(date: LocalDate) {
        selectedMonthDay = date
        viewModel.selectDay(date.dayOfWeek.value)
        refreshMonth()
    }

    private fun showWeekView(): Boolean {
        binding.dayTabs.visibility = View.GONE
        binding.classList.visibility = View.GONE
        binding.weekGrid.visibility = View.VISIBLE
        binding.monthGrid.visibility = View.GONE
        binding.monthClasses.visibility = View.GONE
        binding.monthExams.visibility = View.GONE
        binding.holidayBanner.visibility = View.GONE
        binding.toolbar.menu.findItem(R.id.day_view)?.isChecked = false
        binding.toolbar.menu.findItem(R.id.week_view)?.isChecked = true
        binding.toolbar.menu.findItem(R.id.month_view)?.isChecked = false
        binding.toolbar.title = "${getString(R.string.timetable_title)} · Week ${viewModel.currentWeek()}"
        return true
    }

    private fun editClass(item: ClassSessionWithSubject) {
        ClassEditorBottomSheet.new(item.dayOfWeek, item)
            .show(parentFragmentManager, "classEditor")
    }

    private fun handleResult(result: Bundle) {
        val id = result.getLong(ClassEditorBottomSheet.RESULT_ID)
        if (result.getBoolean(ClassEditorBottomSheet.RESULT_DELETE)) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.findSession(id)?.let(viewModel::delete)
            }
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.save(
                existing = if (id == 0L) null else viewModel.findSession(id),
                subject = result.getString(ClassEditorBottomSheet.RESULT_SUBJECT).orEmpty(),
                color = result.getString(ClassEditorBottomSheet.RESULT_COLOR).orEmpty(),
                dayOfWeek = result.getInt(ClassEditorBottomSheet.RESULT_DAY),
                start = LocalTime.parse(result.getString(ClassEditorBottomSheet.RESULT_START)),
                end = LocalTime.parse(result.getString(ClassEditorBottomSheet.RESULT_END)),
                room = result.getString(ClassEditorBottomSheet.RESULT_ROOM),
                teacher = result.getString(ClassEditorBottomSheet.RESULT_TEACHER),
                notes = result.getString(ClassEditorBottomSheet.RESULT_NOTES),
                weekNumber = result.getInt(ClassEditorBottomSheet.RESULT_WEEK, 0)
            )
        }
    }

    private fun showSettings(): Boolean {
        val settings = SettingsStore(requireContext())
        val choices = arrayOf("1 week", "2 weeks", "3 weeks", "4 weeks")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rotation_length)
            .setSingleChoiceItems(choices, settings.rotationLength - 1) { dialog, which ->
                settings.rotationLength = which + 1
                dialog.dismiss()
            }
            .setPositiveButton(R.string.semester_start) { _, _ ->
                val date = settings.semesterStart
                android.app.DatePickerDialog(requireContext(), { _, year, month, day ->
                    settings.semesterStart = java.time.LocalDate.of(year, month + 1, day)
                }, date.year, date.monthValue - 1, date.dayOfMonth).show()
            }
            .setNeutralButton(R.string.configure_periods) { _, _ -> showPeriodsSettings() }
            .setNegativeButton(R.string.holidays) { _, _ -> showHolidayManager() }
            .show()
        return true
    }

    private fun showHolidayManager() {
        val labels = holidays.map { "${it.name}: ${it.startDate} – ${it.endDate}" }.toMutableList()
        labels += getString(R.string.add_holiday)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.holidays)
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == holidays.size) editHoliday(null) else editHoliday(holidays[which])
            }
            .show()
    }

    private fun editHoliday(existing: Holiday?) {
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 0)
        }
        val name = EditText(requireContext()).apply {
            hint = getString(R.string.holiday_name)
            setText(existing?.name)
        }
        val start = TextView(requireContext()).apply { setPadding(0, 20, 0, 20) }
        val end = TextView(requireContext()).apply { setPadding(0, 20, 0, 20) }
        var startDate = existing?.startDate ?: LocalDate.now()
        var endDate = existing?.endDate ?: startDate
        fun render() {
            start.text = "${getString(R.string.start_date)}: $startDate"
            end.text = "${getString(R.string.end_date)}: $endDate"
        }
        start.setOnClickListener {
            android.app.DatePickerDialog(requireContext(), { _, year, month, day ->
                startDate = LocalDate.of(year, month + 1, day)
                render()
            }, startDate.year, startDate.monthValue - 1, startDate.dayOfMonth).show()
        }
        end.setOnClickListener {
            android.app.DatePickerDialog(requireContext(), { _, year, month, day ->
                endDate = LocalDate.of(year, month + 1, day)
                render()
            }, endDate.year, endDate.monthValue - 1, endDate.dayOfMonth).show()
        }
        content.addView(name)
        content.addView(start)
        content.addView(end)
        render()
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) R.string.add_holiday else R.string.edit_holiday)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
        if (existing != null) {
            builder.setNeutralButton(R.string.delete_holiday) { _, _ ->
                viewModel.deleteHoliday(existing)
            }
        }
        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val trimmed = name.text.toString().trim()
                if (trimmed.isEmpty() || endDate.isBefore(startDate)) {
                    if (endDate.isBefore(startDate)) {
                        name.error = getString(R.string.holiday_invalid_range)
                    }
                    return@setOnClickListener
                }
                viewModel.saveHoliday(
                    Holiday(existing?.id ?: 0L, trimmed, startDate, endDate)
                )
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showPeriodsSettings() {
        val settings = SettingsStore(requireContext())
        val periods = settings.periods.toMutableList()
        val labels = periods.map { "${it.name}, ${it.start} - ${it.end}" }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.configure_periods)
            .setItems(labels) { _, which -> editPeriod(settings, periods, which) }
            .setPositiveButton(R.string.add_period) { _, _ -> editPeriod(settings, periods, null) }
            .show()
    }

    private fun editPeriod(settings: SettingsStore, periods: MutableList<ClassPeriod>, index: Int?) {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 0)
        }
        val name = EditText(requireContext()).apply {
            hint = getString(R.string.period)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(index?.let { periods[it].name })
        }
        val start = EditText(requireContext()).apply {
            hint = getString(R.string.start_time)
            inputType = InputType.TYPE_CLASS_DATETIME
            setText(index?.let { periods[it].start.toString() } ?: "08:00")
        }
        val end = EditText(requireContext()).apply {
            hint = getString(R.string.end_time)
            inputType = InputType.TYPE_CLASS_DATETIME
            setText(index?.let { periods[it].end.toString() } ?: "08:45")
        }
        layout.addView(name); layout.addView(start); layout.addView(end)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (index == null) R.string.add_period else R.string.period)
            .setView(layout)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
        if (index != null) dialog.setNeutralButton(R.string.delete) { _, _ ->
            periods.removeAt(index)
            settings.periods = periods
        }
        val shown = dialog.create()
        shown.setOnShowListener {
            shown.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val value = runCatching {
                    ClassPeriod(
                        name.text.toString().trim().ifEmpty { throw IllegalArgumentException() },
                        LocalTime.parse(start.text.toString()),
                        LocalTime.parse(end.text.toString())
                    )
                }.getOrNull()
                if (value == null || !value.end.isAfter(value.start)) {
                    end.error = "Use HH:mm and an end after start"
                } else {
                    if (index == null) periods.add(value) else periods[index] = value
                    settings.periods = periods
                    shown.dismiss()
                }
            }
        }
        shown.show()
    }

    override fun onDestroyView() {
        binding.classList.adapter = null
        binding.monthClasses.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    }
}
