package com.example.kronos.ui.tasks

import android.content.res.ColorStateList
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.kronos.R
import com.example.kronos.data.SettingsStore
import com.example.kronos.data.local.TaskWithSubject
import com.example.kronos.databinding.ItemTaskBinding
import com.example.kronos.lib.AssignmentType
import com.example.kronos.lib.TaskStatus
import com.example.kronos.lib.assignmentLabelRes
import com.example.kronos.lib.formatDisplayTime
import com.example.kronos.lib.formatShortDate
import com.example.kronos.lib.isOverdue
import com.example.kronos.lib.parseUserColor
import com.example.kronos.lib.statusLabelRes
import com.google.android.material.color.MaterialColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class TaskAdapter(
    private val onClick: (TaskWithSubject) -> Unit,
    private val onStatusSelected: (TaskWithSubject, TaskStatus) -> Unit
) : ListAdapter<TaskWithSubject, TaskAdapter.Holder>(DIFF) {
    override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Holder(
        ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TaskWithSubject) {
            val context = binding.root.context
            val status = TaskStatus.fromCode(item.status)
            val done = status == TaskStatus.DONE
            val dueDate = item.dueDate?.let(LocalDate::parse)
            val today = LocalDate.now()
            val overdue = isOverdue(dueDate, status, today)

            binding.title.text = item.title
            binding.title.setTextColor(
                context.getColor(if (done) R.color.on_surface_variant else R.color.on_surface)
            )
            binding.details.text = details(item)

            val line = when {
                done -> completionLabel(item, dueDate)
                dueDate == null -> null
                overdue -> {
                    val days = ChronoUnit.DAYS.between(dueDate, today).toInt()
                    context.resources.getQuantityString(R.plurals.overdue_by_days, days, days)
                }
                dueDate.isEqual(today) -> context.getString(R.string.due_today)
                else -> {
                    val date = context.getString(R.string.due_on, formatShortDate(dueDate))
                    item.dueTime?.let {
                        "$date, ${formatDisplayTime(LocalTime.parse(it), SettingsStore(context).timeFormat)}"
                    } ?: date
                }
            }
            binding.due.text = line
            binding.due.visibility = if (line == null) View.GONE else View.VISIBLE
            binding.due.setTextColor(
                if (overdue) {
                    MaterialColors.getColor(
                        binding.due, com.google.android.material.R.attr.colorError
                    )
                } else {
                    context.getColor(R.color.on_surface_variant)
                }
            )

            binding.statusText.text = context.getString(statusLabelRes(status))
            binding.statusDot.backgroundTintList =
                ColorStateList.valueOf(context.getColor(statusColor(status)))
            binding.statusChip.setOnClickListener { showStatusMenu(it, item) }
            binding.priorityIndicator.setBackgroundColor(
                when (item.priority) {
                    2 -> context.getColor(R.color.priority_high)
                    1 -> context.getColor(R.color.priority_medium)
                    else -> context.getColor(R.color.priority_low)
                }
            )
            binding.priorityIndicator.alpha = if (done) 0.35f else 1f
            binding.row.setOnClickListener { onClick(item) }
        }

        /** "<Subject> · <Category> · <Individual or Group>", missing parts skipped. */
        private fun details(item: TaskWithSubject): CharSequence {
            val context = binding.root.context
            val text = SpannableStringBuilder()
            fun append(part: String?) {
                if (part.isNullOrBlank()) return
                if (text.isNotEmpty()) text.append(" · ")
                text.append(part)
            }
            append(item.subjectName)
            val categoryStart = if (text.isEmpty()) 0 else text.length + 3
            append(item.categoryName)
            val categoryEnd = text.length
            append(context.getString(assignmentLabelRes(AssignmentType.fromCode(item.assignmentType))))
            val color = item.categoryColor
            if (color != null && categoryEnd > categoryStart) {
                runCatching {
                    text.setSpan(
                        ForegroundColorSpan(
                            parseUserColor(color, context.getColor(R.color.secondary))
                        ),
                        categoryStart,
                        categoryEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            return text
        }

        private fun completionLabel(item: TaskWithSubject, dueDate: LocalDate?): String {
            val context = binding.root.context
            val completed = item.completedAt?.let {
                formatShortDate(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate())
            }
            val base = completed?.let { context.getString(R.string.done_on, it) }
                ?: context.getString(R.string.done)
            return dueDate?.let {
                base + " · " + context.getString(R.string.due_on, formatShortDate(it))
            } ?: base
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
        private fun statusColor(status: TaskStatus): Int = when (status) {
            TaskStatus.NOT_STARTED -> R.color.status_not_started
            TaskStatus.IN_PROGRESS -> R.color.status_in_progress
            TaskStatus.REVIEW -> R.color.status_review
            TaskStatus.REVISION -> R.color.status_revision
            TaskStatus.DONE -> R.color.status_done
        }

        private val DIFF = object : DiffUtil.ItemCallback<TaskWithSubject>() {
            override fun areItemsTheSame(a: TaskWithSubject, b: TaskWithSubject) = a.id == b.id
            override fun areContentsTheSame(a: TaskWithSubject, b: TaskWithSubject) = a == b
        }
    }
}
