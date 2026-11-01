package com.example.kronos.ui.timetable

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
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
) : RecyclerView.Adapter<MonthDayAdapter.ViewHolder>() {
    private var days: List<MonthDay> = emptyList()
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
        days = List(total) { index ->
            val date = first.minusDays(leading.toLong()).plusDays(index.toLong())
            MonthDay(
                date.takeIf { it.month == month.month },
                date in classDays,
                date in holidayDays,
                date in examDays,
                date == selected
            )
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemMonthDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(days[position])
    override fun getItemCount() = days.size

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
}
