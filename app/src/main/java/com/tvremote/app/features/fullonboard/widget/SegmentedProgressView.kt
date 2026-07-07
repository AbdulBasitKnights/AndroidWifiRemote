package com.tvremote.app.features.fullonboard.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.tvremote.app.R
import com.tvremote.app.features.fullonboard.FullOnboardContent

class SegmentedProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val segmentCount = FullOnboardContent.PAGE_COUNT
    private val gapPx = resources.getDimension(R.dimen.full_onboard_progress_gap)
    private val cornerRadius = resources.getDimension(R.dimen.full_onboard_progress_radius)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.full_onboard_progress_track)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.white)
    }
    private val rect = RectF()

    var currentPage: Int = 0
        set(value) {
            field = value.coerceIn(0, segmentCount - 1)
            invalidate()
        }

    var segmentProgress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0) return

        val totalGap = gapPx * (segmentCount - 1)
        val segmentWidth = (width - totalGap) / segmentCount
        val heightF = height.toFloat()

        repeat(segmentCount) { index ->
            val left = index * (segmentWidth + gapPx)
            rect.set(left, 0f, left + segmentWidth, heightF)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, trackPaint)

            val fillFraction = when {
                index < currentPage -> 1f
                index == currentPage -> segmentProgress
                else -> 0f
            }
            if (fillFraction > 0f) {
                rect.set(left, 0f, left + segmentWidth * fillFraction, heightF)
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
            }
        }
    }
}
