package com.example.kronos.ui.tasks

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.kronos.R
import com.example.kronos.data.local.TaskWithSubject
import com.example.kronos.databinding.ItemTaskBinding
import com.example.kronos.databinding.ItemTaskHeaderBinding
import com.example.kronos.lib.AssignmentType
import com.example.kronos.lib.TaskSectionKey
import com.example.kronos.lib.TaskStatus
import com.example.kronos.lib.isOverdue
import com.example.kronos.lib.parseUserColor
import com.example.kronos.lib.statusLabelRes
import com.google.android.material.color.MaterialColors
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** One row of the flat list: either a section header or a task. */
sealed interface TaskListItem {
    data class Header(
        val key: TaskSectionKey,
        val count: Int,
        val collapsible: Boolean,
        val expanded: Boolean
    ) : TaskListItem

    data class Row(
        val task: TaskWithSubject,
        val showStatus: Boolean,
        val subjectColor: String?
    ) : TaskListItem
}

class TaskAdapter(
    private val onClick: (TaskWithSubject) -> Unit,
    private val onStatusSelected: (TaskWithSubject, TaskStatus) -> Unit,
    private val onHeaderClick: () -> Unit
) : ListAdapter<TaskListItem, RecyclerView.ViewHolder>(DIFF) {

    private val locale: Locale = Locale.getDefault()
    private val weekdayLabels: List<String> =
        DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, locale).uppercase(locale) }
    private val monthLabels: List<String> =
        Month.entries.map { it.getDisplayName(TextStyle.SHORT, locale).uppercase(locale) }
    private val dayLabels: Array<String> = Array(32) { if (it == 0) "" else it.toString() }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position) is TaskListItem.Header) TYPE_HEADER else TYPE_ROW

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(ItemTaskHeaderBinding.inflate(inflater, parent, false))
        } else {
            Holder(ItemTaskBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (item is TaskListItem.Header) {
            (holder as HeaderHolder).bind(item)
        } else {
            (holder as Holder).bind(item as TaskListItem.Row)
        }
    }

    inner class HeaderHolder(private val binding: ItemTaskHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TaskListItem.Header) {
            val context = binding.root.context
            binding.headerText.text = context.getString(
                R.string.section_header,
                context.getString(sectionLabelRes(item.key)),
                item.count
            )
            binding.headerDot.setBackgroundResource(
                when (item.key) {
                    TaskSectionKey.OVERDUE -> R.drawable.dot_error
                    TaskSectionKey.TODAY -> R.drawable.dot_accent
                    else -> R.drawable.dot_neutral
                }
            )
            binding.headerText.setTextColor(
                if (item.key == TaskSectionKey.OVERDUE) {
                    errorColor(binding.headerText)
                } else {
                    context.getColor(R.color.on_surface_variant)
                }
            )
            binding.headerArrow.visibility = if (item.collapsible) View.VISIBLE else View.GONE
            if (item.collapsible) {
                binding.headerArrow.setImageResource(
                    if (item.expanded) android.R.drawable.arrow_up_float
                    else android.R.drawable.arrow_down_float
                )
                binding.root.contentDescription =
                    context.getString(if (item.expanded) R.string.hide_done else R.string.show_done)
                binding.root.setOnClickListener { onHeaderClick() }
            } else {
                binding.root.contentDescription = null
                binding.root.setOnClickListener(null)
                binding.root.isClickable = false
            }
        }
    }

    inner class Holder(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {

        /** Subject marker: one dot reused for every bind, recolored in place (no allocation). */
        private val subjectDot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            val size = (6 * binding.root.resources.displayMetrics.density).toInt()
            setSize(size, size)
        }

        init {
            binding.subjectDot.background = subjectDot
        }

        fun bind(item: TaskListItem.Row) {
            val task = item.task
            val context = binding.root.context
            val status = TaskStatus.fromCode(task.status)
            val done = status == TaskStatus.DONE
            val dueDate = task.dueDate?.let(LocalDate::parse)
            val today = LocalDate.now()
            val overdue = isOverdue(dueDate, status, today)
            val group = AssignmentType.fromCode(task.assignmentType) == AssignmentType.GROUP

            binding.lead.bind(
                when {
                    done -> TaskLeadView.State.DONE
                    dueDate == null -> TaskLeadView.State.NONE
                    overdue -> TaskLeadView.State.OVERDUE
                    dueDate.isEqual(today) -> TaskLeadView.State.TODAY
                    else -> TaskLeadView.State.NORMAL
                },
                dayLabels[dueDate?.dayOfMonth ?: 0],
                if (dueDate == null) "" else weekdayLabels[dueDate.dayOfWeek.value - 1],
                if (dueDate == null) "" else monthLabels[dueDate.monthValue - 1],
                if (done) doneLabel(context, task) else ""
            )

            binding.row.alpha = if (done) 0.55f else 1f
            binding.title.text = task.title
            binding.title.setTextColor(
                context.getColor(if (done) R.color.on_surface_variant else R.color.on_surface)
            )
            binding.subject.text = task.subjectName
            val hasSubject = !task.subjectName.isNullOrEmpty()
            binding.subject.visibility = if (hasSubject) View.VISIBLE else View.GONE
            binding.subjectDot.visibility = if (hasSubject) View.VISIBLE else View.GONE
            subjectDot.setColor(
                parseUserColor(item.subjectColor, context.getColor(R.color.on_surface_variant))
            )

            binding.typeTag.text = context.getString(
                if (group) R.string.assignment_group else R.string.assignment_individual
            )
            binding.typeTag.setTagColors(
                context.getColor(if (group) R.color.tag_group_fg else R.color.tag_individual_fg),
                context.getColor(if (group) R.color.tag_group_bg else R.color.tag_individual_bg)
            )

            val high = !done && task.priority >= PRIORITY_HIGH
            binding.highTag.visibility = if (high) View.VISIBLE else View.GONE
            if (high) {
                binding.highTag.text = context.getString(R.string.priority_label_high)
                binding.highTag.setTagColors(
                    context.getColor(R.color.tag_high_fg), context.getColor(R.color.tag_high_bg)
                )
            }

            val showStatus = item.showStatus
            binding.statusTag.visibility = if (showStatus) View.VISIBLE else View.GONE
            if (showStatus) {
                binding.statusTag.text = context.getString(statusLabelRes(status))
                binding.statusTag.setTagOutline(context.getColor(statusColor(status)))
                binding.statusTag.setOnClickListener { showStatusMenu(it, task) }
            } else {
                binding.statusTag.setOnClickListener(null)
            }

            binding.row.setOnClickListener { onClick(task) }
            binding.row.setOnLongClickListener {
                showStatusMenu(binding.row, task)
                true
            }
        }

        /** "9 NOV" from the completion date, or "" when the task carries none. */
        private fun doneLabel(context: Context, task: TaskWithSubject): String {
            val completedAt = task.completedAt ?: return ""
            val date = Instant.ofEpochMilli(completedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            return context.getString(
                R.string.lead_done_date, date.dayOfMonth, monthLabels[date.monthValue - 1]
            )
        }

        private fun showStatusMenu(anchor: View, item: TaskWithSubject) {
            val menu = PopupMenu(anchor.context, anchor)
            TaskStatus.entries.forEach { status ->
                menu.menu.add(
                    0, status.code, status.code, anchor.context.getString(statusLabelRes(status))
                )
            }
            menu.setOnMenuItemClickListener { selected ->
                onStatusSelected(item, TaskStatus.fromCode(selected.itemId))
                true
            }
            menu.show()
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ROW = 1
        private const val PRIORITY_HIGH = 2

        private fun sectionLabelRes(key: TaskSectionKey): Int = when (key) {
            TaskSectionKey.OVERDUE -> R.string.overdue
            TaskSectionKey.TODAY -> R.string.today
            TaskSectionKey.THIS_WEEK -> R.string.this_week
            TaskSectionKey.LATER -> R.string.later
            TaskSectionKey.NO_DATE -> R.string.no_due_date
            TaskSectionKey.IN_PROGRESS -> R.string.status_in_progress
            TaskSectionKey.REVISION -> R.string.status_revision
            TaskSectionKey.REVIEW -> R.string.status_review
            TaskSectionKey.NOT_STARTED -> R.string.status_not_started
            TaskSectionKey.DONE -> R.string.done
        }

        private fun statusColor(status: TaskStatus): Int = when (status) {
            TaskStatus.NOT_STARTED -> R.color.status_not_started
            TaskStatus.IN_PROGRESS -> R.color.status_in_progress
            TaskStatus.REVIEW -> R.color.status_review
            TaskStatus.REVISION -> R.color.status_revision
            TaskStatus.DONE -> R.color.status_done
        }

        private val DIFF = object : DiffUtil.ItemCallback<TaskListItem>() {
            override fun areItemsTheSame(a: TaskListItem, b: TaskListItem): Boolean = when {
                a is TaskListItem.Header && b is TaskListItem.Header -> a.key == b.key
                a is TaskListItem.Row && b is TaskListItem.Row -> a.task.id == b.task.id
                else -> false
            }

            override fun areContentsTheSame(a: TaskListItem, b: TaskListItem): Boolean = a == b
        }
    }
}

/** Theme error color, so overdue text follows Light / Dark / Pure black. */
private fun errorColor(view: View): Int =
    MaterialColors.getColor(view, com.google.android.material.R.attr.colorError)
