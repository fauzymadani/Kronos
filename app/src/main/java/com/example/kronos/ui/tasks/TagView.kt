package com.example.kronos.ui.tasks

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.ColorUtils

/** Small rounded label: draws its own translucent background so no drawable is needed per color. */
class TagView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillBounds = RectF()
    private val strokeBounds = RectF()
    private val radius = 6 * resources.displayMetrics.density
    private val strokeWidth = resources.displayMetrics.density

    init {
        setWillNotDraw(false)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        includeFontPadding = false
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
        val horizontal = (8 * resources.displayMetrics.density).toInt()
        val vertical = (3 * resources.displayMetrics.density).toInt()
        setPadding(horizontal, vertical, horizontal, vertical)
    }

    fun setTagColors(fg: Int, bg: Int) {
        setTextColor(fg)
        fillPaint.color = bg
        strokePaint.strokeWidth = 0f
    }

    /** Outlined variant: 1dp border plus a light tint of the same color, so the pill stays visible. */
    fun setTagOutline(color: Int) {
        setTextColor(color)
        fillPaint.color = ColorUtils.setAlphaComponent(color, TAG_TINT_ALPHA)
        strokePaint.color = color
        strokePaint.strokeWidth = strokeWidth
    }

    override fun onDraw(canvas: Canvas) {
        fillBounds.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(fillBounds, radius, radius, fillPaint)
        if (strokePaint.strokeWidth > 0f) {
            val inset = strokePaint.strokeWidth / 2f
            strokeBounds.set(inset, inset, width - inset, height - inset)
            canvas.drawRoundRect(strokeBounds, radius, radius, strokePaint)
        }
        super.onDraw(canvas)
    }

    private companion object {
        /** 12% of the border color, same tint the filled tags use. */
        const val TAG_TINT_ALPHA = 0x1F
    }
}
