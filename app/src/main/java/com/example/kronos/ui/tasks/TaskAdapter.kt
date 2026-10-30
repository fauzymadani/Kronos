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
import android.graphics.drawable.GradientDrawable
import com.example.kronos.R
import com.example.kronos.lib.contrastingTextColor
import com.example.kronos.lib.parseUserColor

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
            binding.title.text = item.title
            binding.details.text = buildString {
                item.dueDate?.let { append(LocalDate.parse(it)) }
                item.dueTime?.let { append(" · ").append(LocalTime.parse(it)) }
                item.subjectName?.let { append(" · ").append(it) }
                if (item.priority > 0) append(" · ").append(PRIORITIES[item.priority])
            }
            binding.categoryChip.text = item.categoryName.orEmpty()
            binding.categoryChip.visibility = if (item.categoryName == null) android.view.View.GONE else android.view.View.VISIBLE
            item.categoryColor?.let { color ->
                runCatching {
                    val parsed = parseUserColor(color, binding.root.context.getColor(R.color.secondary))
                    binding.categoryChip.setTextColor(contrastingTextColor(parsed))
                    binding.categoryChip.background = GradientDrawable().apply {
                        setColor(parsed)
                        cornerRadius = 16f
                    }
                }
            }
            val overdue = !item.done && item.dueDate?.let { LocalDate.parse(it).isBefore(LocalDate.now()) } == true
            binding.root.alpha = if (overdue) 1f else 0.9f
            binding.title.setTextColor(
                if (overdue) binding.root.context.getColor(R.color.priority_high)
                else binding.root.context.getColor(R.color.on_surface)
            )
            binding.priorityIndicator.setBackgroundColor(
                when (item.priority) {
                    2 -> binding.root.context.getColor(R.color.priority_high)
                    1 -> binding.root.context.getColor(R.color.priority_medium)
                    else -> binding.root.context.getColor(R.color.priority_low)
                }
            )
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
