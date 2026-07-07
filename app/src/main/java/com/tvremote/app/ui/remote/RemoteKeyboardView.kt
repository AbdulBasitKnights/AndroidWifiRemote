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

    var onTextInput: ((String) -> Unit)? = null
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
        addKeyRow("1234567890".map { textCell(it.toString()) })
        addKeyRow("qwertyuiop".map { letterCell(it) })
        addKeyRow(
            "asdfghjkl".map { letterCell(it) } +
                listOf(iconCell(Key.KEYCODE_DEL, weight = 1.4f)),
        )
        addKeyRow(
            listOf(actionCell("⇧", weight = 1.3f) { toggleShift() }) +
                "zxcvbnm".map { letterCell(it) } +
                listOf(textCell("⏎", key = Key.KEYCODE_ENTER, weight = 1.4f, textSizeSp = 12f, sendAsText = false)),
        )
        addKeyRow(
            listOf(
                textCell("@"),
                textCell("-"),
                textCell("_"),
                textCell("Space", text = " ", weight = 3.2f, textSizeSp = 12f),
                textCell("."),
                textCell("/"),
            ),
        )
        updateLetterLabels()
    }

    private fun letterCell(char: Char): KeyCell {
        return KeyCell(
            label = char.toString(),
            isLetter = true,
            sendAsText = true,
        )
    }

    private fun textCell(
        label: String,
        text: String? = null,
        key: Key? = null,
        weight: Float = 1f,
        textSizeSp: Float = 15f,
        sendAsText: Boolean = true,
    ): KeyCell = KeyCell(
        label = label,
        textValue = text ?: label,
        key = key,
        weight = weight,
        textSizeSp = textSizeSp,
        sendAsText = sendAsText,
    )

    private fun actionCell(label: String, weight: Float = 1f, onClick: () -> Unit): KeyCell {
        return KeyCell(label = label, weight = weight, onClick = onClick)
    }

    private fun iconCell(key: Key, weight: Float = 1f): KeyCell {
        return KeyCell(label = "", key = key, weight = weight, isIcon = true, sendAsText = false)
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
                cell.onClick?.invoke() ?: dispatchCellInput(cell)
            }
            if (cell.isLetter && cell.label.length == 1) {
                tag = cell.label[0]
                letterButtons.add(this)
            }
        }
    }

    private fun dispatchCellInput(cell: KeyCell) {
        if (cell.sendAsText) {
            val text = if (cell.isLetter) {
                val base = cell.label[0]
                if (shifted) base.uppercaseChar().toString() else base.lowercaseChar().toString()
            } else {
                cell.textValue
            }
            onTextInput?.invoke(text)
            return
        }
        cell.key?.let { onKeyPress?.invoke(it) }
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

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    private data class KeyCell(
        val label: String,
        val textValue: String = label,
        val key: Key? = null,
        val weight: Float = 1f,
        val textSizeSp: Float = 15f,
        val isLetter: Boolean = false,
        val isIcon: Boolean = false,
        val sendAsText: Boolean = true,
        val onClick: (() -> Unit)? = null,
    )
}
