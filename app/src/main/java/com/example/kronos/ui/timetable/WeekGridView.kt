package com.example.kronos.ui.timetable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import com.example.kronos.data.local.ClassSessionWithSubject
import com.example.kronos.data.local.ExamWithSubject
import com.example.kronos.data.FirstDayOfWeek
import com.example.kronos.data.SettingsStore
import com.example.kronos.data.TimeFormat
import com.example.kronos.lib.dayColumnIndex
import com.example.kronos.lib.displayWeekdayLabels
import com.example.kronos.lib.formatDisplayTime
import com.example.kronos.lib.startOfDisplayedWeek
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
    private val blockRects = ArrayList<RectF>(24)
    private val blockSessions = ArrayList<ClassSessionWithSubject>(24)
    private var exams: List<ExamWithSubject> = emptyList()
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
    private val holidayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.holiday_overlay)
    }
    private val examRect = RectF()
    private val examFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val examStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val examTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.on_surface)
        textSize = dp(11f)
    }
    private val examTintAlpha = 0x1F
    private val days = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val rectPool = ArrayList<RectF>(24)
    private val hourHeight = dp(56f)
    private val headerHeight = dp(36f)
    private val minimumTimeWidth = dp(42f)
    private val padding = dp(8f)
    private val firstHour = 6
    private val lastHour = 22
    private var firstDay = SettingsStore(context).firstDayOfWeek
    private var timeFormat = SettingsStore(context).timeFormat
    private var weekdayLabels = displayWeekdayLabels(firstDay)
    private var timeLabels = emptyList<String>()

    init {
        rebuildLabels()
    }

    fun setDisplaySettings() {
        firstDay = SettingsStore(context).firstDayOfWeek
        timeFormat = SettingsStore(context).timeFormat
        rebuildLabels()
        invalidate()
    }

    private fun rebuildLabels() {
        weekdayLabels = displayWeekdayLabels(firstDay)
        timeLabels = (firstHour..lastHour).map {
            formatDisplayTime(LocalTime.of(it, 0), timeFormat)
        }
    }

    fun setSessions(value: List<ClassSessionWithSubject>) {
        sessions = value
        invalidate()
    }

    fun setHolidays(value: Set<LocalDate>) {
        holidayDates = value
        invalidate()
    }

    fun setExams(value: List<ExamWithSubject>) {
        exams = value
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
        blockRects.clear()
        blockSessions.clear()
        textPaint.textSize = dp(12f)
        val timeWidth = max(
            minimumTimeWidth,
            (firstHour..lastHour)
                .maxOf { textPaint.measureText(formatDisplayTime(LocalTime.of(it, 0), timeFormat)) }
                .plus(dp(8f))
        )
        val columnWidth = (width - timeWidth - padding * 2) / days.size
        val left = padding + timeWidth
        val top = padding + headerHeight
        weekdayLabels.forEachIndexed { index, day ->
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
            canvas.drawText(
                timeLabels[hour - firstHour],
                timeWidth,
                y + dp(4f),
                textPaint
            )
            canvas.drawLine(left, y, left + days.size * columnWidth, y, gridPaint)
        }
        sessions.forEach { session ->
            val dayIndex = dayColumnIndex(session.dayOfWeek, firstDay)
            if (dayIndex !in 0..6) return@forEach
            val start = LocalTime.parse(session.startTime)
            val end = LocalTime.parse(session.endTime)
            val y = top + ((start.hour + start.minute / 60f) - firstHour) * hourHeight
            val bottom = top + ((end.hour + end.minute / 60f) - firstHour) * hourHeight
            if (bottom <= top || y >= height) return@forEach
            val rect = rectPool.getOrNull(blockRects.size) ?: RectF().also { rectPool += it }
            rect.set(
                left + dayIndex * columnWidth + dp(2f),
                max(top, y) + dp(2f),
                left + (dayIndex + 1) * columnWidth - dp(2f),
                minOf(bottom, height.toFloat() - dp(2f))
            )
            blockPaint.color = parseUserColor(session.subjectColor, context.getColor(R.color.primary))
            blockTextPaint.color = contrastingTextColor(blockPaint.color)
            canvas.drawRoundRect(rect, dp(4f), dp(4f), blockPaint)
            blockTextPaint.textAlign = Paint.Align.LEFT
            canvas.save()
            canvas.clipRect(rect)
            drawFittedText(
                canvas,
                session.subjectName,
                rect.left + dp(4f),
                rect.right - dp(4f),
                rect.top + dp(16f)
            )
            canvas.restore()
            blockRects += rect
            blockSessions += session
        }
        val weekStart = startOfDisplayedWeek(LocalDate.now(), firstDay)
        val weekEnd = weekStart.plusDays(6)
        exams.forEach { exam ->
            val date = LocalDate.parse(exam.date)
            if (date.isBefore(weekStart) || date.isAfter(weekEnd)) return@forEach
            val dayIndex = dayColumnIndex(date.dayOfWeek.value, firstDay)
            if (dayIndex !in 0..6) return@forEach
            val start = exam.startTime?.let(LocalTime::parse)
            val y = if (start == null) top + dp(2f)
            else top + ((start.hour + start.minute / 60f) - firstHour) * hourHeight
            if (y >= height) return@forEach
            // no start time: short chip pinned to the top of the day
            val blockHeight = if (start == null) dp(28f) else hourHeight
            examRect.set(
                left + dayIndex * columnWidth + dp(2f),
                max(top, y) + dp(2f),
                left + (dayIndex + 1) * columnWidth - dp(2f),
                minOf(y + blockHeight, height.toFloat() - dp(2f))
            )
            val color = parseUserColor(exam.subjectColor, context.getColor(R.color.primary))
            examFillPaint.color = ColorUtils.setAlphaComponent(color, examTintAlpha)
            examStrokePaint.color = color
            canvas.drawRoundRect(examRect, dp(4f), dp(4f), examFillPaint)
            canvas.drawRoundRect(examRect, dp(4f), dp(4f), examStrokePaint)
            canvas.save()
            canvas.clipRect(examRect)
            drawFittedText(
                canvas,
                exam.subjectName ?: exam.title,
                examRect.left + dp(4f),
                examRect.right - dp(4f),
                examRect.top + dp(15f),
                examTextPaint
            )
            canvas.restore()
        }
        (0..6).forEach { dayIndex ->
            if (weekStart.plusDays(dayIndex.toLong()) in holidayDates) {
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
            var index = blockRects.lastIndex
            while (index >= 0) {
                if (blockRects[index].contains(event.x, event.y)) {
                    onClassClick?.invoke(blockSessions[index])
                    break
                }
                index--
            }
        }
        return true
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun drawFittedText(
        canvas: Canvas,
        value: String,
        left: Float,
        right: Float,
        baseline: Float,
        paint: Paint = blockTextPaint
    ) {
        val maxWidth = right - left
        if (maxWidth <= 0f) return
        var text = value
        while (text.length > 1 && paint.measureText(text) > maxWidth) {
            text = text.dropLast(2) + "\u2026"
        }
        canvas.drawText(text, left, baseline, paint)
    }
}
