package com.example.kronos.ui.tasks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.kronos.R
import com.example.kronos.data.TaskRepository
import com.example.kronos.data.local.Category
import com.example.kronos.data.local.DatabaseProvider
import com.example.kronos.data.local.Task
import com.example.kronos.data.local.TaskWithSubject
import com.example.kronos.databinding.FragmentTasksBinding
import com.example.kronos.notifications.TaskExamReminderScheduler
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import androidx.core.graphics.toColorInt
import java.time.LocalDate
import java.time.LocalTime

class TasksFragment : Fragment() {
    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TaskViewModel by viewModels {
        val database = DatabaseProvider.get(requireContext())
        TaskViewModel.Factory(
            TaskRepository(database.taskDao(), database.subjectDao(), database.categoryDao()),
            TaskExamReminderScheduler(requireContext())
        )
    }
    private val adapter = TaskAdapter(::editTask) { task, done ->
        viewModel.setDone(task.id, done)
    }
    private var filter = Filter.ALL
    private var sort = Sort.DUE_DATE
    private var taskEditor: TaskEditorBottomSheet? = null
    private var categoriesDialog: AlertDialog? = null
    private var categoriesContainer: android.widget.LinearLayout? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        binding.taskList.layoutManager = LinearLayoutManager(requireContext())
        binding.taskList.adapter = adapter
        binding.toolbar.inflateMenu(R.menu.tasks_menu)
        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.manage_categories) {
                showCategories()
                true
            } else false
        }

        setupFilters()
        attachSwipeActions()
        binding.addTask.setOnClickListener {
            openEditor(null)
        }

        parentFragmentManager.setFragmentResultListener(
            TaskEditorBottomSheet.REQUEST,
            viewLifecycleOwner
        ) { _, result -> handleResult(result) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.tasks.collect { submitFiltered(it) } }
                launch {
                    viewModel.subjects.collect { subjects ->
                        taskEditor?.updateSubjects(subjects)
                    }
                }
                launch {
                    viewModel.categories.collect { categories ->
                        taskEditor?.updateCategories(categories)
                        rebuildCategoryList(categories)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    private fun setupFilters() {
        listOf(
            Filter.ALL to R.string.all,
            Filter.TODAY to R.string.today,
            Filter.UPCOMING to R.string.upcoming,
            Filter.OVERDUE to R.string.overdue,
            Filter.DONE to R.string.done
        ).forEach { (value, label) ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = getString(label)
                isCheckable = true
                setOnClickListener {
                    filter = value
                    submitFiltered(viewModel.tasks.value)
                }
            }
            binding.filterChips.addView(chip)
            if (value == Filter.ALL) chip.isChecked = true
        }
        binding.sortButton.setOnClickListener {
            sort = when (sort) {
                Sort.DUE_DATE -> Sort.PRIORITY
                Sort.PRIORITY -> Sort.SUBJECT
                Sort.SUBJECT -> Sort.DUE_DATE
            }
            binding.sortButton.setText(
                when (sort) {
                    Sort.DUE_DATE -> R.string.sort_due_date
                    Sort.PRIORITY -> R.string.sort_priority
                    Sort.SUBJECT -> R.string.sort_subject
                }
            )
            submitFiltered(viewModel.tasks.value)
        }
    }

    private fun attachSwipeActions() {
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val item = adapter.currentList[position]
                if (direction == ItemTouchHelper.RIGHT) {
                    viewModel.setDone(item.id, true)
                } else {
                    val task = item.toTask()
                    viewModel.delete(task)
                    Snackbar.make(binding.root, R.string.task_deleted, Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo) { viewModel.insert(task) }
                        .show()
                }
            }
        }).attachToRecyclerView(binding.taskList)
    }

    private fun submitFiltered(source: List<TaskWithSubject>) {
        val today = LocalDate.now()
        val filtered = source.asSequence()
            .filter {
                val date = it.dueDate?.let(LocalDate::parse)
                when (filter) {
                    Filter.ALL -> true
                    Filter.TODAY -> date == today
                    Filter.UPCOMING -> !it.done && (date == null || !date.isBefore(today))
                    Filter.OVERDUE -> !it.done && date?.isBefore(today) == true
                    Filter.DONE -> it.done
                }
            }
            .sortedWith(
                when (sort) {
                    Sort.DUE_DATE -> Comparator { a, b -> compareNullable(a.dueDate, b.dueDate) }
                    Sort.PRIORITY -> compareByDescending<TaskWithSubject> { it.priority }
                    Sort.SUBJECT -> Comparator { a, b -> compareNullable(a.subjectName, b.subjectName) }
                }
            )
            .toList()
        adapter.submitList(filtered)
        binding.taskList.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyStateSubtitle.text = getString(
            if (source.isEmpty()) R.string.no_tasks_yet_subtitle else R.string.no_matching_tasks_subtitle
        )
    }

    private fun compareNullable(first: String?, second: String?): Int = when {
        first == null && second == null -> 0
        first == null -> 1
        second == null -> -1
        else -> first.compareTo(second)
    }

    private fun editTask(item: TaskWithSubject) {
        viewLifecycleOwner.lifecycleScope.launch {
            openEditor(viewModel.find(item.id))
        }
    }

    private fun openEditor(task: Task?) {
        taskEditor = TaskEditorBottomSheet.new(
            task, viewModel.subjects.value, viewModel.categories.value
        )
        taskEditor?.show(parentFragmentManager, "taskEditor")
    }

    private fun handleResult(result: Bundle) {
        val id = result.getLong(TaskEditorBottomSheet.RESULT_ID)
        if (result.getBoolean(TaskEditorBottomSheet.RESULT_DELETE)) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.find(id)?.let(viewModel::delete)
            }
            return
        }
        val date = result.getString(TaskEditorBottomSheet.RESULT_DATE)?.let(LocalDate::parse)
        val time = result.getString(TaskEditorBottomSheet.RESULT_TIME)?.let(LocalTime::parse)
        val reminder = result.getString(TaskEditorBottomSheet.RESULT_REMINDER)
            ?.let(java.time.LocalDateTime::parse)
        viewModel.save(
            Task(
                id = id,
                title = result.getString(TaskEditorBottomSheet.RESULT_TITLE).orEmpty(),
                notes = result.getString(TaskEditorBottomSheet.RESULT_NOTES),
                dueDate = date,
                dueTime = time,
                subjectId = result.getLong(TaskEditorBottomSheet.RESULT_SUBJECT_ID)
                    .takeIf { it != 0L },
                categoryId = result.getLong(TaskEditorBottomSheet.RESULT_CATEGORY_ID)
                    .takeIf { it != 0L },
                priority = result.getInt(TaskEditorBottomSheet.RESULT_PRIORITY),
                done = result.getBoolean(TaskEditorBottomSheet.RESULT_DONE),
                reminderTime = reminder
            )
        )
    }

    private fun showCategories() {
        val list = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
        }
        categoriesContainer = list
        val scroll = android.widget.ScrollView(requireContext()).apply {
            isFillViewport = true
            addView(list)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                -1, (resources.displayMetrics.heightPixels * .55f).toInt()
            )
        }
        rebuildCategoryList(viewModel.categories.value)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.manage_categories)
            .setView(scroll)
            .setPositiveButton(R.string.add_category) { _, _ -> editCategory(null) }
            .create()
        categoriesDialog = dialog
        dialog.setOnDismissListener {
            categoriesDialog = null
            categoriesContainer = null
        }
        dialog.show()
    }

    private fun rebuildCategoryList(categories: List<Category>) {
        val container = categoriesContainer ?: return
        val density = resources.displayMetrics.density
        container.removeAllViews()
        if (categories.isEmpty()) {
            container.addView(android.widget.TextView(requireContext()).apply {
                text = getString(R.string.no_categories_yet)
                setPadding(24, 32, 24, 32)
            })
        } else {
            categories.forEachIndexed { index, category ->
                if (index > 0) {
                    container.addView(View(requireContext()).apply {
                        setBackgroundColor(
                            R.color.grid_line.let { requireContext().getColor(it) }
                        )
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            -1, density.toInt().coerceAtLeast(1)
                        )
                    })
                }
                container.addView(android.widget.LinearLayout(requireContext()).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    minimumHeight = (56 * density).toInt()
                    setPadding((20 * density).toInt(), 0, (16 * density).toInt(), 0)
                    setBackgroundResource(android.R.drawable.list_selector_background)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { editCategory(category) }
                    addView(android.widget.TextView(requireContext()).apply {
                        text = category.name
                        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                        layoutParams = android.widget.LinearLayout.LayoutParams(0, -2, 1f)
                    })
                    addView(android.widget.TextView(requireContext()).apply {
                        text = ">"
                        textSize = 24f
                        gravity = android.view.Gravity.CENTER
                        setTextColor(requireContext().getColor(R.color.widget_dark_secondary))
                        contentDescription = getString(R.string.edit_category)
                        layoutParams = android.widget.LinearLayout.LayoutParams((40 * density).toInt(), -1)
                    })
                })
            }
        }
    }

    private fun editCategory(category: Category?) {
        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 8, 48, 0)
        }
        val name = android.widget.EditText(requireContext()).apply {
            hint = getString(R.string.category_name)
            setText(category?.name)
        }
        val color = android.widget.EditText(requireContext()).apply {
            hint = getString(R.string.category_color)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setText(category?.color ?: "#607D8B")
        }
        val colorHelp = android.widget.TextView(requireContext()).apply {
            text = getString(R.string.category_color_help)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            setPadding(0, 4, 0, 12)
        }
        val presets = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 12)
        }
        listOf(
            R.string.color_purple to "#7E57C2",
            R.string.color_blue to "#42A5F5",
            R.string.color_green to "#66BB6A",
            R.string.color_orange to "#FFA726",
            R.string.color_red to "#EF5350"
        ).forEach { (label, value) ->
            val chip = Chip(requireContext()).apply {
                text = getString(label)
                isCheckable = false
                setOnClickListener { color.setText(value) }
            }
            presets.addView(
                chip,
                android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 4 }
            )
        }
        val presetScroll = android.widget.HorizontalScrollView(requireContext()).apply {
            isHorizontalScrollBarEnabled = false
            addView(presets)
        }
        layout.addView(name)
        layout.addView(color)
        layout.addView(colorHelp)
        layout.addView(presetScroll)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (category == null) R.string.add_category else R.string.edit_category)
            .setView(layout)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
        if (category != null) {
            dialog.setNeutralButton(R.string.delete) { _, _ -> viewModel.deleteCategory(category) }
        }
        val shown = dialog.create()
        shown.setOnShowListener {
            shown.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val value = name.text.toString().trim()
                val colorValue = color.text.toString().trim()
                val validColor = runCatching { colorValue.toColorInt() }.isSuccess
                if (value.isEmpty() || !validColor) {
                    if (value.isEmpty()) {
                        name.error = getString(R.string.required)
                    } else {
                        color.error = getString(R.string.invalid_color)
                    }
                } else {
                    val duplicate = viewModel.categories.value.any {
                        it.id != category?.id &&
                            it.name.trim().equals(value, ignoreCase = true)
                    }
                    if (duplicate) {
                        name.error = getString(R.string.duplicate_category)
                    } else {
                        viewModel.saveCategory(
                            category?.copy(name = value, color = colorValue)
                                ?: Category(name = value, color = colorValue)
                        )
                        shown.dismiss()
                    }
                }
            }
        }
        shown.show()
    }

    private fun TaskWithSubject.toTask() = Task(
        id = id,
        title = title,
        notes = notes,
        dueDate = dueDate?.let(LocalDate::parse),
        dueTime = dueTime?.let(LocalTime::parse),
        subjectId = subjectId,
        categoryId = categoryId,
        priority = priority,
        done = done
    )

    override fun onDestroyView() {
        binding.taskList.adapter = null
        taskEditor = null
        categoriesDialog?.dismiss()
        categoriesContainer = null
        _binding = null
        super.onDestroyView()
    }

    private enum class Filter { ALL, TODAY, UPCOMING, OVERDUE, DONE }
    private enum class Sort { DUE_DATE, PRIORITY, SUBJECT }
}
