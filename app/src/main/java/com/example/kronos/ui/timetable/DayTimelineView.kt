package com.example.kronos.ui.timetable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.kronos.R
import com.example.kronos.data.local.ClassSessionWithSubject
import com.example.kronos.lib.DayTimelineLayout
import com.example.kronos.lib.calculateDayTimelineLayout
import com.example.kronos.lib.contrastingTextColor
import com.example.kronos.lib.parseUserColor
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.max

class DayTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var onClassClick: ((ClassSessionWithSubject) -> Unit)? = null

    private var day = LocalDate.now()
    private var layout = calculateDayTimelineLayout(emptyList())
    private val rects = ArrayList<RectF>(24)
    private val blocks = ArrayList<ClassSessionWithSubject>(24)
    private val blockText = ArrayList<String>(24)
    private val roomText = ArrayList<String>(24)
    private val blockColors = ArrayList<Int>(24)
    private val textColors = ArrayList<Int>(24)
    private val labelText = ArrayList<String>(24)
    private var currentLineMinute = -1

    private val density = resources.displayMetrics.density
    private val timeWidth = dp(58f)
    private val horizontalPadding = dp(8f)
    private val hourHeight = dp(64f)
    private val minuteHeight = hourHeight / 60f
    private val blockGap = dp(3f)
    private val cornerRadius = dp(6f)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val halfLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(12f)
        typeface = android.graphics.Typeface.DEFAULT
    }
    private val boldTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = dp(12f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val currentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.primary)
        strokeWidth = dp(2f)
    }
    private val currentDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.primary)
    }
    private val primaryColor = resolveColor(com.google.android.material.R.attr.colorPrimary)

    init {
        setBackgroundColor(resolveColor(com.google.android.material.R.attr.colorSurface))
        linePaint.color = resolveColor(com.google.android.material.R.attr.colorOutline)
        linePaint.strokeWidth = dp(1f)
        halfLinePaint.color = resolveColor(com.google.android.material.R.attr.colorOutlineVariant)
        halfLinePaint.strokeWidth = dp(1f)
        textPaint.color = resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        boldTextPaint.color = resolveColor(com.google.android.material.R.attr.colorOnSurface)
    }

    fun setDay(value: LocalDate, sessions: List<ClassSessionWithSubject>) {
        day = value
        layout = calculateDayTimelineLayout(sessions)
        currentLineMinute = if (day == LocalDate.now()) {
            val now = LocalTime.now()
            now.hour * 60 + now.minute
        } else -1
        rebuildRenderData()
        requestLayout()
        invalidate()
    }

    fun minuteForScroll(): Int =
        if (currentLineMinute >= layout.firstMinute && currentLineMinute <= layout.lastMinute) {
            currentLineMinute
        } else {
            layout.blocks.firstOrNull()?.startMinute ?: layout.firstMinute
        }

    fun yForMinute(minute: Int): Int =
        ((minute - layout.firstMinute) * minuteHeight).toInt().coerceAtLeast(0)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (layout.durationMinutes * minuteHeight).toInt() + dp(8f).toInt()
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        rebuildRenderData()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val gridLeft = timeWidth + horizontalPadding
        val gridRight = width - horizontalPadding
        var hour = (layout.firstMinute / 60) * 60
        while (hour <= layout.lastMinute) {
            val y = yForMinute(hour).toFloat()
            canvas.drawLine(gridLeft, y, gridRight, y, linePaint)
            val labelIndex = (hour - ((layout.firstMinute / 60) * 60)) / 60
            drawTimeLabel(canvas, labelText.getOrNull(labelIndex).orEmpty(), y)
            val half = hour + 30
            if (half < layout.lastMinute) {
                val halfY = yForMinute(half).toFloat()
                canvas.drawLine(gridLeft, halfY, gridRight, halfY, halfLinePaint)
            }
            hour += 60
        }

        var index = 0
        while (index < layout.blocks.size) {
            val block = layout.blocks[index]
            val rect = rects[index]
            blockPaint.color = blockColors[index]
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, blockPaint)
            boldTextPaint.color = textColors[index]
            textPaint.color = textColors[index]
            textPaint.textAlign = Paint.Align.LEFT
            canvas.save()
            canvas.clipRect(rect)
            val textLeft = rect.left + dp(6f)
            val textRight = rect.right - dp(6f)
            val baseline = rect.top + dp(16f)
            drawFittedText(canvas, blockText[index], textLeft, textRight, baseline, boldTextPaint)
            if (roomText[index].isNotEmpty() && block.endMinute - block.startMinute >= 30) {
                drawFittedText(
                    canvas, roomText[index], textLeft, textRight, baseline + dp(16f), textPaint
                )
            }
            canvas.restore()
            index++
        }

        if (currentLineMinute >= layout.firstMinute && currentLineMinute <= layout.lastMinute) {
            val y = yForMinute(currentLineMinute).toFloat()
            canvas.drawLine(gridLeft, y, gridRight, y, currentPaint)
            canvas.drawCircle(gridLeft, y, dp(4f), currentDotPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            var index = rects.size - 1
            while (index >= 0) {
                if (rects[index].contains(event.x, event.y)) {
                    onClassClick?.invoke(blocks[index])
                    return true
                }
                index--
            }
        }
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
            blockText += block.session.subjectName
            roomText += block.session.room.orEmpty()
            val color = parseUserColor(block.session.subjectColor, primaryColor)
            blockColors += color
            textColors += contrastingTextColor(color)
            index++
        }
        var hour = (layout.firstMinute / 60) * 60
        while (hour <= layout.lastMinute) {
            labelText += formatMinute(hour)
            hour += 60
        }
    }

    private fun drawTimeLabel(canvas: Canvas, label: String, y: Float) {
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(label, timeWidth, y + dp(4f), textPaint)
    }

    private fun drawFittedText(
        canvas: Canvas,
        value: String,
        left: Float,
        right: Float,
        baseline: Float,
        paint: Paint
    ) {
        val maxWidth = right - left
        if (maxWidth <= 0f) return
        var text = value
        while (text.length > 1 && paint.measureText(text) > maxWidth) {
            text = text.dropLast(2) + "\u2026"
        }
        canvas.drawText(text, left, baseline, paint)
    }

    private fun formatMinute(minute: Int): String {
        val normalized = ((minute % (24 * 60)) + 24 * 60) % (24 * 60)
        return "%02d:%02d".format(normalized / 60, normalized % 60)
    }

    private fun resolveColor(attribute: Int): Int {
        val value = android.util.TypedValue()
        context.theme.resolveAttribute(attribute, value, true)
        return if (value.resourceId != 0) context.getColor(value.resourceId) else value.data
    }

    private fun dp(value: Float): Float = value * density
}
