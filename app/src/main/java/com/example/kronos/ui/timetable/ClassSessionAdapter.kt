package com.example.kronos.ui.timetable

import com.example.kronos.lib.parseUserColor
import com.example.kronos.R
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.kronos.data.local.ClassSessionWithSubject
import com.example.kronos.databinding.ItemClassSessionBinding
import java.time.LocalTime
import com.example.kronos.data.SettingsStore
import com.example.kronos.lib.formatDisplayTime

class ClassSessionAdapter(
    private val onClick: (ClassSessionWithSubject) -> Unit
) : ListAdapter<ClassSessionWithSubject, ClassSessionAdapter.ViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            ItemClassSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemClassSessionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ClassSessionWithSubject) {
            val format = SettingsStore(binding.root.context).timeFormat
            binding.time.text = "${formatDisplayTime(LocalTime.parse(item.startTime), format)} - " +
                formatDisplayTime(LocalTime.parse(item.endTime), format)
            binding.subject.text = item.subjectName
            binding.details.text = listOfNotNull(item.room, item.teacher, item.notes)
                .joinToString(" • ")
            binding.details.visibility =
                if (binding.details.text.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            item.subjectColor?.let {
                binding.subject.setTextColor(
                    parseUserColor(it, binding.root.context.getColor(R.color.primary))
                )
            }
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ClassSessionWithSubject>() {
        override fun areItemsTheSame(
            oldItem: ClassSessionWithSubject,
            newItem: ClassSessionWithSubject
        ) = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: ClassSessionWithSubject,
            newItem: ClassSessionWithSubject
        ) = oldItem == newItem
    }
}
