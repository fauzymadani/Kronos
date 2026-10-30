package com.example.kronos.ui.exams

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.setFragmentResult
import com.example.kronos.R
import com.example.kronos.data.local.Exam
import com.example.kronos.data.local.Subject
import com.example.kronos.databinding.BottomSheetExamBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate
import java.time.LocalTime

class ExamEditorBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetExamBinding? = null
    private val binding get() = _binding!!
    private var examId = 0L
    private var date = LocalDate.now()
    private var time: LocalTime? = null
    private var subjects: List<Subject> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        examId = args.getLong(ARG_ID)
        date = args.getString(ARG_DATE)?.let(LocalDate::parse) ?: date
        time = args.getString(ARG_TIME)?.let(LocalTime::parse)
        val ids = args.getLongArray(ARG_SUBJECT_IDS) ?: LongArray(0)
        val names = args.getStringArrayList(ARG_SUBJECT_NAMES) ?: arrayListOf()
        subjects = ids.mapIndexed { index, id -> Subject(id = id, name = names[index]) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = BottomSheetExamBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.header.setText(if (examId == 0L) R.string.add_exam else R.string.edit_exam)
        val labels = listOf(getString(R.string.no_subject)) + subjects.map(Subject::name)
        binding.subject.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, labels
        )
        binding.subject.setSelection(
            subjects.indexOfFirst { it.id == requireArguments().getLong(ARG_SUBJECT_ID) } + 1
        )
        binding.room.setText(requireArguments().getString(ARG_ROOM))
        binding.seat.setText(requireArguments().getString(ARG_SEAT))
        binding.notes.setText(requireArguments().getString(ARG_NOTES))
        render()
        binding.date.setOnClickListener {
            DatePickerDialog(requireContext(), { _, year, month, day ->
                date = LocalDate.of(year, month + 1, day)
                render()
            }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }
        binding.time.setOnClickListener {
            val value = time ?: LocalTime.of(9, 0)
            TimePickerDialog(requireContext(), { _, hour, minute ->
                time = LocalTime.of(hour, minute)
                render()
            }, value.hour, value.minute, true).show()
        }
        binding.save.setOnClickListener { save() }
        binding.delete.visibility = if (examId == 0L) View.GONE else View.VISIBLE
        binding.delete.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.delete_exam_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    setFragmentResult(REQUEST, Bundle().apply {
                        putLong(RESULT_ID, examId)
                        putBoolean(RESULT_DELETE, true)
                    })
                    dismiss()
                }.show()
        }
    }

    private fun render() {
        binding.date.text = date.toString()
        binding.time.text = time?.toString() ?: getString(R.string.exam_time)
    }

    private fun save() {
        val selected = subjects.getOrNull(binding.subject.selectedItemPosition - 1)
        setFragmentResult(REQUEST, Bundle().apply {
            putLong(RESULT_ID, examId)
            putLong(RESULT_SUBJECT_ID, selected?.id ?: 0L)
            putString(RESULT_DATE, date.toString())
            putString(RESULT_TIME, time?.toString())
            putString(RESULT_ROOM, binding.room.text?.toString())
            putString(RESULT_SEAT, binding.seat.text?.toString())
            putString(RESULT_NOTES, binding.notes.text?.toString())
        })
        dismiss()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val REQUEST = "exam_editor"
        const val RESULT_ID = "id"
        const val RESULT_DELETE = "delete"
        const val RESULT_SUBJECT_ID = "subject_id"
        const val RESULT_DATE = "date"
        const val RESULT_TIME = "time"
        const val RESULT_ROOM = "room"
        const val RESULT_SEAT = "seat"
        const val RESULT_NOTES = "notes"
        private const val ARG_ID = "id"
        private const val ARG_SUBJECT_ID = "subject_id"
        private const val ARG_DATE = "date"
        private const val ARG_TIME = "time"
        private const val ARG_ROOM = "room"
        private const val ARG_SEAT = "seat"
        private const val ARG_NOTES = "notes"
        private const val ARG_SUBJECT_IDS = "subject_ids"
        private const val ARG_SUBJECT_NAMES = "subject_names"

        fun new(exam: Exam?, subjects: List<Subject>) = ExamEditorBottomSheet().apply {
            arguments = Bundle().apply {
                putLong(ARG_ID, exam?.id ?: 0L)
                putLong(ARG_SUBJECT_ID, exam?.subjectId ?: 0L)
                putString(ARG_DATE, exam?.date?.toString())
                putString(ARG_TIME, exam?.startTime?.toString())
                putString(ARG_ROOM, exam?.room)
                putString(ARG_SEAT, exam?.seat)
                putString(ARG_NOTES, exam?.notes)
                putLongArray(ARG_SUBJECT_IDS, subjects.map(Subject::id).toLongArray())
                putStringArrayList(ARG_SUBJECT_NAMES, ArrayList(subjects.map(Subject::name)))
            }
        }
    }
}
