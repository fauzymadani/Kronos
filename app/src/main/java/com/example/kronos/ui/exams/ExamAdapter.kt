package com.example.kronos.ui.exams

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.kronos.R
import com.example.kronos.data.local.ExamWithSubject
import com.example.kronos.databinding.ItemExamBinding
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ExamAdapter(private val onClick: (ExamWithSubject) -> Unit) :
    ListAdapter<ExamWithSubject, ExamAdapter.Holder>(DIFF) {
    override fun onCreateViewHolder(parent: ViewGroup, type: Int) = Holder(
        ItemExamBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemExamBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ExamWithSubject) {
            binding.subject.text = item.subjectName ?: item.title
            item.subjectColor?.let {
                runCatching { binding.subject.setTextColor(Color.parseColor(it)) }
            }
            val days = ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(item.date))
            binding.countdown.text = when (days) {
                0L -> binding.root.context.getString(R.string.today_countdown)
                1L -> binding.root.context.getString(R.string.in_one_day)
                else -> binding.root.context.getString(R.string.in_days, days)
            }
            binding.details.text = listOfNotNull(
                item.date,
                item.startTime,
                item.room?.let { "Room $it" },
                item.seat?.let { "Seat $it" }
            ).joinToString(" · ")
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ExamWithSubject>() {
            override fun areItemsTheSame(a: ExamWithSubject, b: ExamWithSubject) = a.id == b.id
            override fun areContentsTheSame(a: ExamWithSubject, b: ExamWithSubject) = a == b
        }
    }
}
