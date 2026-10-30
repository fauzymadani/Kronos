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
import com.example.kronos.databinding.FragmentTimetableBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.LocalDate
import java.time.YearMonth
import android.widget.EditText
import android.widget.LinearLayout
import android.text.InputType

class TimetableFragment : Fragment() {
    private var _binding: FragmentTimetableBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TimetableViewModel by viewModels {
        val database = DatabaseProvider.get(requireContext())
        TimetableViewModel.Factory(
            TimetableRepository(database.classSessionDao(), database.subjectDao()),
            SettingsStore(requireContext())
        )
    }
    private val adapter = ClassSessionAdapter(::editClass)
    private val monthAdapter = MonthDayAdapter(::selectMonthDay)
    private var selectedMonthDay = LocalDate.now()
    private var monthSessions: List<ClassSessionWithSubject> = emptyList()

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
        binding.monthGrid.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 7)
        binding.monthGrid.adapter = monthAdapter
        binding.monthClasses.layoutManager = LinearLayoutManager(requireContext())
        DAYS.forEach { binding.dayTabs.addTab(binding.dayTabs.newTab().setText(it)) }
        binding.dayTabs.getTabAt(0)?.select()
        binding.dayTabs.addOnTabSelectedListener(object :
            com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                viewModel.selectDay(tab.position + 1)
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
                launch { viewModel.sessions.collect(adapter::submitList) }
                launch {
                    viewModel.weekSessions.collect {
                        monthSessions = it
                        binding.weekGrid.setSessions(it)
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
        binding.classList.visibility = View.VISIBLE
        binding.weekGrid.visibility = View.GONE
        binding.monthGrid.visibility = View.GONE
        binding.monthClasses.visibility = View.GONE
        binding.classList.adapter = adapter
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
        binding.monthClasses.adapter = adapter
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
        monthAdapter.submitMonth(month.atDay(1), classDays, selectedMonthDay)
        adapter.submitList(monthSessions.filter {
            it.dayOfWeek == selectedMonthDay.dayOfWeek.value
        })
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
            .show()
        return true
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
