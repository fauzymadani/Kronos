package com.example.kronos.ui.exams

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.example.kronos.R
import com.example.kronos.lib.DayCell
import com.example.kronos.lib.MAX_DOTS_PER_DAY
import com.example.kronos.lib.NO_EXAM_COLOR
import kotlin.math.min

/** One month-grid cell: day number, today ring, selected fill and exam dots. */
class ExamDayCellView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val cellHeight = dp(44f)
    private val circleRadius = dp(15f)
    private val dotRadius = dp(2f)
    private val dotGap = dp(7f)
    private val dotsBottom = dp(9f)
    private val numberOffset = dp(4f)

    private val primaryColor = context.getColor(R.color.primary)
    private val onPrimaryColor = context.getColor(R.color.on_primary)
    private val numberColor = context.getColor(R.color.on_surface)

    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics
        )
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = primaryColor
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        color = primaryColor
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var dayLabel = ""
    private var inMonth = true
    private var isToday = false
    private var isSelected = false
    private var isPast = false
    private var dotColors: List<Int> = emptyList()

    fun bind(cell: DayCell) {
        dayLabel = cell.date.dayOfMonth.toString()
        inMonth = cell.inMonth
        isToday = cell.isToday
        isSelected = cell.isSelected
        isPast = cell.isPast
        dotColors = cell.dotColors
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(heightMeasureSpec)
        } else {
            cellHeight.toInt()
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val hasDots = dotColors.isNotEmpty()
        val centerY = height / 2f - (if (hasDots) numberOffset else 0f)
        if (isSelected) {
            canvas.drawCircle(centerX, centerY, circleRadius, fillPaint)
            numberPaint.color = onPrimaryColor
            numberPaint.alpha = 255
        } else {
            if (isToday) canvas.drawCircle(centerX, centerY, circleRadius, outlinePaint)
            numberPaint.color = numberColor
            numberPaint.alpha = if (inMonth) 255 else OUT_OF_MONTH_ALPHA
        }
        val baseline = centerY - (numberPaint.ascent() + numberPaint.descent()) / 2f
        canvas.drawText(dayLabel, centerX, baseline, numberPaint)
        if (hasDots) drawDots(canvas, centerX)
    }

    private fun drawDots(canvas: Canvas, centerX: Float) {
        val count = min(dotColors.size, MAX_DOTS_PER_DAY)
        val startX = centerX - (count - 1) * dotGap / 2f
        val dotY = height - dotsBottom
        val alpha = if (isPast) PAST_DOT_ALPHA else 255
        for (index in 0 until count) {
            val color = dotColors[index]
            dotPaint.color = if (color == NO_EXAM_COLOR) primaryColor else color
            dotPaint.alpha = alpha
            canvas.drawCircle(startX + index * dotGap, dotY, dotRadius, dotPaint)
        }
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private companion object {
        const val OUT_OF_MONTH_ALPHA = 102
        const val PAST_DOT_ALPHA = 115
    }
}
