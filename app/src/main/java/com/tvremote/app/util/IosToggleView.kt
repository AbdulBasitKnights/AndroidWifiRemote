package com.tvremote.app.util

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import com.tvremote.app.R

class IosToggleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val trackView: View
    private val thumbView: View
    private var checked = false
    private var thumbTravelPx = 0
    private var checkedChangeListener: ((IosToggleView, Boolean) -> Unit)? = null
    private var suppressListener = false

    init {
        LayoutInflater.from(context).inflate(R.layout.view_ios_toggle, this, true)
        trackView = findViewById(R.id.toggleTrack)
        thumbView = findViewById(R.id.toggleThumb)
        isClickable = true
        isFocusable = true
        setOnClickListener { toggle(animate = true) }
        post { applyCheckedState(animate = false, notifyListener = false) }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val thumbWidth = thumbView.layoutParams.width.takeIf { it > 0 } ?: thumbView.width
        val startMargin = (thumbView.layoutParams as? MarginLayoutParams)?.marginStart ?: 0
        thumbTravelPx = (width - thumbWidth - (startMargin * 2)).coerceAtLeast(0)
        applyCheckedState(animate = false, notifyListener = false)
    }

    fun setChecked(value: Boolean, animate: Boolean) {
        if (checked == value) return
        checked = value
        applyCheckedState(animate = animate, notifyListener = false)
    }

    fun toggle(animate: Boolean = true) {
        checked = !checked
        applyCheckedState(animate = animate, notifyListener = true)
    }

    fun setOnCheckedChangeListener(listener: ((IosToggleView, Boolean) -> Unit)?) {
        checkedChangeListener = listener
    }

    fun setCheckedSilently(value: Boolean, animate: Boolean = false) {
        suppressListener = true
        setChecked(value, animate)
        suppressListener = false
    }

    private fun applyCheckedState(animate: Boolean, notifyListener: Boolean) {
        trackView.setBackgroundResource(
            if (checked) R.drawable.bg_ios_toggle_track_on else R.drawable.bg_ios_toggle_track_off,
        )
        val targetX = if (checked) thumbTravelPx.toFloat() else 0f
        thumbView.animate().cancel()
        if (animate) {
            ValueAnimator.ofFloat(thumbView.translationX, targetX).apply {
                duration = 220L
                interpolator = DecelerateInterpolator()
                addUpdateListener { thumbView.translationX = it.animatedValue as Float }
                start()
            }
        } else {
            thumbView.translationX = targetX
        }
        if (notifyListener && !suppressListener) {
            checkedChangeListener?.invoke(this, checked)
        }
    }
}
