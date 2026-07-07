package com.tvremote.app.features.fullonboard.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.viewpager2.widget.ViewPager2
import com.tvremote.app.R

class BeforeAfterCompareView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val beforeImage: ImageView
    private val afterImage: ImageView
    private val afterClipContainer: FrameLayout
    private val dividerTouchTarget: View
    private val clipRect = Rect()

    private var splitRatio = CENTER_RATIO
    private var touchTargetHalfWidth = 0f
    private var minSplitPx = 0f
    private var maxSplitPx = 0f
    private var isUserDragging = false
    private var hintAnimatorSet: AnimatorSet? = null

    var onCompareTouchChanged: ((Boolean) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_before_after_compare, this, true)
        beforeImage = findViewById(R.id.ivBefore)
        afterImage = findViewById(R.id.ivAfter)
        afterClipContainer = findViewById(R.id.afterClipContainer)
        dividerTouchTarget = findViewById(R.id.dividerTouchTarget)
        isClickable = false
        isFocusable = false
        clipChildren = true
        setupDividerTouch()
    }

    fun setImages(beforeRes: Int, afterRes: Int) {
        beforeImage.setImageResource(beforeRes)
        afterImage.setImageResource(afterRes)
    }

    fun startHintAnimation() {
        if (isUserDragging) return
        if (width <= 0 || height <= 0) {
            post { startHintAnimation() }
            return
        }
        stopHintAnimation()

        val center = width * CENTER_RATIO
        val forward = width * FORWARD_RATIO
        val backward = width * BACKWARD_RATIO

        hintAnimatorSet = AnimatorSet().apply {
            playSequentially(
                createSplitAnimator(center, forward, HINT_FORWARD_DURATION_MS),
                createSplitAnimator(forward, backward, HINT_BACKWARD_DURATION_MS),
                createSplitAnimator(backward, center, HINT_RETURN_DURATION_MS),
            )
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    hintAnimatorSet = null
                }
            })
            start()
        }
    }

    fun stopHintAnimation() {
        hintAnimatorSet?.removeAllListeners()
        hintAnimatorSet?.cancel()
        hintAnimatorSet = null
    }

    override fun onDetachedFromWindow() {
        stopHintAnimation()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        touchTargetHalfWidth = dividerTouchTarget.width / 2f
        if (touchTargetHalfWidth <= 0f) {
            touchTargetHalfWidth =
                resources.getDimension(R.dimen.full_onboard_compare_touch_width) / 2f
        }
        minSplitPx = width * MIN_RATIO
        maxSplitPx = width * MAX_RATIO
        if (hintAnimatorSet?.isRunning != true) {
            applySplitPosition(width * splitRatio)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDividerTouch() {
        dividerTouchTarget.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isUserDragging = true
                    stopHintAnimation()
                    setCompareTouching(true)
                    disallowPagerIntercept(true)
                    afterClipContainer.setLayerType(LAYER_TYPE_HARDWARE, null)
                    updateSplitFromRawX(event.rawX)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    updateSplitFromRawX(event.rawX)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isUserDragging = false
                    setCompareTouching(false)
                    disallowPagerIntercept(false)
                    afterClipContainer.setLayerType(LAYER_TYPE_NONE, null)
                    true
                }

                else -> false
            }
        }
    }

    private fun createSplitAnimator(fromX: Float, toX: Float, durationMs: Long): ValueAnimator {
        return ValueAnimator.ofFloat(fromX, toX).apply {
            duration = durationMs
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                applySplitPosition(animator.animatedValue as Float)
            }
        }
    }

    private fun updateSplitFromRawX(rawX: Float) {
        val location = IntArray(2)
        getLocationOnScreen(location)
        applySplitPosition(rawX - location[0])
    }

    private fun applySplitPosition(splitX: Float) {
        if (width <= 0 || height <= 0) return

        val clampedX = splitX.coerceIn(minSplitPx, maxSplitPx)
        splitRatio = clampedX / width

        clipRect.set(0, 0, clampedX.toInt(), height)
        afterClipContainer.clipBounds = clipRect
        dividerTouchTarget.translationX = clampedX - touchTargetHalfWidth
        afterClipContainer.invalidate()
    }

    private fun setCompareTouching(touching: Boolean) {
        onCompareTouchChanged?.invoke(touching)
    }

    private fun disallowPagerIntercept(disallow: Boolean) {
        var parentView = parent
        while (parentView != null) {
            parentView.requestDisallowInterceptTouchEvent(disallow)
            if (parentView is ViewPager2) break
            parentView = parentView.parent
        }
    }

    companion object {
        private const val CENTER_RATIO = 0.5f
        private const val FORWARD_RATIO = 0.72f
        private const val BACKWARD_RATIO = 0.28f
        private const val MIN_RATIO = 0.08f
        private const val MAX_RATIO = 0.92f

        private const val HINT_FORWARD_DURATION_MS = 700L
        private const val HINT_BACKWARD_DURATION_MS = 900L
        private const val HINT_RETURN_DURATION_MS = 700L
    }
}
