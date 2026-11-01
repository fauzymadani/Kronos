package com.example.kronos.ui.timetable

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ArrayAdapter
import androidx.core.graphics.toColorInt
import androidx.fragment.app.setFragmentResult
import com.example.kronos.R
import com.example.kronos.databinding.BottomSheetClassBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.LocalTime
import com.example.kronos.data.SettingsStore
import com.example.kronos.lib.formatDisplayTime

class ClassEditorBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetClassBinding? = null
    private val binding get() = _binding!!
    private var sessionId: Long = 0
    private var dayOfWeek: Int = 1
    private var color = COLORS.first()
    private var start = LocalTime.of(8, 0)
    private var end = LocalTime.of(9, 0)
    private lateinit var periods: List<com.example.kronos.data.ClassPeriod>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dayOfWeek = requireArguments().getInt(ARG_DAY)
        sessionId = requireArguments().getLong(ARG_SESSION)
        start = requireArguments().getString(ARG_START)?.let(LocalTime::parse) ?: start
        end = requireArguments().getString(ARG_END)?.let(LocalTime::parse) ?: end
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetClassBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val item = requireArguments().getString(ARG_SUBJECT)
        binding.title.setText(if (sessionId == 0L) R.string.add_class else R.string.edit_class)
        binding.subject.setText(item)
        binding.room.setText(requireArguments().getString(ARG_ROOM))
        binding.teacher.setText(requireArguments().getString(ARG_TEACHER))
        binding.notes.setText(requireArguments().getString(ARG_NOTES))
        binding.weekNumber.setText(requireArguments().getInt(ARG_WEEK, 0).toString())
        binding.reminderEnabled.isChecked = requireArguments().getBoolean(ARG_REMINDER_ENABLED, false)
        val reminderOffsets = listOf(
            getString(R.string.reminder_use_default),
            getString(R.string.reminder_five_minutes),
            getString(R.string.reminder_ten_minutes),
            getString(R.string.reminder_fifteen_minutes),
            getString(R.string.reminder_thirty_minutes)
        )
        binding.reminderOffset.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, reminderOffsets
        )
        binding.reminderOffset.setSelection(
            when (requireArguments().getInt(ARG_REMINDER_OFFSET, -1)) {
                5 -> 1
                10 -> 2
                15 -> 3
                30 -> 4
                else -> 0
            }
        )
        periods = SettingsStore(requireContext()).periods
        val periodLabels = listOf(getString(R.string.custom_times)) + periods.map {
            "${it.name}, ${formatDisplayTime(it.start, SettingsStore(requireContext()).timeFormat)} - " +
                formatDisplayTime(it.end, SettingsStore(requireContext()).timeFormat)
        }
        binding.period.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            periodLabels
        )
        val selectedPeriod = periods.indexOfFirst { it.start == start && it.end == end }
        binding.period.setSelection(if (selectedPeriod >= 0) selectedPeriod + 1 else 0)
        binding.period.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (position > 0) {
                    periods[position - 1].let {
                        start = it.start
                        end = it.end
                        renderTimes()
                    }
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        color = requireArguments().getString(ARG_COLOR) ?: COLORS.first()
        renderTimes()
        COLORS.forEach { addColorButton(it) }
        binding.startTime.setOnClickListener { showTimePicker(true) }
        binding.endTime.setOnClickListener { showTimePicker(false) }
        binding.save.setOnClickListener { save() }
        binding.delete.visibility = if (sessionId == 0L) View.GONE else View.VISIBLE
        binding.delete.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage("Delete this class?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    setFragmentResult(REQUEST, Bundle().apply {
                        putLong(RESULT_ID, sessionId)
                        putBoolean(RESULT_DELETE, true)
                    })
                    dismiss()
                }.show()
        }
    }

    private fun addColorButton(value: String) {
        val button = Button(requireContext()).apply {
            setBackgroundColor(value.toColorInt())
            minWidth = 48
            minHeight = 48
            contentDescription = value
            setOnClickListener { color = value; updateSelection() }
        }
        binding.colorChoices.addView(button)
    }

    private fun updateSelection() {
        for (i in 0 until binding.colorChoices.childCount) {
            val child = binding.colorChoices.getChildAt(i)
            child.alpha = if (child.contentDescription == color) 1f else 0.45f
        }
    }

    private fun showTimePicker(isStart: Boolean) {
        val value = if (isStart) start else end
        MaterialTimePicker.Builder()
            .setTimeFormat(
                if (SettingsStore(requireContext()).timeFormat == com.example.kronos.data.TimeFormat.HOUR_12) {
                    TimeFormat.CLOCK_12H
                } else {
                    TimeFormat.CLOCK_24H
                }
            )
            .setHour(value.hour)
            .setMinute(value.minute)
            .setTitleText(if (isStart) R.string.start_time else R.string.end_time)
            .build().apply {
                addOnPositiveButtonClickListener {
                    val selected = LocalTime.of(hour, minute)
                    if (isStart) start = selected else end = selected
                    binding.period.setSelection(0)
                    renderTimes()
                }
            }.show(parentFragmentManager, "timePicker")
    }

    private fun renderTimes() {
        val format = SettingsStore(requireContext()).timeFormat
        binding.startTime.text = formatDisplayTime(start, format)
        binding.endTime.text = formatDisplayTime(end, format)
        updateSelection()
    }

    private fun save() {
        val subject = binding.subject.text?.toString()?.trim().orEmpty()
        if (subject.isEmpty() || !end.isAfter(start)) {
            binding.subject.error = if (subject.isEmpty()) "Required" else "End must be after start"
            return
        }
        setFragmentResult(REQUEST, Bundle().apply {
            putLong(RESULT_ID, sessionId)
            putInt(RESULT_DAY, dayOfWeek)
            putString(RESULT_SUBJECT, subject)
            putString(RESULT_COLOR, color)
            putString(RESULT_START, start.toString())
            putString(RESULT_END, end.toString())
            putString(RESULT_ROOM, binding.room.text?.toString())
            putString(RESULT_TEACHER, binding.teacher.text?.toString())
            putString(RESULT_NOTES, binding.notes.text?.toString())
            putInt(RESULT_WEEK, binding.weekNumber.text?.toString()?.toIntOrNull()?.coerceIn(0, 4) ?: 0)
            putBoolean(RESULT_REMINDER_ENABLED, binding.reminderEnabled.isChecked)
            putInt(
                RESULT_REMINDER_OFFSET,
                when (binding.reminderOffset.selectedItemPosition) {
                    1 -> 5
                    2 -> 10
                    3 -> 15
                    4 -> 30
                    else -> -1
                }
            )
        })
        dismiss()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val REQUEST = "class_editor"
        const val RESULT_DELETE = "delete"
        const val RESULT_ID = "id"
        const val RESULT_DAY = "day"
        const val RESULT_SUBJECT = "subject"
        const val RESULT_COLOR = "color"
        const val RESULT_START = "start"
        const val RESULT_END = "end"
        const val RESULT_ROOM = "room"
        const val RESULT_TEACHER = "teacher"
        const val RESULT_NOTES = "notes"
        const val RESULT_WEEK = "week"
        const val RESULT_REMINDER_ENABLED = "reminder_enabled"
        const val RESULT_REMINDER_OFFSET = "reminder_offset"
        private const val ARG_DAY = "arg_day"
        private const val ARG_SESSION = "arg_session"
        private const val ARG_SUBJECT = "arg_subject"
        private const val ARG_COLOR = "arg_color"
        private const val ARG_ROOM = "arg_room"
        private const val ARG_TEACHER = "arg_teacher"
        private const val ARG_NOTES = "arg_notes"
        private const val ARG_WEEK = "arg_week"
        private const val ARG_START = "arg_start"
        private const val ARG_END = "arg_end"
        private const val ARG_REMINDER_ENABLED = "arg_reminder_enabled"
        private const val ARG_REMINDER_OFFSET = "arg_reminder_offset"
        private val COLORS = listOf("#3F51B5", "#008577", "#E91E63", "#FF9800", "#795548", "#607D8B")

        fun new(day: Int, item: com.example.kronos.data.local.ClassSessionWithSubject? = null) =
            ClassEditorBottomSheet().apply {
                arguments = Bundle().apply {
                    putInt(ARG_DAY, day)
                    item?.let {
                        putLong(ARG_SESSION, it.id)
                        putString(ARG_SUBJECT, it.subjectName)
                        putString(ARG_COLOR, it.subjectColor)
                        putString(ARG_ROOM, it.room)
                        putString(ARG_TEACHER, it.teacher)
                        putString(ARG_NOTES, it.notes)
                        putInt(ARG_WEEK, it.weekNumber)
                        putString(ARG_START, it.startTime)
                        putString(ARG_END, it.endTime)
                        putBoolean(ARG_REMINDER_ENABLED, it.reminderEnabled)
                        putInt(ARG_REMINDER_OFFSET, it.reminderOffsetMinutes ?: -1)
                    }
                }
            }
    }
}
