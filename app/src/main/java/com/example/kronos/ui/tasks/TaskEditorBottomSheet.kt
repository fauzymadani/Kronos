package com.example.kronos.ui.tasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.setFragmentResult
import com.example.kronos.R
import com.example.kronos.data.local.Subject
import com.example.kronos.data.local.Task
import com.example.kronos.data.local.Category
import com.example.kronos.databinding.BottomSheetTaskBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TaskEditorBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetTaskBinding? = null
    private val binding get() = _binding!!
    private var taskId = 0L
    private var dueDate: LocalDate? = null
    private var dueTime: LocalTime? = null
    private var reminderTime: java.time.LocalDateTime? = null
    private var subjects: List<Subject> = emptyList()
    private var categories: List<Category> = emptyList()
    private lateinit var subjectAdapter: ArrayAdapter<String>
    private lateinit var categoryAdapter: ArrayAdapter<String>
    private var selectedSubjectId = 0L
    private var selectedCategoryId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        taskId = requireArguments().getLong(ARG_ID)
        dueDate = requireArguments().getString(ARG_DATE)?.let(LocalDate::parse)
        dueTime = requireArguments().getString(ARG_TIME)?.let(LocalTime::parse)
        reminderTime = requireArguments().getString(ARG_REMINDER)?.let(java.time.LocalDateTime::parse)
        val ids = requireArguments().getLongArray(ARG_SUBJECT_IDS) ?: LongArray(0)
        val names = requireArguments().getStringArrayList(ARG_SUBJECT_NAMES) ?: arrayListOf()
        subjects = ids.mapIndexed { index, id -> Subject(id = id, name = names[index]) }
        val categoryIds = requireArguments().getLongArray(ARG_CATEGORY_IDS) ?: LongArray(0)
        val categoryNames = requireArguments().getStringArrayList(ARG_CATEGORY_NAMES) ?: arrayListOf()
        val categoryColors = requireArguments().getStringArrayList(ARG_CATEGORY_COLORS) ?: arrayListOf()
        categories = categoryIds.mapIndexed { index, id ->
            Category(id = id, name = categoryNames[index], color = categoryColors.getOrNull(index))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = BottomSheetTaskBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.header.setText(if (taskId == 0L) R.string.add_task else R.string.edit_task)
        binding.title.setText(requireArguments().getString(ARG_TITLE))
        binding.notes.setText(requireArguments().getString(ARG_NOTES))
        binding.done.isChecked = requireArguments().getBoolean(ARG_DONE)
        binding.priority.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, PRIORITIES)
        binding.priority.setSelection(requireArguments().getInt(ARG_PRIORITY, 0).coerceIn(0, 2))
        selectedSubjectId = requireArguments().getLong(ARG_SUBJECT_ID)
        selectedCategoryId = requireArguments().getLong(ARG_CATEGORY_ID)
        subjectAdapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item,
            mutableListOf()
        )
        categoryAdapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item,
            mutableListOf()
        )
        binding.subject.adapter = subjectAdapter
        binding.category.adapter = categoryAdapter
        updateSubjects(subjects)
        updateCategories(categories)
        renderDates()
        binding.dueDate.setOnClickListener {
            val date = dueDate ?: LocalDate.now()
            DatePickerDialog(requireContext(), { _, year, month, day ->
                dueDate = LocalDate.of(year, month + 1, day)
                renderDates()
            }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }
        binding.dueTime.setOnClickListener {
            val time = dueTime ?: LocalTime.now()
            TimePickerDialog(requireContext(), { _, hour, minute ->
                dueTime = LocalTime.of(hour, minute)
                renderDates()
            }, time.hour, time.minute, true).show()
        }
        binding.reminderDate.setOnClickListener {
            val date = reminderTime?.toLocalDate() ?: LocalDate.now()
            DatePickerDialog(requireContext(), { _, year, month, day ->
                reminderTime = LocalDateTime.of(
                    LocalDate.of(year, month + 1, day), reminderTime?.toLocalTime() ?: LocalTime.of(9, 0)
                )
                renderDates()
            }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }
        binding.reminderTime.setOnClickListener {
            val time = reminderTime?.toLocalTime() ?: LocalTime.now()
            TimePickerDialog(requireContext(), { _, hour, minute ->
                reminderTime = LocalDateTime.of(
                    reminderTime?.toLocalDate() ?: LocalDate.now(), LocalTime.of(hour, minute)
                )
                renderDates()
            }, time.hour, time.minute, true).show()
        }
        binding.save.setOnClickListener { save() }
        binding.delete.visibility = if (taskId == 0L) View.GONE else View.VISIBLE
        binding.delete.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.delete_task_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ ->
                    setFragmentResult(REQUEST, Bundle().apply {
                        putLong(RESULT_ID, taskId); putBoolean(RESULT_DELETE, true)
                    })
                    dismiss()
                }.show()
        }
    }

    private fun renderDates() {
        binding.dueDate.text = dueDate?.toString() ?: getString(R.string.due_date)
        binding.dueTime.text = dueTime?.toString() ?: getString(R.string.due_time)
        binding.reminderDate.text = reminderTime?.toLocalDate()?.toString()
            ?: getString(R.string.reminder_date)
        binding.reminderTime.text = reminderTime?.toLocalTime()?.toString()
            ?: getString(R.string.reminder_time)
    }

    private fun save() {
        val title = binding.title.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            binding.title.error = getString(R.string.required)
            return
        }
        val selected = binding.subject.selectedItemPosition - 1
        setFragmentResult(REQUEST, Bundle().apply {
            putLong(RESULT_ID, taskId)
            putString(RESULT_TITLE, title)
            putString(RESULT_NOTES, binding.notes.text?.toString())
            putString(RESULT_DATE, dueDate?.toString())
            putString(RESULT_TIME, dueTime?.toString())
            putString(RESULT_REMINDER, reminderTime?.toString())
            putLong(RESULT_SUBJECT_ID, subjects.getOrNull(selected)?.id ?: 0L)
            putInt(RESULT_PRIORITY, binding.priority.selectedItemPosition)
            putBoolean(RESULT_DONE, binding.done.isChecked)
            val category = categories.getOrNull(binding.category.selectedItemPosition - 1)
            putLong(RESULT_CATEGORY_ID, category?.id ?: 0L)
        })
        dismiss()
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }

    fun updateSubjects(value: List<Subject>) {
        subjects = value
        if (!::subjectAdapter.isInitialized) return
        subjectAdapter.clear()
        subjectAdapter.add(getString(R.string.no_subject))
        subjectAdapter.addAll(value.map(Subject::name))
        subjectAdapter.notifyDataSetChanged()
        binding.subject.setSelection(value.indexOfFirst { it.id == selectedSubjectId } + 1)
    }

    fun updateCategories(value: List<Category>) {
        categories = value
        if (!::categoryAdapter.isInitialized) return
        categoryAdapter.clear()
        categoryAdapter.add(getString(R.string.no_category))
        categoryAdapter.addAll(value.map(Category::name))
        categoryAdapter.notifyDataSetChanged()
        binding.category.setSelection(value.indexOfFirst { it.id == selectedCategoryId } + 1)
    }

    companion object {
        const val REQUEST = "task_editor"
        const val RESULT_ID = "id"; const val RESULT_DELETE = "delete"
        const val RESULT_TITLE = "title"; const val RESULT_NOTES = "notes"
        const val RESULT_DATE = "date"; const val RESULT_TIME = "time"
        const val RESULT_REMINDER = "reminder"
        const val RESULT_SUBJECT_ID = "subject_id"; const val RESULT_PRIORITY = "priority"
        const val RESULT_DONE = "done"
        const val RESULT_CATEGORY_ID = "category_id"
        private const val ARG_ID = "id"; private const val ARG_TITLE = "title"
        private const val ARG_NOTES = "notes"; private const val ARG_DATE = "date"
        private const val ARG_TIME = "time"; private const val ARG_SUBJECT_ID = "subject_id"
        private const val ARG_REMINDER = "reminder"
        private const val ARG_PRIORITY = "priority"; private const val ARG_DONE = "done"
        private const val ARG_SUBJECT_IDS = "subject_ids"
        private const val ARG_SUBJECT_NAMES = "subject_names"
        private const val ARG_CATEGORY_ID = "category_id"
        private const val ARG_CATEGORY_IDS = "category_ids"
        private const val ARG_CATEGORY_NAMES = "category_names"
        private const val ARG_CATEGORY_COLORS = "category_colors"
        private val PRIORITIES = listOf("Low", "Medium", "High")

        fun new(task: Task? = null, subjects: List<Subject>, categories: List<Category>) = TaskEditorBottomSheet().apply {
            arguments = Bundle().apply {
                putLong(ARG_ID, task?.id ?: 0L); putString(ARG_TITLE, task?.title)
                putString(ARG_NOTES, task?.notes); putString(ARG_DATE, task?.dueDate?.toString())
                putString(ARG_TIME, task?.dueTime?.toString()); putLong(ARG_SUBJECT_ID, task?.subjectId ?: 0L)
                putString(ARG_REMINDER, task?.reminderTime?.toString())
                putInt(ARG_PRIORITY, task?.priority ?: 0); putBoolean(ARG_DONE, task?.done ?: false)
                putLong(ARG_CATEGORY_ID, task?.categoryId ?: 0L)
                putLongArray(ARG_SUBJECT_IDS, subjects.map(Subject::id).toLongArray())
                putStringArrayList(ARG_SUBJECT_NAMES, ArrayList(subjects.map(Subject::name)))
                putLongArray(ARG_CATEGORY_IDS, categories.map(Category::id).toLongArray())
                putStringArrayList(ARG_CATEGORY_NAMES, ArrayList(categories.map(Category::name)))
                putStringArrayList(ARG_CATEGORY_COLORS, ArrayList(categories.map { it.color.orEmpty() }))
            }
        }
    }
}
