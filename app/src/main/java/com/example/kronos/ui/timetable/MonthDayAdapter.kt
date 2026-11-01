package com.example.kronos.ui.timetable

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.example.kronos.databinding.ItemMonthDayBinding
import java.time.LocalDate
import com.example.kronos.data.FirstDayOfWeek
import com.example.kronos.lib.calendarLeadingDays

data class MonthDay(
    val date: LocalDate?,
    val hasClasses: Boolean,
    val hasHoliday: Boolean,
    val hasExam: Boolean,
    val selected: Boolean
)

class MonthDayAdapter(
    private val onDayClick: (LocalDate) -> Unit
) : ListAdapter<MonthDay, MonthDayAdapter.ViewHolder>(DIFF) {
    private var firstDay = FirstDayOfWeek.MONDAY

    fun submitMonth(
        month: LocalDate,
        classDays: Set<LocalDate>,
        examDays: Set<LocalDate>,
        holidayDays: Set<LocalDate>,
        selected: LocalDate
        , configuredFirstDay: FirstDayOfWeek = firstDay
    ) {
        firstDay = configuredFirstDay
        val first = month.withDayOfMonth(1)
        val leading = calendarLeadingDays(first, firstDay)
        val total = ((leading + month.lengthOfMonth() + 6) / 7) * 7
        val days = List(total) { index ->
            val date = first.minusDays(leading.toLong()).plusDays(index.toLong())
            MonthDay(
                date.takeIf { it.month == month.month },
                date in classDays,
                date in holidayDays,
                date in examDays,
                date == selected
            )
        }
        submitList(days)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemMonthDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(private val binding: ItemMonthDayBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(day: MonthDay) {
            binding.root.isEnabled = day.date != null
            binding.dayNumber.text = day.date?.dayOfMonth?.toString().orEmpty()
            binding.dot.visibility =
                if (day.hasClasses) android.view.View.VISIBLE else android.view.View.INVISIBLE
            binding.examDot.visibility =
                if (day.hasExam) android.view.View.VISIBLE else android.view.View.INVISIBLE
            binding.holidayDot.visibility =
                if (day.hasHoliday) android.view.View.VISIBLE else android.view.View.INVISIBLE
            binding.root.isSelected = day.selected
            binding.root.setOnClickListener { day.date?.let(onDayClick) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<MonthDay>() {
            override fun areItemsTheSame(oldItem: MonthDay, newItem: MonthDay) =
                oldItem.date == newItem.date

            override fun areContentsTheSame(oldItem: MonthDay, newItem: MonthDay) =
                oldItem == newItem
        }
    }
}
