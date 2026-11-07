package com.example.kronos.ui.exams

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.kronos.R
import com.example.kronos.databinding.ItemExamDayBinding
import com.example.kronos.lib.DayCell
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class ExamMonthAdapter(private val onDayClick: (LocalDate) -> Unit) :
    ListAdapter<DayCell, ExamMonthAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemExamDayBinding.inflate(LayoutInflater.from(parent.context), parent, false).root
    )

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val cell: ExamDayCellView) : RecyclerView.ViewHolder(cell) {
        private val context = cell.context
        private val dayFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())
        private var boundDate = LocalDate.MIN

        init {
            cell.setOnClickListener { onDayClick(boundDate) }
        }

        fun bind(item: DayCell) {
            boundDate = item.date
            cell.bind(item)
            val exams = if (item.dotColors.isEmpty()) {
                context.getString(R.string.exam_day_no_exams)
            } else {
                context.resources.getQuantityString(
                    R.plurals.exam_count, item.dotColors.size, item.dotColors.size
                )
            }
            cell.contentDescription = context.getString(
                R.string.exam_day_description, item.date.format(dayFormatter), exams
            )
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DayCell>() {
            override fun areItemsTheSame(a: DayCell, b: DayCell) = a.date == b.date
            override fun areContentsTheSame(a: DayCell, b: DayCell) = a == b
        }
    }
}
