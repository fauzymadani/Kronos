package com.example.kronos.ui.timetable

import android.os.Bundle
import android.content.res.Configuration
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
import com.example.kronos.data.ThemeMode
import com.example.kronos.data.TimetableRepository
import com.example.kronos.data.local.ClassSessionWithSubject
import com.example.kronos.data.local.DatabaseProvider
import com.example.kronos.data.local.ExamWithSubject
import com.example.kronos.data.local.Holiday
import com.example.kronos.notifications.ClassReminderScheduler
import com.example.kronos.notifications.TaskExamReminderScheduler
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
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.google.android.material.switchmaterial.SwitchMaterial

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
    private val reminderScheduler get() = ClassReminderScheduler(requireContext())

    fun selectReminderDay(day: Int) {
        if (day in 1..7 && _binding != null) {
            binding.dayTabs.getTabAt(day - 1)?.select()
            showDayView()
        }
    }

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
                R.id.theme_settings -> showThemeSettings()
                R.id.reminder_settings -> showReminderSettings()
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
        val reminderDay = requireActivity().intent.getIntExtra(
            com.example.kronos.MainActivity.EXTRA_REMINDER_DAY, 0
        )
        binding.dayTabs.getTabAt((reminderDay - 1).coerceIn(0, 6))?.select()
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
        binding.dayTimeline.onClassClick = ::editClass
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
                }
                launch {
                    DatabaseProvider.get(requireContext()).examDao().observeAll().collect {
                        monthExams = it
                        refreshMonth()
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
        binding.dayTimelineContainer.visibility = View.VISIBLE
        binding.classList.visibility = View.GONE
        binding.weekGrid.visibility = View.GONE
        binding.monthGrid.visibility = View.GONE
        binding.monthClasses.visibility = View.GONE
        binding.monthExams.visibility = View.GONE
        applyHolidayState()
        binding.toolbar.menu.findItem(R.id.day_view)?.isChecked = true
        binding.toolbar.menu.findItem(R.id.week_view)?.isChecked = false
        binding.toolbar.menu.findItem(R.id.month_view)?.isChecked = false
        return true
    }

    private fun showMonthView(): Boolean {
        binding.dayTabs.visibility = View.GONE
        binding.dayTimelineContainer.visibility = View.GONE
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
        if (binding.dayTimelineContainer.visibility == View.VISIBLE) {
            binding.dayTimeline.setDay(dayDate, if (holiday != null) emptyList() else daySessions)
            binding.dayTimelineScroll.post {
                binding.dayTimelineScroll.scrollTo(
                    0,
                    binding.dayTimeline.yForMinute(binding.dayTimeline.minuteForScroll()) -
                        binding.dayTimelineScroll.height / 4
                )
            }
            binding.toolbar.title = "${dayDate.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}, " +
                "${dayDate.dayOfMonth} ${dayDate.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} - " +
                "Week ${viewModel.currentWeek()}"
        } else if (binding.classList.visibility == View.VISIBLE) {
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
        binding.dayTimelineContainer.visibility = View.GONE
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

    private fun showReminderSettings(): Boolean {
        val settings = SettingsStore(requireContext())
        val values = intArrayOf(5, 10, 15, 30)
        val labels = values.map { getString(R.string.reminder_minutes_before, it) }.toTypedArray()
        val selected = values.indexOf(settings.defaultReminderOffsetMinutes).coerceAtLeast(0)
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 0, 48, 0)
        }
        val tasks = SwitchMaterial(requireContext()).apply {
            text = getString(R.string.task_reminders)
            isChecked = settings.taskRemindersEnabled
        }
        val classes = SwitchMaterial(requireContext()).apply {
            text = getString(R.string.class_reminder_enabled)
            isChecked = settings.classRemindersEnabled
        }
        val exams = SwitchMaterial(requireContext()).apply {
            text = getString(R.string.exam_reminders)
            isChecked = settings.examRemindersEnabled
        }
        val dayBefore = SwitchMaterial(requireContext()).apply {
            text = getString(R.string.exam_day_before)
            isChecked = settings.examDayBeforeEnabled
        }
        val hourBefore = SwitchMaterial(requireContext()).apply {
            text = getString(R.string.exam_hour_before)
            isChecked = settings.examHourBeforeEnabled
        }
        val examHour = EditText(requireContext()).apply {
            hint = getString(R.string.exam_reminder_hour)
            inputType = InputType.TYPE_CLASS_DATETIME
            setText(settings.examReminderHour.toString())
        }
        layout.addView(classes); layout.addView(tasks); layout.addView(exams); layout.addView(dayBefore); layout.addView(hourBefore)
        layout.addView(examHour)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.reminder_settings)
            .setView(layout)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                settings.defaultReminderOffsetMinutes = values[which]
                dialog.dismiss()
            }
            .setNeutralButton(R.string.open_channel_settings) { _, _ ->
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                        data = Uri.parse("package:${requireContext().packageName}")
                    }
                )
            }
            .setPositiveButton(R.string.save) { _, _ ->
                settings.taskRemindersEnabled = tasks.isChecked
                settings.classRemindersEnabled = classes.isChecked
                settings.examRemindersEnabled = exams.isChecked
                settings.examDayBeforeEnabled = dayBefore.isChecked
                settings.examHourBeforeEnabled = hourBefore.isChecked
                runCatching { settings.examReminderHour = LocalTime.parse(examHour.text.toString()) }
                viewLifecycleOwner.lifecycleScope.launch {
                    TaskExamReminderScheduler(requireContext()).rescheduleAll()
                }
            }
            .show()
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
                viewModel.findSession(id)?.let { viewModel.delete(it).join() }
                reminderScheduler.cancel(id)
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
                weekNumber = result.getInt(ClassEditorBottomSheet.RESULT_WEEK, 0),
                reminderEnabled = result.getBoolean(
                    ClassEditorBottomSheet.RESULT_REMINDER_ENABLED
                ),
                reminderOffsetMinutes = result.getInt(
                    ClassEditorBottomSheet.RESULT_REMINDER_OFFSET, -1
                ).takeUnless { it < 0 }
            ).join()
            reminderScheduler.rescheduleAll()
        }
    }

    private fun showSettings(): Boolean {
        val settings = SettingsStore(requireContext())
        val choices = arrayOf("1 week", "2 weeks", "3 weeks", "4 weeks")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.rotation_length)
            .setSingleChoiceItems(choices, settings.rotationLength - 1) { dialog, which ->
                settings.rotationLength = which + 1
                viewLifecycleOwner.lifecycleScope.launch { reminderScheduler.rescheduleAll() }
                dialog.dismiss()
            }
            .setPositiveButton(R.string.semester_start) { _, _ ->
                val date = settings.semesterStart
                android.app.DatePickerDialog(requireContext(), { _, year, month, day ->
                    settings.semesterStart = java.time.LocalDate.of(year, month + 1, day)
                    viewLifecycleOwner.lifecycleScope.launch { reminderScheduler.rescheduleAll() }
                }, date.year, date.monthValue - 1, date.dayOfMonth).show()
            }
            .setNeutralButton(R.string.configure_periods) { _, _ -> showPeriodsSettings() }
            .setNegativeButton(R.string.holidays) { _, _ -> showHolidayManager() }
            .show()
        return true
    }

    private fun showThemeSettings(): Boolean {
        val settings = SettingsStore(requireContext())
        val modes = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_auto_battery)
        )
        val selected = when (settings.themeMode) {
            ThemeMode.LIGHT -> 0
            ThemeMode.DARK -> 1
            ThemeMode.AUTO_BATTERY -> 2
        }

        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 0, 48, 0)
        }
        val amoled = SwitchMaterial(requireContext()).apply {
            text = getString(R.string.pure_black)
            isChecked = settings.pureBlack
            setPadding(0, 12, 0, 12)
        }
        content.addView(amoled)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.theme_settings)
            .setSingleChoiceItems(modes, selected) { dialogInterface, which ->
                settings.themeMode = ThemeMode.values()[which]
                dialogInterface.dismiss()
                recreateForTheme()
            }
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        amoled.setOnCheckedChangeListener { _, checked ->
            settings.pureBlack = checked
            if (settings.themeMode == ThemeMode.DARK ||
                (settings.themeMode == ThemeMode.AUTO_BATTERY &&
                    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES)
            ) {
                dialog.dismiss()
                recreateForTheme()
            }
        }
        dialog.show()
        return true
    }

    private fun recreateForTheme() {
        val mode = when (SettingsStore(requireContext()).themeMode) {
            ThemeMode.LIGHT -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.AUTO_BATTERY -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
        }
        val modeChanged = androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() != mode
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)
        if (!modeChanged) requireActivity().recreate()
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
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.deleteHoliday(existing).join()
                    reminderScheduler.rescheduleAll()
                }
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
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.saveHoliday(
                        Holiday(existing?.id ?: 0L, trimmed, startDate, endDate)
                    ).join()
                    reminderScheduler.rescheduleAll()
                }
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
