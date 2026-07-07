package com.tvremote.app.ui.remote

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.tvremote.app.R
import com.tvremote.control.commands.Key
import kotlin.math.roundToInt

class RemoteKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    var onKeyPress: ((Key) -> Unit)? = null

    private var shifted = false
    private val letterButtons = mutableListOf<TextView>()

    init {
        orientation = VERTICAL
        val pad = dp(4)
        setPadding(pad, dp(8), pad, 0)
        buildKeyboard()
    }

    private fun buildKeyboard() {
        addKeyRow("1234567890".map { letterCell(it) })
        addKeyRow("qwertyuiop".map { letterCell(it) })
        addKeyRow(
            "asdfghjkl".map { letterCell(it) } +
                listOf(iconCell(Key.KEYCODE_DEL, weight = 1.4f)),
        )
        addKeyRow(
            listOf(actionCell("⇧", weight = 1.3f) { toggleShift() }) +
                "zxcvbnm".map { letterCell(it) } +
                listOf(textCell("⏎", Key.KEYCODE_ENTER, weight = 1.4f, textSizeSp = 12f)),
        )
        addKeyRow(
            listOf(
                textCell("@", Key.KEYCODE_AT),
                textCell("-", Key.KEYCODE_MINUS),
                textCell("_", Key.KEYCODE_MINUS),
                textCell("Space", Key.KEYCODE_SPACE, weight = 3.2f, textSizeSp = 12f),
                textCell(".", Key.KEYCODE_PERIOD),
                textCell("/", Key.KEYCODE_SLASH),
            ),
        )
        updateLetterLabels()
    }

    private fun letterCell(char: Char): KeyCell {
        return KeyCell(
            label = char.toString(),
            key = keyForChar(char),
            isLetter = char.isLetter(),
        )
    }

    private fun textCell(
        label: String,
        key: Key,
        weight: Float = 1f,
        textSizeSp: Float = 15f,
    ): KeyCell = KeyCell(label, key, weight, textSizeSp = textSizeSp)

    private fun actionCell(label: String, weight: Float = 1f, onClick: () -> Unit): KeyCell {
        return KeyCell(label = label, weight = weight, onClick = onClick)
    }

    private fun iconCell(key: Key, weight: Float = 1f): KeyCell {
        return KeyCell(label = "", key = key, weight = weight, isIcon = true)
    }

    private fun addKeyRow(cells: List<KeyCell>) {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(4)
            }
        }
        cells.forEach { cell ->
            row.addView(
                if (cell.isIcon) {
                    createIconKey(cell)
                } else {
                    createTextKey(cell)
                },
            )
        }
        addView(row)
    }

    private fun createTextKey(cell: KeyCell): TextView {
        return TextView(context).apply {
            text = cell.label
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setTextSize(TypedValue.COMPLEX_UNIT_SP, cell.textSizeSp)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setBackgroundResource(R.drawable.bg_remote_key_ripple)
            layoutParams = LayoutParams(0, dp(44), cell.weight).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            setOnClickListener {
                cell.onClick?.invoke() ?: cell.key?.let { onKeyPress?.invoke(it) }
            }
            if (cell.isLetter && cell.label.length == 1) {
                tag = cell.label[0]
                letterButtons.add(this)
            }
        }
    }

    private fun createIconKey(cell: KeyCell): ImageButton {
        return ImageButton(context).apply {
            setImageResource(R.drawable.ic_back)
            imageTintList = ContextCompat.getColorStateList(context, R.color.text_primary)
            setBackgroundResource(R.drawable.bg_remote_key_ripple)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = context.getString(R.string.back)
            layoutParams = LayoutParams(0, dp(44), cell.weight).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            }
            val key = cell.key ?: return@apply
            setOnClickListener { onKeyPress?.invoke(key) }
        }
    }

    private fun toggleShift() {
        shifted = !shifted
        updateLetterLabels()
    }

    private fun updateLetterLabels() {
        letterButtons.forEach { button ->
            val base = button.tag as? Char ?: return@forEach
            button.text = if (shifted) base.uppercaseChar().toString() else base.lowercaseChar().toString()
        }
    }

    private fun keyForChar(char: Char): Key {
        return when {
            char.isDigit() -> Key.valueOf("KEYCODE_$char")
            char.isLetter() -> Key.valueOf("KEYCODE_${char.uppercaseChar()}")
            else -> Key.KEYCODE_UNKNOWN
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    private data class KeyCell(
        val label: String,
        val key: Key? = null,
        val weight: Float = 1f,
        val textSizeSp: Float = 15f,
        val isLetter: Boolean = false,
        val isIcon: Boolean = false,
        val onClick: (() -> Unit)? = null,
    )
}
