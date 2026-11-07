package com.example.kronos.ui.tasks

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.example.kronos.R
import com.google.android.material.color.MaterialColors
import kotlin.math.min

/**
 * Left block of a task row: weekday/day/month in a 52dp date box, or a check when done.
 */
class TaskLeadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class State { NORMAL, TODAY, OVERDUE, NONE, DONE }

    private val density = resources.displayMetrics.density
    private val boxWidth = 52 * density
    private val boxHeight = 56 * density
    private val boxRadius = 10 * density
    private val checkSize = (20 * density).toInt()

    private val onSurface =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
    private val onSurfaceVariant =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)
    private val error = MaterialColors.getColor(this, com.google.android.material.R.attr.colorError)
    private val todayFill = ContextCompat.getColor(context, R.color.today_accent)
    private val todayText = ContextCompat.getColor(context, R.color.today_on_accent)
    private val overdueFill = ContextCompat.getColor(context, R.color.overdue_bg)
    private val noDate = context.getString(R.string.lead_no_date)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val weekdayPaint = textPaint(10f)
    private val dayPaint = textPaint(20f).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val monthPaint = textPaint(10f)
    private val symbolPaint = textPaint(18f)
    private val checkDrawable = ContextCompat.getDrawable(context, R.drawable.ic_check)
    private val box = RectF()
    private val checkBounds = Rect()

    private var state = State.NONE
    private var day = ""
    private var weekday = ""
    private var month = ""
    private var doneLabel = ""

    private fun textPaint(sizeSp: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, sizeSp, resources.displayMetrics
        )
        textAlign = Paint.Align.CENTER
        color = onSurfaceVariant
    }

    fun bind(
        state: State,
        day: String,
        weekday: String,
        month: String,
        doneLabel: String
    ) {
        if (state == this.state && day == this.day && weekday == this.weekday &&
            month == this.month && doneLabel == this.doneLabel
        ) {
            return
        }
        this.state = state
        this.day = day
        this.weekday = weekday
        this.month = month
        this.doneLabel = doneLabel
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
            MeasureSpec.getSize(widthMeasureSpec)
        } else {
            (68 * density).toInt()
        }
        // The row is wrap_content, so AT_MOST here is the viewport: never claim more than 72dp.
        val minHeight = (72 * density).toInt()
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> min(minHeight, MeasureSpec.getSize(heightMeasureSpec))
            else -> minHeight
        }
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        val top = (height - boxHeight) / 2f
        val left = paddingLeft.toFloat()
        val centerX = left + boxWidth / 2f

        if (state == State.TODAY || state == State.OVERDUE) {
            fillPaint.color = if (state == State.TODAY) todayFill else overdueFill
            box.set(left, top, left + boxWidth, top + boxHeight)
            canvas.drawRoundRect(box, boxRadius, boxRadius, fillPaint)
        }

        val strong = when (state) {
            State.TODAY -> todayText
            State.OVERDUE -> error
            else -> onSurface
        }
        val weak = when (state) {
            State.TODAY -> todayText
            State.OVERDUE -> error
            else -> onSurfaceVariant
        }

        when (state) {
            State.NONE -> {
                symbolPaint.color = weak
                canvas.drawText(
                    noDate, centerX, baseline(top + boxHeight / 2f, symbolPaint), symbolPaint
                )
            }

            State.DONE -> {
                val centerY = top + boxHeight / 4f
                val check = checkDrawable
                if (check != null) {
                    checkBounds.set(
                        (centerX - checkSize / 2f).toInt(),
                        (centerY - checkSize / 2f).toInt(),
                        (centerX + checkSize / 2f).toInt(),
                        (centerY + checkSize / 2f).toInt()
                    )
                    check.setBounds(checkBounds)
                    check.draw(canvas)
                }
                if (doneLabel.isNotEmpty()) {
                    weekdayPaint.color = weak
                    canvas.drawText(
                        doneLabel,
                        centerX,
                        baseline(top + boxHeight * 0.78f, weekdayPaint),
                        weekdayPaint
                    )
                }
            }

            else -> {
                weekdayPaint.color = weak
                dayPaint.color = strong
                monthPaint.color = weak
                canvas.drawText(
                    weekday, centerX, baseline(top + boxHeight * 0.26f, weekdayPaint), weekdayPaint
                )
                canvas.drawText(day, centerX, baseline(top + boxHeight * 0.5f, dayPaint), dayPaint)
                canvas.drawText(
                    month, centerX, baseline(top + boxHeight * 0.76f, monthPaint), monthPaint
                )
            }
        }
    }

    /** Baseline that centers text vertically on [centerY]; no allocation. */
    private fun baseline(centerY: Float, paint: Paint): Float =
        centerY - (paint.ascent() + paint.descent()) / 2f
}
