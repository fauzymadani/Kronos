package com.example.kronos.ui.timetable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.kronos.data.local.ClassSessionWithSubject
import com.example.kronos.R
import com.example.kronos.lib.contrastingTextColor
import com.example.kronos.lib.parseUserColor
import java.time.LocalTime
import java.time.LocalDate
import kotlin.math.max

class WeekGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var onClassClick: ((ClassSessionWithSubject) -> Unit)? = null
    private var sessions: List<ClassSessionWithSubject> = emptyList()
    private var holidayDates: Set<LocalDate> = emptySet()
    private val blocks = ArrayList<Pair<RectF, ClassSessionWithSubject>>(24)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.grid_line)
        strokeWidth = dp(1f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.on_surface)
        textSize = dp(12f)
    }
    private val blockTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.on_primary)
        textSize = dp(12f)
    }
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val days = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val hourHeight = dp(56f)
    private val headerHeight = dp(36f)
    private val timeWidth = dp(42f)
    private val padding = dp(8f)
    private val firstHour = 6
    private val lastHour = 22

    fun setSessions(value: List<ClassSessionWithSubject>) {
        sessions = value
        invalidate()
    }

    fun setHolidays(value: Set<LocalDate>) {
        holidayDates = value
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (headerHeight + (lastHour - firstHour) * hourHeight + padding * 2).toInt()
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        blocks.clear()
        val columnWidth = (width - timeWidth - padding * 2) / days.size
        val left = padding + timeWidth
        val top = padding + headerHeight
        days.forEachIndexed { index, day ->
            val x = left + index * columnWidth
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(day, x + columnWidth / 2f, padding + headerHeight * .7f, textPaint)
            canvas.drawLine(x, top, x, top + (lastHour - firstHour) * hourHeight, gridPaint)
        }
        canvas.drawLine(left + days.size * columnWidth, top, left + days.size * columnWidth,
            top + (lastHour - firstHour) * hourHeight, gridPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        for (hour in firstHour..lastHour) {
            val y = top + (hour - firstHour) * hourHeight
            canvas.drawText(String.format("%02d:00", hour), timeWidth, y + dp(4f), textPaint)
            canvas.drawLine(left, y, left + days.size * columnWidth, y, gridPaint)
        }
        sessions.forEach { session ->
            val dayIndex = session.dayOfWeek - 1
            if (dayIndex !in 0..6) return@forEach
            val start = LocalTime.parse(session.startTime)
            val end = LocalTime.parse(session.endTime)
            val y = top + ((start.hour + start.minute / 60f) - firstHour) * hourHeight
            val bottom = top + ((end.hour + end.minute / 60f) - firstHour) * hourHeight
            if (bottom <= top || y >= height) return@forEach
            val rect = RectF(
                left + dayIndex * columnWidth + dp(2f),
                max(top, y) + dp(2f),
                left + (dayIndex + 1) * columnWidth - dp(2f),
                minOf(bottom, height.toFloat() - dp(2f))
            )
            blockPaint.color = parseUserColor(session.subjectColor, context.getColor(R.color.primary))
            blockTextPaint.color = contrastingTextColor(blockPaint.color)
            canvas.drawRoundRect(rect, dp(4f), dp(4f), blockPaint)
            blockTextPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(session.subjectName, rect.left + dp(4f), rect.top + dp(16f), blockTextPaint)
            blocks += rect to session
        }
        val monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY)
        val holidayPaint = Paint().apply { color = context.getColor(R.color.holiday_overlay) }
        (0..6).forEach { dayIndex ->
            if (monday.plusDays(dayIndex.toLong()) in holidayDates) {
                val x = left + dayIndex * columnWidth
                canvas.drawRect(
                    x, padding, x + columnWidth,
                    top + (lastHour - firstHour) * hourHeight,
                    holidayPaint
                )
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            blocks.firstOrNull { it.first.contains(event.x, event.y) }?.second?.let {
                onClassClick?.invoke(it)
            }
        }
        return true
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
