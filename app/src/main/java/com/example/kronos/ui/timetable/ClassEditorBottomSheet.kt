package com.example.kronos.ui.timetable

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.setFragmentResult
import com.example.kronos.R
import com.example.kronos.data.local.ClassSession
import com.example.kronos.databinding.BottomSheetClassBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.LocalTime

class ClassEditorBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetClassBinding? = null
    private val binding get() = _binding!!
    private var sessionId: Long = 0
    private var dayOfWeek: Int = 1
    private var color = COLORS.first()
    private var start = LocalTime.of(8, 0)
    private var end = LocalTime.of(9, 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dayOfWeek = requireArguments().getInt(ARG_DAY)
        sessionId = requireArguments().getLong(ARG_SESSION)
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
            setBackgroundColor(Color.parseColor(value))
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
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(value.hour)
            .setMinute(value.minute)
            .setTitleText(if (isStart) R.string.start_time else R.string.end_time)
            .build().apply {
                addOnPositiveButtonClickListener {
                    val selected = LocalTime.of(hour, minute)
                    if (isStart) start = selected else end = selected
                    renderTimes()
                }
            }.show(parentFragmentManager, "timePicker")
    }

    private fun renderTimes() {
        binding.startTime.text = start.toString()
        binding.endTime.text = end.toString()
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
        private const val ARG_DAY = "arg_day"
        private const val ARG_SESSION = "arg_session"
        private const val ARG_SUBJECT = "arg_subject"
        private const val ARG_COLOR = "arg_color"
        private const val ARG_ROOM = "arg_room"
        private const val ARG_TEACHER = "arg_teacher"
        private const val ARG_NOTES = "arg_notes"
        private const val ARG_WEEK = "arg_week"
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
                    }
                }
            }
    }
}
