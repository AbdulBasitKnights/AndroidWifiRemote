package com.tvremote.app.ui.remote

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.tvremote.app.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Canvas-drawn TV remote D-pad wheel.
 *
 * Geometry:
 * - Outer circle = 100% radius
 * - Center button = 26% of outer radius
 * - Four 90° wedges with [gapPx] separation
 * - Dividers from center edge to outer edge
 */
class NeumorphicDpadWheelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    interface OnDirectionClickListener {
        fun onUp()
        fun onDown()
        fun onLeft()
        fun onRight()
        fun onCenter()
    }

    enum class Zone {
        UP,
        RIGHT,
        DOWN,
        LEFT,
        CENTER,
    }

    var directionListener: OnDirectionClickListener? = null

    /** Legacy lambda API used by [RemoteFragment]. */
    var onUpClick: (() -> Unit)? = null
    var onDownClick: (() -> Unit)? = null
    var onLeftClick: (() -> Unit)? = null
    var onRightClick: (() -> Unit)? = null
    var onOkClick: (() -> Unit)? = null
    var onOkLongClick: (() -> Unit)? = null

    private var padBackgroundColor = ContextCompat.getColor(context, R.color.dpad_pad_background)
    private var centerColor = ContextCompat.getColor(context, R.color.dpad_center_color)
    private var centerPressedColor = ContextCompat.getColor(context, R.color.primary_dark)
    private var dividerColor = ContextCompat.getColor(context, R.color.dpad_divider)
    private var pressedColor = ContextCompat.getColor(context, R.color.dpad_pressed_color)
    private var textColor = ContextCompat.getColor(context, R.color.text_on_accent)
    private var arrowColor = ContextCompat.getColor(context, R.color.dpad_arrow_color)
    private var outlineColor = ContextCompat.getColor(context, R.color.dpad_wheel_outline)
    private var shadowColor = ContextCompat.getColor(context, R.color.dpad_wheel_shadow)

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val wedgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outerBounds = RectF()
    private val innerBounds = RectF()
    private val wedgePath = Path()
    private val arrowIconSizePx = dp(28f).toInt()

    private var centerX = 0f
    private var centerY = 0f
    private var outerRadius = 0f
    private var centerRadius = 0f
    private var gapPx = 0f
    private var wedgeGapDegrees = 0f
    private var dividerStroke = 0f
    private var outlineStroke = 0f
    private var shadowRadius = 0f
    private var shadowDx = 0f
    private var shadowDy = 0f

    private var activeZone: Zone? = null
    private var pressProgress = 0f
    private var centerPressProgress = 0f
    private var pressAnimator: ValueAnimator? = null
    private var okLongPressTriggered = false

    private val okTypeface: Typeface? = ResourcesCompat.getFont(context, R.font.inter_semibold)
    private val directionalZones = listOf(Zone.UP, Zone.RIGHT, Zone.DOWN, Zone.LEFT)

    init {
        isClickable = true
        isFocusable = true
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            defaultFocusHighlightEnabled = false
        }

        val defaultTextSize = sp(18f)
        context.theme.obtainStyledAttributes(attrs, R.styleable.NeumorphicDpadWheelView, defStyleAttr, 0).apply {
            try {
                padBackgroundColor = getColor(R.styleable.NeumorphicDpadWheelView_padBackgroundColor, padBackgroundColor)
                padBackgroundColor = getColor(R.styleable.NeumorphicDpadWheelView_dpadWheelColor, padBackgroundColor)
                padBackgroundColor = getColor(R.styleable.NeumorphicDpadWheelView_dpadRingColor, padBackgroundColor)

                centerColor = getColor(R.styleable.NeumorphicDpadWheelView_centerColor, centerColor)
                centerColor = getColor(R.styleable.NeumorphicDpadWheelView_dpadOkColor, centerColor)
                centerPressedColor = getColor(R.styleable.NeumorphicDpadWheelView_dpadOkPressedColor, centerPressedColor)

                dividerColor = getColor(R.styleable.NeumorphicDpadWheelView_dividerColor, dividerColor)
                dividerColor = getColor(R.styleable.NeumorphicDpadWheelView_dpadDividerColor, dividerColor)

                pressedColor = getColor(R.styleable.NeumorphicDpadWheelView_pressedColor, pressedColor)
                pressedColor = getColor(R.styleable.NeumorphicDpadWheelView_dpadSegmentPressedColor, pressedColor)

                textColor = getColor(R.styleable.NeumorphicDpadWheelView_textColor, textColor)
                textColor = getColor(R.styleable.NeumorphicDpadWheelView_dpadOkTextColor, textColor)

                arrowColor = getColor(R.styleable.NeumorphicDpadWheelView_arrowColor, arrowColor)
                arrowColor = getColor(R.styleable.NeumorphicDpadWheelView_dpadArrowColor, arrowColor)

                outlineColor = getColor(R.styleable.NeumorphicDpadWheelView_outlineColor, outlineColor)
                outlineColor = getColor(R.styleable.NeumorphicDpadWheelView_dpadOutlineColor, outlineColor)
                outlineColor = getColor(R.styleable.NeumorphicDpadWheelView_dpadSegmentStrokeColor, outlineColor)

                shadowColor = getColor(R.styleable.NeumorphicDpadWheelView_shadowColor, shadowColor)
                shadowColor = getColor(R.styleable.NeumorphicDpadWheelView_dpadShadowColor, shadowColor)

                textPaint.textSize = getDimension(R.styleable.NeumorphicDpadWheelView_textSize, defaultTextSize)
            } finally {
                recycle()
            }
        }

        contentDescription = context.getString(R.string.dpad_wheel)
        textPaint.typeface = okTypeface ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textAlign = Paint.Align.CENTER
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val defaultSize = dp(280f).toInt()
        val width = resolveSize(defaultSize, widthMeasureSpec)
        val height = resolveSize(defaultSize, heightMeasureSpec)
        val square = min(width, height)
        setMeasuredDimension(square, square)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h).toFloat()
        centerX = w / 2f
        centerY = h / 2f

        outerRadius = size * 0.5f - dp(2f)
        centerRadius = outerRadius * 0.26f
        gapPx = dp(2f)
        dividerStroke = dp(2f)
        outlineStroke = dp(1f)
        shadowRadius = dp(6f)
        shadowDx = dp(0f)
        shadowDy = dp(2f)

        val midRadius = (centerRadius + outerRadius) * 1.5f
        wedgeGapDegrees = Math.toDegrees((gapPx / midRadius).toDouble()).toFloat()

        shadowPaint.setShadowLayer(shadowRadius, shadowDx, shadowDy, shadowColor)
        wheelPaint.setShadowLayer(shadowRadius, shadowDx, shadowDy, shadowColor)
        setLayerType(LAYER_TYPE_SOFTWARE, null)

        dividerPaint.strokeWidth = dividerStroke
        dividerPaint.color = dividerColor
        outlinePaint.strokeWidth = outlineStroke
        outlinePaint.color = outlineColor
        outerBounds.set(centerX - outerRadius, centerY - outerRadius, centerX + outerRadius, centerY + outerRadius)
        innerBounds.set(centerX - centerRadius, centerY - centerRadius, centerX + centerRadius, centerY + centerRadius)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Shadow + 2. Outer circle
        wheelPaint.color = padBackgroundColor
        canvas.drawCircle(centerX, centerY, outerRadius, wheelPaint)

        // 3. Direction wedges
        directionalZones.forEach { zone ->
            drawWedge(canvas, zone, wedgeColor(zone))
        }

        // 4. Dividers
        drawDividers(canvas)

        // Arrow icons
        directionalZones.forEach { zone ->
            drawArrow(canvas, zone)
        }

        outlinePaint.clearShadowLayer()
        canvas.drawCircle(centerX, centerY, outerRadius, outlinePaint)

        // 6. Center circle + 7. OK text
        drawCenterButton(canvas)
    }

    private fun drawWedge(canvas: Canvas, zone: Zone, color: Int) {
        val (start, sweep) = wedgeArc(zone)
        wedgePath.reset()
        wedgePath.addArc(outerBounds, start, sweep)
        wedgePath.arcTo(innerBounds, start + sweep, -sweep)
        wedgePath.close()
        wedgePaint.color = color
        canvas.drawPath(wedgePath, wedgePaint)
    }

    private fun wedgeColor(zone: Zone): Int {
        if (activeZone != zone) return padBackgroundColor
        return blendColor(padBackgroundColor, pressedColor, pressProgress)
    }

    private fun drawDividers(canvas: Canvas) {
        val angles = floatArrayOf(45f, 135f, 225f, 315f)
        angles.forEach { angle ->
            val radians = Math.toRadians(angle.toDouble())
            val cos = cos(radians).toFloat()
            val sin = sin(radians).toFloat()
            canvas.drawLine(
                centerX + centerRadius * cos,
                centerY + centerRadius * sin,
                centerX + outerRadius * cos,
                centerY + outerRadius * sin,
                dividerPaint,
            )
        }
    }

    private fun drawArrow(canvas: Canvas, zone: Zone) {
        val drawable = arrowDrawableFor(zone) ?: return
        val centerAngle = wedgeCenterAngle(zone)
        val radians = Math.toRadians(centerAngle.toDouble())
        val midRadius = (centerRadius + outerRadius) * 0.5f
        val ax = centerX + midRadius * cos(radians).toFloat()
        val ay = centerY + midRadius * sin(radians).toFloat()
        val half = arrowIconSizePx / 2
        val left = (ax - half).toInt()
        val top = (ay - half).toInt()
        drawable.mutate().setTint(arrowColor)
        drawable.setBounds(left, top, left + arrowIconSizePx, top + arrowIconSizePx)
        drawable.draw(canvas)
    }

    private fun arrowDrawableFor(zone: Zone): Drawable? {
        val resId = when (zone) {
            Zone.UP -> R.drawable.ic_arrow_up
            Zone.DOWN -> R.drawable.ic_arrow_down
            Zone.LEFT -> R.drawable.ic_arrow_left
            Zone.RIGHT -> R.drawable.ic_arrow_right
            Zone.CENTER -> return null
        }
        return ContextCompat.getDrawable(context, resId)
    }

    private fun drawCenterButton(canvas: Canvas) {
        val color = if (activeZone == Zone.CENTER) {
            blendColor(centerColor, centerPressedColor, centerPressProgress)
        } else {
            centerColor
        }
        centerPaint.color = color
        canvas.drawCircle(centerX, centerY, centerRadius, centerPaint)

        textPaint.color = textColor
        val text = context.getString(R.string.ok)
        val textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, centerX, textY, textPaint)
    }

    private fun wedgeCenterAngle(zone: Zone): Float = when (zone) {
        Zone.UP -> 270f
        Zone.RIGHT -> 0f
        Zone.DOWN -> 90f
        Zone.LEFT -> 180f
        Zone.CENTER -> 0f
    }

    /** Canvas [drawArc] wedges — 0° = 3 o'clock, angles increase clockwise. */
    private fun wedgeArc(zone: Zone): Pair<Float, Float> {
        val (rawStart, rawSweep) = when (zone) {
            Zone.UP -> 225f to 90f
            Zone.RIGHT -> 315f to 90f
            Zone.DOWN -> 45f to 90f
            Zone.LEFT -> 135f to 90f
            Zone.CENTER -> 0f to 0f
        }
        val inset = wedgeGapDegrees
        return normalizeAngle(rawStart + inset) to (rawSweep - inset * 2f)
    }

    private fun normalizeAngle(angle: Float): Float {
        var normalized = angle % 360f
        if (normalized < 0f) normalized += 360f
        return normalized
    }

    private fun isAngleInWedge(angle: Float, zone: Zone): Boolean {
        val (start, sweep) = wedgeArc(zone)
        val normalized = normalizeAngle(angle)
        val end = normalizeAngle(start + sweep)
        return if (start <= end) {
            normalized >= start && normalized < end
        } else {
            normalized >= start || normalized < end
        }
    }

    private fun zoneAt(x: Float, y: Float): Zone? {
        val dx = x - centerX
        val dy = y - centerY
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (distance > outerRadius) return null
        if (distance <= centerRadius) return Zone.CENTER

        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        return directionalZones.firstOrNull { isAngleInWedge(angle, it) }
            ?: quadrantForAngle(angle)
    }

    private fun quadrantForAngle(angle: Float): Zone {
        val normalized = normalizeAngle(angle)
        return when {
            normalized >= 315f || normalized < 45f -> Zone.RIGHT
            normalized < 135f -> Zone.DOWN
            normalized < 225f -> Zone.LEFT
            else -> Zone.UP
        }
    }

    private fun animatePress(targetZone: Zone?) {
        pressAnimator?.cancel()
        if (targetZone == null) {
            val startWedge = pressProgress
            val startCenter = centerPressProgress
            pressAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = PRESS_ANIMATION_MS
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    val t = 1f - (animator.animatedValue as Float)
                    pressProgress = startWedge * t
                    centerPressProgress = startCenter * t
                    invalidate()
                }
                start()
            }
            return
        }

        if (targetZone == Zone.CENTER) {
            pressProgress = 0f
            pressAnimator = ValueAnimator.ofFloat(centerPressProgress, 1f).apply {
                duration = PRESS_ANIMATION_MS
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    centerPressProgress = animator.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            centerPressProgress = 0f
            pressAnimator = ValueAnimator.ofFloat(pressProgress, 1f).apply {
                duration = PRESS_ANIMATION_MS
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    pressProgress = animator.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val zone = zoneAt(event.x, event.y) ?: return false
                activeZone = zone
                okLongPressTriggered = false
                if (zone == Zone.CENTER) {
                    postDelayed(okLongPressRunnable, OK_LONG_PRESS_TIMEOUT_MS)
                }
                animatePress(zone)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val zone = zoneAt(event.x, event.y)
                if (zone == null) {
                    if (activeZone != null) {
                        activeZone = null
                        animatePress(null)
                    }
                    invalidate()
                    return true
                }
                if (zone != activeZone) {
                    activeZone = zone
                    animatePress(zone)
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(okLongPressRunnable)
                val zone = zoneAt(event.x, event.y)
                activeZone = null
                animatePress(null)
                if (zone != null) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (zone == Zone.CENTER && okLongPressTriggered) {
                        return true
                    }
                    dispatchZoneClick(zone)
                    announceZone(zone)
                    performClick()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(okLongPressRunnable)
                activeZone = null
                animatePress(null)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private val okLongPressRunnable = Runnable {
        if (activeZone == Zone.CENTER) {
            okLongPressTriggered = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onOkLongClick?.invoke()
        }
    }

    private fun dispatchZoneClick(zone: Zone) {
        when (zone) {
            Zone.UP -> {
                directionListener?.onUp()
                onUpClick?.invoke()
            }
            Zone.DOWN -> {
                directionListener?.onDown()
                onDownClick?.invoke()
            }
            Zone.LEFT -> {
                directionListener?.onLeft()
                onLeftClick?.invoke()
            }
            Zone.RIGHT -> {
                directionListener?.onRight()
                onRightClick?.invoke()
            }
            Zone.CENTER -> {
                directionListener?.onCenter()
                onOkClick?.invoke()
            }
        }
    }

    private fun announceZone(zone: Zone) {
        val label = when (zone) {
            Zone.UP -> context.getString(R.string.up)
            Zone.DOWN -> context.getString(R.string.down)
            Zone.LEFT -> context.getString(R.string.left)
            Zone.RIGHT -> context.getString(R.string.right)
            Zone.CENTER -> context.getString(R.string.ok)
        }
        announceForAccessibility(label)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.text = context.getString(R.string.dpad_wheel)
        info.isClickable = true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        pressAnimator?.cancel()
        removeCallbacks(okLongPressRunnable)
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
    }

    private fun sp(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
    }

    private fun blendColor(from: Int, to: Int, ratio: Float): Int {
        val inverse = 1f - ratio
        val a = Color.alpha(from) * inverse + Color.alpha(to) * ratio
        val r = Color.red(from) * inverse + Color.red(to) * ratio
        val g = Color.green(from) * inverse + Color.green(to) * ratio
        val b = Color.blue(from) * inverse + Color.blue(to) * ratio
        return Color.argb(a.toInt(), r.toInt(), g.toInt(), b.toInt())
    }

    private companion object {
        const val PRESS_ANIMATION_MS = 120L
        const val OK_LONG_PRESS_TIMEOUT_MS = 500L
    }
}
