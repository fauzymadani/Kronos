package com.example.kronos.ui.exams

import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.kronos.R
import com.example.kronos.data.TimeFormat
import com.example.kronos.data.local.ExamWithSubject
import com.example.kronos.databinding.ItemExamBinding
import com.example.kronos.databinding.ItemExamHeaderBinding
import com.example.kronos.lib.CountdownKind
import com.example.kronos.lib.ExamCountdown
import com.example.kronos.lib.ExamSection
import com.example.kronos.lib.ExamSectionGroup
import com.example.kronos.lib.examCountdown
import com.example.kronos.lib.formatDisplayDate
import com.example.kronos.lib.formatDisplayTime
import com.example.kronos.lib.parseUserColor
import java.time.LocalDate
import java.time.LocalTime

sealed interface ExamListRow {
    data class Header(val section: ExamSection, val count: Int) : ExamListRow
    data class Exam(
        val item: ExamWithSubject,
        val countdown: ExamCountdown,
        val isNext: Boolean = false
    ) : ExamListRow
}

class ExamAdapter(
    private val onClick: (ExamWithSubject) -> Unit,
    private val onHeaderClick: (ExamSection) -> Unit = {}
) : ListAdapter<ExamListRow, RecyclerView.ViewHolder>(DIFF) {

    @Suppress("NotifyDataSetChanged")
    var timeFormat: TimeFormat = TimeFormat.HOUR_24
        set(value) {
            if (field == value) return
            field = value
            // shortcut: full rebind only when the 12/24-hour setting changes
            notifyDataSetChanged()
        }

    fun submitExams(exams: List<ExamWithSubject>, today: LocalDate) =
        submitList(exams.map { row(it, today) }.markNext())

    fun submitSections(sections: List<ExamSectionGroup>, pastExpanded: Boolean, today: LocalDate) {
        val rows = ArrayList<ExamListRow>(sections.sumOf { it.exams.size } + sections.size)
        for (group in sections) {
            rows += ExamListRow.Header(group.section, group.exams.size)
            if (group.section != ExamSection.PAST || pastExpanded) {
                group.exams.forEach { rows += row(it, today) }
            }
        }
        submitList(rows.markNext())
    }

    /** Flags the soonest upcoming exam so its row can show the "Next" badge. */
    private fun List<ExamListRow>.markNext(): List<ExamListRow> {
        val index = indexOfFirst {
            it is ExamListRow.Exam && it.countdown.kind != CountdownKind.PAST
        }
        if (index < 0) return this
        val rows = toMutableList()
        rows[index] = (rows[index] as ExamListRow.Exam).copy(isNext = true)
        return rows
    }

    private fun row(exam: ExamWithSubject, today: LocalDate) =
        ExamListRow.Exam(exam, examCountdown(today, LocalDate.parse(exam.date)))

    override fun getItemViewType(position: Int) =
        if (getItem(position) is ExamListRow.Header) TYPE_HEADER else TYPE_EXAM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(ItemExamHeaderBinding.inflate(inflater, parent, false))
        } else {
            ExamHolder(ItemExamBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is ExamListRow.Header -> (holder as HeaderHolder).bind(row)
            is ExamListRow.Exam -> (holder as ExamHolder).bind(row)
        }
    }

    inner class HeaderHolder(private val binding: ItemExamHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var boundSection: ExamSection? = null

        init {
            binding.root.setOnClickListener { boundSection?.let(onHeaderClick) }
        }

        fun bind(row: ExamListRow.Header) {
            boundSection = row.section
            val context = binding.root.context
            binding.root.text = when (row.section) {
                ExamSection.TODAY -> context.getString(R.string.today)
                ExamSection.THIS_WEEK -> context.getString(R.string.this_week)
                ExamSection.LATER -> context.getString(R.string.later)
                ExamSection.PAST -> context.getString(R.string.past_section, row.count)
            }
            binding.root.isClickable = row.section == ExamSection.PAST
        }
    }

    inner class ExamHolder(private val binding: ItemExamBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private val context = binding.root.context
        private val bar = ColorDrawable()
        private val primaryColor = context.getColor(R.color.primary)
        private val onSurfaceColor = context.getColor(R.color.on_surface)
        private val secondaryColor = context.getColor(R.color.on_surface_variant)
        private var boundItem: ExamWithSubject? = null

        init {
            binding.colorBar.background = bar
            binding.nextTag.text = context.getString(R.string.exam_next)
            binding.nextTag.setTagOutline(primaryColor)
            binding.root.setOnClickListener { boundItem?.let(onClick) }
        }

        fun bind(row: ExamListRow.Exam) {
            val item = row.item
            boundItem = item
            bar.color = parseUserColor(item.subjectColor, primaryColor)
            binding.subject.text = item.subjectName ?: item.title
            binding.nextTag.isVisible = row.isNext
            when (row.countdown.kind) {
                CountdownKind.DAYS -> {
                    binding.countdownTitle.text = row.countdown.days.toString()
                    binding.countdownTitle.textSize = 20f
                    binding.countdownTitle.setTextColor(onSurfaceColor)
                    binding.countdownUnit.isVisible = true
                }
                CountdownKind.TODAY -> word(R.string.today, primaryColor)
                CountdownKind.TOMORROW -> word(R.string.countdown_tomorrow, onSurfaceColor)
                CountdownKind.PAST -> word(R.string.countdown_past, secondaryColor)
            }
            binding.examWhen.text = listOfNotNull(
                formatDisplayDate(LocalDate.parse(item.date)),
                item.startTime?.let { formatDisplayTime(LocalTime.parse(it), timeFormat) }
            ).joinToString(" · ")
            val where = listOfNotNull(
                item.room?.let { context.getString(R.string.exam_room, it) },
                item.seat?.let { context.getString(R.string.exam_seat, it) }
            ).joinToString(" · ")
            binding.examWhere.isVisible = where.isNotEmpty()
            binding.examWhere.text = where
            val note = item.notes
            binding.examNote.isVisible = !note.isNullOrEmpty()
            binding.examNote.text = note
            binding.root.alpha = if (row.countdown.kind == CountdownKind.PAST) 0.55f else 1f
        }

        private fun word(textRes: Int, color: Int) {
            binding.countdownTitle.setText(textRes)
            binding.countdownTitle.textSize = 11f
            binding.countdownTitle.setTextColor(color)
            binding.countdownUnit.isVisible = false
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_EXAM = 1

        private val DIFF = object : DiffUtil.ItemCallback<ExamListRow>() {
            override fun areItemsTheSame(a: ExamListRow, b: ExamListRow) = when {
                a is ExamListRow.Header && b is ExamListRow.Header -> a.section == b.section
                a is ExamListRow.Exam && b is ExamListRow.Exam -> a.item.id == b.item.id
                else -> false
            }

            override fun areContentsTheSame(a: ExamListRow, b: ExamListRow) = a == b
        }
    }
}
