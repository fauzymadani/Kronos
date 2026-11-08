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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kronos.R
import com.example.kronos.data.SettingsStore
import com.example.kronos.data.TimetableRepository
import com.example.kronos.data.local.ClassSessionWithSubject
import com.example.kronos.data.local.DatabaseProvider
import com.example.kronos.data.local.ExamWithSubject
import com.example.kronos.data.local.Holiday
import com.example.kronos.notifications.ClassReminderScheduler
import com.example.kronos.notifications.TaskExamReminderScheduler
import com.example.kronos.lib.isDateInHolidayRange
import com.example.kronos.lib.displayWeekdayLabels
import com.example.kronos.lib.startOfDisplayedWeek
import com.example.kronos.lib.formatDisplayDate
import com.example.kronos.ui.exams.ExamAdapter
import com.example.kronos.databinding.FragmentTimetableBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.LocalDate
import java.time.YearMonth
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import com.example.kronos.data.BackupJsonCodec
import com.example.kronos.data.BackupRepository
import com.example.kronos.lib.classesCsv
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.OutputStreamWriter

class TimetableFragment : Fragment() {
    private var _binding: FragmentTimetableBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TimetableViewModel by viewModels {
        val database = DatabaseProvider.get(requireContext())
        TimetableViewModel.Factory(
            TimetableRepository(database.classSessionDao(), database.subjectDao(), database.holidayDao()),
            SettingsStore(requireContext()),
            requireContext()
        )
    }
    private val adapter = ClassSessionAdapter(::editClass)
    private val monthAdapter = MonthDayAdapter(::selectMonthDay)
    private var selectedMonthDay = LocalDate.now()
    private var monthSessions: List<ClassSessionWithSubject> = emptyList()
    private var monthExams: List<ExamWithSubject> = emptyList()
    private var holidays: List<Holiday> = emptyList()
    private var daySessions: List<ClassSessionWithSubject> = emptyList()
    private val examAdapter = ExamAdapter(onClick = {})
    private val reminderScheduler get() = ClassReminderScheduler(requireContext())
    private val createBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) exportBackup(uri) }
    private val openBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) readBackup(uri) }
    private val createClassesCsv = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) exportClassesCsv(uri) }

    fun selectReminderDay(day: Int) {
        if (day in 1..7 && _binding != null) {
            binding.dayTabs.getTabAt(dayTabIndex(day))?.select()
            showDayView()
        }
    }

    private fun dayTabIndex(isoDay: Int): Int =
        com.example.kronos.lib.orderedIsoDays(SettingsStore(requireContext()).firstDayOfWeek)
            .indexOf(isoDay).coerceAtLeast(0)

    private fun dayTabDay(index: Int): Int =
        com.example.kronos.lib.orderedIsoDays(SettingsStore(requireContext()).firstDayOfWeek)
            .getOrElse(index) { 1 }

    private fun applyDisplaySettings() {
        val settings = SettingsStore(requireContext())
        binding.dayTabs.removeAllTabs()
        displayWeekdayLabels(settings.firstDayOfWeek).forEach {
            binding.dayTabs.addTab(binding.dayTabs.newTab().setText(it), false)
        }
        binding.dayTabs.getTabAt(dayTabIndex(viewModel.day.value))?.select()
        binding.weekGrid.setDisplaySettings()
        adapter.notifyDataSetChanged()
        refreshMonth()
        applyHolidayState()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentTimetableBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) applyDisplaySettings()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolbar.title = "${getString(R.string.timetable_title)} · Week ${viewModel.currentWeek()}"
        binding.toolbar.inflateMenu(R.menu.timetable_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.day_view -> showDayView()
                R.id.week_view -> showWeekView()
                R.id.month_view -> showMonthView()
                R.id.show_exams -> toggleDayExams()
                R.id.settings -> {
                    findNavController().navigate(R.id.settingsFragment)
                    true
                }
                R.id.export_backup -> { createBackup.launch(getString(R.string.backup_file_name)); true }
                R.id.import_backup -> { openBackup.launch(arrayOf("application/json", "text/plain")); true }
                R.id.export_classes_csv -> { createClassesCsv.launch(getString(R.string.classes_csv_file_name)); true }
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
        applyDisplaySettings()
        val reminderDay = requireActivity().intent.getIntExtra(
            com.example.kronos.MainActivity.EXTRA_REMINDER_DAY, 0
        )
        binding.dayTabs.getTabAt(dayTabIndex(reminderDay))?.select()
        binding.dayTabs.addOnTabSelectedListener(object :
            com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                viewModel.selectDay(dayTabDay(tab.position))
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
                        binding.weekGrid.setExams(it)
                        refreshMonth()
                        if (binding.dayTimelineContainer.visibility == View.VISIBLE) {
                            applyHolidayState()
                        }
                    }
                }
                launch {
                    viewModel.day.collect { binding.dayTabs.getTabAt(dayTabIndex(it))?.select() }
                }
            }
        }
        showDayView()
    }

    private fun backupRepository() = BackupRepository(
        DatabaseProvider.get(requireContext()), SettingsStore(requireContext())
    )

    private fun exportBackup(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) {
                    BackupJsonCodec.encode(backupRepository().exportData())
                }
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openOutputStream(uri)?.use {
                        OutputStreamWriter(it, Charsets.UTF_8).use { writer -> writer.write(json) }
                    } ?: error("Unable to open destination")
                }
            }.onFailure { toast(R.string.backup_export_failed) }
        }
    }

    private fun readBackup(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        BackupJsonCodec.decode(reader.readText())
                    } ?: error("Unable to open backup")
                }
            }
            result.onFailure { error ->
                toast(error.message ?: getString(R.string.backup_invalid))
            }.onSuccess { backup ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.replace_backup_title)
                    .setMessage(R.string.replace_backup_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.import_backup) { _, _ ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) { backupRepository().importData(backup) }
                                withContext(Dispatchers.IO) {
                                    TaskExamReminderScheduler(requireContext()).rescheduleAll()
                                }
                                com.example.kronos.widget.TimetableWidgetProvider.requestUpdate(requireContext())
                            }.onFailure { toast(R.string.backup_import_failed) }
                        }
                    }.show()
            }
        }
    }

    private fun exportClassesCsv(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val csv = withContext(Dispatchers.IO) {
                    classesCsv(DatabaseProvider.get(requireContext()).classSessionDao().getAllWithSubjects())
                }
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openOutputStream(uri)?.use {
                        OutputStreamWriter(it, Charsets.UTF_8).use { writer -> writer.write(csv) }
                    } ?: error("Unable to open destination")
                }
            }.onFailure { toast(R.string.csv_export_failed) }
        }
    }

    private fun toast(message: Int) =
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_LONG).show()

    private fun toast(message: String) =
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_LONG).show()

    private fun toggleDayExams(): Boolean {
        val settings = SettingsStore(requireContext())
        settings.dayViewShowsExams = !settings.dayViewShowsExams
        binding.toolbar.menu.findItem(R.id.show_exams)?.isChecked = settings.dayViewShowsExams
        applyHolidayState()
        return true
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
        binding.toolbar.menu.findItem(R.id.show_exams)?.isVisible = true
        binding.toolbar.menu.findItem(R.id.show_exams)?.isChecked =
            SettingsStore(requireContext()).dayViewShowsExams
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
        binding.toolbar.menu.findItem(R.id.show_exams)?.isVisible = false
        binding.toolbar.title = "${getString(R.string.timetable_title)} · ${formatDisplayDate(selectedMonthDay)}"
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
        monthAdapter.submitMonth(
            month.atDay(1), classDays, examDays, holidayDays, selectedMonthDay,
            SettingsStore(requireContext()).firstDayOfWeek
        )
        if (binding.monthGrid.visibility == View.VISIBLE) {
            adapter.submitList(monthSessions.filter {
                it.dayOfWeek == selectedMonthDay.dayOfWeek.value
            }.takeUnless { isHoliday(selectedMonthDay) } ?: emptyList())
            examAdapter.submitExams(
                monthExams.filter { it.date == selectedMonthDay.toString() },
                LocalDate.now()
            )
        }
    }

    private fun isHoliday(date: LocalDate): Boolean =
        holidays.any { isDateInHolidayRange(date, it.startDate, it.endDate) }

    private fun applyHolidayState() {
        if (_binding == null) return
        val settings = SettingsStore(requireContext())
        val dayDate = startOfDisplayedWeek(
            LocalDate.now(),
            settings.firstDayOfWeek
        ).plusDays(
            dayTabIndex(viewModel.day.value).toLong()
        )
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
            val exams = if (settings.dayViewShowsExams) {
                monthExams.filter { it.date == dayDate.toString() }
            } else {
                emptyList()
            }
            binding.dayTimeline.setDay(
                dayDate,
                if (holiday != null) emptyList() else daySessions,
                exams
            )
            binding.toolbar.title = "${formatDisplayDate(dayDate)} - " +
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
        binding.toolbar.menu.findItem(R.id.show_exams)?.isVisible = false
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

    override fun onDestroyView() {
        binding.classList.adapter = null
        binding.monthGrid.adapter = null
        binding.monthClasses.adapter = null
        binding.monthExams.adapter = null
        binding.weekGrid.onClassClick = null
        binding.dayTimeline.onClassClick = null
        _binding = null
        super.onDestroyView()
    }

}
