package com.example.kronos.ui.tasks

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.kronos.data.local.TaskWithSubject
import com.example.kronos.databinding.ItemTaskBinding
import java.time.LocalDate
import java.time.LocalTime
import android.graphics.Paint
import com.example.kronos.R
import com.example.kronos.lib.parseUserColor
import com.example.kronos.data.SettingsStore
import com.example.kronos.lib.formatDisplayDate
import com.example.kronos.lib.formatDisplayTime

class TaskAdapter(
    private val onClick: (TaskWithSubject) -> Unit,
    private val onDoneChanged: (TaskWithSubject, Boolean) -> Unit
) : ListAdapter<TaskWithSubject, TaskAdapter.Holder>(DIFF) {
    override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Holder(
        ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: TaskWithSubject) {
            val settings = SettingsStore(binding.root.context)
            binding.title.text = item.title
            binding.title.paintFlags = if (item.done) {
                binding.title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            binding.details.text = buildString {
                item.dueDate?.let { append(formatDisplayDate(LocalDate.parse(it))) }
                item.dueTime?.let {
                    append(" · ").append(formatDisplayTime(LocalTime.parse(it), settings.timeFormat))
                }
                item.subjectName?.let { append(" · ").append(it) }
                if (item.priority > 0) append(" · ").append(PRIORITIES[item.priority])
            }
            binding.categoryChip.text = item.categoryName.orEmpty()
            binding.categoryChip.visibility = if (item.categoryName == null) android.view.View.GONE else android.view.View.VISIBLE
            binding.categoryChip.setTextColor(binding.root.context.getColor(R.color.on_surface_variant))
            item.categoryColor?.let { color ->
                runCatching {
                    val parsed = parseUserColor(color, binding.root.context.getColor(R.color.secondary))
                    binding.categoryChip.setTextColor(parsed)
                }
            }
            val overdue = !item.done && item.dueDate?.let { LocalDate.parse(it).isBefore(LocalDate.now()) } == true
            binding.root.alpha = if (item.done) 0.62f else 1f
            binding.title.setTextColor(
                if (item.done) binding.root.context.getColor(R.color.on_surface_variant)
                else if (overdue) binding.root.context.getColor(R.color.priority_high)
                else binding.root.context.getColor(R.color.on_surface)
            )
            binding.details.setTextColor(
                binding.root.context.getColor(
                    if (item.done) R.color.on_surface_variant else R.color.on_surface
                )
            )
            binding.priorityIndicator.setBackgroundColor(
                when (item.priority) {
                    2 -> binding.root.context.getColor(R.color.priority_high)
                    1 -> binding.root.context.getColor(R.color.priority_medium)
                    else -> binding.root.context.getColor(R.color.priority_low)
                }
            )
            binding.priorityIndicator.alpha = if (item.done) 0.35f else 1f
            binding.done.setOnCheckedChangeListener(null)
            binding.done.isChecked = item.done
            binding.done.setOnCheckedChangeListener { _, checked -> onDoneChanged(item, checked) }
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val PRIORITIES = listOf("Low", "Medium", "High")
        private val DIFF = object : DiffUtil.ItemCallback<TaskWithSubject>() {
            override fun areItemsTheSame(a: TaskWithSubject, b: TaskWithSubject) = a.id == b.id
            override fun areContentsTheSame(a: TaskWithSubject, b: TaskWithSubject) = a == b
        }
    }
}
