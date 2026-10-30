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
import com.example.kronos.data.TimetableRepository
import com.example.kronos.data.local.ClassSessionWithSubject
import com.example.kronos.data.local.DatabaseProvider
import com.example.kronos.databinding.FragmentTimetableBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.time.LocalTime

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
                R.id.settings -> showSettings()
                else -> false
            }
        }
        binding.classList.layoutManager = LinearLayoutManager(requireContext())
        binding.classList.adapter = adapter
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
                launch { viewModel.weekSessions.collect(binding.weekGrid::setSessions) }
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
        binding.toolbar.menu.findItem(R.id.day_view)?.isChecked = true
        binding.toolbar.menu.findItem(R.id.week_view)?.isChecked = false
        return true
    }

    private fun showWeekView(): Boolean {
        binding.dayTabs.visibility = View.GONE
        binding.classList.visibility = View.GONE
        binding.weekGrid.visibility = View.VISIBLE
        binding.toolbar.menu.findItem(R.id.day_view)?.isChecked = false
        binding.toolbar.menu.findItem(R.id.week_view)?.isChecked = true
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
            .show()
        return true
    }

    override fun onDestroyView() {
        binding.classList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    }
}
