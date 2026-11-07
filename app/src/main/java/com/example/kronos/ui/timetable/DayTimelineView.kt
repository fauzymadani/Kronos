package com.example.kronos.ui.timetable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ScrollView
import androidx.core.graphics.ColorUtils
import com.example.kronos.R
import com.example.kronos.data.local.ClassSessionWithSubject
import com.example.kronos.data.local.ExamWithSubject
import com.example.kronos.data.SettingsStore
import com.example.kronos.lib.calculateDayTimelineLayout
import com.example.kronos.lib.contrastingTextColor
import com.example.kronos.lib.initialScrollMinutes
import com.example.kronos.lib.parseUserColor
import com.example.kronos.lib.formatDisplayTime
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Fallback block length for an exam that has no end time.
private const val EXAM_HOUR_MINUTES = 60

class DayTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var onClassClick: ((ClassSessionWithSubject) -> Unit)? = null

    private var day = LocalDate.now()
    private var layout = calculateDayTimelineLayout(emptyList())
    private var pendingScrollMinute = -1
    private var scrolledDay: LocalDate? = null
    private val rects = ArrayList<RectF>(24)
    private val blocks = ArrayList<ClassSessionWithSubject>(24)
    private var dayExams: List<ExamWithSubject> = emptyList()
    private val examRects = ArrayList<RectF>(8)
    private val examTitles = ArrayList<String>(8)
    private val examDetails = ArrayList<String>(8)
    private val examColors = ArrayList<Int>(8)
    private val blockText = ArrayList<String>(24)
    private val roomText = ArrayList<String>(24)
    private val blockColors = ArrayList<Int>(24)
    private val textColors = ArrayList<Int>(24)
    private val labelText = ArrayList<String>(24)
    private var currentLineMinute = -1
    private var timeFormat = SettingsStore(context).timeFormat

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val clipBounds = Rect()
    private var downX = 0f
    private var downY = 0f
    private val timeWidth = dp(58f)
    private val horizontalPadding = dp(8f)
    private val hourHeight = resources.getDimension(R.dimen.hour_height)
    private val minuteHeight = hourHeight / 60f
    private val blockGap = dp(3f)
    private val cornerRadius = dp(6f)
    private val lineColor = context.getColor(R.color.grid_line)
    private val textColor = context.getColor(R.color.on_surface_variant)
    private val strongTextColor = context.getColor(R.color.on_surface)
    private val accentColor = context.getColor(R.color.primary)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        strokeWidth = dp(1f)
    }
    private val halfLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        strokeWidth = dp(1f)
        alpha = 0x66
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(12f)
        typeface = android.graphics.Typeface.DEFAULT
        color = textColor
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(12f)
        typeface = android.graphics.Typeface.DEFAULT
        color = textColor
    }
    private val boldTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(12f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        color = strongTextColor
    }
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val examFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val examStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val examTintAlpha = 0x1F
    private val currentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        strokeWidth = dp(2f)
    }
    private val currentDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
    }
    private val primaryColor = accentColor

    fun setDay(
        value: LocalDate,
        sessions: List<ClassSessionWithSubject>,
        exams: List<ExamWithSubject> = emptyList()
    ) {
        timeFormat = SettingsStore(context).timeFormat
        day = value
        dayExams = exams
        layout = calculateDayTimelineLayout(sessions)
        val now = LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute
        val isToday = day == LocalDate.now()
        currentLineMinute = if (isToday) nowMinutes else -1
        if (value != scrolledDay) {
            scrolledDay = value
            pendingScrollMinute = initialScrollMinutes(
                sessions.map { LocalTime.parse(it.startTime).toSecondOfDay() / 60 } +
                    exams.mapNotNull { it.startTime }
                        .map { LocalTime.parse(it).toSecondOfDay() / 60 },
                isToday,
                nowMinutes
            )
        }
        rebuildRenderData()
        requestLayout()
        invalidate()
        post { applyPendingScroll() }
    }

    private fun yForMinute(minute: Int): Int =
        (minute * minuteHeight).toInt().coerceAtLeast(0)

    private fun applyPendingScroll() {
        if (pendingScrollMinute < 0 || height == 0) return
        val target = yForMinute(pendingScrollMinute)
        pendingScrollMinute = -1
        (parent as? ScrollView)?.scrollTo(0, target)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val fullDayHeight = (layout.durationMinutes * minuteHeight).toInt()
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(fullDayHeight, heightMeasureSpec)
        )
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        rebuildRenderData()
        applyPendingScroll()
    }

    // Keeps the "now" line and exam countdown honest while the timeline stays on screen.
    private val minuteTicker = object : Runnable {
        override fun run() {
            refreshCurrentMinute()
            postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        postDelayed(minuteTicker, 60_000L - System.currentTimeMillis() % 60_000L)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(minuteTicker)
        super.onDetachedFromWindow()
    }

    private fun refreshCurrentMinute() {
        val now = LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute
        val line = if (day == LocalDate.now()) nowMinutes else -1
        if (line == currentLineMinute) return
        currentLineMinute = line
        rebuildRenderData()
        invalidate()
    }

    /** Heads-up such as "Starts in 1 hour"; null when the day is not today or the exam is over. */
    private fun examCountdownLabel(startMinute: Int, endMinute: Int): String? {
        if (day != LocalDate.now()) return null
        val now = LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute
        if (nowMinutes >= endMinute) return null
        if (nowMinutes >= startMinute) return context.getString(R.string.status_in_progress)
        val minutes = startMinute - nowMinutes
        return when {
            minutes <= 1 -> context.getString(R.string.exam_starts_soon)
            minutes < 60 -> context.getString(R.string.exam_starts_minutes, minutes)
            minutes <= 90 -> context.getString(R.string.exam_starts_hour)
            else -> context.getString(R.string.exam_starts_hours, (minutes / 60f).roundToInt())
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gridLeft = timeWidth + horizontalPadding
        val gridRight = width - horizontalPadding
        val hours = layout.lastMinute / 60
        canvas.getClipBounds(clipBounds)
        val firstHour = (clipBounds.top / hourHeight).toInt().coerceIn(0, hours)
        val lastHour = ceil(clipBounds.bottom / hourHeight).toInt().coerceIn(0, hours)
        var hour = firstHour
        while (hour <= lastHour) {
            val y = hour * hourHeight
            canvas.drawLine(gridLeft, y, gridRight, y, linePaint)
            if (hour < labelText.size) {
                drawTimeLabel(canvas, labelText[hour], y)
            }
            if (hour < hours) {
                val halfY = y + hourHeight / 2f
                canvas.drawLine(gridLeft, halfY, gridRight, halfY, halfLinePaint)
            }
            hour++
        }

        var index = 0
        while (index < rects.size) {
            val rect = rects[index]
            if (rect.bottom >= clipBounds.top && rect.top <= clipBounds.bottom) {
                val block = layout.blocks[index]
                blockPaint.color = blockColors[index]
                boldTextPaint.color = textColors[index]
                textPaint.color = textColors[index]
                textPaint.textAlign = Paint.Align.LEFT
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, blockPaint)
                canvas.save()
                canvas.clipRect(rect)
                val textLeft = rect.left + dp(6f)
                val baseline = rect.top + dp(16f)
                canvas.drawText(blockText[index], textLeft, baseline, boldTextPaint)
                if (roomText[index].isNotEmpty() && block.endMinute - block.startMinute >= 30) {
                    canvas.drawText(roomText[index], textLeft, baseline + dp(16f), textPaint)
                }
                canvas.restore()
            }
            index++
        }

        var examIndex = 0
        while (examIndex < examRects.size) {
            val rect = examRects[examIndex]
            if (rect.bottom >= clipBounds.top && rect.top <= clipBounds.bottom) {
                val color = examColors[examIndex]
                examFillPaint.color = ColorUtils.setAlphaComponent(color, examTintAlpha)
                examStrokePaint.color = color
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, examFillPaint)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, examStrokePaint)
                canvas.save()
                canvas.clipRect(rect)
                boldTextPaint.color = strongTextColor
                textPaint.color = textColor
                textPaint.textAlign = Paint.Align.LEFT
                val textLeft = rect.left + dp(6f)
                val baseline = rect.top + dp(16f)
                canvas.drawText(examTitles[examIndex], textLeft, baseline, boldTextPaint)
                if (rect.height() >= dp(40f) && examDetails[examIndex].isNotEmpty()) {
                    canvas.drawText(examDetails[examIndex], textLeft, baseline + dp(16f), textPaint)
                }
                canvas.restore()
            }
            examIndex++
        }

        if (currentLineMinute >= 0) {
            val y = currentLineMinute * minuteHeight
            canvas.drawLine(gridLeft, y, gridRight, y, currentPaint)
            canvas.drawCircle(gridLeft, y, dp(4f), currentDotPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (abs(event.x - downX) <= touchSlop && abs(event.y - downY) <= touchSlop) {
                    performClick()
                    var index = rects.size - 1
                    while (index >= 0) {
                        if (rects[index].contains(event.x, event.y)) {
                            onClassClick?.invoke(blocks[index])
                            break
                        }
                        index--
                    }
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun rebuildRenderData() {
        rects.clear()
        blocks.clear()
        blockText.clear()
        roomText.clear()
        blockColors.clear()
        textColors.clear()
        labelText.clear()
        val contentWidth = max(0f, width.toFloat() - timeWidth - horizontalPadding * 2)
        var index = 0
        while (index < layout.blocks.size) {
            val block = layout.blocks[index]
            val columnWidth = contentWidth / block.columnCount
            val left = timeWidth + horizontalPadding + block.column * columnWidth + blockGap
            val right = timeWidth + horizontalPadding + (block.column + 1) * columnWidth - blockGap
            val top = yForMinute(block.startMinute) + blockGap
            val bottom = max(top + dp(28f), yForMinute(block.endMinute) - blockGap)
            rects += RectF(left, top, right, bottom)
            blocks += block.session
            blockText += ""
            roomText += ""
            updateBlockText(index)
            val color = parseUserColor(block.session.subjectColor, primaryColor)
            blockColors += color
            textColors += contrastingTextColor(color)
            index++
        }
        examRects.clear()
        examTitles.clear()
        examDetails.clear()
        examColors.clear()
        // An exam that shares a slot with a class gets its own column on the right.
        val examStarts = IntArray(dayExams.size)
        val examEnds = IntArray(dayExams.size)
        val examColumns = IntArray(dayExams.size)
        val examColumnEnds = ArrayList<Int>(4)
        var examIndex = 0
        while (examIndex < dayExams.size) {
            val exam = dayExams[examIndex]
            val start = exam.startTime?.let(LocalTime::parse)
                ?.let { it.hour * 60 + it.minute } ?: 0
            examStarts[examIndex] = start
            examEnds[examIndex] = exam.endTime?.let(LocalTime::parse)
                ?.let { it.hour * 60 + it.minute }
                ?.takeIf { it > start } ?: (start + EXAM_HOUR_MINUTES)
            var column = 0
            while (column < examColumnEnds.size && examColumnEnds[column] > start) column++
            if (column == examColumnEnds.size) examColumnEnds += examEnds[examIndex]
            else examColumnEnds[column] = examEnds[examIndex]
            examColumns[examIndex] = column
            examIndex++
        }
        val examSlotCount = max(1, examColumnEnds.size)
        val contentLeft = timeWidth + horizontalPadding
        examIndex = 0
        while (examIndex < dayExams.size) {
            val exam = dayExams[examIndex]
            var collisionLeft = Float.MAX_VALUE
            var collisionRight = -1f
            var blockIndex = 0
            while (blockIndex < rects.size) {
                val block = layout.blocks[blockIndex]
                if (block.startMinute < examEnds[examIndex] && block.endMinute > examStarts[examIndex]) {
                    collisionLeft = min(collisionLeft, rects[blockIndex].left)
                    collisionRight = max(collisionRight, rects[blockIndex].right)
                }
                blockIndex++
            }
            val left: Float
            val right: Float
            if (collisionRight < 0f) {
                left = contentLeft + blockGap
                right = contentLeft + contentWidth - blockGap
            } else {
                val slot = (collisionRight - collisionLeft) / (examSlotCount + 1)
                right = collisionRight - slot * (examSlotCount - 1 - examColumns[examIndex])
                left = max(contentLeft + blockGap, right - max(slot, dp(64f)))
                blockIndex = 0
                while (blockIndex < rects.size) {
                    val block = layout.blocks[blockIndex]
                    if (block.startMinute < examEnds[examIndex] &&
                        block.endMinute > examStarts[examIndex] &&
                        rects[blockIndex].right > left
                    ) {
                        val rect = rects[blockIndex]
                        rect.right = max(rect.left + dp(16f), left - blockGap)
                        updateBlockText(blockIndex)
                    }
                    blockIndex++
                }
            }
            val top = yForMinute(examStarts[examIndex]) + blockGap
            val bottom = max(top + dp(28f), yForMinute(examEnds[examIndex]) - blockGap)
            examRects += RectF(left, top, right, bottom)
            examTitles += fitText(
                context.getString(R.string.exam_block_title, exam.subjectName ?: exam.title),
                right - left - dp(12f),
                boldTextPaint
            )
            val countdown = examCountdownLabel(examStarts[examIndex], examEnds[examIndex])
            val timeRange = exam.startTime?.let { startTime ->
                exam.endTime?.let { "$startTime-$it" } ?: startTime
            }
            examDetails += fitText(
                listOfNotNull(countdown, timeRange, exam.room).joinToString(" \u00b7 "),
                right - left - dp(12f),
                textPaint
            )
            examColors += parseUserColor(exam.subjectColor, primaryColor)
            examIndex++
        }
        val hours = layout.lastMinute / 60
        var hour = 0
        while (hour <= hours) {
            labelText += formatDisplayTime(LocalTime.of(hour % 24, 0), timeFormat)
            hour++
        }
    }

    private fun drawTimeLabel(canvas: Canvas, label: String, y: Float) {
        labelPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(label, timeWidth, y + dp(4f), labelPaint)
    }

    private fun updateBlockText(index: Int) {
        val rect = rects[index]
        val maxWidth = rect.right - rect.left - dp(12f)
        blockText[index] = fitText(blocks[index].subjectName, maxWidth, boldTextPaint)
        roomText[index] = fitText(blocks[index].room.orEmpty(), maxWidth, textPaint)
    }

    private fun fitText(value: String, maxWidth: Float, paint: Paint): String {
        if (maxWidth <= 0f || paint.measureText(value) <= maxWidth) return value
        var text = value
        while (text.length > 1 && paint.measureText(text) > maxWidth) {
            text = text.dropLast(2) + "\u2026"
        }
        return text
    }

    private fun dp(value: Float): Float = value * density
}
